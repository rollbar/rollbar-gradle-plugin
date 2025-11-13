plugins {
    `kotlin-dsl`
    `maven-publish`
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
        create("rollbar-android") {
            id = properties["PLUGIN_NAME"].toString()
            implementationClass = properties["PLUGIN_NAME_CLASS"].toString()
        }
    }
}
