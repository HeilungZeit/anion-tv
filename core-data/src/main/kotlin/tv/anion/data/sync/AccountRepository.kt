package tv.anion.data.sync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface SessionStore {
    fun read(): String?
    fun write(sessionId: String?)
}

class PreferencesSessionStore(context: Context) : SessionStore {
    private val preferences = context.applicationContext
        .getSharedPreferences("anion_account", Context.MODE_PRIVATE)

    override fun read(): String? = preferences.getString(KEY, null)?.takeIf(String::isNotBlank)
    override fun write(sessionId: String?) {
        preferences.edit().apply {
            if (sessionId == null) remove(KEY) else putString(KEY, sessionId)
        }.apply()
    }

    private companion object { const val KEY = "session_id" }
}

interface AccountRepository {
    val signedIn: StateFlow<Boolean>
    suspend fun login(login: String, password: String)
    fun logout()
}

class DefaultAccountRepository(
    private val api: BookmarkRemote,
    private val sessions: SessionStore,
) : AccountRepository {
    private val _signedIn = MutableStateFlow(sessions.read() != null)
    override val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    override suspend fun login(login: String, password: String) {
        require(login.isNotBlank()) { "введите email или логин" }
        require(password.isNotBlank()) { "введите пароль" }
        sessions.write(api.login(login.trim(), password))
        _signedIn.value = true
    }

    override fun logout() {
        sessions.write(null)
        _signedIn.value = false
    }
}
