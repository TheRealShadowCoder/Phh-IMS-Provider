// SPDX-License-Identifier: GPL-2.0
package me.phh.sip

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.telephony.Rlog
import android.telephony.TelephonyManager
import android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN
import android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_LTE
import java.net.Inet6Address
import java.net.InetAddress

internal sealed class ImsNetworkEndpointResolution {
    data class Success(
        val pcscfAddr: InetAddress,
        val localAddr: InetAddress,
    ) : ImsNetworkEndpointResolution()

    object WaitingForPcscf : ImsNetworkEndpointResolution()
    object NoLocalAddress : ImsNetworkEndpointResolution()
}

internal object ImsNetworkState {
    fun registrationTechName(tech: Int): String =
        when (tech) {
            REGISTRATION_TECH_IWLAN -> "IWLAN"
            REGISTRATION_TECH_LTE -> "LTE"
            else -> "unknown($tech)"
        }

    fun detectRegistrationTech(
        connectivityManager: ConnectivityManager,
        network: Network?,
        lp: LinkProperties,
    ): Int {
        val iface = lp.interfaceName ?: ""
        if (iface.startsWith("ipsec", ignoreCase = true)) {
            return REGISTRATION_TECH_IWLAN
        }

        val caps = if (network != null) {
            try {
                connectivityManager.getNetworkCapabilities(network)
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }

        return if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            REGISTRATION_TECH_IWLAN
        } else {
            REGISTRATION_TECH_LTE
        }
    }

    fun getPcscfServers(
        lp: LinkProperties,
        ipVersionPolicy: SipIpVersionPolicy = SipIpVersionPolicy.ANY,
    ): List<InetAddress> {
        val addresses = (lp.javaClass.getMethod("getPcscfServers").invoke(lp) as List<*>)
            .filterIsInstance<InetAddress>()
            .sortedBy { if (it is Inet6Address) 0 else 1 }
        val matching = addresses.filter(ipVersionPolicy::accepts)
        return matching.ifEmpty { addresses }
    }

    fun getImsLocalAddress(
        lp: LinkProperties,
        ipVersionPolicy: SipIpVersionPolicy = SipIpVersionPolicy.ANY,
        peerAddress: InetAddress? = null,
    ): InetAddress? {
        val addresses = lp.linkAddresses
            .map { it.address }
            .filter { !it.isAnyLocalAddress && !it.isLoopbackAddress }
            .sortedBy { if (it is Inet6Address) 0 else 1 }
        val sameFamily = peerAddress?.let { peer ->
            addresses.filter { candidate ->
                (candidate is Inet6Address) == (peer is Inet6Address)
            }
        }.orEmpty()
        return sameFamily.firstOrNull(ipVersionPolicy::accepts)
            ?: addresses.firstOrNull(ipVersionPolicy::accepts)
            ?: sameFamily.firstOrNull()
            ?: addresses.firstOrNull()
    }

    fun resolveEndpoint(
        tag: String,
        lp: LinkProperties,
        mnc: String,
        mcc: String,
        preferredPcscf: InetAddress? = null,
        ipVersionPolicy: SipIpVersionPolicy = SipIpVersionPolicy.ANY,
    ): ImsNetworkEndpointResolution {
        val pcscfs = getPcscfServers(lp, ipVersionPolicy)
        val pcscf = preferredPcscf ?: if (pcscfs.isNotEmpty()) {
            pcscfs[0]
        } else {
            // RIL did not provide P-CSCF via LinkProperties. Try standard
            // 3GPP DNS discovery (TS 23.003 §13.2): resolve the well-known
            // IMS domain for this PLMN.
            val dnsFallback = try {
                InetAddress.getAllByName(
                    "ims.mnc${mnc}.mcc${mcc}.pub.3gppnetwork.org",
                ).firstOrNull(ipVersionPolicy::accepts)
            } catch (t: Throwable) {
                null
            } ?: try {
                InetAddress.getAllByName(
                    "ims.mnc${mnc}.mcc${mcc}.3gppnetwork.org",
                ).firstOrNull(ipVersionPolicy::accepts)
            } catch (t: Throwable) {
                null
            } ?: android.os.SystemProperties
                .get("persist.ims.pcscf_fallback", "")
                .takeIf { it.isNotEmpty() }
                ?.let {
                    try {
                        InetAddress.getByName(it)
                    } catch (t: Throwable) {
                        null
                    }
                }

            if (dnsFallback != null) {
                Rlog.w(tag, "No P-CSCF from RIL, using fallback: $dnsFallback")
                dnsFallback
            } else {
                Rlog.w(tag, "No P-CSCF and all fallbacks failed, waiting for onLinkPropertiesChanged")
                return ImsNetworkEndpointResolution.WaitingForPcscf
            }
        }

        val localAddr = getImsLocalAddress(lp, ipVersionPolicy, pcscf)
        if (localAddr == null) {
            Rlog.w(tag, "No usable local address on IMS link properties")
            return ImsNetworkEndpointResolution.NoLocalAddress
        }

        return ImsNetworkEndpointResolution.Success(pcscf, localAddr)
    }

    fun ratName(rat: Int): String =
        when (rat) {
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_NR -> "NR"
            TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN"
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
            TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
            TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
            TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
            TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
            TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
            TelephonyManager.NETWORK_TYPE_UNKNOWN -> "UNKNOWN"
            else -> "rat($rat)"
        }

    fun isRatReadyForImsNetworkRequest(
        tag: String,
        telephonyManager: TelephonyManager,
        connectivityManager: ConnectivityManager,
    ): Boolean {
        val dataRat = try {
            telephonyManager.dataNetworkType
        } catch (t: Throwable) {
            TelephonyManager.NETWORK_TYPE_UNKNOWN
        }

        val voiceRat = try {
            telephonyManager.voiceNetworkType
        } catch (t: Throwable) {
            TelephonyManager.NETWORK_TYPE_UNKNOWN
        }

        val wifiInternetAvailable = hasValidatedWifiInternet(connectivityManager)
        val cellularRatReady =
            dataRat == TelephonyManager.NETWORK_TYPE_LTE ||
                dataRat == TelephonyManager.NETWORK_TYPE_NR ||
                dataRat == TelephonyManager.NETWORK_TYPE_IWLAN ||
                voiceRat == TelephonyManager.NETWORK_TYPE_LTE ||
                voiceRat == TelephonyManager.NETWORK_TYPE_NR
        val ready = ImsNetworkRequestPolicy.isReadyForRequest(
            cellularRatReady = cellularRatReady,
            wifiInternetAvailable = wifiInternetAvailable,
        )

        Rlog.d(
            tag,
            "IMS network request RAT gate: data=${ratName(dataRat)} " +
                "voice=${ratName(voiceRat)} wifiInternet=$wifiInternetAvailable ready=$ready",
        )

        return ready
    }

    /**
     * Android's validation probe can fail on otherwise usable networks. IMS/ePDG
     * should be allowed to try any Wi-Fi network advertising INTERNET; actual
     * IPsec and SIP connectivity still determines whether registration succeeds.
     */
    fun hasValidatedWifiInternet(connectivityManager: ConnectivityManager): Boolean {
        val networks = try {
            connectivityManager.allNetworks
        } catch (_: Throwable) {
            emptyArray()
        }

        return networks.any { network ->
            val capabilities = try {
                connectivityManager.getNetworkCapabilities(network)
            } catch (_: Throwable) {
                null
            } ?: return@any false

            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }
}
