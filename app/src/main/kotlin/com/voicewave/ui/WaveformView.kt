package com.voicewave.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.sin

/**
 * Draws the animated waveform bars shown while listening.
 *
 * Each bar animates up and down with a slight delay (phase offset),
 * creating a ripple/wave effect across the bars.
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val barCount = 11
    private val barWidth = 6f
    private val barGap = 8f
    private val minBarHeight = 8f
    private val maxBarHeight = 80f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var phase = 0f
    private var animator: ValueAnimator? = null

    // Colors for each bar — left to right: blue → purple → pink → purple → blue
    private val barColors = listOf(
        "#5b8fff", "#6d8fff", "#818cf8",
        "#a78bfa", "#c084fc",
        "#e879f9",
        "#c084fc", "#a78bfa",
        "#818cf8", "#6d8fff", "#5b8fff"
    ).map { Color.parseColor(it) }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 1100
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val totalWidth = barCount * barWidth + (barCount - 1) * barGap
        val startX = (width - totalWidth) / 2f
        val centerY = height / 2f

        for (i in 0 until barCount) {
            // Each bar has a phase offset so they wave sequentially
            val phaseOffset = i * (Math.PI / barCount).toFloat()
            val sinVal = sin((phase + phaseOffset).toDouble()).toFloat()
            // sinVal goes -1 to 1 — remap to minHeight..maxHeight
            val barHeight = minBarHeight + ((sinVal + 1f) / 2f) * (maxBarHeight - minBarHeight)

            val x = startX + i * (barWidth + barGap)
            val top = centerY - barHeight / 2f
            val bottom = centerY + barHeight / 2f

            paint.color = barColors[i]
            canvas.drawRoundRect(x, top, x + barWidth, bottom, barWidth / 2, barWidth / 2, paint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (barCount * barWidth + (barCount - 1) * barGap + 0.5f).toInt()
        val desiredHeight = maxBarHeight.toInt() + 16
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }
}
