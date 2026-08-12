package tv.anion.tv.ui.search

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.anion.tv.di.LocalAppContainer
import tv.anion.tv.ui.components.MessagePane
import tv.anion.tv.ui.components.PosterCard
import tv.anion.tv.ui.components.initialFocus
import tv.anion.tv.ui.components.rememberInitialFocus
import tv.anion.tv.ui.components.rowFocus
import tv.anion.tv.ui.components.ScreenHeader
import tv.anion.tv.ui.components.SectionHeader

@Composable
fun SearchScreen(
    onOpenAnime: (source: String, animeId: String) -> Unit,
) {
    val container = LocalAppContainer.current
    val vm = viewModel { SearchViewModel(container.sources, container.searchHistory) }
    val state by vm.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val initial = rememberInitialFocus()

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?: return@rememberLauncherForActivityResult
        query = spoken
        vm.search(spoken)
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenHeader("Поиск", "Голосом быстрее, клавиатура всегда остаётся запасным вариантом")
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Название аниме")
                    }
                    try {
                        voice.launch(intent)
                    } catch (_: ActivityNotFoundException) {
                        vm.voiceUnavailable()
                    }
                },
                modifier = Modifier.initialFocus(initial),
            ) { Text("Голосом") }

            Surface(
                onClick = { },
                colors = ClickableSurfaceDefaults.colors(),
                modifier = Modifier.weight(1f),
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.search(query) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text("Клавиатура — если голос недоступен", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    },
                )
            }
            OutlinedButton(onClick = { vm.search(query) }) { Text("Найти") }
        }

        if (state.history.isNotEmpty()) {
            SectionHeader("Недавние")
            LazyRow(
                modifier = Modifier.rowFocus(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.history, key = { it }) { item ->
                    OutlinedButton(onClick = {
                        query = item
                        vm.search(item)
                    }) { Text(item) }
                }
            }
        }

        when {
            state.loading -> MessagePane("Ищу…")
            state.error != null -> MessagePane(state.error!!)
            state.searched && state.items.isEmpty() -> MessagePane("Ничего не нашлось")
            state.items.isNotEmpty() -> LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.items, key = { "${it.source}:${it.id}" }) { anime ->
                    PosterCard(
                        anime = anime,
                        onClick = { onOpenAnime(anime.source.name, anime.id) },
                    )
                }
            }
        }
    }
}
