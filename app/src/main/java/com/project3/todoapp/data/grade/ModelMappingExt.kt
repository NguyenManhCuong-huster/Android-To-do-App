package com.project3.todoapp.data.grade

import com.project3.todoapp.data.grade.local.LocalGrade
import com.project3.todoapp.data.grade.network.NetworkGrade

/*
 * ModelMappingExt — chuyển đổi giữa 3 model:
 *
 *      LocalGrade (Room) ◄──── Grade (external) ────► NetworkGrade (API)
 *
 * Grade là model dùng trong app (UI, ViewModel). LocalGrade và NetworkGrade chỉ tồn tại
 * ở data layer. Hai cờ isDirty/isDeleted chỉ có ở LocalGrade — convert về Grade thì bỏ.
 */

// ─── Grade ↔ LocalGrade ────────────────────────────────

fun Grade.toLocal(
    isDirty: Boolean = true,
    isDeleted: Boolean = false,
): LocalGrade = LocalGrade(
    id           = id,
    semester     = semester,
    courseCode   = courseCode,
    courseName   = courseName,
    courseNameEn = courseNameEn,
    credits      = credits,
    letterGrade  = letterGrade,
    modTime      = modTime,
    isDeleted    = isDeleted,
    isDirty      = isDirty,
)

fun LocalGrade.toExternal(): Grade = Grade(
    id           = id,
    semester     = semester,
    courseCode   = courseCode,
    courseName   = courseName,
    courseNameEn = courseNameEn,
    credits      = credits,
    letterGrade  = letterGrade,
    modTime      = modTime,
)

@JvmName("localListToExternal")
fun List<LocalGrade>.toExternal(): List<Grade> = map(LocalGrade::toExternal)

// ─── NetworkGrade ↔ Grade ──────────────────────────────

fun NetworkGrade.toExternal(): Grade = Grade(
    id           = id,
    semester     = semester,
    courseCode   = courseCode,
    courseName   = courseName,
    courseNameEn = courseNameEn,
    credits      = credits,
    letterGrade  = letterGrade,
    modTime      = modTime,
)

@JvmName("networkListToExternal")
fun List<NetworkGrade>.toExternal(): List<Grade> = map(NetworkGrade::toExternal)

// ─── NetworkGrade → LocalGrade (dùng khi pull về từ server) ───

fun NetworkGrade.toLocal(isDirty: Boolean = false): LocalGrade = LocalGrade(
    id           = id,
    semester     = semester,
    courseCode   = courseCode,
    courseName   = courseName,
    courseNameEn = courseNameEn,
    credits      = credits,
    letterGrade  = letterGrade,
    modTime      = modTime,
    isDeleted    = isDeleted,
    isDirty      = isDirty,
)
