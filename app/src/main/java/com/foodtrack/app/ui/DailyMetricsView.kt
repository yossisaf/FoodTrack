package com.foodtrack.app.ui

import android.content.Context
import android.graphics.Canvas
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
 * Lightweight metrics visualization implemented with Canvas so it remains
 * compatible with API 19 and does not require a charting dependency.
 *
 * The calorie ring sits on the reading-start side (right for RTL/Hebrew, left
 * for LTR), with the three macro bars extending from it toward the far edge,
 * mirroring the whole layout rather than just flipping the text.
 */
class DailyMetricsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    // Separate paints for the ring and the bars: sharing one paint would permanently
    // shrink the ring's stroke width after the first draw picked up the bar's paint state.
    private val ringTrack = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barTrack = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val small = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var calories = 0f
    private var goal = 2000f
    private var protein = 0f
    private var carbs = 0f
    private var fat = 0f

    private val colorPrimary = ContextCompat.getColor(context, R.color.primary)
    private val colorDanger = ContextCompat.getColor(context, R.color.danger)

    init {
        ringTrack.style = Paint.Style.STROKE
        ringTrack.strokeWidth = dp(9f)
        ringTrack.strokeCap = Paint.Cap.ROUND
        ringTrack.color = ContextCompat.getColor(context, R.color.divider)
        ring.style = Paint.Style.STROKE
        ring.strokeWidth = dp(9f)
        ring.strokeCap = Paint.Cap.ROUND
        barTrack.style = Paint.Style.FILL
        barFill.style = Paint.Style.FILL
        text.color = ContextCompat.getColor(context, R.color.text_primary)
        text.textAlign = Paint.Align.CENTER
        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = sp(16f)
        small.color = ContextCompat.getColor(context, R.color.text_secondary)
        small.textSize = sp(11f)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        updateDescription()
    }

    fun setData(calories: Float, goal: Float, protein: Float, carbs: Float, fat: Float) {
        this.calories = max(0f, calories)
        this.goal = max(1f, goal)
        this.protein = max(0f, protein)
        this.carbs = max(0f, carbs)
        this.fat = max(0f, fat)
        updateDescription()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = dp(120f).toInt()
        val h = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST -> minOf(desired, MeasureSpec.getSize(heightMeasureSpec))
            else -> desired
        }
        setMeasuredDimension(resolveSize(dp(240f).toInt(), widthMeasureSpec), h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = minOf(w * .16f, h * .5f - ring.strokeWidth)
        if (radius <= 0f) return
        val cx = if (rtl) w - paddingRight - ring.strokeWidth - radius else paddingLeft + ring.strokeWidth + radius
        val cy = h / 2f
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        val over = calories > goal
        ring.color = if (over) colorDanger else colorPrimary
        canvas.drawArc(rect, -90f, 360f, false, ringTrack)
        val sweep = 360f * (calories / goal).coerceIn(0f, 1f)
        if (sweep > 0f) canvas.drawArc(rect, -90f, sweep, false, ring)

        canvas.drawText(String.format(Locale.getDefault(), "%.0f", calories), cx, cy + text.textSize * 0.1f, text)
        small.textAlign = Paint.Align.CENTER
        small.textSize = sp(10f)
        canvas.drawText("קק״ל", cx, cy + text.textSize * 0.1f + small.textSize + dp(2f), small)

        // Macro bars fill the area between the ring and the far edge, whichever side that is.
        val ringOuter = radius + ring.strokeWidth
        val barsFrom = if (rtl) paddingLeft.toFloat() else cx + ringOuter + dp(16f)
        val barsTo = if (rtl) cx - ringOuter - dp(16f) else w - paddingRight - dp(4f)
        if (barsTo <= barsFrom) return
        val maxMacro = max(1f, maxOf(protein, carbs, fat))
        val rowH = h / 3f
        drawMacro(canvas, barsFrom, barsTo, rowH * 0.5f, rtl, "חלבון", protein, maxMacro, R.color.macro_protein, R.color.macro_protein_track)
        drawMacro(canvas, barsFrom, barsTo, rowH * 1.5f, rtl, "פחמימות", carbs, maxMacro, R.color.macro_carbs, R.color.macro_carbs_track)
        drawMacro(canvas, barsFrom, barsTo, rowH * 2.5f, rtl, "שומן", fat, maxMacro, R.color.macro_fat, R.color.macro_fat_track)
    }

    /**
     * [centerY] is the vertical middle of this macro's row: label/value on top, bar beneath.
     * The label sits on the side nearest the ring (the row's reading-start side); the value
     * sits on the far side. [left]/[right] are already resolved screen coordinates.
     */
    private fun drawMacro(
        canvas: Canvas, left: Float, right: Float, centerY: Float, ringOnRight: Boolean,
        label: String, value: Float, maxValue: Float, fillColor: Int, trackColor: Int
    ) {
        small.textSize = sp(12f)
        val barH = dp(7f)
        val textBaseline = centerY - dp(2f)
        // Ring on the right → label (near ring) on the right, value on the left; and vice versa.
        val labelAlign = if (ringOnRight) Paint.Align.RIGHT else Paint.Align.LEFT
        val labelX = if (ringOnRight) right else left
        val valueX = if (ringOnRight) left else right
        small.textAlign = labelAlign
        canvas.drawText(label, labelX, textBaseline, small)
        small.textAlign = if (ringOnRight) Paint.Align.LEFT else Paint.Align.RIGHT
        canvas.drawText(String.format(Locale.getDefault(), "%.0f ג׳", value), valueX, textBaseline, small)

        val barTop = centerY + dp(4f)
        barTrack.color = ContextCompat.getColor(context, trackColor)
        rect.set(left, barTop, right, barTop + barH)
        canvas.drawRoundRect(rect, barH / 2f, barH / 2f, barTrack)
        val fraction = (value / maxValue).coerceIn(0f, 1f)
        if (fraction > 0f) {
            barFill.color = ContextCompat.getColor(context, fillColor)
            // Fill grows from the ring side outward, matching how the ring "feeds" the bars.
            if (ringOnRight) {
                rect.set(right - max(barH, (right - left) * fraction), barTop, right, barTop + barH)
            } else {
                rect.set(left, barTop, left + max(barH, (right - left) * fraction), barTop + barH)
            }
            canvas.drawRoundRect(rect, barH / 2f, barH / 2f, barFill)
        }
    }

    private fun updateDescription() {
        contentDescription = String.format(
            Locale.getDefault(),
            "%.0f מתוך %.0f קק״ל. חלבון %.0f גרם, פחמימות %.0f גרם, שומן %.0f גרם",
            calories, goal, protein, carbs, fat
        )
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
}
