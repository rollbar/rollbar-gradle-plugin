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
    testImplementation(kotlin("test"))
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("com.squareup.okio:okio:3.6.0")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("com.google.truth:truth:1.4.2")
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

sourceSets {
    named("test") {
        kotlin.srcDirs("src/test/kotlin")
    }
}

tasks.test {
    useJUnitPlatform()
}
