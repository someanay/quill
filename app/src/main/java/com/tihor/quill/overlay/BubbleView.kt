package com.tihor.quill.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

/**
 * The floating bubble, drawn on a [Canvas] (no Compose-in-overlay lifecycle plumbing for M1).
 *
 * Legibility over any background (spec §3.1): every state has a soft drop shadow (reads on light
 * surfaces) *and* a white stroke ring (reads on dark surfaces) around a state-coloured fill.
 *
 * Touch: drag to move (the host positions the window), tap to [onTap], long-press to [onLongPress].
 * Tap-vs-drag and the work of moving the window live in the host service; this view only reports
 * gestures and exposes the press-origin so the host can compute deltas.
 */
@SuppressLint("ViewConstructor")
class BubbleView(
    context: Context,
    var onTap: () -> Unit = {},
    var onLongPress: () -> Unit = {},
    var onDragStart: () -> Unit = {},
    var onDrag: (dxPx: Float, dyPx: Float) -> Unit = { _, _ -> },
    var onDragEnd: () -> Unit = {},
) : View(context) {

    var state: BubbleState = BubbleState.IDLE
        set(value) {
            field = value
            invalidate()
        }

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    /** Visible circle diameter; the view is larger to leave room for the shadow. */
    private val diameter = dp(56f)
    private val shadowPad = dp(6f)
    val viewSize = (diameter + shadowPad * 2).toInt()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = Color.WHITE
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
    }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val handler = Handler(Looper.getMainLooper())

    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var dragging = false
    private var longPressed = false
    private val longPressRunnable = Runnable {
        longPressed = true
        onLongPress()
    }

    init {
        // Software layer so Paint.setShadowLayer renders reliably.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private fun fillColor(state: BubbleState): Int = when (state) {
        BubbleState.IDLE -> Color.argb(0xCC, 0x42, 0x47, 0x4F)        // slate, semi-transparent
        BubbleState.RECORDING -> Color.rgb(0xE5, 0x39, 0x35)          // red
        BubbleState.TRANSCRIBING -> Color.rgb(0xFB, 0x8C, 0x00)       // amber
        BubbleState.PASTE -> Color.rgb(0x1E, 0x88, 0xE5)             // blue
        BubbleState.ERROR -> Color.rgb(0xC6, 0x28, 0x28)            // deep red
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(viewSize, viewSize)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = diameter / 2f

        fillPaint.color = fillColor(state)
        fillPaint.setShadowLayer(dp(4f), 0f, dp(1.5f), 0x66000000)
        canvas.drawCircle(cx, cy, r, fillPaint)
        // Ring is drawn without a shadow.
        canvas.drawCircle(cx, cy, r - ringPaint.strokeWidth / 2f, ringPaint)

        drawGlyph(canvas, cx, cy, r)
    }

    private fun drawGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        when (state) {
            BubbleState.IDLE -> Unit // bare circle
            BubbleState.RECORDING -> canvas.drawCircle(cx, cy, r * 0.32f, glyphPaint)
            BubbleState.TRANSCRIBING -> {
                val dot = r * 0.13f
                val gap = r * 0.42f
                canvas.drawCircle(cx - gap, cy, dot, glyphPaint)
                canvas.drawCircle(cx, cy, dot, glyphPaint)
                canvas.drawCircle(cx + gap, cy, dot, glyphPaint)
            }
            BubbleState.PASTE -> drawText(canvas, "P", cx, cy, r)
            BubbleState.ERROR -> drawText(canvas, "!", cx, cy, r)
        }
    }

    private fun drawText(canvas: Canvas, text: String, cx: Float, cy: Float, r: Float) {
        glyphPaint.textSize = r * 1.1f
        glyphPaint.isFakeBoldText = true
        val baseline = cy - (glyphPaint.descent() + glyphPaint.ascent()) / 2f
        canvas.drawText(text, cx, baseline, glyphPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                dragging = false
                longPressed = false
                handler.postDelayed(longPressRunnable, longPressTimeout)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging &&
                    !BubbleGeometry.isTap(downRawX, downRawY, event.rawX, event.rawY, touchSlop)
                ) {
                    dragging = true
                    handler.removeCallbacks(longPressRunnable)
                    onDragStart()
                }
                if (dragging) {
                    onDrag(event.rawX - lastRawX, event.rawY - lastRawY)
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                when {
                    longPressed -> Unit // already handled
                    dragging -> onDragEnd()
                    else -> onTap()
                }
                dragging = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                if (dragging) onDragEnd()
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
