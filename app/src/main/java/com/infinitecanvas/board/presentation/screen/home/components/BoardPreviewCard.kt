package com.infinitecanvas.board.presentation.screen.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.infinitecanvas.board.domain.model.BoardSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Карточка доски в гриде: тонкий координатор.
 * Превью и нижняя панель — отдельные composable'ы, карточка только связывает их.
 */
@Composable
internal fun BoardPreviewCard(
    board: BoardSummary,
    folderName: String?,
    onOpenBoard: () -> Unit,
    onRequestRename: () -> Unit,
    onRequestMove: () -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Форматтер зависит только от Locale, не от board.updatedAt — держим
    // его отдельно, чтобы не пересоздавать на каждое обновление доски.
    // (В прежней версии remember(board.updatedAt) пересобирал SimpleDateFormat
    //  на каждую новую дату — комментарий там обещал обратное.)
    val dateFormatter = remember {
        SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    }
    val formattedDate = remember(board.updatedAt, dateFormatter) {
        dateFormatter.format(Date(board.updatedAt))
    }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenBoard),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column {
            BoardThumbnail(board = board)
            BoardCardFooter(
                title = board.title,
                formattedDate = formattedDate,
                folderName = folderName,
                onRequestRename = onRequestRename,
                onRequestMove = onRequestMove,
                onRequestDelete = onRequestDelete
            )
        }
    }
}