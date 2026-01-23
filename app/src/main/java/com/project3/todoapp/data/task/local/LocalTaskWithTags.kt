package com.project3.todoapp.data.task.local

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.project3.todoapp.data.tag.local.LocalTag
import com.project3.todoapp.data.tasktag.local.LocalTaskTagCrossRef

data class LocalTaskWithTags(
    @Embedded val task: LocalTask,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = LocalTaskTagCrossRef::class,
            parentColumn = "taskId",
            entityColumn = "tagId"
        )
    )
    val tags: List<LocalTag>
)