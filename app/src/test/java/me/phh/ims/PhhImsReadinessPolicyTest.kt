// SPDX-License-Identifier: GPL-2.0
package me.phh.ims

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhhImsReadinessPolicyTest {
    @Test
    fun cellularRegistrationCanStartIms() {
        assertTrue(
            PhhImsReadinessPolicy.isReady(
                cellularUnavailable = false,
                cellularRegistered = true,
                iwlanRegistered = false,
                wifiInternetAvailable = false,
            ),
        )
    }

    @Test
    fun iwlanRegistrationCanStartImsWhenCellularIsOutOfService() {
        assertTrue(
            PhhImsReadinessPolicy.isReady(
                cellularUnavailable = true,
                cellularRegistered = false,
                iwlanRegistered = true,
                wifiInternetAvailable = false,
            ),
        )
    }

    @Test
    fun iwlanRegistrationCanStartImsInAirplaneMode() {
        assertTrue(
            PhhImsReadinessPolicy.isReady(
                cellularUnavailable = true,
                cellularRegistered = false,
                iwlanRegistered = true,
                wifiInternetAvailable = false,
            ),
        )
    }

    @Test
    fun earlyIwlanRegistrationDoesNotStartImsWhileCellularIsInService() {
        assertFalse(
            PhhImsReadinessPolicy.isReady(
                cellularUnavailable = false,
                cellularRegistered = false,
                iwlanRegistered = true,
                wifiInternetAvailable = true,
            ),
        )
    }

    @Test
    fun missingCellularAndIwlanRegistrationDoesNotStartIms() {
        assertFalse(
            PhhImsReadinessPolicy.isReady(
                cellularUnavailable = true,
                cellularRegistered = false,
                iwlanRegistered = false,
                wifiInternetAvailable = false,
            ),
        )
    }

    @Test
    fun validatedWifiCanStartImsBeforeIwlanRegistrationWhenCellularIsUnavailable() {
        assertTrue(
            PhhImsReadinessPolicy.isReady(
                cellularUnavailable = true,
                cellularRegistered = false,
                iwlanRegistered = false,
                wifiInternetAvailable = true,
            ),
        )
    }
}
