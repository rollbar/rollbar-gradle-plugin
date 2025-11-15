package com.rollbar.plugins.tasks

import com.rollbar.plugins.utils.FileUploader
import com.rollbar.plugins.utils.zipMappingFile
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
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
import javax.inject.Inject

@CacheableTask
internal abstract class ZipAndUploadMappingFileTask @Inject constructor(
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
    val appVersion: Property<String>
}

private abstract class UploadMappingFileTaskWorkAction : WorkAction<UploadMappingFileTaskWorkParams> {

    private val logger = Logging.getLogger(ZipAndUploadMappingFileTask::class.java)

    override fun execute() {
        val apiToken = parameters.apiToken.orNull
        if (apiToken.isNullOrBlank()) {
            logger.lifecycle("Rollbar API token not provided, skipping upload of mapping.txt file")
            return
        }

        val zippedFile = zip(parameters.mappingFile.get().asFile)
        uploadZippedMappingFile(zippedFile, apiToken)
    }

    private fun zip(mappingFile: File): File {
        val destinationZipFile = parameters.destinationZipFile.get().asFile
        if (destinationZipFile.exists() && destinationZipFile.lastModified() > mappingFile.lastModified()) {
            logger.lifecycle("${destinationZipFile.name} file is up-to-date, skipping compression")
            return destinationZipFile
        }

        logger.lifecycle("Zipping mapping file ${mappingFile.path} to ${destinationZipFile.path}")
        return zipMappingFile(
            mappingFile = mappingFile,
            destinationZipFile = destinationZipFile,
        )
    }

    private fun uploadZippedMappingFile(zippedMappingFile: File, apiToken: String) {
        FileUploader().upload(zippedMappingFile, parameters.appVersion.get(), apiToken)
    }
}
