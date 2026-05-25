package com.project3.todoapp.data.tasktag.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.project3.todoapp.data.tag.local.LocalTag
import com.project3.todoapp.data.task.local.LocalTask

/**
 * LocalTaskTagCrossRef — bảng nối M-N giữa Task và Tag trong Room.
 *
 *  ⚠️ `onUpdate = ForeignKey.CASCADE`:
 *  Khi DefaultTagRepository.sync push 1 tag tạo offline lên server, server cấp id mới
 *  → Room sẽ UPDATE tag.id. Nhờ CASCADE, mọi cross-ref đang trỏ tới id cũ được tự động
 *  cập nhật theo. Không cần code migration thủ công.
 */
@Entity(
    tableName = "task_tag_cross_ref",
    primaryKeys = ["taskId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = LocalTask::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LocalTag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE, // ← đổi id Tag sẽ tự cascade
        ),
    ],
    indices = [Index(value = ["tagId"])],
)
data class LocalTaskTagCrossRef(
    val taskId: String,
    val tagId: String,
    val modTime: Long,
)
