pluginManagement {
    repositories {
        google()
        maven { url = java.net.URI("https://maven.aliyun.com/repository/public") }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven { url = java.net.URI("https://maven.aliyun.com/repository/public") }
        mavenCentral()
    }
}
rootProject.name = "punt"
include(":app")
