package com.branchmapping.plugin.model

import kotlinx.serialization.Serializable

@Serializable
enum class BranchMappingStatus {
    READY,
    EMPTY,
    ERROR,
}

@Serializable
data class BranchMappingViewState(
    val status: BranchMappingStatus,
    val items: List<BranchMappingItem>,
    val message: String = "",
    val defaultBranchName: String = "",
)
