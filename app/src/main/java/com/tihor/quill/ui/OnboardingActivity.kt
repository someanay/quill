package com.tihor.quill.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tihor.quill.accessibility.AccessibilityUtil
import com.tihor.quill.audio.AudioRecorder
import com.tihor.quill.copy.Copy
import com.tihor.quill.copy.CopyKeys as K
import com.tihor.quill.copy.QuillRoot
import com.tihor.quill.copy.copy
import com.tihor.quill.overlay.OverlayPermission
import com.tihor.quill.overlay.OverlayService
import com.tihor.quill.secrets.KeystoreSecretStore
import com.tihor.quill.secrets.SecretStore
import com.tihor.quill.secrets.SttKeyStore
import com.tihor.quill.settings.SettingsRepository
import com.tihor.quill.settings.SttProvider
import com.tihor.quill.stt.KeyValidation
import com.tihor.quill.stt.KeyValidator
import com.tihor.quill.carousel.CarouselSurface
import com.tihor.quill.ui.components.Banner
import com.tihor.quill.ui.components.BubbleState
import com.tihor.quill.ui.components.DemoCarousel
import com.tihor.quill.ui.components.FieldLabel
import com.tihor.quill.ui.components.HelpText
import com.tihor.quill.ui.components.IllustrationVariant
import com.tihor.quill.ui.components.InkMeter
import com.tihor.quill.ui.components.LeadIcon
import com.tihor.quill.ui.components.LeadIconVariant
import com.tihor.quill.ui.components.PermissionIllustration
import com.tihor.quill.ui.components.PillKind
import com.tihor.quill.ui.components.QuillBubble
import com.tihor.quill.ui.components.QuillCard
import com.tihor.quill.ui.components.QuillChip
import com.tihor.quill.ui.components.QuillGhostButton
import com.tihor.quill.ui.components.QuillListContainer
import com.tihor.quill.ui.components.QuillPrimaryButton
import com.tihor.quill.ui.components.QuillRow
import com.tihor.quill.ui.components.QuillTopBar
import com.tihor.quill.ui.components.RowDivider
import com.tihor.quill.ui.components.StatusPill
import com.tihor.quill.ui.components.Wordmark
import com.tihor.quill.ui.theme.quill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Guided onboarding (M8, design board A — reordered flow). Drives the real permission grants. */
class OnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { QuillRoot { OnboardingFlow(onFinish = { finish() }) } }
    }

}

private enum class Step {
    SPLASH, WELCOME, KEY_WARNING, ADD_KEY, CHECKLIST,
    MIC_TEST, OVERLAY, ACC_ENABLE, ALL_SET,
}

