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
    SPLASH, WELCOME, KEY_WARNING, ADD_KEY, HOW_IT_WORKS, CHECKLIST,
    MIC, MIC_TEST, OVERLAY, ACC_DISCLOSURE, ACC_ENABLE, ALL_SET,
}

@Composable
internal fun OnboardingFlow(onFinish: () -> Unit) {
    val context = LocalContext.current
    val secretStore = remember { KeystoreSecretStore(context) }
    val settings = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(Step.SPLASH) }

    fun back() {
        step = when (step) {
            Step.WELCOME -> Step.SPLASH
            Step.KEY_WARNING -> Step.WELCOME
            Step.ADD_KEY -> Step.KEY_WARNING
            Step.HOW_IT_WORKS -> Step.ADD_KEY
            Step.CHECKLIST -> Step.HOW_IT_WORKS
            Step.MIC -> Step.CHECKLIST
            Step.MIC_TEST -> Step.MIC
            Step.OVERLAY -> Step.MIC_TEST
            Step.ACC_DISCLOSURE -> Step.OVERLAY
            Step.ACC_ENABLE -> Step.ACC_DISCLOSURE
            Step.ALL_SET -> Step.ACC_ENABLE
            Step.SPLASH -> Step.SPLASH
        }
    }

    // Request the mic and (Android 13+) POST_NOTIFICATIONS together: the latter is needed for the FGS
    // notification + its "Show Quill" summon action. Advance on the mic grant; notifications are
    // best-effort (the Home toggle is the synced fallback if denied).
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) step = Step.MIC_TEST
    }
    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (OverlayPermission.isGranted(context)) step = Step.ACC_DISCLOSURE
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

    when (step) {
        Step.SPLASH -> SplashStep(onDone = { step = Step.WELCOME })
        Step.WELCOME -> WelcomeStep(onBack = ::back, onNext = { step = Step.KEY_WARNING }, onOpen = ::finishOnboarding)
        Step.KEY_WARNING -> {
            // Dialog over a dimmed Welcome (board A2b/A2c): the scrim does the dimming.
            WelcomeStep(onBack = ::back, onNext = {}, onOpen = {})
            KeyWarningDialog(onAccept = { step = Step.ADD_KEY }, onDismiss = { step = Step.WELCOME })
        }
        Step.ADD_KEY -> AddKeyStep(::back, secretStore, onVerified = { step = Step.HOW_IT_WORKS })
        Step.HOW_IT_WORKS -> HowItWorksStep(::back, onNext = { step = Step.CHECKLIST })
        Step.CHECKLIST -> ChecklistStep(::back, context, onNext = { step = Step.MIC })
        Step.MIC -> MicStep(::back, onAllow = {
            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                arrayOf(Manifest.permission.RECORD_AUDIO)
            }
            micLauncher.launch(perms)
        })
        Step.MIC_TEST -> MicTestStep(::back, onNext = { step = Step.OVERLAY })
        Step.OVERLAY -> OverlayStep(::back, onAllow = { overlayLauncher.launch(OverlayPermission.settingsIntent(context)) })
        Step.ACC_DISCLOSURE -> AccessibilityDisclosureStep(
            ::back,
            onAccept = { step = Step.ACC_ENABLE },
            onDecline = { step = Step.ALL_SET },
        )
        Step.ACC_ENABLE -> AccessibilityEnableStep(::back, onOpen = { accLauncher.launch(AccessibilityUtil.settingsIntent()) })
        Step.ALL_SET -> AllSetStep(onFinish = ::finishOnboarding, onHome = ::finishOnboarding)
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
private fun FormStep(
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Wordmark()
            Spacer(Modifier.height(12.dp))
            Text(copy(K.SPLASH_TAGLINE), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.quill.muted)
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
    var showConsoles by remember { mutableStateOf(false) }
    if (showConsoles) {
        com.tihor.quill.ui.components.KeyConsoleDialog(onDismiss = { showConsoles = false })
    }

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
                    // Match the label: open the all-providers console chooser (like AddKeyStep's
                    // sibling link), rather than just advancing. "Accept" below is what proceeds.
                    TextButton(
                        onClick = { showConsoles = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(copy(K.KEY_WHERE), color = MaterialTheme.quill.brandInk)
                    }
                    QuillPrimaryButton(copy(K.KEY_WARNING_ACCEPT), onClick = onAccept, leadingIcon = Icons.Rounded.Check)
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

private enum class AddKeyState { IDLE, CHECKING, VALID, REJECTED, UNVERIFIED }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddKeyStep(onBack: () -> Unit, secretStore: SecretStore, onVerified: () -> Unit) {
    val context = LocalContext.current
    val sttKeys = remember { SttKeyStore(secretStore) }
    val settingsRepo = remember { SettingsRepository(context) }
    val validator = remember { KeyValidator(context.cacheDir) }
    val scope = rememberCoroutineScope()
    var providerId by remember { mutableStateOf(SttProvider.DEFAULT.id) }
    val provider = SttProvider.fromId(providerId)
    var key by remember(providerId) { mutableStateOf(sttKeys.get(provider).orEmpty()) }
    var reveal by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf(AddKeyState.IDLE) }
    var showConsoles by remember { mutableStateOf(false) }
    if (showConsoles) {
        com.tihor.quill.ui.components.KeyConsoleDialog(onDismiss = { showConsoles = false })
    }

    FormStep(
        onBack = onBack,
        primaryLabel = if (state == AddKeyState.CHECKING) copy(K.KEY_VERIFYING) else copy(K.KEY_CTA),
        primaryEnabled = key.isNotBlank() && state != AddKeyState.CHECKING,
        onPrimary = {
            // Per-provider slot (decision 3), validated against the provider the key is FOR.
            sttKeys.put(provider, key.trim())
            state = AddKeyState.CHECKING
            scope.launch {
                val result = withContext(Dispatchers.IO) { validator.validate(provider, key.trim()) }
                when (result) {
                    KeyValidation.Valid -> {
                        settingsRepo.setProvider(provider) // the onboarded key's provider becomes active
                        // Probe paid-only access (Gemini pro-preview) so the model picker only
                        // lists what this key can actually run; inconclusive → leave unchanged.
                        provider.models.firstOrNull { it.paidOnly }?.let { paid ->
                            withContext(Dispatchers.IO) { validator.hasPaidAccess(provider, key.trim(), paid.id) }
                                ?.let { has -> settingsRepo.setFreeTierOnly(provider, freeOnly = !has) }
                        }
                        state = AddKeyState.VALID
                        onVerified()
                    }
                    KeyValidation.Rejected -> {
                        sttKeys.clear(provider)
                        state = AddKeyState.REJECTED
                    }
                    is KeyValidation.Unverified -> {
                        settingsRepo.setProvider(provider)
                        state = AddKeyState.UNVERIFIED
                        onVerified()
                    }
                }
            }
        },
    ) {
        Spacer(Modifier.height(4.dp))
        Text(copy(K.KEY_TITLE), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(copy(K.KEY_BODY), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.quill.muted)
        Spacer(Modifier.height(16.dp))

        FieldLabel(copy(K.SETTINGS_SPEECH_PROVIDER))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Custom is settings-only (it needs a base URL + model configured first).
            SttProvider.selectable().filter { it != SttProvider.CUSTOM }.forEach { p ->
                QuillChip(
                    p.displayName, // copy-exempt (proper nouns)
                    selected = provider == p,
                    onClick = { providerId = p.id; state = AddKeyState.IDLE },
                )
            }
        }
        if (provider == SttProvider.GEMINI) {
            // Decision 4d: honest expectations — Gemini may paraphrase spoken commands.
            HelpText(copy(K.SETTINGS_SPEECH_GEMINI_CAVEAT))
        }
        Spacer(Modifier.height(16.dp))

        FieldLabel(copy(K.KEY_FIELD_LABEL))
        OutlinedTextField(
            value = key,
            onValueChange = { key = it; state = AddKeyState.IDLE },
            singleLine = true,
            isError = state == AddKeyState.REJECTED,
            placeholder = { Text(provider.keyMask, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)) },
            leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { reveal = !reveal }) {
                    Icon(
                        if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (reveal) copy(K.A11Y_HIDE_KEY) else copy(K.A11Y_SHOW_KEY),
                    )
                }
            },
            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions.Default,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )

        when (state) {
            AddKeyState.CHECKING -> {
                Spacer(Modifier.height(14.dp))
                QuillCard(border = MaterialTheme.quill.brandSoft) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.quill.brandInk)
                        Text(copy(K.KEY_VERIFYING), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.quill.brandInk)
                    }
                }
            }
            AddKeyState.REJECTED -> {
                HelpText(copy(K.KEY_ERROR_INVALID), bad = true)
                Spacer(Modifier.height(12.dp))
                Banner(text = copy(K.PRIVACY_PROMISE), icon = Icons.Rounded.Info, kind = PillKind.INFO)
            }
            else -> {
                if (provider.keyConsoleUrl != null) {
                    Spacer(Modifier.height(6.dp))
                    // Opens the all-providers console chooser, not just the selected provider's page.
                    TextButton(onClick = { showConsoles = true }) {
                        Text(copy(K.KEY_WHERE), color = MaterialTheme.quill.brandInk)
                    }
                }
            }
        }
        // Optional demo clips for the key step (e.g. copying a key from a provider console). Empty
        // asset folder → renders nothing; FormStep keeps the Save & verify CTA pinned below.
        DemoCarousel(CarouselSurface.ONBOARDING_KEY, modifier = Modifier.padding(top = 20.dp))
    }
}

