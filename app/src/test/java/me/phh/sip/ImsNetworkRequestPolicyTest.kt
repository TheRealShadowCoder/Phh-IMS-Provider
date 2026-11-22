// SPDX-License-Identifier: GPL-2.0
package me.phh.sip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImsNetworkRequestPolicyTest {
    @Test
    fun cellularImsRatCanRequestNetwork() {
        assertTrue(ImsNetworkRequestPolicy.isReadyForRequest(cellularRatReady = true, wifiInternetAvailable = false))
    }

    @Test
    fun validatedWifiCanRequestIwlanBeforeModemReportsIwlan() {
        assertTrue(ImsNetworkRequestPolicy.isReadyForRequest(cellularRatReady = false, wifiInternetAvailable = true))
    }

    @Test
    fun unsupportedCellularRatWithoutWifiDefersRequest() {
        assertFalse(ImsNetworkRequestPolicy.isReadyForRequest(cellularRatReady = false, wifiInternetAvailable = false))
    }
}
