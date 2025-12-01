# rollbar-android

Gradle plugin for uploading Android ProGuard/R8 mapping files to Rollbar for deobfuscation.

## Usage

```groovy
plugins {
    id 'com.rollbar.plugins.android' version '<latest>'
}

rollbar {
    apiToken = "<token>"
    variantsToUpload = ["externalProductionRelease", "internalProductionRelease"] // if left empty, "release" buildType will be used
}
```

This should produce the below output:
```shell
...
> Configure project :app
Configuring Rollbar plugin for variant externalProductionRelease with task name uploadRollbarMappingFileExternalProductionRelease
Configuring Rollbar plugin for variant internalProductionRelease with task name uploadRollbarMappingFileInternalProductionRelease
...
```

## How to test

Publish the latest changes from the `rollbar-android` plugin to mavenLocal: 
```shell
./gradlew publishToMavenLocal
```

Credit to [https://github.com/anthonymonori](https://github.com/anthonymonori) for getting this off the ground. 
