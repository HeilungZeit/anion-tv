package tv.anion.source

/** Реальные ответы API, снятые 12.08.2026. Тесты не ходят в сеть. */
object Fixtures {
    fun read(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/fixtures/$name")) { "нет фикстуры $name" }
            .bufferedReader()
            .use { it.readText() }
}
