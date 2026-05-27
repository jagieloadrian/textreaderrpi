rootProject.name = "TextReaderRpi"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }

    versionCatalogs {
        create("ktorLibs") {
            from(files("gradle/ktor-libs.versions.toml"))
        }
    }
}
