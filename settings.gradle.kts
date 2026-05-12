pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Fallback to Maven Central mirror if primary fails
        maven { url = uri("https://repo1.maven.org/maven2/") }
    }
}

rootProject.name = "ZeroTierProxy"
include(":app")
