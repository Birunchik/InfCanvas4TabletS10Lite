package com.infinitecanvas.board.presentation.screen.board.huds

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.infinitecanvas.feature.board.R
import java.io.File

/**
 * Вертикальный HUD в правом верхнем углу экрана доски для вставки фотографий
 * из хранилища (галереи) или с камеры планшета.
 */
@Composable
internal fun BoardMediaHud(
    createTempCameraFile: () -> File,
    onImageUriReady: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // URI нужно запомнить до запуска камеры: TakePicture возвращает только Boolean,
    // и связать результат с temp-файлом можно лишь через это состояние.
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    // 1. Системный пикер галереи (не требует разрешений на Android 15)
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onImageUriReady(uri)
        }
    }

    // 2. Системная камера с сохранением в FileProvider URI
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedUri = pendingCameraUri
        if (success && capturedUri != null) {
            onImageUriReady(capturedUri)
        }
        pendingCameraUri = null
    }

    // Файл и FileProvider-URI создаются заранее; authority обязан совпадать с manifest
    // ("$packageName.fileprovider"), иначе launch упадёт с IllegalArgumentException.
    val launchCameraWithTempFile: () -> Unit = {
        val tempFile = createTempCameraFile()
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    // 3. Запрос runtime-разрешения CAMERA при необходимости
    val permissionDeniedMsg = stringResource(R.string.camera_permission_denied)
    // CAMERA спрашиваем лениво, только по кнопке камеры: галерея идёт через
    // системный PickVisualMedia, который runtime-разрешений не требует.
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraWithTempFile()
        } else {
            Toast.makeText(context, permissionDeniedMsg, Toast.LENGTH_SHORT).show()
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = stringResource(R.string.action_insert_from_gallery),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = {
                    val hasCameraPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasCameraPermission) {
                        launchCameraWithTempFile()
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = stringResource(R.string.action_take_photo),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}