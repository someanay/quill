package com.tihor.quill.ui

import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tihor.quill.accessibility.AccessibilityUtil
import com.tihor.quill.audio.AudioRecorder
import com.tihor.quill.copy.QuillRoot
import com.tihor.quill.copy.copy
import com.tihor.quill.copy.CopyKeys as K
import com.tihor.quill.overlay.OverlayPermission
import com.tihor.quill.overlay.OverlayService
import com.tihor.quill.secrets.KeystoreSecretStore
import com.tihor.quill.secrets.SecretStore
import com.tihor.quill.settings.SettingsRepository
import com.tihor.quill.settings.SttProvider
import com.tihor.quill.stt.KeyValidation
import com.tihor.quill.stt.KeyValidator
import com.tihor.quill.ui.components.Banner
import com.tihor.quill.ui.components.IllustrationVariant
import com.tihor.quill.ui.components.InkMeter
import com.tihor.quill.ui.components.PermissionIllustration
import com.tihor.quill.ui.components.PillKind
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
private fun OnboardingFlow(onFinish: () -> Unit) {
    val context = LocalContext.current
    val secretStore = remember { KeystoreSecretStore(context) }
    val settings = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(Step.SPLASH) }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) step = Step.MIC_TEST
    }
    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (OverlayPermission.isGranted(context)) step = Step.ACC_DISCLOSURE
    }
    val accLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (AccessibilityUtil.isServiceEnabled(context)) step = Step.ALL_SET
    }

    BackHandler(enabled = step != Step.SPLASH) {
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

    when (step) {
        Step.SPLASH -> SplashStep(onDone = { step = Step.WELCOME })
        Step.WELCOME -> WelcomeStep(onNext = { step = Step.KEY_WARNING })
        Step.KEY_WARNING -> KeyWarningStep(onNext = { step = Step.ADD_KEY })
        Step.ADD_KEY -> AddKeyStep(secretStore, onVerified = { step = Step.HOW_IT_WORKS })
        Step.HOW_IT_WORKS -> HowItWorksStep(onNext = { step = Step.CHECKLIST })
        Step.CHECKLIST -> ChecklistStep(context, onNext = { step = Step.MIC })
        Step.MIC -> MicStep(onAllow = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) })
        Step.MIC_TEST -> MicTestStep(onNext = { step = Step.OVERLAY })
        Step.OVERLAY -> OverlayStep(onAllow = { overlayLauncher.launch(OverlayPermission.settingsIntent(context)) })
        Step.ACC_DISCLOSURE -> AccessibilityDisclosureStep(
            onAccept = { step = Step.ACC_ENABLE },
            onDecline = { step = Step.ALL_SET },
        )
        Step.ACC_ENABLE -> AccessibilityEnableStep(onOpen = { accLauncher.launch(AccessibilityUtil.settingsIntent()) })
        Step.ALL_SET -> AllSetStep(onFinish = {
            scope.launch { settings.setOnboardingComplete(true) }
            if (OverlayPermission.isGranted(context)) OverlayService.start(context)
            onFinish()
        })
    }
}

// --- Common step layout ---

@Composable
private fun StepScaffold(
    title: String,
    body: String? = null,
    illustration: @Composable (() -> Unit)? = null,
    primaryLabel: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean = true,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(24.dp))
        illustration?.let { it(); Spacer(Modifier.height(28.dp)) }
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.quill.text2,
                textAlign = TextAlign.Center,
            )
        }
        content?.let { Spacer(Modifier.height(20.dp)); it() }
        Spacer(Modifier.height(28.dp))
        Button(onClick = onPrimary, enabled = primaryEnabled, modifier = Modifier.fillMaxWidth()) {
            Text(primaryLabel)
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) { Text(secondaryLabel) }
        }
        Spacer(Modifier.height(24.dp))
    }
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
private fun WelcomeStep(onNext: () -> Unit) {
    StepScaffold(
        illustration = { PermissionIllustration(Icons.Rounded.GraphicEq) },
        title = copy(K.WELCOME_TITLE),
        body = copy(K.WELCOME_BODY),
        primaryLabel = copy(K.WELCOME_CTA),
        onPrimary = onNext,
    )
}

