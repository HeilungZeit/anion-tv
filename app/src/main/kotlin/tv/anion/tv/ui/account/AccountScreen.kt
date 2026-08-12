package tv.anion.tv.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.anion.data.sync.AccountRepository
import tv.anion.data.sync.BookmarkSync
import tv.anion.tv.di.LocalAppContainer

data class AccountUiState(
    val signedIn: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
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
    }

    fun login(login: String, password: String) {
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { account.login(login, password) }.onSuccess {
                _state.value = AccountUiState(signedIn = true)
                // Вход успешен независимо от временной недоступности sync.
                runCatching { sync.syncNow() }
            }.onFailure { error ->
                _state.value = AccountUiState(error = error.message ?: "не удалось войти")
            }
        }
    }

    fun logout() = account.logout()
}

@Composable
fun AccountScreen() {
    val container = LocalAppContainer.current
    val vm = viewModel { AccountViewModel(container.account, container.bookmarkSync) }
    val state by vm.state.collectAsStateWithLifecycle()
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.width(560.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Аккаунт", style = MaterialTheme.typography.displaySmall)
        if (state.signedIn) {
            Text("Синхронизация с сайтом включена")
            Button(onClick = vm::logout) { Text("Выйти") }
        } else {
            AccountField(login, { login = it }, "Email или логин")
            AccountField(password, { password = it }, "Пароль", password = true)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { vm.login(login, password) }, enabled = !state.loading) {
                Text(if (state.loading) "Вхожу…" else "Войти")
            }
        }
    }
}

@Composable
private fun AccountField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    password: Boolean = false,
) {
    Surface(onClick = {}, colors = ClickableSurfaceDefaults.colors(), modifier = Modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant)
                inner()
            },
        )
    }
}
