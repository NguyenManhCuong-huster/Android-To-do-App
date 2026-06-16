package com.project.hustassistant.data.userinfo

import com.project.hustassistant.data.userinfo.local.LocalUserInfo
import com.project.hustassistant.data.userinfo.network.NetworkUserInfo

/*
 * ModelMappingExt — chuyển đổi giữa 3 model:
 *
 *      LocalUserInfo (Room) ◄── UserInfo (UI) ──► NetworkUserInfo (DTO trung gian)
 */

// ─── LocalUserInfo → UserInfo ──────────────────────────
fun LocalUserInfo.toExternal() = UserInfo(
    email = email,
    studentId = studentId,
    fullName = fullName,
    dateOfBirth = dateOfBirth,
    phone = phone,
    school = school,
    major = major,
    className = className,
    course = course,
    isDirty = isDirty,
    modTime = modTime,
)

// ─── NetworkUserInfo → LocalUserInfo (sau pull) ───────
fun NetworkUserInfo.toLocal(isDirty: Boolean = false) = LocalUserInfo(
    id = LocalUserInfo.SINGLETON_ID,
    email = email,
    studentId = studentId,
    fullName = fullName,
    dateOfBirth = dateOfBirth,
    phone = phone,
    school = school,
    major = major,
    className = className,
    course = course,
    isDirty = isDirty,
    modTime = if (modTime > 0L) modTime else System.currentTimeMillis(),
)

// ─── LocalUserInfo → NetworkUserInfo (để push) ────────
fun LocalUserInfo.toNetwork() = NetworkUserInfo(
    email = email,
    studentId = studentId,
    fullName = fullName,
    dateOfBirth = dateOfBirth,
    phone = phone,
    school = school,
    major = major,
    className = className,
    course = course,
    modTime = modTime,
)
