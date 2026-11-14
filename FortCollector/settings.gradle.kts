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
        maven { url = uri("${System.getProperty("user.home")}/localrepo") }
        google()
        mavenCentral()
    }
}
rootProject.name = "FortCollector"
include(":app")
