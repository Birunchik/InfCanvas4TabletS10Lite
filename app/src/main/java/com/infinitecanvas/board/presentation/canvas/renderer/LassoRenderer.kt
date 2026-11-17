package com.infinitecanvas.board.presentation.canvas.renderer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Контур активного лассо в world-space. Вызывать внутри withTransform.
 * Толщина и dash нормализуются по масштабу, чтобы выглядеть константно на экране.
 */
fun DrawScope.drawLassoPreview(
    lassoWorldPath: Path,
    currentScaleFloat: Float,
    worldDashEffect: PathEffect
) {
    drawPath(
        path = lassoWorldPath,
        color = Color(0x1A1B6EF3)
    )
    drawPath(
        path = lassoWorldPath,
        color = Color(0xFF1B6EF3),
        style = Stroke(
            width = 2.5f / currentScaleFloat,
            pathEffect = worldDashEffect
        )
    )
}