@Composable
private fun HowItWorksStep(onBack: () -> Unit, onNext: () -> Unit) {
    FormStep(onBack = onBack, primaryLabel = copy(K.HOWITWORKS_CTA), onPrimary = onNext) {
        Spacer(Modifier.height(4.dp))
        Text(
            copy(K.HOWITWORKS_EYEBROW).uppercase(),
            style = com.tihor.quill.ui.theme.EyebrowStyle,
            color = MaterialTheme.quill.brandInk,
        )
        Spacer(Modifier.height(6.dp))
        Text(copy(K.HOWITWORKS_TITLE), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StepCard(Icons.Rounded.Mic, LeadIconVariant.GOLD, copy(K.HOWITWORKS_T1), copy(K.HOWITWORKS_STEP1))
            StepCard(Icons.Rounded.Key, LeadIconVariant.BRAND, copy(K.HOWITWORKS_T2), copy(K.HOWITWORKS_STEP2))
            StepCard(Icons.Rounded.Create, LeadIconVariant.BRAND, copy(K.HOWITWORKS_T3), copy(K.HOWITWORKS_STEP3))
        }
    }
}

@Composable
private fun StepCard(icon: ImageVector, variant: LeadIconVariant, title: String, body: String) {
    QuillCard {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            LeadIcon(icon, variant)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(3.dp))
                Text(body, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.quill.muted)
            }
        }
    }
}

