import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    id("org.jetbrains.intellij.platform") version "2.7.2"
}

group = "com.branchmapping"
version = "0.1.3"

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
            """
            Git Task Branch Mapper helps teams track requirement names and Git branches inside WebStorm.
            <ul>
              <li>Search by requirement name or branch name</li>
              <li>Create mappings with the current branch prefilled by default</li>
              <li>Copy branch names and remove mappings in batches</li>
              <li>Read and write mappings from desktop <code>git-branch-mapping.json</code></li>
            </ul>
            """.trimIndent()
        changeNotes =
            """
            <p><strong>0.1.3</strong> 兼容性修订版本。</p>
            <ul>
              <li>提供 WebStorm 工具窗口，支持需求名/分支名双字段搜索</li>
              <li>支持新增、复制、删除映射，并在写入后立即刷新</li>
              <li>支持管理模式批量删除与当前分支默认预填</li>
              <li>兼容桌面 <code>Desktop</code> 与 <code>OneDrive/Desktop</code> 映射文件位置</li>
              <li>修复 <code>JBCefJSQuery</code> 废弃调用，减少 Marketplace 废弃 API 告警</li>
              <li>移除 <code>until-build</code> 上限，避免未来 IDE 大版本发布时反复触发下架提醒</li>
            </ul>
            """.trimIndent()
        version = project.version.toString()
        vendor {
            name = "xianchenxy"
            url = "https://github.com/xianchenxy/git-task-branch-mapper"
        }

        ideaVersion {
            sinceBuild = "243"
        }
    }
}

val jetbrainsReleaseDir = layout.projectDirectory.dir("../../releases/jetbrains")

tasks.register<Copy>("publishJetBrainsPackage") {
    group = "distribution"
    description = "Build JetBrains plugin and copy zip packages to ../../releases/jetbrains"
    dependsOn("buildPlugin")
    from(layout.buildDirectory.dir("distributions")) {
        include("*.zip")
    }
    into(jetbrainsReleaseDir)
}

kotlin {
    jvmToolchain(21)
}
