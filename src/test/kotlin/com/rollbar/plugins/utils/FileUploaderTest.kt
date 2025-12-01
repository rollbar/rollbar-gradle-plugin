package com.rollbar.plugins.utils

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gradle.api.logging.Logger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class FileUploaderTest {
    private lateinit var logger: Logger
    private lateinit var uploader: FileUploader
    private lateinit var mockConnection: HttpURLConnection
    private lateinit var outputStream: ByteArrayOutputStream

    @BeforeTest
    fun setup() {
        logger = mockk(relaxed = true)
        mockConnection = mockk(relaxed = true)
        outputStream = ByteArrayOutputStream()
        every { mockConnection.outputStream } returns outputStream

        uploader = FileUploader(
            logger = logger,
            connectionFactory = { _: URL -> mockConnection }
        )
    }

    @Test
    fun `upload configures connection correctly`() {
        var capturedUrl: URL? = null
        uploader = FileUploader(
            logger = logger,
            connectionFactory = { url: URL ->
                capturedUrl = url
                mockConnection
            }
        )
        every { mockConnection.responseCode } returns 200

        uploader.upload(createZipFile(), APP_VERSION, SERVER_TOKEN)

        verify { mockConnection.requestMethod = "POST" }
        verify { mockConnection.doOutput = true }
        verify {
            mockConnection.setRequestProperty(
                match { it == "Content-Type" },
                match { it.startsWith("multipart/form-data; boundary=") }
            )
        }
        assert(capturedUrl.toString() == "https://api.rollbar.com/api/1/proguard") {
            "Expected Rollbar endpoint but got $capturedUrl"
        }
    }

    @Test
    fun `upload sends multipart request with required fields`() {
        val responseCode = 200
        every { mockConnection.responseCode } returns responseCode

        uploader.upload(createZipFile(), APP_VERSION, SERVER_TOKEN)

        val body = outputStream.toString()

        assertTrue(body.contains("Content-Disposition: form-data; name=\"access_token\""))
        assertTrue(body.contains(SERVER_TOKEN))
        assertTrue(body.contains("Content-Disposition: form-data; name=\"version\""))
        assertTrue(body.contains(APP_VERSION))
        assertTrue(body.contains("Content-Disposition: form-data; name=\"mapping\""))
        assertTrue(body.contains(ZIP_CONTENT))
        verify { logger.lifecycle(match { it.contains("Uploading") }) }
        verify { logger.lifecycle(match { it.contains("Mapping file uploaded successfully to Rollbar (HTTP $responseCode)") }) }
    }

    @Test
    fun `upload logs warning on non-2xx response`() {
        val responseCode = 400
        val errorMessage = "Bad Request"
        every { mockConnection.responseCode } returns responseCode
        every { mockConnection.errorStream } returns ByteArrayInputStream(errorMessage.toByteArray())

        uploader.upload(createZipFile(), APP_VERSION, SERVER_TOKEN)

        verify {
            logger.log(
                match { it.name == "WARN" },
                match {
                    it.contains("Failed to upload")
                    it.contains("(HTTP $responseCode): $errorMessage")
                }
            )
        }
    }

    @Test
    fun `print logs exception when an exception happens`() {
        val exceptionMessage = "Disk full"
        every { mockConnection.outputStream } throws IOException(exceptionMessage)

        uploader.upload(createZipFile(), APP_VERSION, SERVER_TOKEN)

        verify {
            logger.log(
                match { it.name == "WARN" },
                match { it.contains("Upload failed: $exceptionMessage") },
                any()
            )
        }
    }

    private fun createZipFile() = File.createTempFile("mapping", ".zip").apply {
        writeText(ZIP_CONTENT)
    }
}

private const val SERVER_TOKEN = "123-456"
private const val APP_VERSION = "1.0.0"
private const val ZIP_CONTENT = "Any zip data"
