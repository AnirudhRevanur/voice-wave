package com.voicewave.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Full-screen custom view that draws an animated glowing border.
 *
 * HOW IT WORKS:
 * We draw a rounded-rect stroke around the screen edge using a SweepGradient
 * (a gradient that spins around like a color wheel).
 * We rotate that gradient over time via a ValueAnimator, making the colors
 * flow around the border continuously.
 *
 * The "glow" is drawn as multiple layered strokes: wide+faint → narrow+bright.
 * No actual blur needed — layering creates the soft glow look.
 */
class GlowBorderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val rect = RectF()
    private val cornerRadius = 64f
    private val strokeWidth = 6f
    private var rotationAngle = 0f

    // Blue → purple → pink → back to blue, flowing around the border
    private val glowColors = intArrayOf(
        Color.parseColor("#5b8fff"),
        Color.parseColor("#818cf8"),
        Color.parseColor("#a78bfa"),
        Color.parseColor("#c084fc"),
        Color.parseColor("#e879f9"),
        Color.parseColor("#c084fc"),
        Color.parseColor("#a78bfa"),
        Color.parseColor("#5b8fff"),
    )

    private var animator: ValueAnimator? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startGlow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    private fun startGlow() {
        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotationAngle = it.animatedValue as Float
                invalidate() // tells Android to call onDraw again
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val margin = strokeWidth * 4
        rect.set(margin, margin, width - margin, height - margin)

        val cx = width / 2f
        val cy = height / 2f

        // Rotate the sweep gradient so it appears to flow around the border
        val matrix = Matrix()
        matrix.postRotate(rotationAngle, cx, cy)
        val gradient = SweepGradient(cx, cy, glowColors, null)
        gradient.setLocalMatrix(matrix)

        // Layered glow: wide+transparent → narrow+opaque
        val layers = listOf(
            Pair(strokeWidth * 8,   40),
            Pair(strokeWidth * 5,   80),
            Pair(strokeWidth * 3,  140),
            Pair(strokeWidth * 1.5f, 200),
            Pair(strokeWidth,       255),
        )

        for ((layerWidth, alpha) in layers) {
            paint.shader = gradient
            paint.strokeWidth = layerWidth
            paint.alpha = alpha
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        }
    }
}
