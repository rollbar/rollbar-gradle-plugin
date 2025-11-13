package com.rollbar.plugins.tasks


import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

@CacheableTask
// One may consider separating zipping and uploading into two tasks for better reusability
internal abstract class ZipAndUploadMappingFileTask @Inject internal constructor(
    private val workerExecutor: WorkerExecutor
) : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mappingFile: RegularFileProperty

    @get:OutputFile
    abstract val destinationZipFile: RegularFileProperty

    @get:Input
    abstract val appVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val apiToken: Property<String>

    init {
        group = TASK_GROUP
        description = TASK_DESCRIPTION
    }

    @TaskAction
    fun execute() {
        workerExecutor.processIsolation().submit(UploadMappingFileTaskWorkAction::class.java) {
            appVersion.set(this@ZipAndUploadMappingFileTask.appVersion)
            mappingFile.set(this@ZipAndUploadMappingFileTask.mappingFile)
            apiToken.set(this@ZipAndUploadMappingFileTask.apiToken)
            destinationZipFile.set(this@ZipAndUploadMappingFileTask.destinationZipFile)
        }
    }

    internal companion object {
        private const val TASK_GROUP = "rollbar"
        private const val TASK_DESCRIPTION = "Zips and uploads mapping.txt to Rollbar"
        const val TASK_NAME = "uploadRollbarMappingFile"
    }
}

private interface UploadMappingFileTaskWorkParams : WorkParameters {
    val mappingFile: RegularFileProperty
    val destinationZipFile: RegularFileProperty
    val apiToken: Property<String>
    val projectId: Property<String>
    val appVersion: Property<String>
}

private abstract class UploadMappingFileTaskWorkAction : WorkAction<UploadMappingFileTaskWorkParams> {

    private val logger = Logging.getLogger(ZipAndUploadMappingFileTask::class.java)
    private val uploadEndpoint = "https://api.rollbar.com/api/1/proguard"
    private val clientBuilder = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)

    override fun execute() {
        if (parameters.apiToken.orNull == null) {
            logger.lifecycle("Rollbar API token not provided, skipping upload of mapping.txt file")
            return
        }

        zip(parameters.mappingFile.get().asFile).also { zippedMappingFile ->
            uploadZippedMappingFile(zippedMappingFile)
        }
    }

    /**
     * TODO: Consider using zstd over java.util.zip.ZipOutputStream for multi-threaded compression
     * Since the mapping file is quite big (can be well over 100MB), it can make an impact on the build time.
     * The current approach of zipping and uploading takes 14s on a 190MB mapping file.
     */
    private fun zip(mappingFile: File): File {
        val destinationZipFile = parameters.destinationZipFile.get().asFile

        // Skip if output exists and is newer than input
        if (destinationZipFile.exists() && destinationZipFile.lastModified() > mappingFile.lastModified()) {
            logger.lifecycle("${destinationZipFile.name} file is up-to-date, skipping compression")
            return destinationZipFile
        }

        logger.lifecycle("Zipping mapping file ${mappingFile.path} to ${destinationZipFile.path}")
        destinationZipFile.parentFile.mkdirs()
        FileOutputStream(destinationZipFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                val zipEntry = ZipEntry(mappingFile.name)
                zos.setLevel(9) // Maximum compression
                zos.setMethod(ZipOutputStream.DEFLATED)
                zos.putNextEntry(zipEntry)

                // Copy file content to zip
                FileInputStream(mappingFile).use { fis ->
                    val buffer = ByteArray(1024)
                    var len: Int
                    while (fis.read(buffer).also { len = it } > 0) {
                        zos.write(buffer, 0, len)
                    }
                }

                zos.closeEntry()
            }
        }
        return destinationZipFile
    }

    private fun uploadZippedMappingFile(zippedMappingFile: File) {
        logger.lifecycle("Uploading ${zippedMappingFile.path} file")

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("access_token", parameters.apiToken.get())
            .addFormDataPart("version", parameters.appVersion.get())
            .addFormDataPart(
                "mapping",
                zippedMappingFile.name,
                zippedMappingFile.asRequestBody("application/zip".toMediaType())
            )
            .build()

        val uploadRequest = Request.Builder()
            .url(uploadEndpoint)
            .post(requestBody)
            .build()

        clientBuilder.build().newCall(uploadRequest).execute().use { response ->
            if (!response.isSuccessful) {
                logger.log(LogLevel.WARN, "Failed to upload ${zippedMappingFile.name} (${zippedMappingFile.path}) for app version ${parameters.appVersion.get()} to Rollbar")
                logger.lifecycle("Skipping upload of ${zippedMappingFile.name} file to Rollbar.")
                return
            }
            logger.lifecycle("Mapping file uploaded successfully to Rollbar")
        }
    }
}
