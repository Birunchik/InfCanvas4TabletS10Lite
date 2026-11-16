package com.infinitecanvas.board.presentation.screen.board.viewConrollers

import android.net.Uri
import android.util.Log
import com.infinitecanvas.board.data.local.storage.ImageAssetStorage
import com.infinitecanvas.board.domain.model.BoardImage
import com.infinitecanvas.board.domain.model.WorldBounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.min

class BoardImageController(
    private val imageAssetStorage: ImageAssetStorage,
    private val scope: CoroutineScope,
    private val viewportController: BoardViewportController,
    private val onContentChanged: () -> Unit,
    private val onSelectionActivated: () -> Unit
) {
    private val _images = MutableStateFlow<List<BoardImage>>(emptyList())
    val images: StateFlow<List<BoardImage>> = _images.asStateFlow()

    private val _selectedImageId = MutableStateFlow<Long?>(null)
    val selectedImageId: StateFlow<Long?> = _selectedImageId.asStateFlow()

    val hasSelectedImage: StateFlow<Boolean> = _selectedImageId
        .map { it != null }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    val isSelectedImagePinned: StateFlow<Boolean> = combine(
        _images,
        _selectedImageId
    ) { list, selId ->
        selId != null && list.firstOrNull { it.id == selId }?.isPinned == true
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    fun setInitialImages(images: List<BoardImage>) {
        _images.value = images
        _selectedImageId.value = null
        clampPinnedImagesToViewport(
            viewportController.canvasWidthPx,
            viewportController.canvasHeightPx
        )
    }

    fun clampPinnedImagesToViewport(widthPx: Int, heightPx: Int) {
        if (widthPx <= 0 || heightPx <= 0) return
        val maxW = widthPx.toDouble()
        val maxH = heightPx.toDouble()
        val margin = 16.0

        var changed = false
        _images.update { list ->
            list.map { img ->
                if (!img.isPinned) return@map img
                var w = img.width.coerceAtLeast(1.0)
                var h = img.height.coerceAtLeast(1.0)

                val allowedW = (maxW - margin * 2).coerceAtLeast(80.0)
                val allowedH = (maxH - margin * 2).coerceAtLeast(80.0)
                if (w > allowedW || h > allowedH) {
                    val scale = min(allowedW / w, allowedH / h)
                    w *= scale
                    h *= scale
                }

                val clampedMinX = img.bounds.minX.coerceIn(0.0, (maxW - w).coerceAtLeast(0.0))
                val clampedMinY = img.bounds.minY.coerceIn(0.0, (maxH - h).coerceAtLeast(0.0))
                val newBounds = WorldBounds(
                    minX = clampedMinX,
                    minY = clampedMinY,
                    maxX = clampedMinX + w,
                    maxY = clampedMinY + h
                )
                if (newBounds != img.bounds) {
                    changed = true
                    img.copy(bounds = newBounds)
                } else {
                    img
                }
            }
        }
        if (changed) {
            onContentChanged()
        }
    }

    fun createTempCameraFile(): File = imageAssetStorage.createTempCameraFile()

    fun insertImageFromUri(boardId: Long, uri: Uri) {
        if (boardId <= 0L) return
        scope.launch {
            val imported = imageAssetStorage.importFromUri(boardId, uri)
            if (imported == null) {
                Log.e("BoardImageController", "Failed to import image from uri: $uri")
                return@launch
            }
            val vp = viewportController.viewportState.value
            val widthPx = viewportController.canvasWidthPx
            val heightPx = viewportController.canvasHeightPx

            val centerWorldX = vp.screenToWorldX(widthPx * 0.5f)
            val centerWorldY = vp.screenToWorldY(heightPx * 0.5f)

            val desiredScreenWidth = widthPx * 0.45
            val scaleFactor = (desiredScreenWidth / imported.widthPx.coerceAtLeast(1))
                .coerceAtMost(1.0)
            val worldW = (imported.widthPx * scaleFactor) / vp.scale.coerceAtLeast(0.15)
            val worldH = (imported.heightPx * scaleFactor) / vp.scale.coerceAtLeast(0.15)

            val newImage = BoardImage(
                id = System.nanoTime(),
                fileName = imported.fileName,
                bounds = WorldBounds(
                    minX = centerWorldX - worldW * 0.5,
                    minY = centerWorldY - worldH * 0.5,
                    maxX = centerWorldX + worldW * 0.5,
                    maxY = centerWorldY + worldH * 0.5
                )
            )
            _images.update { it + newImage }
            _selectedImageId.value = newImage.id
            onSelectionActivated()
            onContentChanged()
        }
    }

    fun selectImage(imageId: Long?) {
        _selectedImageId.value = imageId
        if (imageId != null) {
            onSelectionActivated()
        }
    }

    fun clearSelection() {
        _selectedImageId.value = null
    }

    fun togglePinSelectedImage() {
        val targetId = _selectedImageId.value ?: return
        val vp = viewportController.viewportState.value

        _images.update { list ->
            list.map { img ->
                if (img.id != targetId) return@map img
                if (!img.isPinned) {
                    val screenBounds = WorldBounds(
                        minX = vp.worldToScreenX(img.bounds.minX).toDouble(),
                        minY = vp.worldToScreenY(img.bounds.minY).toDouble(),
                        maxX = vp.worldToScreenX(img.bounds.maxX).toDouble(),
                        maxY = vp.worldToScreenY(img.bounds.maxY).toDouble()
                    )
                    img.copy(bounds = screenBounds, isPinned = true)
                } else {
                    val worldBounds = WorldBounds(
                        minX = vp.screenToWorldX(img.bounds.minX.toFloat()),
                        minY = vp.screenToWorldY(img.bounds.minY.toFloat()),
                        maxX = vp.screenToWorldX(img.bounds.maxX.toFloat()),
                        maxY = vp.screenToWorldY(img.bounds.maxY.toFloat())
                    )
                    img.copy(bounds = worldBounds, isPinned = false)
                }
            }
        }
        clampPinnedImagesToViewport(
            viewportController.canvasWidthPx,
            viewportController.canvasHeightPx
        )
        onContentChanged()
    }

    fun updateImageBounds(imageId: Long, newBounds: WorldBounds) {
        _images.update { list ->
            list.map { img -> if (img.id == imageId) img.resized(newBounds) else img }
        }
        onContentChanged()
    }

    fun deleteSelectedImage(boardId: Long) {
        val targetId = _selectedImageId.value ?: return
        val target = _images.value.firstOrNull { it.id == targetId } ?: return

        _images.update { list -> list.filterNot { it.id == targetId } }
        _selectedImageId.value = null
        scope.launch {
            imageAssetStorage.deleteImageFile(boardId, target.fileName)
        }
        onContentChanged()
    }
}