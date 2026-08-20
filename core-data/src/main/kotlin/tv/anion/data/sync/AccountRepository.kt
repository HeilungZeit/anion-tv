package tv.anion.data.sync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

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

/** Отметка последней удачной синхронизации переживает перезапуск приложения. */
interface SyncStateStore {
    fun read(): Long?
    fun write(at: Long)
}

class PreferencesSyncStateStore(context: Context) : SyncStateStore {
    private val preferences = context.applicationContext
        .getSharedPreferences("anion_account", Context.MODE_PRIVATE)

    override fun read(): Long? = preferences.getLong(KEY, 0L).takeIf { it > 0L }
    override fun write(at: Long) { preferences.edit().putLong(KEY, at).apply() }

    private companion object { const val KEY = "last_sync_at" }
}

interface AccountRepository {
    val signedIn: StateFlow<Boolean>
    val profile: StateFlow<UserProfile?>
    suspend fun login(login: String, password: String)
    suspend fun refreshProfile()
    suspend fun logout()
}

class DefaultAccountRepository(
    private val api: BookmarkRemote,
    private val sessions: SessionStore,
) : AccountRepository {
    private val _signedIn = MutableStateFlow(sessions.read() != null)
    override val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private val _profile = MutableStateFlow<UserProfile?>(null)
    override val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    override suspend fun login(login: String, password: String) {
        require(login.isNotBlank()) { "введите email или логин" }
        require(password.isNotBlank()) { "введите пароль" }
        val session = try {
            api.login(login.trim(), password)
        } catch (error: ApiException) {
            throw IOException(loginErrorMessage(error), error)
        }
        sessions.write(session)
        _signedIn.value = true
        refreshProfile()
    }

    /** Профиль не критичен: сессия уже есть, а имя — украшение экрана. */
    override suspend fun refreshProfile() {
        val session = sessions.read() ?: return
        _profile.value = runCatching { api.profile(session) }.getOrNull()
    }

    /**
     * Текст ошибки входа для экрана аккаунта.
     *
     * После нескольких неудачных попыток бэкенд требует решить арифметическую
     * задачу. На телевизоре решать её негде — экран капчи есть только в вебе,
     * поэтому вместо буквального «подтвердите, что вы не робот» человеку нужно
     * сказать, что делать: подождать или войти на сайте. Само ограничение
     * снимается по истечении окна в 15 минут.
     */
    private fun loginErrorMessage(error: ApiException): String = when (error.code) {
        ApiErrorCode.CAPTCHA_REQUIRED, ApiErrorCode.CAPTCHA_INVALID ->
            "Слишком много неудачных попыток. Подождите 15 минут или войдите на сайте anion.online"
        else -> error.message ?: "не удалось войти"
    }

    override suspend fun logout() {
        sessions.read()?.let { api.logout(it) }
        sessions.write(null)
        _profile.value = null
        _signedIn.value = false
    }
}