@Composable
private fun KeyWarningStep(onNext: () -> Unit) {
    // 3-second read-gate (design board A): CTA enables only after the gold progress fills.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(3000)) }
    StepScaffold(
        illustration = { PermissionIllustration(Icons.Rounded.Key, variant = IllustrationVariant.GOLD) },
        title = copy(K.KEY_REQUIRED_WARNING_TITLE),
        body = copy(K.KEY_REQUIRED_WARNING_BODY),
        primaryLabel = copy(K.ACTION_CONTINUE),
        onPrimary = onNext,
        primaryEnabled = progress.value >= 1f,
        content = {
            LinearProgressIndicator(
                progress = { progress.value },
                color = MaterialTheme.quill.gold,
                trackColor = MaterialTheme.quill.lineStrong,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

private enum class AddKeyState { IDLE, CHECKING, VALID, REJECTED, UNVERIFIED }

@Composable
private fun AddKeyStep(secretStore: SecretStore, onVerified: () -> Unit) {
    val context = LocalContext.current
    val validator = remember { KeyValidator(context.cacheDir) }
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf(secretStore.get(SecretStore.ALIAS_STT_KEY).orEmpty()) }
    var state by remember { mutableStateOf(AddKeyState.IDLE) }

    StepScaffold(
        illustration = { PermissionIllustration(Icons.Rounded.Key) },
        title = copy(K.KEY_TITLE),
        body = copy(K.KEY_BODY),
        primaryLabel = if (state == AddKeyState.CHECKING) copy(K.KEY_VERIFYING) else copy(K.KEY_CTA),
        primaryEnabled = key.isNotBlank() && state != AddKeyState.CHECKING,
        onPrimary = {
            secretStore.put(SecretStore.ALIAS_STT_KEY, key.trim())
            state = AddKeyState.CHECKING
            scope.launch {
                val result = withContext(Dispatchers.IO) { validator.validate(SttProvider.GROQ, key.trim()) }
                when (result) {
                    KeyValidation.Valid -> { state = AddKeyState.VALID; onVerified() }
                    KeyValidation.Rejected -> state = AddKeyState.REJECTED
                    is KeyValidation.Unverified -> { state = AddKeyState.UNVERIFIED; onVerified() }
                }
            }
        },
        content = {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it; state = AddKeyState.IDLE },
                singleLine = true,
                label = { Text(copy(K.KEY_FIELD_LABEL)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions.Default,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            when (state) {
                AddKeyState.REJECTED -> Banner(copy(K.KEY_ERROR_INVALID), Icons.Rounded.Key, PillKind.ERROR)
                AddKeyState.VALID -> StatusPill(copy(K.KEY_VALID), PillKind.OK)
                else -> Unit
            }
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://console.groq.com/keys")))
            }) { Text(copy(K.KEY_WHERE)) }
        },
    )
}

@Composable
private fun HowItWorksStep(onNext: () -> Unit) {
    StepScaffold(
        illustration = { PermissionIllustration(Icons.Rounded.GraphicEq) },
        title = copy(K.HOWITWORKS_TITLE),
        primaryLabel = copy(K.HOWITWORKS_CTA),
        onPrimary = onNext,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(K.HOWITWORKS_STEP1, K.HOWITWORKS_STEP2, K.HOWITWORKS_STEP3).forEachIndexed { i, k ->
                    NumberedRow(i + 1, copy(k))
                }
            }
        },
    )
}

@Composable
private fun ChecklistStep(context: android.content.Context, onNext: () -> Unit) {
    val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    StepScaffold(
        title = copy(K.CHECKLIST_TITLE),
        primaryLabel = copy(K.CHECKLIST_CTA),
        onPrimary = onNext,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChecklistRow(copy(K.CHECKLIST_KEY), done = true)
                ChecklistRow(copy(K.CHECKLIST_MIC), done = hasMic)
                ChecklistRow(copy(K.CHECKLIST_OVERLAY), done = OverlayPermission.isGranted(context))
                ChecklistRow(copy(K.CHECKLIST_ACCESSIBILITY), done = AccessibilityUtil.isServiceEnabled(context))
            }
        },
    )
}

@Composable
private fun MicStep(onAllow: () -> Unit) {
    StepScaffold(
        illustration = { PermissionIllustration(Icons.Rounded.Mic) },
        title = copy(K.MIC_TITLE),
        body = copy(K.RATIONALE_MIC),
        primaryLabel = copy(K.MIC_CTA),
        onPrimary = onAllow,
    )
}

@Composable
private fun MicTestStep(onNext: () -> Unit) {
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

    StepScaffold(
        title = copy(K.MICTEST_TITLE),
        body = copy(K.MICTEST_BODY),
        primaryLabel = copy(K.MICTEST_CTA),
        onPrimary = onNext,
        illustration = {
            InkMeter(amplitude = amplitude, modifier = Modifier.size(width = 240.dp, height = 64.dp), recording = true)
        },
    )
}

@Composable
private fun OverlayStep(onAllow: () -> Unit) {
    StepScaffold(
        illustration = { PermissionIllustration(Icons.Rounded.PictureInPicture) },
        title = copy(K.OVERLAY_TITLE),
        body = copy(K.RATIONALE_OVERLAY),
        primaryLabel = copy(K.OVERLAY_CTA),
        onPrimary = onAllow,
    )
}

@Composable
private fun AccessibilityDisclosureStep(onAccept: () -> Unit, onDecline: () -> Unit) {
    // Binary-only compliance copy (locked keys).
    StepScaffold(
        illustration = { PermissionIllustration(Icons.Rounded.AccessibilityNew) },
        title = copy(K.DISCLOSURE_TITLE),
        body = copy(K.DISCLOSURE_BODY),
        primaryLabel = copy(K.DISCLOSURE_ACCEPT),
        onPrimary = onAccept,
        secondaryLabel = copy(K.DISCLOSURE_DECLINE),
        onSecondary = onDecline,
    )
}

@Composable
private fun AccessibilityEnableStep(onOpen: () -> Unit) {
    StepScaffold(
        illustration = { PermissionIllustration(Icons.Rounded.AccessibilityNew) },
        title = copy(K.ACC_ENABLE_TITLE),
        body = copy(K.RATIONALE_ACCESSIBILITY),
        primaryLabel = copy(K.ACC_ENABLE_CTA),
        onPrimary = onOpen,
    )
}

@Composable
private fun AllSetStep(onFinish: () -> Unit) {
    StepScaffold(
        illustration = { PermissionIllustration(Icons.Rounded.Check, variant = IllustrationVariant.OK) },
        title = copy(K.ALLSET_TITLE),
        body = copy(K.ALLSET_BODY),
        primaryLabel = copy(K.ALLSET_CTA),
        onPrimary = onFinish,
    )
}

@Composable
private fun NumberedRow(number: Int, text: String) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(28.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.quill.brandSoft),
            contentAlignment = Alignment.Center,
        ) { Text(number.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.quill.brandInk) }
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ChecklistRow(text: String, done: Boolean) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val tint = if (done) MaterialTheme.quill.ok else MaterialTheme.quill.faint
        Icon(Icons.Rounded.Check, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (done) StatusPill(copy(K.STATUS_DONE), PillKind.OK)
    }
}
