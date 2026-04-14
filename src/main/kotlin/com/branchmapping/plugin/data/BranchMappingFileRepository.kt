package com.branchmapping.plugin.data

import com.branchmapping.plugin.model.BranchMappingItem
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BranchMappingFileRepository {

    fun load(project: Project): LoadResult {
        val filePath = resolveMappingFilePath()

        return runCatching {
            if (!Files.exists(filePath)) {
                return LoadResult.ReadFailure("未找到文件：${filePath.toAbsolutePath()}")
            }

            val items = toItems(readMapping(filePath))

            if (items.isEmpty()) {
                LoadResult.Empty
            } else {
                LoadResult.Success(items)
            }
        }.getOrElse { error ->
            LoadResult.ReadFailure("读取文件失败：${error.message ?: "未知错误"}")
        }
    }

    fun saveMapping(project: Project, branchName: String, requirementName: String): SaveResult {
        val filePath = resolveMappingFilePath()

        return runCatching {
            val currentMapping = readMapping(filePath)
            val nextMapping = linkedMapOf<String, String>()
            nextMapping[branchName] = requirementName
            currentMapping.forEach { (currentBranchName, currentRequirementName) ->
                if (currentBranchName != branchName) {
                    nextMapping[currentBranchName] = currentRequirementName
                }
            }

            val content = json.encodeToString(nextMapping)
            Files.writeString(filePath, content, StandardCharsets.UTF_8)
            VfsUtil.markDirtyAndRefresh(false, false, false, filePath.toFile())
            SaveResult.Success(1)
        }.getOrElse { error ->
            SaveResult.Failure("写入文件失败：${error.message ?: "未知错误"}")
        }
    }

    fun deleteMappings(project: Project, branchNames: Collection<String>): SaveResult {
        val filePath = resolveMappingFilePath()
        if (branchNames.isEmpty()) {
            return SaveResult.Failure("删除失败：未选择要删除的映射")
        }

        return runCatching {
            val targetBranchNames = branchNames.toSet()
            val nextMapping = linkedMapOf<String, String>()
            var removedCount = 0
            readMapping(filePath).forEach { (branchName, requirementName) ->
                if (targetBranchNames.contains(branchName)) {
                    removedCount += 1
                } else {
                    nextMapping[branchName] = requirementName
                }
            }
            if (removedCount == 0) {
                return SaveResult.Failure("删除失败：未找到选中的映射")
            }

            val content = json.encodeToString(nextMapping)
            Files.writeString(filePath, content, StandardCharsets.UTF_8)
            VfsUtil.markDirtyAndRefresh(false, false, false, filePath.toFile())
            SaveResult.Success(removedCount)
        }.getOrElse { error ->
            SaveResult.Failure("写入文件失败：${error.message ?: "未知错误"}")
        }
    }

    private fun readMapping(filePath: Path): Map<String, String> {
        if (!Files.exists(filePath)) {
            return emptyMap()
        }

        val content = Files.readString(filePath, StandardCharsets.UTF_8).trim()
        if (content.isEmpty()) {
            return emptyMap()
        }
        return json.decodeFromString(content)
    }

    private fun toItems(mapping: Map<String, String>): List<BranchMappingItem> {
        return mapping.entries
            .map { (branchName, requirementName) ->
                BranchMappingItem(branchName = branchName, requirementName = requirementName)
            }
            .sortedBy { it.branchName.lowercase() }
    }

    private fun resolveMappingFilePath(): Path {
        val userHome = System.getProperty("user.home")
            ?: throw IllegalStateException("无法定位用户目录")
        val osName = System.getProperty("os.name", "").lowercase()
        val desktopCandidates = mutableListOf(
            Path.of(userHome, "Desktop"),
        )
        if (osName.contains("win")) {
            desktopCandidates.add(Path.of(userHome, "OneDrive", "Desktop"))
        }

        val desktopDir = desktopCandidates.firstOrNull { Files.exists(it) } ?: desktopCandidates.first()
        return desktopDir.resolve(FILE_NAME)
    }

    sealed interface LoadResult {
        data class Success(val items: List<BranchMappingItem>) : LoadResult

        data object Empty : LoadResult

        data class ReadFailure(val message: String) : LoadResult
    }

    sealed interface SaveResult {
        data class Success(val affectedCount: Int) : SaveResult

        data class Failure(val message: String) : SaveResult
    }

    private companion object {
        private const val FILE_NAME = "git-branch-mapping.json"

        private val json = Json {
            ignoreUnknownKeys = false
            isLenient = false
            prettyPrint = true
        }
    }
}
