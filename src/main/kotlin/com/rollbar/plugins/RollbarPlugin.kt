package com.rollbar.plugins

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.VariantSelector
import com.android.build.gradle.AppPlugin
import com.rollbar.plugins.tasks.ZipAndUploadMappingFileTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.cc.base.logger
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.gradle.kotlin.dsl.withType

/**
 * This plugin is responsible for attaching itself to the right assemble tasks and uploading the mapping file to Rollbar.
 */
class RollbarPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            pluginManager.findPlugin("com.android.application") ?: throw GradleException("Rollbar plugin must be applied to an Android application project. Have you forget to apply 'com.android.application'?")
            val rollbarExtension = project.extensions.create("rollbar", RollbarExtension::class.java)
            plugins.withType<AppPlugin> {
                extensions.getByType<ApplicationAndroidComponentsExtension>().apply {
                    onVariants(selector().withBuildType("release")) { variant ->
                        val variantsToUpload = rollbarExtension.variantsToUpload.orNull
                        // Only initialize the task if the variant is in the list of variants to upload or if the list is null
                        variant.name.takeIf { variantsToUpload == null || variantsToUpload.contains(it) } ?: return@onVariants
                        configureGradleTask(project, variant, rollbarExtension)
                    }
                }
            }
        }
    }

    private fun configureGradleTask(project: Project, variant: ApplicationVariant, rollbarExtension: RollbarExtension) {
        val taskName = "${ZipAndUploadMappingFileTask.TASK_NAME}${variant.name.uppercaseFirstChar()}"
        logger.lifecycle("Configuring Rollbar plugin for variant ${variant.name} with task name $taskName")
        val rollbarMappingTask = project.tasks.register<ZipAndUploadMappingFileTask>(taskName) {
            apiToken.set(rollbarExtension.apiToken)
            mappingFile.set(variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE))
            appVersion.set(variant.outputs.single().versionName)
            destinationZipFile.set(project.layout.buildDirectory.file("outputs/rollbar/${variant.name}/mapping.zip"))
        }

        // The tasks frequently invoked to publish the APK/AAB, meaning that a mapping file is created that rollbar should upload
        val tasksToAttachTo = setOf(
            "assemble${variant.name.uppercaseFirstChar()}", // building an APK
            "bundle${variant.name.uppercaseFirstChar()}", // building an AAB
            "publish${variant.name.uppercaseFirstChar()}Apk", // custom - publishing via gradle-play-publisher as an APK
            "publish${variant.name.uppercaseFirstChar()}Bundle", // custom - publishing via gradle-play-publisher as an AAB
        )

        // Make sure the rollbarMappingTask is invoked after the above tasksToAttachTo are invoked
        project.afterEvaluate {
            for (task in tasksToAttachTo) {
                if (project.tasks.names.contains(task)) {
                    project.tasks.named(task).configure {
                        finalizedBy(rollbarMappingTask)
                    }
                }
            }
        }
    }
}
