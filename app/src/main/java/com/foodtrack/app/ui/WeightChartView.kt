package com.foodtrack.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.foodtrack.app.R
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Weight trend line. [values] is always oldest → newest. On screen, the
 * timeline mirrors layout direction: in RTL (Hebrew) the oldest point is on
 * the right and the line reads right-to-left toward today on the left,
 * matching how a Hebrew calendar reads; in LTR it runs the usual left-to-right.
 * The value axis moves to whichever side is the reading-start side, so it
 * stays next to the start of the timeline in both directions. Shows a
 * labelled Y axis (top / middle / bottom), the latest value highlighted,
 * optional first/last date labels, an empty state, and a spoken summary for
 * TalkBack.
 */
class WeightChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    // A "halo" cut into the line right at the latest point, so the dot reads as
    // sitting on top rather than blending into the line. Must match the card's
    // own background, not a hardcoded white, or it shows up as a bright ring
    // floating on the dark surface in dark mode.
    private val pointRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.surface)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val axisText = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dateText = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valueText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val emptyText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private val linePath = Path()
    private val fillPath = Path()

    private var values: List<Float> = emptyList()
    private var startLabel: String? = null
    private var endLabel: String? = null

    init {
        val line = ContextCompat.getColor(context, R.color.chart_line)
        linePaint.color = line
        linePaint.strokeWidth = dp(2.5f)
        pointPaint.color = line
        fillPaint.color = ContextCompat.getColor(context, R.color.chart_fill)
        gridPaint.color = ContextCompat.getColor(context, R.color.chart_grid)
        gridPaint.strokeWidth = dp(1f)
        val secondary = ContextCompat.getColor(context, R.color.text_secondary)
        axisText.color = secondary
        axisText.textSize = sp(11f)
        dateText.color = secondary
        dateText.textSize = sp(11f)
        valueText.color = ContextCompat.getColor(context, R.color.text_primary)
        valueText.textSize = sp(12f)
        emptyText.color = secondary
        emptyText.textSize = sp(14f)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        updateDescription()
    }

    fun setValues(newValues: List<Float>) {
        values = newValues
        updateDescription()
        invalidate()
    }

    /** Optional short date labels for the oldest and newest points (chronological order). */
    fun setDateRange(start: String?, end: String?) {
        startLabel = start
        endLabel = end
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = dp(160f).toInt()
        val h = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST -> minOf(desired, MeasureSpec.getSize(heightMeasureSpec))
            else -> desired
        }
        setMeasuredDimension(resolveSize(dp(200f).toInt(), widthMeasureSpec), h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val axisWidth = axisText.measureText("000.0") + dp(8f)
        // The value axis sits on the reading-start side: right for RTL, left for LTR.
        val chartLeft = paddingLeft + if (rtl) dp(10f) else axisWidth
        val chartRight = width - paddingRight - if (rtl) axisWidth else dp(10f)
        val hasDates = !startLabel.isNullOrBlank() || !endLabel.isNullOrBlank()
        val bottomReserve = if (hasDates) dateText.textSize + dp(8f) else dp(4f)
        val top = paddingTop + dp(20f) // room for the latest-value label
        val bottom = height - paddingBottom - bottomReserve
        if (chartRight <= chartLeft || bottom <= top) return

        if (values.isEmpty()) {
            for (i in 0..2) {
                val y = top + (bottom - top) * i / 2f
                canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
            }
            canvas.drawText("אין עדיין מדידות", (chartLeft + chartRight) / 2f, (top + bottom) / 2f - dp(6f), emptyText)
            return
        }

        // Y range with 10% breathing room; flat data is centred instead of glued to the bottom.
        val dataMin = values.minOrNull() ?: 0f
        val dataMax = values.maxOrNull() ?: 1f
        val span = max(1f, dataMax - dataMin)
        val mid = (dataMin + dataMax) / 2f
        val yMin = mid - span * 0.6f
        val yMax = mid + span * 0.6f
        val yRange = yMax - yMin

        fun yFor(v: Float) = bottom - (v - yMin) / yRange * (bottom - top)
        // Data is oldest→newest; the on-screen position mirrors for RTL so the
        // timeline still reads in the same direction as the surrounding text.
        fun xFor(i: Int): Float {
            if (values.size == 1) return (chartLeft + chartRight) / 2f
            val t = i / (values.size - 1).toFloat()
            val fraction = if (rtl) 1f - t else t
            return chartLeft + (chartRight - chartLeft) * fraction
        }

        // Grid + axis labels: top, middle, bottom, drawn on the reading-start side.
        axisText.textAlign = if (rtl) Paint.Align.LEFT else Paint.Align.RIGHT
        val axisTextX = if (rtl) chartRight + dp(6f) else chartLeft - dp(6f)
        val gridValues = floatArrayOf(yMax, mid, yMin)
        for (gv in gridValues) {
            val y = yFor(gv)
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
            canvas.drawText(String.format(Locale.getDefault(), "%.1f", gv), axisTextX, y + axisText.textSize / 3f, axisText)
        }

        // Line + soft fill
        linePath.reset()
        fillPath.reset()
        values.forEachIndexed { i, v ->
            val x = xFor(i)
            val y = yFor(v)
            if (i == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, bottom)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(xFor(values.size - 1), bottom)
        fillPath.close()
        if (values.size > 1) {
            canvas.drawPath(fillPath, fillPaint)
            canvas.drawPath(linePath, linePaint)
        }

        // Dots: every point for short series, otherwise only the latest (avoids a beaded line).
        val dotRadius = dp(3.5f)
        if (values.size <= 14) {
            values.forEachIndexed { i, v -> canvas.drawCircle(xFor(i), yFor(v), dotRadius, pointPaint) }
        }
        val lastX = xFor(values.size - 1)
        val lastY = yFor(values.last())
        canvas.drawCircle(lastX, lastY, dotRadius + dp(3f), pointRingPaint)
        canvas.drawCircle(lastX, lastY, dotRadius + dp(1.5f), pointPaint)

        // Latest value label; keep it inside the view horizontally and above the point.
        val label = String.format(Locale.getDefault(), "%.1f", values.last())
        val halfW = valueText.measureText(label) / 2f
        val labelX = lastX.coerceIn(paddingLeft + halfW, width - paddingRight - halfW)
        val labelY = (lastY - dotRadius - dp(8f)).coerceAtLeast(paddingTop + valueText.textSize)
        canvas.drawText(label, labelX, labelY, valueText)

        // Date labels follow the same mirrored positions as their data points.
        if (hasDates) {
            val baseY = height - paddingBottom - dp(2f)
            val startX = xFor(0)
            val endX = xFor(values.size - 1)
            startLabel?.takeIf { it.isNotBlank() }?.let {
                dateText.textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
                canvas.drawText(it, startX, baseY, dateText)
            }
            endLabel?.takeIf { it.isNotBlank() }?.let {
                dateText.textAlign = if (rtl) Paint.Align.LEFT else Paint.Align.RIGHT
                canvas.drawText(it, endX, baseY, dateText)
            }
        }
    }

    private fun updateDescription() {
        contentDescription = if (values.isEmpty()) {
            "גרף משקל. אין עדיין מדידות"
        } else {
            val last = values.last()
            val min = values.minOrNull() ?: last
            val max = values.maxOrNull() ?: last
            val change = last - values.first()
            val trend = when {
                values.size < 2 || abs(change) < 0.05f -> "ללא שינוי"
                change > 0 -> String.format(Locale.getDefault(), "עלייה של %.1f ק״ג", change)
                else -> String.format(Locale.getDefault(), "ירידה של %.1f ק״ג", -change)
            }
            String.format(
                Locale.getDefault(),
                "גרף משקל, %d מדידות. אחרון %.1f ק״ג. טווח %.1f עד %.1f. %s",
                values.size, last, min, max, trend
            )
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
}
