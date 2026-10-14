package com.tihor.quill.accessibility

import android.os.Bundle
import android.content.Intent
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.tihor.quill.R
import com.tihor.quill.ui.components.OnboardingVideo
import com.tihor.quill.ui.theme.QuillTheme
import kotlin.math.roundToInt

private const val GUIDE_VIDEO_ASSET = "onboarding/quill-accessibility-pip-60fps.mp4"
private const val GUIDE_VIDEO_WIDTH = 720
private const val GUIDE_VIDEO_HEIGHT = 1584

/**
 * Prominent disclosure + affirmative consent shown **before** the user is sent to enable the
 * accessibility service (Play policy / spec §8). All copy here is binary-only compliance text
 * (`strings.xml`). After Agree, a bundled native PiP guide accompanies Settings.
 */
class AccessibilityDisclosureActivity : ComponentActivity() {
    private val flow = AccessibilityGuideFlow()
    private var guideShowing by mutableStateOf(false)
    private var pipShowing by mutableStateOf(false)
    private var frameReady by mutableStateOf(false)
    private var videoBounds: Rect? = null
    private val handler = Handler(Looper.getMainLooper())
    private val guideTimeout = Runnable { guideUnavailable() }
    private var observingService = false
    private val serviceObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = finishIfEnabled()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A killed guide session ends here; a new request must show the full disclosure again.
        if (savedInstanceState?.getBoolean(STATE_GUIDE_ACTIVE) == true) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            QuillTheme {
                BackHandler(enabled = guideShowing) { perform(flow.close()) }
                if (guideShowing) {
                    GuideContent(
                        pipShowing = pipShowing,
                        frameReady = frameReady,
                        onReady = {
                            frameReady = true
                            perform(flow.videoReady(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)))
                        },
                        onError = ::guideUnavailable,
                        onBounds = ::updateVideoBounds,
                        onSettings = { perform(flow.reopenSettings()) },
                        onClose = { perform(flow.close()) },
                    )
                } else Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    DisclosureContent(
                        modifier = Modifier.padding(padding),
                        onAccept = ::beginAccessibilitySetup,
                        onDecline = { finish() },
                    )
                }
            }
        }
    }

    private fun beginAccessibilitySetup() {
        val supported = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
            && !AccessibilityUtil.isServiceEnabled(this)
        perform(flow.accept(supported))
    }

    private fun perform(action: GuideAction?) {
        when (action) {
            GuideAction.PREPARE_VIDEO -> {
                guideShowing = true
                contentResolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, serviceObserver)
                observingService = true
                armTimeout()
                finishIfEnabled()
            }
            GuideAction.ENTER_PIP -> {
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    perform(flow.close())
                    return
                }
                armTimeout()
                val entered = runCatching { enterPictureInPictureMode(pipParams()) }.getOrDefault(false)
                if (!entered) guideUnavailable()
            }
            GuideAction.OPEN_SETTINGS -> {
                handler.removeCallbacks(guideTimeout)
                openSettings(finishAfter = false)
            }
            GuideAction.OPEN_SETTINGS_AND_FINISH -> {
                handler.removeCallbacks(guideTimeout)
                openSettings(finishAfter = true)
            }
            GuideAction.FINISH -> {
                handler.removeCallbacks(guideTimeout)
                finish()
            }
            null -> Unit
        }
    }

    private fun openSettings(finishAfter: Boolean) {
        // The Settings task must stay separate from the Activity that Android has pinned.
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && !isInPictureInPictureMode) {
            flow.close()
            finish()
            return
        }
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        if (finishAfter) finish()
    }

    private fun armTimeout() {
        handler.removeCallbacks(guideTimeout)
        handler.postDelayed(guideTimeout, 5_000L)
    }

    private fun guideUnavailable() {
        val action = flow.unavailable()
        if (action == GuideAction.OPEN_SETTINGS_AND_FINISH &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            Toast.makeText(this, R.string.a11y_guide_unavailable, Toast.LENGTH_SHORT).show()
        }
        perform(action)
    }

    private fun finishIfEnabled() {
        if (AccessibilityUtil.isServiceEnabled(this)) perform(flow.enabled())
    }

    private fun pipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(GUIDE_VIDEO_WIDTH, GUIDE_VIDEO_HEIGHT))
        videoBounds?.takeUnless { it.isEmpty }?.let(builder::setSourceRectHint)
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setAutoEnterEnabled(false).setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun updateVideoBounds(bounds: Rect) {
        if (videoBounds == bounds || isFinishing) return
        videoBounds = bounds
        runCatching { setPictureInPictureParams(pipParams()) }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipShowing = isInPictureInPictureMode
        if (isInPictureInPictureMode) perform(flow.pipEntered())
    }

    override fun onResume() {
        super.onResume()
        if (guideShowing) {
            finishIfEnabled()
            if (frameReady) perform(flow.videoReady(resumed = true))
        }
    }

    override fun onStop() {
        super.onStop()
        if (guideShowing && !isInPictureInPictureMode && !isChangingConfigurations) perform(flow.close())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_GUIDE_ACTIVE, flow.accepted)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        flow.close()
        handler.removeCallbacks(guideTimeout)
        if (observingService) contentResolver.unregisterContentObserver(serviceObserver)
        super.onDestroy()
    }

    private companion object {
        const val STATE_GUIDE_ACTIVE = "accessibility_guide_active"
    }
}

@Composable
private fun GuideContent(
    pipShowing: Boolean,
    frameReady: Boolean,
    onReady: () -> Unit,
    onError: () -> Unit,
    onBounds: (Rect) -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val insets = if (pipShowing) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
    Column(Modifier.fillMaxSize().background(Color.Black).then(insets)
        .padding(if (pipShowing) 0.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        if (!pipShowing) Text(stringResource(R.string.a11y_guide_title), color = Color.White,
            style = MaterialTheme.typography.titleLarge)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val ratio = GUIDE_VIDEO_WIDTH.toFloat() / GUIDE_VIDEO_HEIGHT
            val videoWidth = minOf(maxWidth, maxHeight * ratio)
            OnboardingVideo(
                visible = true,
                assetPath = GUIDE_VIDEO_ASSET,
                description = stringResource(R.string.a11y_guide_title),
                onReady = onReady,
                onError = onError,
                modifier = Modifier.width(videoWidth).aspectRatio(ratio).onGloballyPositioned {
                    val bounds = it.boundsInWindow()
                    onBounds(Rect(bounds.left.roundToInt(), bounds.top.roundToInt(),
                        bounds.right.roundToInt(), bounds.bottom.roundToInt()))
                },
            )
        }
        if (!pipShowing) {
            Button(onClick = onSettings, enabled = frameReady) {
                Text(stringResource(R.string.a11y_guide_open_settings))
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.a11y_guide_close)) }
        }
    }
}

@Composable
private fun DisclosureContent(
    modifier: Modifier = Modifier,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.a11y_disclosure_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            stringResource(R.string.a11y_disclosure_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onAccept) {
            Text(stringResource(R.string.a11y_disclosure_accept))
        }
        TextButton(onClick = onDecline) {
            Text(stringResource(R.string.a11y_disclosure_decline))
        }
    }
}
