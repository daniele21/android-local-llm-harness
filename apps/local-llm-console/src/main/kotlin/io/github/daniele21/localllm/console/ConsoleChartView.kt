package io.github.daniele21.localllm.console

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import java.util.Locale
import kotlin.math.roundToInt

@Suppress("MagicNumber", "TooManyFunctions")
class ConsoleChartView(
    context: Context,
    private val chart: ConsoleChart,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val textColor = resolveTextColor()
    private val axisColor = Color.argb(90, Color.red(textColor), Color.green(textColor), Color.blue(textColor))
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = sp(12f)
    }
    private val titlePaint = Paint(textPaint).apply {
        textSize = sp(17f)
        isFakeBoldText = true
    }
    private val subtitlePaint = Paint(textPaint).apply { textSize = sp(12f) }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = axisColor
        strokeWidth = dp(1f)
        style = Paint.Style.STROKE
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = dp(2f)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val seriesColors = intArrayOf(
        Color.rgb(33, 150, 243),
        Color.rgb(255, 152, 0),
        Color.rgb(76, 175, 80),
        Color.rgb(156, 39, 176),
    )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = dp(360f).roundToInt()
        val desiredHeight = dp(330f).roundToInt()
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = paddingLeft + dp(8f)
        canvas.drawText(chart.title, left, dp(24f), titlePaint)
        canvas.drawText(fitText(chart.subtitle, width - left - dp(8f), subtitlePaint), left, dp(44f), subtitlePaint)
        drawLegend(canvas, left)

        val plot = RectF(
            left + dp(48f),
            dp(64f + chart.series.size * 18f),
            width - paddingRight - dp(12f),
            height - paddingBottom - dp(38f),
        )
        if (plot.width() <= 0f || plot.height() <= 0f) return

        val points = chart.series.flatMap { it.points }
        val values = points.mapNotNull { it.value }
        if (values.isEmpty()) {
            canvas.drawText("No available values", plot.left, plot.centerY(), textPaint)
            return
        }

        val minimumTimestamp = points.minOf { it.timestampEpochMs }
        val maximumTimestamp = points.maxOf { it.timestampEpochMs }
        val minimumValue = chart.minimumValue ?: values.min()
        val observedMaximum = chart.maximumValue ?: values.max()
        val maximumValue = when {
            observedMaximum > minimumValue -> observedMaximum
            else -> minimumValue + 1.0
        }
        val paddedMaximum = if (chart.maximumValue == null) {
            maximumValue + (maximumValue - minimumValue) * 0.05
        } else {
            maximumValue
        }

        drawGrid(canvas, plot, minimumValue, paddedMaximum)
        drawSeries(
            canvas = canvas,
            plot = plot,
            minimumTimestamp = minimumTimestamp,
            maximumTimestamp = maximumTimestamp,
            minimumValue = minimumValue,
            maximumValue = paddedMaximum,
        )
        drawTimeLabels(canvas, plot, minimumTimestamp, maximumTimestamp)
    }

    private fun drawLegend(canvas: Canvas, left: Float) {
        chart.series.forEachIndexed { index, series ->
            val y = dp(64f + index * 18f)
            val color = seriesColors[index % seriesColors.size]
            linePaint.color = color
            canvas.drawLine(left, y - dp(4f), left + dp(18f), y - dp(4f), linePaint)
            canvas.drawText(series.label, left + dp(24f), y, textPaint)
        }
    }

    private fun drawGrid(canvas: Canvas, plot: RectF, minimumValue: Double, maximumValue: Double) {
        val ticks = if (chart.valueLabels.isEmpty()) {
            (0..4).map { index -> minimumValue + (maximumValue - minimumValue) * index / 4.0 }
        } else {
            chart.valueLabels.keys.sorted().map(Int::toDouble)
        }
        ticks.forEach { value ->
            val y = valueToY(value, plot, minimumValue, maximumValue)
            canvas.drawLine(plot.left, y, plot.right, y, axisPaint)
            val label = formatValue(value)
            canvas.drawText(label, plot.left - dp(6f) - textPaint.measureText(label), y + dp(4f), textPaint)
        }
        canvas.drawRect(plot, axisPaint)
    }

    private fun drawSeries(
        canvas: Canvas,
        plot: RectF,
        minimumTimestamp: Long,
        maximumTimestamp: Long,
        minimumValue: Double,
        maximumValue: Double,
    ) {
        chart.series.forEachIndexed { index, series ->
            val color = seriesColors[index % seriesColors.size]
            linePaint.color = color
            pointPaint.color = color
            val path = Path()
            var segmentStarted = false
            series.points.sortedBy { it.timestampEpochMs }.forEach { point ->
                val value = point.value
                if (value == null) {
                    segmentStarted = false
                } else {
                    val x = timestampToX(point.timestampEpochMs, plot, minimumTimestamp, maximumTimestamp)
                    val y = valueToY(value, plot, minimumValue, maximumValue)
                    if (segmentStarted) path.lineTo(x, y) else path.moveTo(x, y)
                    segmentStarted = true
                    canvas.drawCircle(x, y, dp(2.5f), pointPaint)
                }
            }
            canvas.drawPath(path, linePaint)
        }
    }

    private fun drawTimeLabels(canvas: Canvas, plot: RectF, minimumTimestamp: Long, maximumTimestamp: Long) {
        canvas.drawText("0s", plot.left, plot.bottom + dp(20f), textPaint)
        val duration = formatDuration(maximumTimestamp - minimumTimestamp)
        canvas.drawText(duration, plot.right - textPaint.measureText(duration), plot.bottom + dp(20f), textPaint)
    }

    private fun timestampToX(timestamp: Long, plot: RectF, minimum: Long, maximum: Long): Float {
        if (maximum <= minimum) return plot.centerX()
        val ratio = (timestamp - minimum).toDouble() / (maximum - minimum).toDouble()
        return plot.left + plot.width() * ratio.toFloat()
    }

    private fun valueToY(value: Double, plot: RectF, minimum: Double, maximum: Double): Float {
        val ratio = (value - minimum) / (maximum - minimum)
        return plot.bottom - plot.height() * ratio.toFloat()
    }

    private fun formatValue(value: Double): String {
        chart.valueLabels[value.roundToInt()]?.let { return it }
        return when {
            value >= 1000.0 -> String.format(Locale.US, "%.0f %s", value, chart.valueUnit)
            else -> String.format(Locale.US, "%.1f %s", value, chart.valueUnit)
        }
    }

    private fun formatDuration(durationMs: Long): String = when {
        durationMs >= 60_000 -> String.format(Locale.US, "+%.1f min", durationMs / 60_000.0)
        durationMs >= 1_000 -> String.format(Locale.US, "+%.1f s", durationMs / 1_000.0)
        else -> "+$durationMs ms"
    }

    private fun fitText(value: String, maximumWidth: Float, paint: Paint): String {
        if (paint.measureText(value) <= maximumWidth) return value
        val ellipsis = "…"
        val available = maximumWidth - paint.measureText(ellipsis)
        val count = paint.breakText(value, true, available, null)
        return value.take(count) + ellipsis
    }

    private fun resolveTextColor(): Int {
        val attributes = context.obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
        return try {
            attributes.getColor(0, Color.DKGRAY)
        } finally {
            attributes.recycle()
        }
    }

    private fun dp(value: Float): Float = value * density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
