package com.tihor.quill.reliability

import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * M12: the only battery route is the non-restricted settings LIST. The Play-restricted one-tap request
 * (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + the permission) was removed — its absence is
 * enforced by the deletion + the repo-wide grep, since a unit test can't reference a deleted method.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BatteryOptimizationTest {

    @Test
    fun settingsIntent_usesNonRestrictedListAction() {
        val intent = BatteryOptimization.settingsIntent()
        assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, intent.action)
    }

    @Test
    fun settingsIntent_actionIsNotTheRestrictedRequest() {
        // Guard against a regression that re-introduces the restricted one-tap request action.
        assertEquals(
            false,
            BatteryOptimization.settingsIntent().action == Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        )
    }
}
