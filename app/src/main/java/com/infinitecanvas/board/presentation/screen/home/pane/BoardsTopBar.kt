package com.infinitecanvas.board.presentation.screen.home.pane

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitecanvas.board.R

/**
 * Верхняя панель экрана досок: заголовок фильтра и строка поиска в одну линию.
 *
 * Почему BoxWithConstraints, а не weight:
 *  - На узком экране (телефон 360dp, split-screen) поиск должен занимать
 *    ~45% ширины бара, иначе либо заголовок схлопывается, либо placeholder
 *    поиска не влезает.
 *  - На планшете поиск должен упираться в разумный максимум (280dp),
 *    а не растягиваться на пол-экрана.
 *  Просто weight(1f)/weight(0.7f) дал бы фиксированную пропорцию на всех
 *  ширинах — на телефоне поле было бы слишком узким, на планшете слишком
 *  широким. BoxWithConstraints даёт одну точку расчёта: clamp по фактической
 *  ширине с обеих сторон.
 *
 * Почему в слоте title, а не actions:
 *  - actions в M3 рассчитан на иконки-кнопки фиксированного размера
 *    (48dp). Поле растягивается, ему нужен fillMaxWidth + weight —
 *    в title уже есть Row, где это работает естественно.
 *
 * Форма поиска — pill (percent = 50): по гайдлайну M3 SearchBar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoardsTopBar(
    title: String,
    searchQuery: String,
    onOpenDrawer: (() -> Unit)?,
    onSearchChange: (String) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // 45% от фактической ширины, clamp [140dp, 280dp]:
        //  - 140dp — минимум, при котором помещаются иконка лупы и placeholder
        //    «Поиск...» без обрезки;
        //  - 280dp — потолок, дальше поле визуально «повисает» на планшете.
        val searchWidth = (maxWidth * 0.4f).coerceIn(140.dp, 280.dp)

        TopAppBar(
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BoardsSearchField(
                        query = searchQuery,
                        onQueryChange = onSearchChange,
                        modifier = Modifier.width(searchWidth)
                    )
                }
            },
            navigationIcon = {
                if (onOpenDrawer != null) {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = stringResource(R.string.action_open_folders)
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun BoardsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        shape = RoundedCornerShape(percent = 50),
        placeholder = { Text(stringResource(R.string.search_boards_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.action_clear_search)
                    )
                }
            }
        },
        modifier = modifier
    )
}