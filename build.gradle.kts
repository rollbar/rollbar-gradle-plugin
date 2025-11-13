plugins {
    `kotlin-dsl`
    `maven-publish`
    id("com.gradle.plugin-publish") version "2.0.0"
}

group = properties["GROUP"].toString()
version = properties["PLUGIN_VERSION"].toString()

dependencies {
    compileOnly(gradleApi())
    compileOnly(libs.android.gradle.plugin)
    compileOnlyApi(libs.okhttp)
}

gradlePlugin {
    plugins {
        website = "https://rollbar.com/"
        vcsUrl = "https://github.com/rollbar/rollbar-gradle-plugin"

        create("rollbar-android") {
            id = properties["PLUGIN_NAME"].toString()
            displayName = "Rollbar gradle plugin"
            description = "This Plugin uploads your mapping files to Rollbar"
            tags = listOf("upload", "mappings", "android", "Rollbar")
            implementationClass = properties["PLUGIN_NAME_CLASS"].toString()
        }
    }
}
