package tv.anion.tv.ui.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.anion.data.sync.AccountRepository
import tv.anion.data.sync.BookmarkSync
import tv.anion.data.sync.UserProfile
import tv.anion.data.sync.SyncState
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import tv.anion.tv.di.LocalAppContainer
import tv.anion.tv.ui.components.ScreenHeader
import tv.anion.tv.ui.components.TvKeyboard
import tv.anion.tv.ui.components.initialFocus
import tv.anion.tv.ui.components.rememberInitialFocus

data class AccountUiState(
    val signedIn: Boolean = false,
    val profile: UserProfile? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val sync: SyncState = SyncState(),
)

class AccountViewModel(
    private val account: AccountRepository,
    private val sync: BookmarkSync,
) : ViewModel() {
    private val _state = MutableStateFlow(AccountUiState(account.signedIn.value))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            account.signedIn.collect { signedIn -> _state.value = _state.value.copy(signedIn = signedIn) }
        }
        viewModelScope.launch {
            account.profile.collect { profile -> _state.value = _state.value.copy(profile = profile) }
        }
        viewModelScope.launch {
            sync.state.collect { value -> _state.value = _state.value.copy(sync = value) }
        }
        // Сессия могла остаться с прошлого запуска — тогда имя нужно подтянуть
        // сразу, не дожидаясь повторного входа.
        viewModelScope.launch { runCatching { account.refreshProfile() } }
    }

    fun login(login: String, password: String) {
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { account.login(login, password) }
                .onSuccess {
                    _state.value = AccountUiState(signedIn = true, profile = account.profile.value)
                    // Вход успешен независимо от временной недоступности sync.
                    runCatching { sync.syncNow() }
                }
                .onFailure { error ->
                    _state.value = AccountUiState(error = error.message ?: "не удалось войти")
                }
        }
    }

    /** Повтор нужен только после сбоя: в норме синхронизация идёт сама. */
    fun retrySync() {
        viewModelScope.launch { runCatching { sync.syncNow() } }
    }

    fun logout() {
        viewModelScope.launch { runCatching { account.logout() } }
    }
}

/** Какое поле сейчас набирает клавиатура. */
private enum class Field { None, Login, Password }

@Composable
fun AccountScreen() {
    val container = LocalAppContainer.current
    val vm = viewModel { AccountViewModel(container.account, container.bookmarkSync) }
    val state by vm.state.collectAsStateWithLifecycle()

    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(Field.None) }
    val initial = rememberInitialFocus()
    val keyboardFocus = rememberInitialFocus(editing != Field.None)

    BackHandler(enabled = editing != Field.None) { editing = Field.None }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (state.signedIn) {
            ScreenHeader("Аккаунт", "Просмотренное и закладки синхронизируются с сайтом")
            SignedIn(
                profile = state.profile,
                sync = state.sync,
                error = state.error,
                onRetry = vm::retrySync,
                onLogout = vm::logout,
                firstFocus = Modifier.initialFocus(initial),
            )
            return@Column
        }

        ScreenHeader("Вход", "Тот же аккаунт, что и на сайте — закладки станут общими")

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.width(760.dp)) {
            CredentialField(
                value = login,
                hint = "Email или логин",
                active = editing == Field.Login,
                onClick = { editing = Field.Login },
                modifier = Modifier
                    .weight(1f)
                    .initialFocus(initial),
            )
            CredentialField(
                value = "•".repeat(password.length),
                hint = "Пароль",
                active = editing == Field.Password,
                onClick = { editing = Field.Password },
                modifier = Modifier.weight(1f),
            )
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        if (editing != Field.None) {
            Text(
                if (editing == Field.Login) "Набираем логин" else "Набираем пароль",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TvKeyboard(
                onKey = { char -> if (editing == Field.Login) login += char else password += char },
                onBackspace = {
                    if (editing == Field.Login) login = login.dropLast(1) else password = password.dropLast(1)
                },
                onClear = { if (editing == Field.Login) login = "" else password = "" },
                onSubmit = {
                    // С логина клавиатура ведёт к паролю, с пароля — сразу вход:
                    // так вся форма заполняется, не отрываясь от клавиатуры.
                    if (editing == Field.Login) {
                        editing = Field.Password
                    } else {
                        editing = Field.None
                        vm.login(login, password)
                    }
                },
                submitLabel = if (editing == Field.Login) "Далее" else "Войти",
                startLatin = true,
                firstKeyFocus = Modifier.initialFocus(keyboardFocus),
            )
            Text(
                "Назад — убрать клавиатуру",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.5f),
            )
        } else {
            Button(
                onClick = { vm.login(login, password) },
                enabled = !state.loading,
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                ),
            ) { Text(if (state.loading) "Вхожу…" else "Войти") }
        }
    }
}

@Composable
private fun SignedIn(
    profile: UserProfile?,
    sync: SyncState,
    error: String?,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    firstFocus: Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (profile != null) {
            Text(
                profile.username.ifBlank { profile.email },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (profile.username.isNotBlank() && profile.email.isNotBlank()) {
                Text(profile.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text("Вы вошли", style = MaterialTheme.typography.headlineSmall)
        }

        // Вместо кнопки — состояние: синхронизация и так идёт при каждом заходе
        // в приложение, при входе и вокруг просмотра. Руками её дёргают только
        // когда что-то сломалось, поэтому «Повторить» появляется лишь тогда.
        Text(
            text = syncLabel(sync),
            color = if (sync.error != null) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (sync.error != null) {
                Button(onClick = onRetry, enabled = !sync.running, modifier = firstFocus) {
                    Text(if (sync.running) "Синхронизирую…" else "Повторить")
                }
            }
            Button(
                onClick = onLogout,
                modifier = if (sync.error == null) firstFocus else Modifier,
                colors = ButtonDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.10f),
                    contentColor = Color.White,
                ),
            ) { Text("Выйти") }
        }
    }
}

private fun syncLabel(sync: SyncState): String {
    val at = sync.lastSuccessAt
    return when {
        sync.running -> "Синхронизация…"
        sync.error != null -> "Синхронизация не удалась: ${sync.error}"
        at == null -> "Ещё не синхронизировалось"
        else -> "Синхронизировано ${humanAgo(at)}"
    }
}

/** Точное время на ТВ не нужно — важно «свежо или давно». */
private fun humanAgo(at: Long): String {
    val minutes = ((System.currentTimeMillis() - at) / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> "только что"
        minutes < 60 -> "$minutes мин назад"
        minutes < 24 * 60 -> "${minutes / 60} ч назад"
        else -> SimpleDateFormat("d MMMM, HH:mm", Locale.forLanguageTag("ru-RU")).format(Date(at))
    }
}

@Composable
private fun CredentialField(
    value: String,
    hint: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val highlighted = focused || active

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(46.dp)
            .onFocusChanged { focused = it.hasFocus },
        shape = ClickableSurfaceDefaults.shape(shape, shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = if (active) 0.14f else 0.08f),
            focusedContainerColor = Color.White.copy(alpha = 0.14f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .border(
                    width = if (highlighted) 2.dp else 1.dp,
                    color = if (highlighted) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.14f),
                    shape = shape,
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = value.ifEmpty { hint },
                style = MaterialTheme.typography.titleSmall,
                color = if (value.isEmpty()) Color.White.copy(alpha = 0.45f) else Color.White,
            )
        }
    }
}
