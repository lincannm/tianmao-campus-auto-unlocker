// --- 国内镜像优先、官方源兜底。仓库顺序 = 解析顺序，命中即停。---
// 阿里云 Maven 镜像（https://maven.aliyun.com）实测供货正常，证据见 work/tmp/aliyun_refresh.log
// （命令：gradlew --refresh-dependencies --info assembleDebug）：
//   命中 maven.aliyun.com/repository/google 338 次、/gradle-plugin 222 次、/public 65 次；
//   官方源（dl.google.com / repo1.maven.org / plugins.gradle.org）被访问 0 次；
//   AGP 9.4.1、androidx/compose/*（material3 1.4.0、core-ktx 1.19.0…）、Kotlin 2.2.10、
//   以及下面 plugins 块要的 foojay-resolver-convention 1.0.0，全部由阿里云供上。
// 官方源仍然留着兜底：这次一次没被用到，留着成本为零（镜像有滞后时就靠它）。
// ★ 别只看镜像的 maven-metadata.xml 就断言「它没有某个版本」：阿里云是按路径回源的代理式镜像，
//   列表页可能是几年前的陈旧缓存 —— 我据此误判过 foojay 1.0.0「没有」，被构建日志推翻。
pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
    }
}

rootProject.name = "UnlockAssistant"
include(":app")
