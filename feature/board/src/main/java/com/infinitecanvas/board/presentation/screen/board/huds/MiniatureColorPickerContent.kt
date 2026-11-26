package com.infinitecanvas.board.presentation.screen.board.huds

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush as ComposeBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infinitecanvas.feature.board.R

@Composable
internal fun MiniatureColorPickerContent(
    initialColorInt: Int,
    canPin: Boolean,
    canDelete: Boolean,
    onColorChanged: (Int) -> Unit,
    onPinColor: (Int) -> Unit,
    onDeleteColor: () -> Unit
) {
    // initialColorInt — только стартовое значение: hsv ниже живут в отдельных
    // mutableFloatStateOf и намеренно не синхронизируются с внешним цветом.
    val initialHsv = remember {
        FloatArray(3).apply { AndroidColor.colorToHSV(initialColorInt, this) }
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }

    // Буфер переиспользуется, чтобы не аллоцировать FloatArray на каждый кадр drag;
    // remember(hue, saturation, value) пересчитывает ARGB только при их изменении.
    val hsvBuffer = remember { FloatArray(3) }
    val currentInt = remember(hue, saturation, value) {
        hsvBuffer[0] = hue
        hsvBuffer[1] = saturation
        hsvBuffer[2] = value
        AndroidColor.HSVToColor(hsvBuffer)
    }

    val hueGradientColors = remember {
        listOf(
            Color(AndroidColor.RED),
            Color(AndroidColor.YELLOW),
            Color(AndroidColor.GREEN),
            Color(AndroidColor.CYAN),
            Color(AndroidColor.BLUE),
            Color(AndroidColor.MAGENTA),
            Color(AndroidColor.RED)
        )
    }

    // Градиенты зависят от текущих компонент: шкала насыщенности — от hue/value,
    // шкала яркости — от hue/saturation, иначе ползунки показывали бы чужой цвет.
    val saturationGradientColors = remember(hue, value) {
        listOf(
            Color(AndroidColor.HSVToColor(floatArrayOf(hue, 0f, value))),
            Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, value)))
        )
    }

    val valueGradientColors = remember(hue, saturation) {
        listOf(
            Color.Black,
            Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, 1f)))
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onPinColor(currentInt) },
                    enabled = canPin,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = stringResource(R.string.action_pin_color),
                        tint = if (canPin) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                }

                IconButton(
                    onClick = onDeleteColor,
                    enabled = canDelete,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete_color),
                        tint = if (canDelete) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(currentInt))
                    .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            )
        }

        HsvGradientSlider(
            label = stringResource(R.string.color_slider_hue),
            value = hue,
            valueRange = 0f..360f,
            gradientColors = hueGradientColors,
            onValueChange = { newHue ->
                hue = newHue
                onColorChanged(AndroidColor.HSVToColor(floatArrayOf(newHue, saturation, value)))
            }
        )

        HsvGradientSlider(
            label = stringResource(R.string.color_slider_saturation),
            value = saturation,
            valueRange = 0f..1f,
            gradientColors = saturationGradientColors,
            onValueChange = { newSat ->
                saturation = newSat
                onColorChanged(AndroidColor.HSVToColor(floatArrayOf(hue, newSat, value)))
            }
        )

        HsvGradientSlider(
            label = stringResource(R.string.color_slider_value),
            value = value,
            valueRange = 0f..1f,
            gradientColors = valueGradientColors,
            onValueChange = { newVal ->
                value = newVal
                onColorChanged(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, newVal)))
            }
        )
    }
}

@Composable
// valueRange: hue — градусы 0..360, saturation/value — 0..1 (диапазоны HSVToColor).
// Два pointerInput: tap и drag независимы и не перехватывают друг друга.
private fun HsvGradientSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    gradientColors: List<Color>,
    onValueChange: (Float) -> Unit
) {
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
        .coerceIn(0f, 1f)
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(valueRange) {
                    detectTapGestures { offset ->
                        val f = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onValueChange(valueRange.start + f * (valueRange.endInclusive - valueRange.start))
                    }
                }
                .pointerInput(valueRange) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val f = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onValueChange(valueRange.start + f * (valueRange.endInclusive - valueRange.start))
                    }
                }
        ) {
            val trackHeight = 14.dp.toPx()
            val trackTop = (size.height - trackHeight) * 0.5f
            val cornerRadius = CornerRadius(trackHeight * 0.5f)

            drawRoundRect(
                brush = ComposeBrush.horizontalGradient(gradientColors),
                topLeft = Offset(0f, trackTop),
                size = Size(size.width, trackHeight),
                cornerRadius = cornerRadius
            )
            drawRoundRect(
                color = borderColor,
                topLeft = Offset(0f, trackTop),
                size = Size(size.width, trackHeight),
                cornerRadius = cornerRadius,
                style = Stroke(width = 1.dp.toPx())
            )

            val thumbRadius = 10.dp.toPx()
            // Бегунок ходит в пределах [radius, width-radius], чтобы кружок не вылезал
            // за скруглённый трек на краях.
            val thumbX = thumbRadius + fraction * (size.width - thumbRadius * 2f)
            val thumbCenter = Offset(thumbX, size.height * 0.5f)

            drawCircle(
                color = Color.White,
                radius = thumbRadius,
                center = thumbCenter
            )
            drawCircle(
                color = Color(0xFF1F1F1F),
                radius = thumbRadius,
                center = thumbCenter,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}