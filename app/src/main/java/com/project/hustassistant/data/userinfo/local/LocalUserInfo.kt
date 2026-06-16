package com.project.hustassistant.data.userinfo.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * LocalUserInfo — bảng user_info, SINGLETON (1 user → 1 row).
 *
 * PK cố định = "me" để DAO lúc nào cũng truy vấn đúng row hiện tại.
 * Khi đăng xuất → xoá row này (DAO.clear()).
 *
 * `isDirty`:
 *   - false: khớp server.
 *   - true : user vừa edit offline → repository sẽ push khi sync().
 *
 * `modTime`: epoch millis. Khi pull về thấy server.modTime > local.modTime
 * VÀ local không dirty → overwrite local. Nếu local dirty → giữ local
 * (sẽ push trước, rồi sync lại sau).
 *
 * KHÔNG có `isDeleted` — user không xoá profile từ app.
 */
@Entity(tableName = "user_info")
data class LocalUserInfo(
    @PrimaryKey
    val id: String = SINGLETON_ID,
    val email: String?,
    val studentId: String?,
    val fullName: String?,
    val dateOfBirth: String?,
    val phone: String?,
    val school: String?,
    val major: String?,
    val className: String?,
    val course: String?,
    val isDirty: Boolean = false,
    val modTime: Long = System.currentTimeMillis(),
) {
    companion object {
        const val SINGLETON_ID = "me"
    }
}