@Composable
private fun ChecklistStep(onBack: () -> Unit, context: android.content.Context, onNext: () -> Unit) {
    val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    val hasOverlay = OverlayPermission.isGranted(context)
    val hasAcc = AccessibilityUtil.isServiceEnabled(context)
    val done = listOf(true, hasMic, hasOverlay, hasAcc)
    val doneCount = done.count { it }

    FormStep(onBack = onBack, primaryLabel = copy(K.CHECKLIST_CTA), onPrimary = onNext) {
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(copy(K.CHECKLIST_TITLE), style = MaterialTheme.typography.titleLarge)
            Text(copy(K.CHECKLIST_PROGRESS, doneCount, done.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.quill.muted)
        }
        Spacer(Modifier.height(16.dp))
        QuillListContainer {
            ChecklistRow(Icons.Rounded.Key, LeadIconVariant.OK, copy(K.CHECKLIST_KEY), copy(K.CHECKLIST_KEY_SUB), done[0])
            RowDivider()
            ChecklistRow(Icons.Rounded.Mic, LeadIconVariant.GOLD, copy(K.CHECKLIST_MIC), copy(K.CHECKLIST_MIC_SUB), done[1])
            RowDivider()
            ChecklistRow(Icons.Rounded.PictureInPicture, LeadIconVariant.PLAIN, copy(K.CHECKLIST_OVERLAY), copy(K.CHECKLIST_OVERLAY_SUB), done[2])
            RowDivider()
            ChecklistRow(Icons.Rounded.AccessibilityNew, LeadIconVariant.PLAIN, copy(K.CHECKLIST_ACCESSIBILITY), copy(K.CHECKLIST_ACC_SUB), done[3])
        }
        Spacer(Modifier.height(16.dp))
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
private fun MicStep(onBack: () -> Unit, onAllow: () -> Unit) {
    CenteredStep(
        onBack = onBack,
        illustration = { PermissionIllustration(Icons.Rounded.Mic, variant = IllustrationVariant.GOLD) },
        title = copy(K.MIC_TITLE),
        body = copy(K.RATIONALE_MIC),
        primaryLabel = copy(K.MIC_CTA),
        onPrimary = onAllow,
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
private fun AccessibilityDisclosureStep(onBack: () -> Unit, onAccept: () -> Unit, onDecline: () -> Unit) {
    // Compliance copy stays verbatim on its locked keys (CLAUDE.md hard rule); only the layout changes.
    FormStep(onBack = onBack, primaryLabel = copy(K.DISCLOSURE_ACCEPT), onPrimary = onAccept) {
        Spacer(Modifier.height(4.dp))
        LeadIcon(Icons.Rounded.AccessibilityNew)
        Spacer(Modifier.height(14.dp))
        Text(copy(K.DISCLOSURE_TITLE), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        QuillCard {
            Text(
                copy(K.DISCLOSURE_BODY),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.quill.text2,
                modifier = Modifier.padding(16.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        QuillGhostButton(copy(K.DISCLOSURE_DECLINE), onClick = onDecline)
        // Optional demo clips illustrating the accessibility flow. Empty asset folder → renders nothing.
        // The FormStep pins the CTA below this scrollable body, so it never pushes the CTA off-screen.
        DemoCarousel(CarouselSurface.ONBOARDING_ACCESSIBILITY, modifier = Modifier.padding(top = 16.dp))
    }
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
