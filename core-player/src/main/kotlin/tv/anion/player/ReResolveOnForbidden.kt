package tv.anion.player

/**
 * Один тихий переролв по 403, затем ошибка пользователю.
 *
 * ExoPlayer на протухшей подписи сыпет 403 пачкой — по сегменту. Без этого
 * сторожа каждый из них дёрнул бы резолвер заново (PLAN Э3). После того как
 * воспроизведение снова пошло, следующий срок подписи имеет право на ещё
 * одну попытку.
 */
class ReResolveOnForbidden {

    @Volatile private var awaitingResume = false

    @Volatile var savedPositionMs: Long = 0
        private set

    fun rememberPosition(positionMs: Long) {
        if (positionMs >= 0) savedPositionMs = positionMs
    }

    @Synchronized
    fun onSegmentError(code: Int): Decision {
        if (code != 403) return Decision.Propagate
        if (awaitingResume) return Decision.Fail
        awaitingResume = true
        return Decision.ReResolve
    }

    /** Воспроизведение после переролва пошло — можно снова реагировать на 403. */
    @Synchronized
    fun onPlaybackResumed() {
        awaitingResume = false
    }

    enum class Decision { ReResolve, Fail, Propagate }
}
