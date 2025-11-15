package com.rollbar.plugins

import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty

open class RollbarExtension internal constructor(objects: ObjectFactory) {
    /**
     * The server API token connected to your project. This will be used to upload the mapping.txt
     * file to your project accordingly.
     * If not provided, the plugin will skip any upload.
     */
    val apiToken = objects.property<String>().convention(null)

    /**
     * Variants of the application that produces the mapping.txt and intended for upload.
     * If not provided, any release build type will be used.
     */
    val variantsToUpload = objects.setProperty<String>().convention(null)
}
