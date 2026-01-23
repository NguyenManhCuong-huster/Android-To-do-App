package com.project3.todoapp.data.task.network

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.project3.todoapp.authentication.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class GoogleDriveDatabase(
    private val context: Context,
    private val authManager: AuthManager
) : NetworkDataSource {

    private val gson = Gson()

    // Hàm khởi tạo Drive Service từ account đã đăng nhập
    private fun getDriveService(): Drive? {
        val account = authManager.getGoogleAccount() ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_APPDATA)
        ).setSelectedAccount(account.account)

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Todo App").build()
    }

    override suspend fun loadTasks(): List<NetworkTask> = withContext(Dispatchers.IO) {
        val service = getDriveService() ?: return@withContext emptyList()

        // Tìm file tasks_backup.json trong AppDataFolder
        val files = service.files().list().setSpaces("appDataFolder").execute().files
        if (files.isNullOrEmpty()) return@withContext emptyList()

        val outputStream = ByteArrayOutputStream()
        service.files().get(files[0].id).executeMediaAndDownloadTo(outputStream)

        val json = outputStream.toString()
        val type = object : TypeToken<List<NetworkTask>>() {}.type
        gson.fromJson(json, type) ?: emptyList()
    }

    override suspend fun saveTasks(tasks: List<NetworkTask>) {
        val service = getDriveService() ?: return
        withContext(Dispatchers.IO) {
            val fileMetadata = File().apply {
                name = "tasks_backup.json"
                parents = listOf("appDataFolder")
            }
            val json = gson.toJson(tasks)
            val contentStream = ByteArrayContent.fromString("application/json", json)

            val existingFile =
                service.files().list().setSpaces("appDataFolder").execute().files.firstOrNull()
            if (existingFile == null) {
                service.files().create(fileMetadata, contentStream).execute()
            } else {
                service.files().update(existingFile.id, null, contentStream).execute()
            }
        }
    }
}