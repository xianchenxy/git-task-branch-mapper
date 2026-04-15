package com.branchmapping.plugin.data

import com.branchmapping.plugin.model.BranchMappingItem
import com.intellij.openapi.project.Project
import java.time.Instant
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
            val nextMapping = linkedMapOf<String, MappingValue>()
            nextMapping[branchName] = MappingValue(
                requirementName = requirementName,
                updatedAt = Instant.now().toString(),
            )
            currentMapping.forEach { (currentBranchName, currentValue) ->
                if (currentBranchName != branchName) {
                    nextMapping[currentBranchName] = currentValue
                }
            }

            val content = json.encodeToString(nextMapping)
            Files.writeString(filePath, content, StandardCharsets.UTF_8)
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
            val nextMapping = linkedMapOf<String, MappingValue>()
            var removedCount = 0
            readMapping(filePath).forEach { (branchName, value) ->
                if (targetBranchNames.contains(branchName)) {
                    removedCount += 1
                } else {
                    nextMapping[branchName] = value
                }
            }
            if (removedCount == 0) {
                return SaveResult.Failure("删除失败：未找到选中的映射")
            }

            val content = json.encodeToString(nextMapping)
            Files.writeString(filePath, content, StandardCharsets.UTF_8)
            SaveResult.Success(removedCount)
        }.getOrElse { error ->
            SaveResult.Failure("写入文件失败：${error.message ?: "未知错误"}")
        }
    }

    private fun readMapping(filePath: Path): Map<String, MappingValue> {
        if (!Files.exists(filePath)) {
            return emptyMap()
        }

        val content = Files.readString(filePath, StandardCharsets.UTF_8).trim()
        if (content.isEmpty()) {
            return emptyMap()
        }
        return normalizeMapping(json.parseToJsonElement(content))
    }

    private fun normalizeMapping(element: JsonElement): Map<String, MappingValue> {
        val mappingObject = element as? JsonObject
            ?: throw IllegalStateException("映射文件格式错误：根节点必须是对象")

        return mappingObject.entries.associate { (branchName, value) ->
            branchName to value.toMappingValue(branchName)
        }
    }

    private fun JsonElement.toMappingValue(branchName: String): MappingValue {
        val objectValue = this as? JsonObject
        if (objectValue != null) {
            val requirementName = objectValue["requirementName"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalStateException("映射文件格式错误：$branchName 缺少 requirementName")

            return MappingValue(
                requirementName = requirementName,
                updatedAt = objectValue["updatedAt"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }

        val stringValue = jsonPrimitive.contentOrNull
        if (stringValue != null) {
            return MappingValue(requirementName = stringValue)
        }

        throw IllegalStateException("映射文件格式错误：$branchName 的值必须是字符串或对象")
    }

    private fun toItems(mapping: Map<String, MappingValue>): List<BranchMappingItem> {
        return mapping.entries
            .map { (branchName, value) ->
                BranchMappingItem(
                    branchName = branchName,
                    requirementName = value.requirementName,
                    updatedAt = value.updatedAt,
                )
            }
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

    @kotlinx.serialization.Serializable
    private data class MappingValue(
        val requirementName: String,
        val updatedAt: String = "",
    )
}
