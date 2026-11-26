package com.infinitecanvas.board.presentation.canvas.gestures

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import com.infinitecanvas.board.domain.model.CanvasTool

// isHardwareEraserHeld/isHardwareLassoHeld — состояние удержания кнопки, а не
// переключатели: сбрасываются при отпускании и не «залипают».
internal class StylusButtonManager(
    private val activeTool: () -> CanvasTool,
    private val onStateChanged: () -> Unit,
    private val clock: () -> Long = { SystemClock.uptimeMillis() }
) {
    var eraserScreenPos by mutableStateOf<Offset?>(null)
    var isHardwareEraserHeld by mutableStateOf(false)
        private set
    var isHardwareLassoHeld by mutableStateOf(false)
        private set

    private var wasStylusButtonPressed = false
    private var lastButtonPressTimeMs by mutableLongStateOf(0L)

    // Повторное нажатие кнопки в пределах 400 мс = лассо, одиночное = ластик.
    // Всегда возвращаем false: событие должно дойти до Compose-pointerInput.
    fun handlePointerInterop(motionEvent: MotionEvent): Boolean {
        val state = motionEvent.buttonState
        val pressed = (state and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0 ||
                (state and MotionEvent.BUTTON_SECONDARY) != 0

        if (pressed && !wasStylusButtonPressed) {
            val now = clock()
            if (now - lastButtonPressTimeMs <= 400L) {
                isHardwareLassoHeld = true
                isHardwareEraserHeld = false
            } else {
                isHardwareEraserHeld = true
                isHardwareLassoHeld = false
            }
            lastButtonPressTimeMs = now
        } else if (!pressed && wasStylusButtonPressed) {
            isHardwareEraserHeld = false
            isHardwareLassoHeld = false
        }
        wasStylusButtonPressed = pressed
        return false
    }

    // Hover приходит для стилуса без касания: превью ластика показываем при зажатой
    // кнопке или активном ластике, но подавляем в режиме лассо.
    suspend fun AwaitPointerEventScope.observeHover() {
        while (true) {
            val event = awaitPointerEvent()
            val stylusChange = event.changes.firstOrNull {
                it.type == PointerType.Stylus || it.type == PointerType.Eraser
            }

            if (stylusChange == null || event.type == PointerEventType.Exit) {
                if (eraserScreenPos != null) {
                    eraserScreenPos = null
                }
            } else if (!stylusChange.pressed) {
                val showPreview = !isHardwareLassoHeld && (
                        isHardwareEraserHeld ||
                                event.buttons.isSecondaryPressed ||
                                stylusChange.type == PointerType.Eraser ||
                                activeTool() == CanvasTool.ERASER
                        )

                if (showPreview) {
                    if (eraserScreenPos != stylusChange.position) {
                        eraserScreenPos = stylusChange.position
                    }
                } else if (eraserScreenPos != null) {
                    eraserScreenPos = null
                }
            }
        }
    }

    fun updateEraserPos(pos: Offset) {
        if (eraserScreenPos != pos) {
            eraserScreenPos = pos
        }
    }

    fun clearEraserPos() {
        if (eraserScreenPos != null) {
            eraserScreenPos = null
        }
    }
}