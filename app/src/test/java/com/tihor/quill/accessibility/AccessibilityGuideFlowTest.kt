package com.tihor.quill.accessibility

import org.junit.Assert.*
import org.junit.Test

class AccessibilityGuideFlowTest {
    @Test
    fun callbacksBeforeConsent_cannotOpenSettingsOrStartVideo() {
        val flow = AccessibilityGuideFlow()
        assertNull(flow.videoReady(resumed = true))
        assertNull(flow.pipEntered())
        assertNull(flow.unavailable())
        assertNull(flow.enabled())
        assertNull(flow.reopenSettings())
        assertFalse(flow.accepted)
    }

    @Test
    fun supportedPip_waitsForReadyAndForeground_thenOpensSettingsOnce() {
        val flow = AccessibilityGuideFlow()
        assertEquals(GuideAction.PREPARE_VIDEO, flow.accept(pipSupported = true))
        assertNull(flow.accept(pipSupported = true))
        assertNull(flow.pipEntered())
        assertNull(flow.videoReady(resumed = false))
        assertEquals(GuideAction.ENTER_PIP, flow.videoReady(resumed = true))
        assertNull(flow.videoReady(resumed = true))
        assertEquals(GuideAction.OPEN_SETTINGS, flow.pipEntered())
        assertNull(flow.pipEntered())
        assertNull(flow.videoReady(resumed = true))
    }

    @Test
    fun unsupportedPip_goesStraightToSettings_withoutVideo() {
        val flow = AccessibilityGuideFlow()
        assertEquals(GuideAction.OPEN_SETTINGS_AND_FINISH, flow.accept(pipSupported = false))
        assertNull(flow.videoReady(resumed = true))
        assertNull(flow.pipEntered())
    }

    @Test
    fun rejectedPip_orFailedVideo_fallsBackOnce_andIgnoresLateCallbacks() {
        listOf(false, true).forEach { requestPip ->
            val flow = AccessibilityGuideFlow()
            flow.accept(pipSupported = true)
            if (requestPip) flow.videoReady(resumed = true)
            assertEquals(GuideAction.OPEN_SETTINGS_AND_FINISH, flow.unavailable())
            assertNull(flow.unavailable())
            assertNull(flow.pipEntered())
            assertNull(flow.videoReady(resumed = true))
        }
    }

    @Test
    fun enablingService_closesGuide_andCannotOpenSettingsAgain() {
        listOf(false, true).forEach { alreadyInSettings ->
            val flow = AccessibilityGuideFlow()
            flow.accept(pipSupported = true)
            if (alreadyInSettings) {
                flow.videoReady(resumed = true)
                flow.pipEntered()
            }
            assertEquals(GuideAction.FINISH, flow.enabled())
            assertNull(flow.videoReady(resumed = true))
            assertNull(flow.pipEntered())
            assertNull(flow.unavailable())
            assertNull(flow.reopenSettings())
        }
    }

    @Test
    fun failureAfterOpeningSettings_closesGuide_withoutRelaunchingSettings() {
        val flow = AccessibilityGuideFlow()
        flow.accept(pipSupported = true)
        flow.videoReady(resumed = true)
        flow.pipEntered()
        assertEquals(GuideAction.FINISH, flow.unavailable())
        assertNull(flow.pipEntered())
    }

    @Test
    fun closeDuringPreparation_cancelsAllFutureLaunches() {
        val flow = AccessibilityGuideFlow()
        flow.accept(pipSupported = true)
        assertEquals(GuideAction.FINISH, flow.close())
        assertNull(flow.videoReady(resumed = true))
        assertNull(flow.pipEntered())
        assertNull(flow.unavailable())
        assertNull(flow.accept(pipSupported = true))
    }

    @Test
    fun expandedGuide_canReopenSettings_onlyThroughExplicitAction() {
        val flow = AccessibilityGuideFlow()
        flow.accept(pipSupported = true)
        assertNull(flow.reopenSettings())
        flow.videoReady(resumed = true)
        flow.pipEntered()
        assertEquals(GuideAction.ENTER_PIP, flow.reopenSettings())
        assertNull(flow.reopenSettings())
        assertEquals(GuideAction.OPEN_SETTINGS, flow.pipEntered())
        assertNull(flow.pipEntered())
    }
}
