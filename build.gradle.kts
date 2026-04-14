import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    id("org.jetbrains.intellij.platform") version "2.7.2"
}

group = "com.branchmapping"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    intellijPlatform {
        webstorm("2024.3")
        bundledPlugin("Git4Idea")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    instrumentCode.set(false)

    pluginConfiguration {
        id = "com.branchmapping.plugin"
        name = "Git Task Branch Mapper"
        description =
            "在多任务并行开发过程中，开发分支命名与需求/缺陷名称缺乏明确映射关系，<br/>" +
                "导致后续回溯处理某项需求/缺陷时，难以快速定位其对应的分支名称。<br/>" +
                "使用方式：桌面新增 git-branch-mapping.json 文件，然后打开插件，点击刷新按钮即可"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "243"
            untilBuild = "252.*"
        }
    }
}

kotlin {
    jvmToolchain(21)
}
