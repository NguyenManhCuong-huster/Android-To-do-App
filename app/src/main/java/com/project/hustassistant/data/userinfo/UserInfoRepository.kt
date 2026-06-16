package com.project.hustassistant.data.userinfo

import kotlinx.coroutines.flow.Flow

/**
 * UserInfoRepository — local-first cho profile user (offline edit OK).
 *
 * Hợp đồng:
 *  - observe(): Flow<UserInfo?> — UI bind cái này, tự cập nhật khi save / sync.
 *    `null` = chưa từng load (chưa sync lần nào).
 *
 *  - save(...): GHI VÀO LOCAL trước (set isDirty = true), trả về ngay → UI snappy.
 *    Sau đó tự push background nếu online.
 *
 *  - sync(): force pull từ server. Nếu local đang dirty → push trước, rồi pull.
 *
 *  - clearLocal(): xoá khi logout.
 */
interface UserInfoRepository {

    fun observe(): Flow<UserInfo?>

    suspend fun save(
        studentId: String?,
        fullName: String?,
        dateOfBirth: String?,
        phone: String?,
        school: String?,
        major: String?,
        className: String?,
        course: String?,
    )

    suspend fun sync(): Result<Unit>

    suspend fun clearLocal()
}
