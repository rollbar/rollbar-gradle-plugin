package com.rollbar.plugins.utils

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.TimeUnit

class FileUploader(
    val logger: Logger = Logging.getLogger(FileUploader::class.java),
    val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    fun upload(
        file: File,
        appVersion: String,
        apiToken: String,
    ) {
        logger.lifecycle("Uploading ${file.path} file")

        val boundary = "----GradleBoundary${UUID.randomUUID()}"
        val url = URI.create(UPLOAD_ENDPOINT).toURL()
        val connection = connectionFactory(url)
        connection.apply {
            connectTimeout = TimeUnit.SECONDS.toMillis(60).toInt()
            readTimeout = TimeUnit.SECONDS.toMillis(60).toInt()
            doOutput = true
            requestMethod = "POST"
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        try {
            DataOutputStream(connection.outputStream).use { output ->
                fun writeField(name: String, value: String) {
                    output.writeBytes("--$boundary\r\n")
                    output.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                    output.writeBytes(value)
                    output.writeBytes("\r\n")
                }

                fun writeFileField(name: String, file: File, mimeType: String) {
                    output.writeBytes("--$boundary\r\n")
                    output.writeBytes("Content-Disposition: form-data; name=\"$name\"; filename=\"${file.name}\"\r\n")
                    output.writeBytes("Content-Type: $mimeType\r\n\r\n")
                    Files.newInputStream(file.toPath()).use { it.copyTo(output) }
                    output.writeBytes("\r\n")
                }

                writeField("access_token", apiToken)
                writeField("version", appVersion)
                writeFileField("mapping", file, "application/zip")
                output.writeBytes("--$boundary--\r\n")
                output.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode.isSuccessful()) {
                logger.lifecycle("Mapping file uploaded successfully to Rollbar (HTTP $responseCode)")
            } else {
                val errorMessage = connection.errorStream?.bufferedReader()?.readText()?.takeIf { it.isNotBlank() }
                    ?: "No error message"
                logger.log(
                    LogLevel.WARN,
                    "Failed to upload ${file.name} (HTTP $responseCode): $errorMessage"
                )
            }
        } catch (e: Exception) {
            logger.log(LogLevel.WARN, "Upload failed: ${e.message}", e)
        } finally {
            connection.disconnect()
        }
    }
}

private fun Int.isSuccessful() = this in 200..299
private const val UPLOAD_ENDPOINT = "https://api.rollbar.com/api/1/proguard"
