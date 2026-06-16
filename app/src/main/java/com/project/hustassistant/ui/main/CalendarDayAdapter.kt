package com.project.hustassistant.ui.main

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project.hustassistant.R
import com.project.hustassistant.databinding.ItemCalendarDayBinding
import java.util.Calendar

data class CalendarDayItem(
    val dayMillis: Long,
    val taskCount: Int,
    val isSelected: Boolean,
    val isToday: Boolean
)

class CalendarDayAdapter(
    private val onDayClick: (Long) -> Unit
) : ListAdapter<CalendarDayItem, CalendarDayAdapter.DayViewHolder>(DiffCallback()) {

    companion object {
        // Monday-first ordering
        private val DAY_SHORT = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        private const val COLOR_SELECTED_TEXT = Color.WHITE
        private const val COLOR_TODAY_TEAL = 0xFF0DC7D0.toInt()
        private const val COLOR_NORMAL_NUMBER = 0xFF333333.toInt()
        private const val COLOR_NORMAL_NAME = 0xFF888888.toInt()
        private const val COLOR_TODAY_BADGE = 0xFF0DC7D0.toInt()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val binding = ItemCalendarDayBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        // Each cell = 1/7 of RecyclerView width
        val w = if (parent.width > 0) parent.width / 7 else ViewGroup.LayoutParams.WRAP_CONTENT
        binding.root.layoutParams =
            RecyclerView.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT)
        return DayViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) =
        holder.bind(getItem(position))

    inner class DayViewHolder(private val b: ItemCalendarDayBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(item: CalendarDayItem) {
            val cal = Calendar.getInstance().apply { timeInMillis = item.dayMillis }

            // Day name: Mon=2 → index 0
            val dayIndex = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 0
                Calendar.TUESDAY -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY -> 3
                Calendar.FRIDAY -> 4
                Calendar.SATURDAY -> 5
                else -> 6 // Sunday
            }
            b.tvDayName.text = DAY_SHORT[dayIndex]
            b.tvDayNumber.text = cal.get(Calendar.DAY_OF_MONTH).toString()

            // Task count badge
            if (item.taskCount > 0) {
                b.tvTaskCount.visibility = View.VISIBLE
                b.tvTaskCount.text = item.taskCount.toString()
            } else {
                b.tvTaskCount.visibility = View.INVISIBLE
            }

            // Circle state
            when {
                item.isSelected -> {
                    b.flDayCircle.setBackgroundResource(R.drawable.bg_selected_day)
                    b.tvDayNumber.setTextColor(COLOR_SELECTED_TEXT)
                    b.tvDayName.setTextColor(COLOR_TODAY_TEAL)
                }

                item.isToday -> {
                    b.flDayCircle.setBackgroundResource(R.drawable.bg_today_day)
                    b.tvDayNumber.setTextColor(COLOR_TODAY_TEAL)
                    b.tvDayName.setTextColor(COLOR_TODAY_TEAL)
                }

                else -> {
                    b.flDayCircle.background = null
                    b.tvDayNumber.setTextColor(COLOR_NORMAL_NUMBER)
                    b.tvDayName.setTextColor(COLOR_NORMAL_NAME)
                }
            }

            b.root.setOnClickListener { onDayClick(item.dayMillis) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<CalendarDayItem>() {
        override fun areItemsTheSame(o: CalendarDayItem, n: CalendarDayItem) =
            o.dayMillis == n.dayMillis

        override fun areContentsTheSame(o: CalendarDayItem, n: CalendarDayItem) = o == n
    }
}
