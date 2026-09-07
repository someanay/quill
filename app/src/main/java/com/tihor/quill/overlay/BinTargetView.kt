package com.tihor.quill.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.tihor.quill.R
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The drag-to-dismiss bin target (v1.1, Wispr-Flow-style): a circular trash target that fades and
 * grows in near the bottom of the screen while the user drags the bubble down, and pops to error-red
 * once releasing would actually hide it. Lives in its own `NOT_FOCUSABLE | NOT_TOUCHABLE` overlay
 * window owned by [OverlayService] — it must never eat the drag it's reacting to.
 *
 * Canvas-drawn like [BubbleView] / [CancelButtonView] (design_system.md §9 — no Compose in the
 * overlay), with the bin glyph coming from the `ic_bin_target` vector drawable. Every visual is
 * driven by [progress] and [active]; the view holds no state of its own beyond the short pop
 * animation, so the service can drive it straight from the drag with no coordination.
 */
@SuppressLint("ViewConstructor")
class BinTargetView(
    context: Context,
    private val label: String,
    private val activeLabel: String,
) : View(context) {

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val baseRadius = dp(30f)                 // 60 dp resting target
    private val maxRadius = baseRadius * ACTIVE_SCALE
    private val shadowPad = dp(12f)
    private val labelGap = dp(9f)
    private val pillHeight = dp(23f)
    private val pillPadH = dp(11f)

    /**
     * Proximity to the dismiss zone, 0..1 (see [BubbleGeometry.dismissProximity]). Drives the whole
     * target's opacity and how far it has grown in. The service keeps the window GONE at 0.
     */
    var progress: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    /** True while the bubble is actually inside the dismiss zone — pops the target and turns it red. */
    var active: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            animateActive(if (value) 1f else 0f)
        }

    /** Eased 0..1 mirror of [active], so the pop isn't a jump-cut. */
    private var activeAmount = 0f
    private var popAnimator: ValueAnimator? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = Color.WHITE
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = context.resources.getFont(R.font.ibm_plex_sans)
        textSize = dp(12.5f)
        isFakeBoldText = true
    }

    private val binIcon = ContextCompat.getDrawable(context, R.drawable.ic_bin_target)

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) } // Paint.setShadowLayer needs a software layer

    // Size for the *widest* label so the window never has to be resized mid-drag.
    private val labelWidth = max(textPaint.measureText(label), textPaint.measureText(activeLabel))

    val viewWidth: Int get() =
        (max(maxRadius * 2, labelWidth + pillPadH * 2) + shadowPad * 2).roundToInt()

    val viewHeight: Int get() =
        (shadowPad + maxRadius * 2 + labelGap + pillHeight + shadowPad).roundToInt()

    /** Y of the target circle's centre within the view — lets the service aim the fly-in animation. */
    val circleCenterY: Int get() = (shadowPad + maxRadius).roundToInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(viewWidth, viewHeight)
    }

    private fun animateActive(target: Float) {
        popAnimator?.cancel()
        popAnimator = ValueAnimator.ofFloat(activeAmount, target).apply {
            duration = POP_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                activeAmount = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val p = progress
        if (p <= 0f) return

        val cx = width / 2f
        val cy = circleCenterY.toFloat()
        // Grows in as the bubble approaches, then pops when releasing would dismiss.
        val radius = baseRadius * (0.74f + 0.26f * p) * (1f + (ACTIVE_SCALE - 1f) * activeAmount)

        fillPaint.color = withAlpha(lerpColor(INK, ERR, activeAmount), 0.9f * p)
        fillPaint.setShadowLayer(dp(14f), 0f, dp(5f), withAlpha(INK, 0.4f * p))
        canvas.drawCircle(cx, cy, radius, fillPaint)
        fillPaint.clearShadowLayer()

        ringPaint.color = withAlpha(Color.WHITE, (0.5f + 0.4f * activeAmount) * p)
        canvas.drawCircle(cx, cy, radius - ringPaint.strokeWidth / 2f, ringPaint)

        binIcon?.let { icon ->
            val half = (radius * 0.46f).roundToInt()
            icon.setBounds(cx.roundToInt() - half, cy.roundToInt() - half, cx.roundToInt() + half, cy.roundToInt() + half)
            icon.setTint(withAlpha(Color.WHITE, p))
            icon.draw(canvas)
        }

        // Caption pill under the target — dark card so the copy reads over a light host app too.
        val text = if (active) activeLabel else label
        val textW = textPaint.measureText(text)
        val pillTop = shadowPad + maxRadius * 2 + labelGap
        val pillRect = RectF(
            cx - textW / 2f - pillPadH,
            pillTop,
            cx + textW / 2f + pillPadH,
            pillTop + pillHeight,
        )
        pillPaint.color = withAlpha(lerpColor(INK, ERR, activeAmount), 0.92f * p)
        canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, pillPaint)
        textPaint.color = withAlpha(Color.WHITE, p)
        canvas.drawText(text, cx - textW / 2f, pillRect.centerY() + textPaint.textSize / 3f, textPaint)
    }

    override fun onDetachedFromWindow() {
        popAnimator?.cancel()
        popAnimator = null
        super.onDetachedFromWindow()
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        Color.argb((255 * alpha.coerceIn(0f, 1f)).roundToInt(), Color.red(color), Color.green(color), Color.blue(color))

    private fun lerpColor(from: Int, to: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(from) + (Color.red(to) - Color.red(from)) * k).roundToInt(),
            (Color.green(from) + (Color.green(to) - Color.green(from)) * k).roundToInt(),
            (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * k).roundToInt(),
        )
    }

    private companion object {
        /** design_system.md §2.1 `text` (ink) and `err`. */
        const val INK = 0xFF202522.toInt()
        const val ERR = 0xFFD6453D.toInt()

        /** How much the target grows once the bubble is inside the dismiss zone. */
        const val ACTIVE_SCALE = 1.16f
        const val POP_MS = 130L
    }
}
