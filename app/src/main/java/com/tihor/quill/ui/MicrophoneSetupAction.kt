package com.tihor.quill.ui

internal enum class MicrophoneSetupAction { REQUEST_PERMISSION, OPEN_SETTINGS, TEST_MIC }

internal fun microphoneSetupAction(granted: Boolean, previouslyDenied: Boolean, canRequestAgain: Boolean): MicrophoneSetupAction = when {
    granted -> MicrophoneSetupAction.TEST_MIC
    previouslyDenied && !canRequestAgain -> MicrophoneSetupAction.OPEN_SETTINGS
    else -> MicrophoneSetupAction.REQUEST_PERMISSION
}
