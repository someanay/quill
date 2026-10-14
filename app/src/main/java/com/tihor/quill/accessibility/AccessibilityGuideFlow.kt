package com.tihor.quill.accessibility

internal enum class GuideAction {
    PREPARE_VIDEO, ENTER_PIP, OPEN_SETTINGS, OPEN_SETTINGS_AND_FINISH, FINISH,
}

/** Consent and asynchronous video/PiP callbacks belong to this one disclosure session. */
internal class AccessibilityGuideFlow {
    var accepted = false
        private set
    private var finished = false
    private var pipRequested = false
    private var settingsOpened = false

    fun accept(pipSupported: Boolean): GuideAction? {
        if (accepted || finished) return null
        accepted = true
        if (!pipSupported) {
            finished = true
            settingsOpened = true
            return GuideAction.OPEN_SETTINGS_AND_FINISH
        }
        return GuideAction.PREPARE_VIDEO
    }

    fun videoReady(resumed: Boolean): GuideAction? {
        if (!accepted || finished || settingsOpened || pipRequested || !resumed) return null
        pipRequested = true
        return GuideAction.ENTER_PIP
    }

    fun pipEntered(): GuideAction? {
        if (!accepted || finished || !pipRequested || settingsOpened) return null
        settingsOpened = true
        pipRequested = false
        return GuideAction.OPEN_SETTINGS
    }

    fun unavailable(): GuideAction? {
        if (!accepted || finished) return null
        finished = true
        return if (settingsOpened) GuideAction.FINISH else GuideAction.OPEN_SETTINGS_AND_FINISH
    }

    fun enabled(): GuideAction? = if (accepted) close() else null

    fun close(): GuideAction? {
        if (finished) return null
        finished = true
        return GuideAction.FINISH
    }

    fun reopenSettings(): GuideAction? {
        if (!accepted || finished || !settingsOpened) return null
        settingsOpened = false
        pipRequested = true
        return GuideAction.ENTER_PIP
    }
}
