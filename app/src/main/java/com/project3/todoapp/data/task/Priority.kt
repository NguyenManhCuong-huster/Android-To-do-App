package com.project3.todoapp.data.task

enum class Priority(val value: Int) {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    URGENT(4);

    companion object {
        fun fromInt(value: Int) = Priority.entries.firstOrNull { it.value == value } ?: MEDIUM
    }
}