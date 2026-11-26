package com.infinitecanvas.board.presentation.screen.home

import androidx.annotation.StringRes

/**
 * Ошибка операции, пригодная для показа пользователю.
 *
 * messageResId — ресурс, а не готовая строка: ViewModel не знает про
 * контекст и не должен хардкодить локализованный текст. cause — для логов,
 * в UI не показываем (пользователю бесполезен stack trace).
 */
data class HomeError(
    @StringRes val messageResId: Int,
    val cause: Throwable? = null
)