package com.infinitecanvas.board.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BoardManifestDto(
    val version: Int,
    val id: String,
    val title: String,
    val worldWidth: Double,
    val worldHeight: Double,
    val backgroundColor: Int,
    val backgroundType: String,
    val layers: List<LayerMetaDto>,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class LayerMetaDto(
    val id: String,
    val name: String,
    val zIndex: Int,
    val isVisible: Boolean,
    val opacity: Float,
    val dataFile: String
)