package com.tihor.quill.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.hypot

/**
 * A small "✕ Cancel" pill that floats just above the bubble while a dictation is being transcribed
 * or after it failed (board E). Tapping it aborts the in-flight transcription / clears the error and
 * returns the bubble to idle. A separate touchable `WindowManager` view so the tested [BubbleView]
 * geometry/touch stays untouched. Drawn on a white card with a soft shadow + an error-red glyph so it
 * reads over any host app.
 */
@SuppressLint("ViewConstructor")
class CancelButtonView(
    context: Context,
    var onTap: () -> Unit = {},
) : View(context) {

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val padH = dp(13f)
    private val shadowPad = dp(10f)
    private val iconSize = dp(11f)
    private val iconGap = dp(7f)
    private val cardH = dp(36f)
    private val label = "Cancel"

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(dp(14f), 0f, dp(4f), 0x40000000)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD6453D.toInt()
        textSize = dp(12.5f)
        isFakeBoldText = true
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD6453D.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(2.2f)
        strokeCap = Paint.Cap.ROUND
    }

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    private fun contentWidth() = iconSize + iconGap + textPaint.measureText(label)
    val viewWidth: Int get() = (contentWidth() + padH * 2 + shadowPad * 2).toInt()
    val viewHeight: Int get() = (cardH + shadowPad * 2).toInt()

    /** Distance from the view's bottom edge to the visible card's bottom — for flush tucking. */
    val bottomInset: Int get() = shadowPad.toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(viewWidth, viewHeight)
    }

    override fun onDraw(canvas: Canvas) {
        val left = shadowPad
        val top = (height - cardH) / 2f
        val right = width - shadowPad
        val bottom = top + cardH
        val radius = cardH / 2f
        canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, cardPaint)

        val cy = (top + bottom) / 2f
        val ix = left + padH
        val half = iconSize / 2f
        canvas.drawLine(ix, cy - half, ix + iconSize, cy + half, iconPaint) // ✕
        canvas.drawLine(ix, cy + half, ix + iconSize, cy - half, iconPaint)
        canvas.drawText(label, ix + iconSize + iconGap, cy + textPaint.textSize / 3f, textPaint)
    }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var downX = 0f
    private var downY = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (hypot(event.rawX - downX, event.rawY - downY) <= touchSlop * 2) onTap()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
