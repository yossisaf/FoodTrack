package com.foodtrack.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.foodtrack.app.R
import java.util.Locale
import kotlin.math.max

/**
 * 7-day calorie bar chart. Data is always oldest → newest (today last) in
 * [values]/[labels], but the on-screen slot order follows layout direction:
 * in RTL (Hebrew) today sits on the right and the week reads right-to-left,
 * mirroring how a Hebrew weekly calendar reads; in LTR today is on the right
 * as usual, oldest on the left. A dashed goal line, a value above each bar,
 * red bars for days over the goal, and a spoken summary for TalkBack.
 */
class WeeklyCaloriesView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stubPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val goalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val goalTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private val colorPast = ContextCompat.getColor(context, R.color.chart_bar_past)
    private val colorToday = ContextCompat.getColor(context, R.color.chart_bar_today)
    private val colorOver = ContextCompat.getColor(context, R.color.chart_bar_over)
    private val colorOverToday = ContextCompat.getColor(context, R.color.chart_bar_over_today)
    private val colorSecondary = ContextCompat.getColor(context, R.color.text_secondary)
    private val colorPrimary = ContextCompat.getColor(context, R.color.text_primary)

    private var values = FloatArray(7)
    private var goal = 2000f
    private var labels = listOf("א", "ב", "ג", "ד", "ה", "ו", "ש")

    init {
        stubPaint.color = ContextCompat.getColor(context, R.color.divider)
        goalPaint.color = colorSecondary
        goalPaint.strokeWidth = dp(1f)
        goalPaint.pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f)
        goalTextPaint.color = colorSecondary
        goalTextPaint.textSize = sp(10f)
        labelPaint.textSize = sp(12f)
        valuePaint.textSize = sp(10f)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        updateDescription()
    }

    /**
     * @param values 7 daily calorie totals, oldest first, today last.
     * @param goal the daily calorie goal (used to scale the bars).
     * @param labels 7 single-letter weekday labels matching [values], oldest first.
     */
    fun setData(values: List<Float>, goal: Float, labels: List<String> = this.labels) {
        this.values = FloatArray(7) { i -> values.getOrNull(i)?.coerceAtLeast(0f) ?: 0f }
        this.goal = max(1f, goal)
        this.labels = List(7) { i -> labels.getOrNull(i) ?: "" }
        updateDescription()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = dp(140f).toInt()
        val h = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST -> minOf(desired, MeasureSpec.getSize(heightMeasureSpec))
            else -> desired
        }
        setMeasuredDimension(resolveSize(dp(200f).toInt(), widthMeasureSpec), h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = (width - paddingLeft - paddingRight).toFloat()
        val h = (height - paddingTop - paddingBottom).toFloat()
        if (w <= 0f || h <= 0f) return
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL

        val gap = dp(8f)
        val bw = (w - gap * 6f) / 7f
        val labelH = sp(12f) + dp(8f)
        val valueH = sp(10f) + dp(6f)
        val chartTop = paddingTop + valueH
        val chartBottom = paddingTop + h - labelH
        val chartH = chartBottom - chartTop
        if (chartH <= 0f) return

        val maxValue = max(goal, values.maxOrNull() ?: 0f).coerceAtLeast(1f)

        // Goal line, with its label anchored to the reading-start edge of this layout direction.
        val goalY = chartBottom - chartH * (goal / maxValue)
        canvas.drawLine(paddingLeft.toFloat(), goalY, paddingLeft + w, goalY, goalPaint)
        val goalLabel = "יעד ${String.format(Locale.getDefault(), "%,.0f", goal)}"
        goalTextPaint.textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
        val goalLabelX = if (rtl) paddingLeft + w else paddingLeft.toFloat()
        canvas.drawText(goalLabel, goalLabelX, goalY + goalTextPaint.textSize + dp(1f), goalTextPaint)

        for (i in 0 until 7) {
            // Data stays oldest→today in [values]/[labels]; only the on-screen slot mirrors.
            val slot = if (rtl) 6 - i else i
            val x = paddingLeft + slot * (bw + gap)
            val isToday = i == 6
            val v = values[i]
            val over = v > goal
            val barH = chartH * (v / maxValue)

            if (v <= 0f) {
                // Empty day: a small stub so the slot is still visible.
                rect.set(x, chartBottom - dp(3f), x + bw, chartBottom)
                canvas.drawRoundRect(rect, dp(2f), dp(2f), stubPaint)
            } else {
                barPaint.color = when {
                    over && isToday -> colorOverToday
                    over -> colorOver
                    isToday -> colorToday
                    else -> colorPast
                }
                rect.set(x, chartBottom - barH.coerceAtLeast(dp(3f)), x + bw, chartBottom)
                canvas.drawRoundRect(rect, dp(5f), dp(5f), barPaint)

                valuePaint.color = if (isToday) colorPrimary else colorSecondary
                valuePaint.typeface = if (isToday) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                val valueY = (rect.top - dp(3f)).coerceAtLeast(paddingTop + valuePaint.textSize)
                canvas.drawText(String.format(Locale.getDefault(), "%,.0f", v), x + bw / 2f, valueY, valuePaint)
            }

            labelPaint.color = if (isToday) colorPrimary else colorSecondary
            labelPaint.typeface = if (isToday) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            canvas.drawText(labels[i], x + bw / 2f, paddingTop + h - dp(3f), labelPaint)
        }
    }

    private fun updateDescription() {
        val parts = (0 until 7).joinToString(", ") { i ->
            val name = labels.getOrNull(i).orEmpty().ifBlank { "יום ${i + 1}" }
            "$name: ${String.format(Locale.getDefault(), "%,.0f", values[i])}"
        }
        contentDescription = "צריכת קלוריות ב־7 הימים האחרונים, היום אחרון. $parts. יעד יומי ${String.format(Locale.getDefault(), "%,.0f", goal)}"
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
}
