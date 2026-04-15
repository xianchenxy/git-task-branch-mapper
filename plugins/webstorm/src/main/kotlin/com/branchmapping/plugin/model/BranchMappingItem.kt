package com.branchmapping.plugin.model

import kotlinx.serialization.Serializable

@Serializable
data class BranchMappingItem(
    val branchName: String,
    val requirementName: String,
    val updatedAt: String = "",
)
