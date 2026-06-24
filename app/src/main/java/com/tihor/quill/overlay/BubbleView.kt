package com.tihor.quill.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AnimationUtils
import kotlin.math.sin

/**
 * The floating bubble, drawn on a [Canvas] in a `WindowManager` overlay (design_system.md §9 — no
 * Compose-in-overlay plumbing). Identity baked in so it stays legible over **any** app:
 * a 2.5 dp white contrast ring (reads on dark hosts) + a soft drop shadow (reads on light hosts),
 * around a state-coloured gradient fill. Recording adds the gold "listening" halo with a 1.8 s pulse.
 *
 * Touch: drag to move, tap to [onTap], long-press to [onLongPress] — gestures reported to the host.
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
            updateAnimation()
            invalidate()
        }

    /** Live input level 0..1 for the recording meter (design_system.md §9/§10). */
    var level: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            if (state == BubbleState.RECORDING) invalidate()
        }

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val diameter = dp(56f)            // §9 base geometry
    private val haloMax = dp(13f)             // gold halo reaches ~13 dp out
    private val shadowPad = dp(12f)
    val viewSize = (diameter + (haloMax + shadowPad) * 2).toInt()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)                // §9 white contrast ring
        color = Color.argb(0xEB, 0xFF, 0xFF, 0xFF) // rgba(255,255,255,0.92)
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val glyphStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(2.2f)
        strokeCap = Paint.Cap.ROUND
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

    private val reducedMotion: Boolean
        get() = Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) == 0f

    private val animator = Runnable { invalidate() }
    private var animating = false

    init {
        // Software layer so Paint.setShadowLayer renders reliably.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    /** §2.4 gradient stops per state (150° linear). */
    private fun gradientFor(state: BubbleState): IntArray = when (state) {
        BubbleState.IDLE -> intArrayOf(0xFF4A3FD0.toInt(), 0xFF6A5BEA.toInt())       // Brand
        BubbleState.RECORDING -> intArrayOf(0xFFE0A03A.toInt(), 0xFFEFC069.toInt())  // Gold (live)
        BubbleState.TRANSCRIBING -> intArrayOf(0xFF5B5BD6.toInt(), 0xFF7E78E8.toInt()) // Busy
        BubbleState.PASTE -> intArrayOf(0xFF4A3FD0.toInt(), 0xFF6A5BEA.toInt())      // Brand
        BubbleState.ERROR -> intArrayOf(0xFFD6453D.toInt(), 0xFFE8635B.toInt())      // Error
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(viewSize, viewSize)
    }

    private fun updateAnimation() {
        val shouldAnimate = !reducedMotion &&
            (state == BubbleState.RECORDING || state == BubbleState.TRANSCRIBING)
        if (shouldAnimate && !animating) {
            animating = true
            postOnAnimation(animator)
        } else if (!shouldAnimate) {
            animating = false
            removeCallbacks(animator)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = diameter / 2f
        val now = AnimationUtils.currentAnimationTimeMillis()

        // Recording gold halo with a 1.8 s breathe (§9). Drawn behind the fill.
        if (state == BubbleState.RECORDING) {
            val phase = if (reducedMotion) 0.5f else (sin(now / 1800.0 * 2 * Math.PI) * 0.5 + 0.5).toFloat()
            val haloR = r + dp(7f) + dp(6f) * phase
            val haloAlpha = (0x47 - (0x2D * phase)).toInt().coerceIn(0x10, 0x47) // .28 → .10
            haloPaint.color = Color.argb(haloAlpha, 0xE0, 0xA0, 0x3A)
            canvas.drawCircle(cx, cy, haloR, haloPaint)
        }

        // Soft drop shadow (§9: y10 blur24 rgba(28,27,51,.34)); error tints its shadow red.
        val shadowColor = if (state == BubbleState.ERROR) 0x4D961E1E.toInt() else 0x57_1C_1B_33
        fillPaint.setShadowLayer(dp(12f), 0f, dp(5f), shadowColor)
        fillPaint.shader = null
        fillPaint.color = Color.WHITE // base for shadow; overdrawn by gradient
        canvas.drawCircle(cx, cy, r, fillPaint)

        // Gradient fill (150° ≈ top-left → bottom-right).
        fillPaint.clearShadowLayer()
        fillPaint.shader = LinearGradient(
            cx - r, cy - r, cx + r, cy + r, gradientFor(state), null, Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, r, fillPaint)
        fillPaint.shader = null

        // White contrast ring.
        canvas.drawCircle(cx, cy, r - ringPaint.strokeWidth / 2f, ringPaint)

        drawGlyph(canvas, cx, cy, r, now)

        // Keep the pulse/spinner going while animating.
        if (animating) postOnAnimation(animator)
    }

    private fun drawGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float, now: Long) {
        when (state) {
            BubbleState.IDLE, BubbleState.RECORDING -> drawMic(canvas, cx, cy, r)
            BubbleState.TRANSCRIBING -> drawSpinner(canvas, cx, cy, r, now)
            BubbleState.PASTE -> drawCopy(canvas, cx, cy, r)
            BubbleState.ERROR -> drawBang(canvas, cx, cy, r)
        }
    }

    private fun drawMic(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val bodyW = r * 0.42f
        val bodyH = r * 0.62f
        val top = cy - r * 0.5f
        val body = RectF(cx - bodyW / 2, top, cx + bodyW / 2, top + bodyH)
        canvas.drawRoundRect(body, bodyW / 2, bodyW / 2, glyphPaint)
        val cradle = r * 0.4f
        canvas.drawArc(
            RectF(cx - cradle, cy - cradle * 0.7f, cx + cradle, cy + cradle * 0.7f),
            25f, 130f, false, glyphStroke,
        )
        canvas.drawLine(cx, cy + cradle * 0.7f, cx, cy + r * 0.55f, glyphStroke)
        canvas.drawLine(cx - r * 0.22f, cy + r * 0.55f, cx + r * 0.22f, cy + r * 0.55f, glyphStroke)
    }

    private fun drawSpinner(canvas: Canvas, cx: Float, cy: Float, r: Float, now: Long) {
        val sweepStart = if (reducedMotion) 0f else (now % 1000L) / 1000f * 360f
        val rad = r * 0.5f
        canvas.drawArc(
            RectF(cx - rad, cy - rad, cx + rad, cy + rad),
            sweepStart, 270f, false, glyphStroke,
        )
    }

    private fun drawCopy(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val s = r * 0.5f
        canvas.drawRoundRect(RectF(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2), dp(3f), dp(3f), glyphStroke)
        canvas.drawRoundRect(
            RectF(cx - s / 2 - dp(3f), cy - s / 2 - dp(3f), cx + s / 2 - dp(3f), cy + s / 2 - dp(3f)),
            dp(3f), dp(3f), glyphStroke,
        )
    }

    private fun drawBang(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        glyphStroke.strokeWidth = dp(3f)
        canvas.drawLine(cx, cy - r * 0.42f, cx, cy + r * 0.12f, glyphStroke)
        canvas.drawCircle(cx, cy + r * 0.38f, dp(2.2f), glyphPaint)
        glyphStroke.strokeWidth = dp(2.2f)
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
                    longPressed -> Unit
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

    override fun onDetachedFromWindow() {
        animating = false
        removeCallbacks(animator)
        super.onDetachedFromWindow()
    }
}
