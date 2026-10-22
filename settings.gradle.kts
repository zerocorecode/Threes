pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin/") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/") }
        maven { url = uri("https://maven.aliyun.com/repository/public/") }
        maven { url = uri("https://maven.aliyun.com/repository/google/") }
    }
}

rootProject.name = "Threes"
include(":app", ":game-core", ":ai-core")

