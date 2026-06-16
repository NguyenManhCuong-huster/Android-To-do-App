package com.project.hustassistant.data.task.local

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.project.hustassistant.data.tag.local.LocalTag
import com.project.hustassistant.data.tasktag.local.LocalTaskTagCrossRef

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