package com.foodtrack.app.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.foodtrack.app.R

/**
 * Swipe-to-delete for the log-style lists (today's food log, today's activity
 * log, weight history): swipe a row either direction to delete it, with a red
 * "trash" reveal that grows with the swipe. Works the same regardless of
 * layout direction — dX's sign already tells us which edge is being revealed,
 * so nothing here needs to special-case RTL.
 *
 * Every screen that uses this pairs it with an undo snackbar (via the same
 * delete callback the row's own delete button already calls), so swiping is
 * exactly as safe as tapping the button — just faster.
 */
class SwipeToDeleteCallback(
    recyclerView: RecyclerView,
    private val onSwiped: (position: Int) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    private val background = ContextCompat.getColor(recyclerView.context, R.color.danger)
    private val icon: Drawable = ContextCompat.getDrawable(recyclerView.context, R.drawable.ic_delete)!!
        .mutate().also { DrawableCompat.setTint(it, Color.WHITE) }
    private val iconMargin = (16 * recyclerView.resources.displayMetrics.density).toInt()
    private val cornerRadius = 16 * recyclerView.resources.displayMetrics.density
    private val rect = RectF()

    override fun onMove(
        recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
    ): Boolean = false // no drag-to-reorder, swipe only

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onSwiped(viewHolder.bindingAdapterPosition)
    }

    override fun onChildDraw(
        c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
        dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
    ) {
        val itemView = viewHolder.itemView
        if (dX != 0f) {
            // Match the card's own rounded corners so the red reveal doesn't
            // poke out past them.
            rect.set(
                if (dX > 0) itemView.left.toFloat() else itemView.right + dX,
                itemView.top.toFloat(),
                if (dX > 0) itemView.left + dX else itemView.right.toFloat(),
                itemView.bottom.toFloat()
            )
            c.save()
            c.clipRect(itemView.left.toFloat(), itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = background }
            c.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
            c.restore()

            val iconTop = itemView.top + (itemView.height - icon.intrinsicHeight) / 2
            val iconBottom = iconTop + icon.intrinsicHeight
            if (dX > 0) {
                val iconLeft = itemView.left + iconMargin
                icon.setBounds(iconLeft, iconTop, iconLeft + icon.intrinsicWidth, iconBottom)
            } else {
                val iconRight = itemView.right - iconMargin
                icon.setBounds(iconRight - icon.intrinsicWidth, iconTop, iconRight, iconBottom)
            }
            icon.draw(c)
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}
