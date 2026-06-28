package com.tihor.quill.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tihor.quill.copy.CopyKeys as K
import com.tihor.quill.copy.CopyRepository
import com.tihor.quill.copy.QuillRoot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Onboarding state-machine pieces that need no permission grant and no network:
 *  - splash auto-advances to Welcome,
 *  - the key-required warning enforces a 3-second read gate: while the gate is open the affirmative
 *    "Accept" is hidden and only the disabled "Continue" + a "reading…" caption show; once 3s elapse
 *    the Accept appears.
 *
 * The gate is an [androidx.compose.animation.core.Animatable] tween, which the Compose test clock
 * fast-forwards under auto-advance — so we PAUSE the clock and drive it by hand to observe both
 * sides of the 3s boundary deterministically. The later steps (key verify, mic-test recorder,
 * overlay/accessibility grants) gate on real system UI and stay in the manual device matrix.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val copy by lazy { CopyRepository(ctx) }

    @Test
    fun splashAdvances_thenKeyWarning_enforces3sReadGate() {
        rule.setContent { QuillRoot { OnboardingFlow(onFinish = {}) } }

        // Splash (1.2s) auto-advances to Welcome.
        val welcomeCta = copy.get(K.WELCOME_CTA)
        rule.waitUntil(8_000) {
            rule.onAllNodesWithText(welcomeCta).fetchSemanticsNodes().isNotEmpty()
        }

        // Take manual control of the clock so the gate animation doesn't fast-forward past us.
        rule.mainClock.autoAdvance = false

        // Welcome CTA opens the key-required warning dialog.
        rule.onNodeWithText(welcomeCta).performClick()
        rule.mainClock.advanceTimeByFrame() // compose the dialog's first frame
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(250)   // ~8% into the 3s gate — still closed

        val readingCopy = copy.get(K.KEY_WARNING_READING)
        val acceptCopy = copy.get(K.KEY_WARNING_ACCEPT)

        // Gate open: reading caption + disabled Continue shown; Accept NOT yet present.
        rule.onNodeWithText(readingCopy).assertExists()
        rule.onNodeWithText(copy.get(K.ACTION_CONTINUE)).assertExists()
        assert(rule.onAllNodesWithText(acceptCopy).fetchSemanticsNodes().isEmpty()) {
            "Accept must be hidden while the 3s read-gate is open"
        }

        // Cross the 3s boundary: Accept now appears, reading caption gone.
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        rule.onNodeWithText(acceptCopy).assertExists()
        assert(rule.onAllNodesWithText(readingCopy).fetchSemanticsNodes().isEmpty()) {
            "reading caption should disappear once the gate elapses"
        }

        rule.mainClock.autoAdvance = true
    }
}