@Composable
internal fun OnboardingFlow(onFinish: () -> Unit) {
    val context = LocalContext.current
    val secretStore = remember { KeystoreSecretStore(context) }
    val settings = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()

    var step by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(Step.SPLASH) }
    val activity = androidx.activity.compose.LocalActivity.current
    var micDenied by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var permissionPending by remember { mutableStateOf(false) }
    fun microphoneGranted() = androidx.core.content.ContextCompat.checkSelfPermission(context,
        Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
    var hasMic by remember { mutableStateOf(microphoneGranted()) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) hasMic = microphoneGranted()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val micAction = microphoneSetupAction(hasMic, micDenied,
        activity?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == true)

    fun back() {
        step = when (step) {
            Step.WELCOME -> Step.SPLASH
            Step.KEY_WARNING -> Step.WELCOME
            Step.ADD_KEY -> Step.KEY_WARNING
            Step.CHECKLIST -> Step.ADD_KEY
            Step.MIC_TEST -> Step.CHECKLIST
            Step.OVERLAY -> Step.MIC_TEST
            Step.ACC_ENABLE -> Step.OVERLAY
            Step.ALL_SET -> Step.ACC_ENABLE
            Step.SPLASH -> Step.SPLASH
        }
    }

    // The checklist requests the microphone directly. Notifications are requested after the mic test.
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionPending = false
        hasMic = granted
        micDenied = !granted
        if (granted) step = Step.MIC_TEST
    }
    val micSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissionPending = false
        hasMic = microphoneGranted()
        micDenied = !hasMic
        if (hasMic) step = Step.MIC_TEST
    }
    val notificationsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (OverlayPermission.isGranted(context)) step = Step.ACC_ENABLE
    }
    val accLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (AccessibilityUtil.isServiceEnabled(context)) step = Step.ALL_SET
    }

    BackHandler(enabled = step != Step.SPLASH) { back() }

    // Accessibility enablement lags the toggle (the service binds asynchronously) and the settings
    // screen returns no result — so poll while on this step and auto-advance the moment it's on,
    // instead of leaving the user staring at "turn it on" after they already did.
    LaunchedEffect(step) {
        if (step == Step.ACC_ENABLE) {
            while (step == Step.ACC_ENABLE) {
                if (AccessibilityUtil.isServiceEnabled(context)) {
                    step = Step.ALL_SET
                    break
                }
                kotlinx.coroutines.delay(300)
            }
        }
    }

    fun finishOnboarding() {
        scope.launch { settings.setOnboardingComplete(true) }
        if (OverlayPermission.isGranted(context)) {
            OverlayService.start(context)
            // Mark the service desired (gates M11 recovery) and the bubble shown — the unified state the
            // Home toggle, drag-dismiss, and notification-summon all share.
            scope.launch { settings.setBubbleActive(true); settings.setBubbleVisible(true) }
        }
        onFinish()
    }

    // A dialog owns a separate window. Keep it outside the page transition so dismissal
    // does not crossfade two welcome screens while retaining the outgoing dialog window.
    val page = if (step == Step.KEY_WARNING) Step.WELCOME else step
    androidx.compose.animation.Crossfade(targetState = page, label = "onboardingTransition") { shownStep -> // copy-exempt
    when (shownStep) {
        Step.SPLASH -> SplashStep(onDone = { step = Step.WELCOME })
        Step.WELCOME -> WelcomeStep(onBack = ::back, onNext = { step = Step.KEY_WARNING }, onOpen = ::finishOnboarding)
        Step.KEY_WARNING -> Unit // Rendered separately from the page transition below.
        Step.ADD_KEY -> AddKeyStep(::back, secretStore, onVerified = { step = Step.CHECKLIST })
        Step.CHECKLIST -> ChecklistStep(::back, context, hasMic, micDenied, micAction, !permissionPending, onNext = {
            when (microphoneSetupAction(microphoneGranted(), micDenied,
                activity?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == true)) {
                MicrophoneSetupAction.TEST_MIC -> step = Step.MIC_TEST
                MicrophoneSetupAction.REQUEST_PERMISSION -> {
                    permissionPending = true
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
                MicrophoneSetupAction.OPEN_SETTINGS -> {
                    permissionPending = true
                    micSettingsLauncher.launch(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:${context.packageName}")))
                }
            }
        })
        Step.MIC_TEST -> MicTestStep(::back, onNext = {
            step = Step.OVERLAY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        })
        Step.OVERLAY -> OverlayStep(::back, onAllow = { overlayLauncher.launch(OverlayPermission.settingsIntent(context)) })
        Step.ACC_ENABLE -> AccessibilityEnableStep(::back, onOpen = { accLauncher.launch(AccessibilityUtil.disclosureIntent(context)) })
        Step.ALL_SET -> AllSetStep(onFinish = ::finishOnboarding, onHome = ::finishOnboarding)
    }
    }
    if (step == Step.KEY_WARNING) {
        KeyWarningDialog(onAccept = { step = Step.ADD_KEY }, onDismiss = { step = Step.WELCOME })
    }
}

// --- Common scaffolds ---

/** Centered illustration + title/body, bottom-anchored CTA — the permission/rationale frames. */
@Composable
private fun CenteredStep(
    onBack: (() -> Unit)?,
    title: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    body: String? = null,
    illustration: @Composable (() -> Unit)? = null,
    primaryEnabled: Boolean = true,
    caption: String? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    content: @Composable (ColumnScope.() -> Unit)? = null,
) {
    StepBar(onBack) { pad ->
        Column(
            pad.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(8.dp))
            illustration?.let { it(); Spacer(Modifier.height(24.dp)) }
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            if (body != null) {
                Spacer(Modifier.height(10.dp))
                Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.quill.text2, textAlign = TextAlign.Center)
            }
            content?.let { Spacer(Modifier.height(20.dp)); it() }
            Spacer(Modifier.height(28.dp))
            QuillPrimaryButton(primaryLabel, onPrimary, enabled = primaryEnabled)
            if (secondaryLabel != null && onSecondary != null) {
                Spacer(Modifier.height(10.dp))
                QuillGhostButton(secondaryLabel, onSecondary)
            }
            if (caption != null) {
                Spacer(Modifier.height(12.dp))
                Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.quill.muted, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Top-aligned scrollable body with a bottom-anchored CTA — the form frames (key entry, etc.). */
@Composable
internal fun FormStep(
    onBack: (() -> Unit)?,
    primaryLabel: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean = true,
    body: @Composable ColumnScope.() -> Unit,
) {
    StepBar(onBack) { pad ->
        Column(pad.fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                content = body,
            )
            Column(Modifier.padding(16.dp)) {
                QuillPrimaryButton(primaryLabel, onPrimary, enabled = primaryEnabled)
            }
        }
    }
}

@Composable
private fun StepBar(onBack: (() -> Unit)?, body: @Composable (Modifier) -> Unit) {
    Scaffold(
        topBar = { QuillTopBar(title = {}, onBack = onBack) },
        modifier = Modifier.fillMaxSize(),
    ) { padding -> body(Modifier.padding(padding)) }
}

// --- Steps ---

@Composable
private fun SplashStep(onDone: () -> Unit) {
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(1200); onDone() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Wordmark()
            Spacer(Modifier.height(12.dp))
            Text(copy(K.SPLASH_TAGLINE), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.quill.muted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun WelcomeStep(onBack: () -> Unit, onNext: () -> Unit, onOpen: () -> Unit) {
    CenteredStep(
        onBack = onBack,
        illustration = { Wordmark() },
        title = copy(K.WELCOME_TITLE),
        body = copy(K.WELCOME_BODY),
        primaryLabel = copy(K.WELCOME_CTA),
        onPrimary = onNext,
        content = {
            StatusPill(copy(K.WELCOME_PRIVACY_PILL), PillKind.LIVE)
        },
    )
    // "Already set up? Open Quill" sits under the CTA; rendered as part of caption row.
    // (Kept simple: a text button overlay would over-complicate the centered layout.)
    Box(Modifier.fillMaxSize().padding(bottom = 18.dp), contentAlignment = Alignment.BottomCenter) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(copy(K.WELCOME_ALREADY), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.quill.muted)
            TextButton(onClick = onOpen) { Text(copy(K.WELCOME_OPEN), color = MaterialTheme.quill.brandInk) }
        }
    }
}

@Composable
private fun KeyWarningDialog(onAccept: () -> Unit, onDismiss: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(3000)) }
    val ready = progress.value >= 1f
    Dialog(onDismissRequest = onDismiss) {
        QuillCard {
            Column(Modifier.padding(20.dp)) {
                LeadIcon(Icons.Rounded.Key, LeadIconVariant.GOLD)
                Spacer(Modifier.height(12.dp))
                Text(copy(K.KEY_REQUIRED_WARNING_TITLE), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(copy(K.KEY_REQUIRED_WARNING_BODY), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.quill.muted)
                Spacer(Modifier.height(12.dp))
                QuillCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BulletRow(Icons.Rounded.Key, MaterialTheme.quill.brandInk, copy(K.KEY_WARNING_FREE_TIER))
                        BulletRow(Icons.Rounded.Info, MaterialTheme.quill.goldDeep, copy(K.KEY_WARNING_USAGE))
                        BulletRow(Icons.Rounded.Lock, MaterialTheme.quill.ok, copy(K.PRIVACY_PROMISE))
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (!ready) {
                    LinearProgressIndicator(
                        progress = { progress.value },
                        color = MaterialTheme.quill.gold,
                        trackColor = MaterialTheme.quill.lineStrong,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    QuillPrimaryButton(copy(K.ACTION_CONTINUE), onClick = {}, enabled = false)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        copy(K.KEY_WARNING_READING),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.quill.faint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    QuillPrimaryButton(copy(K.KEY_WARNING_ACCEPT), onClick = onAccept)
                    Spacer(Modifier.height(10.dp))
                    QuillGhostButton(copy(K.ACTION_NOT_NOW), onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun BulletRow(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.quill.muted)
    }
}

@Composable
private fun ChecklistStep(onBack: () -> Unit, context: android.content.Context, hasMic: Boolean,
    micDenied: Boolean, micAction: MicrophoneSetupAction, primaryEnabled: Boolean, onNext: () -> Unit) {
    val settings = remember { SettingsRepository(context) }
    val currentSettings by settings.settings.collectAsState(initial = null)
    val hasOverlay = OverlayPermission.isGranted(context)
    val hasAcc = AccessibilityUtil.isServiceEnabled(context)
    val done = listOf(true, hasMic, hasOverlay, hasAcc)
    val doneCount = done.count { it }

    FormStep(onBack = onBack, primaryLabel = copy(when (micAction) {
        MicrophoneSetupAction.TEST_MIC -> K.CHECKLIST_CTA
        MicrophoneSetupAction.REQUEST_PERMISSION -> K.MIC_CTA
        MicrophoneSetupAction.OPEN_SETTINGS -> K.MIC_OPEN_SETTINGS
    }), primaryEnabled = primaryEnabled, onPrimary = onNext) {
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(copy(K.CHECKLIST_TITLE), style = MaterialTheme.typography.titleLarge)
            Text(copy(K.CHECKLIST_PROGRESS, doneCount, done.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.quill.muted)
        }
        Spacer(Modifier.height(16.dp))
        QuillListContainer {
            ChecklistRow(Icons.Rounded.Key, LeadIconVariant.OK, copy(K.CHECKLIST_KEY),
                currentSettings?.let { copy(K.CHECKLIST_PROVIDER_CONNECTED, it.provider.displayName) }
                    ?: copy(K.CHECKLIST_KEY_SUB), done[0])
            RowDivider()
            ChecklistRow(Icons.Rounded.Mic, LeadIconVariant.GOLD, copy(K.CHECKLIST_MIC), copy(K.CHECKLIST_MIC_SUB), done[1])
            RowDivider()
            ChecklistRow(Icons.Rounded.PictureInPicture, LeadIconVariant.PLAIN, copy(K.CHECKLIST_OVERLAY), copy(K.CHECKLIST_OVERLAY_SUB), done[2])
            RowDivider()
            ChecklistRow(Icons.Rounded.AccessibilityNew, LeadIconVariant.PLAIN, copy(K.CHECKLIST_ACCESSIBILITY), copy(K.CHECKLIST_ACC_SUB), done[3])
        }
        Spacer(Modifier.height(16.dp))
        if (micDenied && !hasMic) {
            HelpText(copy(if (micAction == MicrophoneSetupAction.OPEN_SETTINGS) K.MIC_BLOCKED else K.MIC_DENIED), bad = true)
            Spacer(Modifier.height(12.dp))
        }
        LinearProgressIndicator(
            progress = { doneCount / done.size.toFloat() },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.quill.lineStrong,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ChecklistRow(
    icon: ImageVector,
    variant: LeadIconVariant,
    title: String,
    subtitle: String,
    done: Boolean,
) {
    QuillRow(
        leadIcon = icon,
        leadVariant = if (done) LeadIconVariant.OK else variant,
        title = title,
        subtitle = subtitle,
        trailing = {
            if (done) {
                Icon(Icons.Rounded.Check, contentDescription = copy(K.STATUS_DONE), tint = MaterialTheme.quill.ok, modifier = Modifier.size(20.dp))
            } else {
                Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.quill.lineStrong))
            }
        },
    )
}

@Composable
private fun MicTestStep(onBack: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    val recorder = remember { AudioRecorder(context) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var amplitude by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val temp = File(context.cacheDir, "quill_mictest.wav")
        recorder.start(
            output = temp,
            onAmplitude = { rms -> mainHandler.post { amplitude = (rms / 7000f).coerceIn(0f, 1f) } },
        )
        onDispose { recorder.requestStop(); temp.delete() }
    }

    CenteredStep(
        onBack = onBack,
        title = copy(K.MICTEST_HEARD),
        body = copy(K.MICTEST_BODY),
        primaryLabel = copy(K.MICTEST_CTA),
        onPrimary = onNext,
        illustration = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StatusPill(copy(K.MICTEST_TITLE), PillKind.LIVE)
                Spacer(Modifier.height(22.dp))
                InkMeter(amplitude = amplitude, modifier = Modifier.size(width = 240.dp, height = 64.dp), recording = true)
            }
        },
    )
}

@Composable
private fun OverlayStep(onBack: () -> Unit, onAllow: () -> Unit) {
    CenteredStep(
        onBack = onBack,
        illustration = { PermissionIllustration(Icons.Rounded.PictureInPicture) },
        title = copy(K.OVERLAY_TITLE),
        body = copy(K.RATIONALE_OVERLAY),
        primaryLabel = copy(K.OVERLAY_CTA),
        onPrimary = onAllow,
    )
}

@Composable
private fun AccessibilityEnableStep(onBack: () -> Unit, onOpen: () -> Unit) {
    CenteredStep(
        onBack = onBack,
        illustration = { PermissionIllustration(Icons.Rounded.AccessibilityNew) },
        title = copy(K.ACC_ENABLE_TITLE),
        body = copy(K.RATIONALE_ACCESSIBILITY),
        primaryLabel = copy(K.ACC_ENABLE_CTA),
        onPrimary = onOpen,
    )
}

@Composable
private fun AllSetStep(onFinish: () -> Unit, onHome: () -> Unit) {
    CenteredStep(
        onBack = null,
        illustration = { PermissionIllustration(Icons.Rounded.Check, variant = IllustrationVariant.OK) },
        title = copy(K.ALLSET_TITLE),
        body = copy(K.ALLSET_BODY),
        primaryLabel = copy(K.ALLSET_CTA),
        onPrimary = onFinish,
        content = {
            QuillCard {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    QuillBubble(BubbleState.IDLE, Icons.Rounded.Mic, size = 40.dp)
                    Text(copy(K.ALLSET_LOOK), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.quill.muted)
                }
            }
        },
    )
    Box(Modifier.fillMaxSize().padding(bottom = 18.dp), contentAlignment = Alignment.BottomCenter) {
        TextButton(onClick = onHome) { Text(copy(K.ALLSET_HOME_LINK), color = MaterialTheme.quill.brandInk) }
    }
}
