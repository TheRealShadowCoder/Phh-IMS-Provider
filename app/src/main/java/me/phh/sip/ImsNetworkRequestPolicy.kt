// SPDX-License-Identifier: GPL-2.0
package me.phh.sip

/** Allows the framework IMS request to wait for a usable cellular or Wi-Fi route. */
internal object ImsNetworkRequestPolicy {
    fun isReadyForRequest(cellularRatReady: Boolean, wifiInternetAvailable: Boolean): Boolean =
        cellularRatReady || wifiInternetAvailable
}
