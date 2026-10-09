package com.elevation.mobile

import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DriveUploader {
    suspend fun uploadGpx(
        accessToken: String,
        fileName: String,
        gpx: String
    ): DriveUploadResult = withContext(Dispatchers.IO) {
        val boundary = "ElevationBoundary${System.currentTimeMillis()}"
        val metadataJson =
            """{"name":"${fileName.escapeJson()}","mimeType":"application/gpx+xml"}"""
        val body = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadataJson)
            append("\r\n--$boundary\r\n")
            append("Content-Type: application/gpx+xml; charset=UTF-8\r\n\r\n")
            append(gpx)
            append("\r\n--$boundary--\r\n")
        }.toByteArray(StandardCharsets.UTF_8)

        val connection =
            (URL(DRIVE_UPLOAD_URL)
                .openConnection() as HttpURLConnection)

        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        connection.setRequestProperty("Content-Length", body.size.toString())

        connection.outputStream.use { output ->
            output.write(body)
        }

        val responseCode = connection.responseCode
        val responseText = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
        }

        if (responseCode in 200..299) {
            DriveUploadResult(
                id = responseText.extractJsonString("id").orEmpty(),
                name = responseText.extractJsonString("name").ifEmpty { fileName },
                webViewLink = responseText.extractJsonString("webViewLink")
            )
        } else {
            error("Drive upload failed: HTTP $responseCode")
        }
    }

    private fun String.escapeJson(): String {
        return replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private fun String?.ifEmpty(defaultValue: () -> String): String {
        return if (this.isNullOrEmpty()) defaultValue() else this
    }

    private fun String.extractJsonString(key: String): String? {
        val regex = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
        return regex.find(this)?.groupValues?.getOrNull(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
    }
}

data class DriveUploadResult(
    val id: String,
    val name: String,
    val webViewLink: String?
)

private const val DRIVE_UPLOAD_URL =
    "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,webViewLink"
