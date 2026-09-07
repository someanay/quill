package com.tihor.quill.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MicrophoneSetupActionTest {
    @Test fun firstTapRequestsPermissionWithoutAnotherScreen() {
        assertEquals(MicrophoneSetupAction.REQUEST_PERMISSION, microphoneSetupAction(false, false, false))
    }
    @Test fun aTemporaryDenialCanBeRetriedFromTheChecklist() {
        assertEquals(MicrophoneSetupAction.REQUEST_PERMISSION, microphoneSetupAction(false, true, true))
    }
    @Test fun aPermanentDenialOpensAppSettingsInsteadOfAnUnusablePrompt() {
        assertEquals(MicrophoneSetupAction.OPEN_SETTINGS, microphoneSetupAction(false, true, false))
    }
    @Test fun aGrantedPermissionGoesDirectlyToTheMicTest() {
        assertEquals(MicrophoneSetupAction.TEST_MIC, microphoneSetupAction(true, false, false))
        assertEquals(MicrophoneSetupAction.TEST_MIC, microphoneSetupAction(true, true, false))
    }
}
