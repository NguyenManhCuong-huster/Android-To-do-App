package com.project3.todoapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project3.todoapp.data.task.Task
import com.project3.todoapp.data.task.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

class MainViewModel(private val taskRepository: TaskRepository) : ViewModel() {

    // ── Week navigation ───────────────────────────────────────
    private val _weekOffset = MutableStateFlow(0) // 0 = this week
    val weekOffset: StateFlow<Int> = _weekOffset

    // ── Selected day (start-of-day millis) ────────────────────
    private val _selectedDayMillis = MutableStateFlow(getDayStart(System.currentTimeMillis()))
    val selectedDayMillis: StateFlow<Long> = _selectedDayMillis

    // ── All tasks stream ──────────────────────────────────────
    private val _allTasks: StateFlow<List<Task>> = taskRepository.getTasksStream()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── 7 day starts for the current week ────────────────────
    val weekDays: StateFlow<List<Long>> = _weekOffset
        .map { offset -> getWeekDays(offset) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), getWeekDays(0))

    // ── task count per day (for calendar badges) ─────────────
    val taskCountsForWeek: StateFlow<Map<Long, Int>> = combine(
        _allTasks, _weekOffset
    ) { tasks, offset ->
        val days = getWeekDays(offset)
        days.associateWith { dayMillis ->
            tasks.count { task -> isSameDay(task.start, dayMillis) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // ── tasks for the selected day ────────────────────────────
    val tasksForSelectedDay: StateFlow<List<Task>> = combine(
        _allTasks, _selectedDayMillis
    ) { tasks, dayMillis ->
        tasks
            .filter { task -> isSameDay(task.start, dayMillis) }
            .sortedBy { it.start }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Actions ───────────────────────────────────────────────
    fun selectDay(dayMillis: Long) {
        _selectedDayMillis.value = dayMillis
    }

    fun goToPrevWeek() {
        _weekOffset.value -= 1
    }

    fun goToNextWeek() {
        _weekOffset.value += 1
    }

    /** Select a day AND navigate the calendar to the week containing that day. */
    fun jumpToDay(dayMillis: Long) {
        _selectedDayMillis.value = getDayStart(dayMillis)
        val thisMonday = getMondayOf(System.currentTimeMillis())
        val targetMonday = getMondayOf(dayMillis)
        _weekOffset.value = ((targetMonday - thisMonday) / (7L * 24 * 60 * 60 * 1000)).toInt()
    }

    private fun getMondayOf(millis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = millis
            val dow = get(Calendar.DAY_OF_WEEK)
            add(Calendar.DAY_OF_MONTH, if (dow == Calendar.SUNDAY) -6 else Calendar.MONDAY - dow)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    // ── Helpers (internal & accessible from Activity) ─────────
    fun isSameDay(a: Long, b: Long): Boolean {
        val c1 = Calendar.getInstance().apply { timeInMillis = a }
        val c2 = Calendar.getInstance().apply { timeInMillis = b }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }

    fun getDayStart(millis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun getWeekDays(weekOffset: Int): List<Long> {
        val cal = Calendar.getInstance()
        // Move to Monday of the given week
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val toMonday = if (dow == Calendar.SUNDAY) -6 else Calendar.MONDAY - dow
        cal.add(Calendar.DAY_OF_MONTH, toMonday)
        cal.add(Calendar.WEEK_OF_YEAR, weekOffset)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        return (0..6).map { i ->
            (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }.timeInMillis
        }
    }

    companion object {
        fun provideFactory(taskRepository: TaskRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MainViewModel(taskRepository) as T
            }
    }
}
