package com.tihor.quill.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.text.TextUtils
import com.tihor.quill.copy.Copy
import com.tihor.quill.errors.DictationIssue
import com.tihor.quill.errors.Recovery
import kotlin.math.max

/**
 * The little status card that sits beside the bubble (design board E2–E5 / F3): the ink-stroke meter
 * + timer while recording, "Transcribing…", "Tap to paste", the error line, and the offline line.
 * A separate `WindowManager` view so the tested [BubbleView] geometry/touch stays untouched. Drawn on
 * a white card with a soft shadow so it reads over any host app, like the bubble's contrast ring.
 */
@SuppressLint("ViewConstructor")
class CaptionView(context: Context) : View(context) {

    /** What the caption shows. The service maps a [BubbleState] (+ offline) onto one of these. */
    enum class Mode { RECORDING, TRANSCRIBING, PASTE, ERROR, OFFLINE }

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private var errorTitle: String = Copy.repository(context).get(DictationIssue.UNKNOWN.titleKey)
    private var errorAction: String = Copy.repository(context).get(Recovery.RECORD_AGAIN.labelKey)

    private var mode: Mode = Mode.TRANSCRIBING
    private var level: Float = 0f
    private var timer: String = "0:00"

    /** When set, this dictation targets the clipboard (long-press), so the copy re-labels accordingly. */
    var clipboardTarget: Boolean = false
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    private val padH = dp(12f)
    private val padV = dp(9f)
    private val shadowPad = dp(10f)
    private val cardH1 = dp(40f) // single line
    private val cardH2 = dp(54f) // two lines (error / offline) — taller so they don't overlap
    private fun cardH(): Float = if (lines().second != null) cardH2 else cardH1
    private val meterBars = 6
    private val barW = dp(2.5f)
    private val barGap = dp(2.5f)
    private val meterW = meterBars * barW + (meterBars - 1) * barGap

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(dp(14f), 0f, dp(4f), 0x40000000)
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF234DCE.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = context.resources.getFont(com.tihor.quill.R.font.ibm_plex_sans)
        textSize = dp(12f)
        isFakeBoldText = true
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = context.resources.getFont(com.tihor.quill.R.font.ibm_plex_sans); textSize = dp(11f) }

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    fun configure(mode: Mode, level: Float = 0f, seconds: Int = 0) {
        this.mode = mode
        contentDescription = null
        this.level = level.coerceIn(0f, 1f)
        this.timer = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
        requestLayout()
        invalidate()
    }

    fun configureError(title: String, action: String, explanation: String) {
        errorTitle = title
        errorAction = action
        configure(Mode.ERROR)
        contentDescription = "$title. $explanation. $action"
    }

    fun setLevel(level: Float) {
        this.level = level.coerceIn(0f, 1f)
        if (mode == Mode.RECORDING) invalidate()
    }

    /** Content text for the single/lead line and optional sub-line per mode. */
    private fun lines(): Pair<String, String?> = when (mode) {
        Mode.RECORDING -> "" to null
        Mode.TRANSCRIBING -> (if (clipboardTarget) "Transcribing → clipboard" else "Transcribing…") to null
        Mode.PASTE -> if (clipboardTarget) "Copied to clipboard" to "Paste anywhere" else "Tap to paste" to null
        Mode.ERROR -> errorTitle to errorAction
        Mode.OFFLINE -> "No connection" to "Saved · Tap to retry"
    }

    private fun leadColor(): Int = when (mode) {
        Mode.TRANSCRIBING, Mode.PASTE -> 0xFF1A3FAF.toInt() // brand-ink
        Mode.ERROR, Mode.OFFLINE -> 0xFFD6453D.toInt()      // error
        Mode.RECORDING -> 0xFF1A3FAF.toInt()                // gold-deep
    }

    private fun contentWidth(): Float {
        if (mode == Mode.RECORDING) return meterW + dp(8f) + textPaint.measureText(timer)
        val (lead, sub) = lines()
        return max(textPaint.measureText(lead), sub?.let { subPaint.measureText(it) } ?: 0f)
    }

    val viewWidth: Int get() = (contentWidth() + padH * 2 + shadowPad * 2)
        .coerceAtMost(resources.displayMetrics.widthPixels * 0.72f).toInt()
    val viewHeight: Int get() = (cardH() + shadowPad * 2).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(viewWidth, viewHeight)
    }

    override fun onDraw(canvas: Canvas) {
        val cardH = cardH()
        val left = shadowPad
        val top = (height - cardH) / 2f
        val right = width - shadowPad
        val bottom = top + cardH
        val radius = cardH1 / 2f
        canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, cardPaint)

        val cy = (top + bottom) / 2f
        if (mode == Mode.RECORDING) {
            // ink-stroke meter
            val heights = floatArrayOf(0.35f, 0.7f, 1f, 0.6f, 0.85f, 0.45f)
            var x = left + padH
            for (i in 0 until meterBars) {
                val h = (dp(6f) + (cardH - padV * 2 - dp(6f)) * heights[i] * (0.3f + 0.7f * level))
                val bTop = cy - h / 2f
                canvas.drawRoundRect(RectF(x, bTop, x + barW, bTop + h), barW / 2f, barW / 2f, barPaint)
                x += barW + barGap
            }
            textPaint.color = leadColor()
            x += dp(6f)
            canvas.drawText(timer, x, cy + textPaint.textSize / 3f, textPaint)
            return
        }

        val (rawLead, rawSub) = lines()
        val available = (width - shadowPad * 2 - padH * 2).coerceAtLeast(0f)
        val lead = TextUtils.ellipsize(rawLead, android.text.TextPaint(textPaint), available, TextUtils.TruncateAt.END).toString()
        val sub = rawSub?.let { TextUtils.ellipsize(it, android.text.TextPaint(subPaint), available, TextUtils.TruncateAt.END).toString() }
        textPaint.color = leadColor()
        if (sub == null) {
            canvas.drawText(lead, left + padH, cy + textPaint.textSize / 3f, textPaint)
        } else {
            // Two lines: stack a leading + muted sub-line, centred, with a gap so they never overlap.
            val gap = dp(6f)
            val blockTop = cy - (textPaint.textSize + gap + subPaint.textSize) / 2f
            canvas.drawText(lead, left + padH, blockTop + textPaint.textSize, textPaint)
            subPaint.color = 0xFF5C665F.toInt() // muted
            canvas.drawText(sub, left + padH, blockTop + textPaint.textSize + gap + subPaint.textSize, subPaint)
        }
    }
}
