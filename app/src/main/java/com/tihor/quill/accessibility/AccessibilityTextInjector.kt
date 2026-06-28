package com.tihor.quill.accessibility

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * [TextInjector] over the accessibility APIs (spec §5B/§5C).
 *
 * Happy path: resolve the editable node → read its text + caret → compose the full new string
 * ([InsertionComposer]) → `ACTION_SET_TEXT` → reposition the caret with `ACTION_SET_SELECTION`.
 * If no SET_TEXT-capable node exists (WebViews) or SET_TEXT fails, fall back to the clipboard and
 * let the user paste; restore their previous clip only after a *confirmed* paste (never a silent
 * timed restore — spec §5C rung 3).
 */
class AccessibilityTextInjector(private val context: Context) : TextInjector {

    private val clipboard
        get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private var priorClip: ClipData? = null
    private var pendingTranscript: String? = null

    override fun insert(focus: AccessibilityNodeInfo, text: String): InsertResult {
        // Sensitive-field guard (P0-5) FIRST, on the field that actually holds input focus — even
        // if it lacks SET_TEXT. Otherwise the SET_TEXT target search could wander to a non-sensitive
        // sibling and inject there while the user is on a password field.
        val focusedEditable = EditableNodeResolver.findFocusedEditable(focus)
            ?: focus.takeIf { it.isEditable }
        if (focusedEditable != null && isSensitive(focusedEditable)) {
            Log.i(TAG, "Refusing: focused field is sensitive (${focusedEditable.className}).")
            return InsertResult.RefusedSensitive
        }

        // Prefer the focused editable itself when it can take SET_TEXT (don't jump to a sibling).
        val target = when {
            focusedEditable != null && EditableNodeResolver.supportsSetText(focusedEditable) ->
                focusedEditable
            else -> EditableNodeResolver.resolve(focus)
        }
        if (target != null && isSensitive(target)) {
            Log.i(TAG, "Refusing injection into sensitive field (${target.className}).")
            return InsertResult.RefusedSensitive
        }
        if (target == null) {
            Log.i(TAG, "No SET_TEXT-capable node (likely WebView); using clipboard fallback.")
            return clipboardFallback(text)
        }
        return trySetText(target, text)
    }

    private fun trySetText(target: AccessibilityNodeInfo, text: String): InsertResult {
        // Insert at the caret, preserving existing field content (InsertionComposer). SET_TEXT
        // replaces the whole field, so we re-send existing text + the spliced-in transcript.
        // Log only hint + flags + selection, never the field content (privacy, spec §6).
        Log.d(
            TAG,
            "field hint='${target.hintText}' showingHint=${target.isShowingHintText} " +
                "textLen=${target.text?.length ?: 0} sel=${target.textSelectionStart}/${target.textSelectionEnd}",
        )
        val comp = InsertionComposer.composeForInjection(
            existingText = target.text,
            transcript = text,
            selectionStart = target.textSelectionStart,
            selectionEnd = target.textSelectionEnd,
            isShowingHintText = target.isShowingHintText,
            hintText = target.hintText,
        )
        val setArgs = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, comp.newText)
        }
        val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)
        if (!ok) {
            Log.i(TAG, "ACTION_SET_TEXT rejected; using clipboard fallback.")
            return clipboardFallback(text)
        }
        // SET_TEXT often resets the caret — reposition it to just after the inserted text.
        val selArgs = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, comp.newCaret)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, comp.newCaret)
        }
        runCatching { target.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs) }
        Log.i(TAG, "Inserted via ACTION_SET_TEXT (${text.length} chars).")
        return InsertResult.Success
    }

    private fun clipboardFallback(text: String): InsertResult {
        priorClip = runCatching { clipboard.primaryClip }.getOrNull()
        pendingTranscript = text
        val clip = ClipData.newPlainText("Quill dictation", text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
        return InsertResult.NeedsClipboardFallback
    }

    override fun paste(focus: AccessibilityNodeInfo): Boolean {
        val transcript = pendingTranscript
        val pasted = focus.performAction(AccessibilityNodeInfo.ACTION_PASTE)

        // Confirm the transcript actually landed before touching the user's clipboard again.
        val confirmed = transcript != null && run {
            runCatching { focus.refresh() }
            focus.text?.toString()?.contains(transcript) == true
        }

        if (confirmed && priorClip != null) {
            runCatching { clipboard.setPrimaryClip(priorClip!!) }
            priorClip = null
            pendingTranscript = null
            Log.i(TAG, "Paste confirmed; restored previous clipboard.")
        } else if (!confirmed) {
            // Leave the transcript on the clipboard so the user can paste manually; do NOT restore.
            Log.i(TAG, "Paste not confirmed; leaving transcript on clipboard.")
        }
        return pasted && confirmed
    }

    private fun isSensitive(node: AccessibilityNodeInfo): Boolean =
        SensitiveFieldClassifier.isSensitive(
            SensitiveFieldClassifier.EditableFieldInfo(
                isPassword = node.isPassword,
                inputType = node.inputType,
                className = node.className,
                packageName = node.packageName,
            ),
        )

    companion object {
        private const val TAG = "Quill.Inject"
    }
}
