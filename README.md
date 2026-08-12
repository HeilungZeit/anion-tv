# anion-tv

Android TV-клиент. Здесь — установка окружения и что нужно для тестирования.

- Скачать и установить приложение — [docs/INSTALL.md](docs/INSTALL.md).
- Архитектура, конвенции и пройденные грабли — [AGENT.md](AGENT.md).
- Этапы и история решений — [PLAN.md](PLAN.md).
- Подпись, выпуск версии и установка на телевизор — [docs/RELEASING.md](docs/RELEASING.md).

## Структура

```
app/                   Compose for TV: экраны, навигация, фокус, апдейтер
core-source/  (JVM)    AnimeSource + AniLibria + Kodik/anion-go
core-resolve/ (JVM)    iframe URL → m3u8: ftor, ROT+base64, лестница качества
core-player/  (AAR)    Media3: Referer, скипы, позиция, переролв по 403
core-data/    (AAR)    Room: просмотренное, кэш каталога, синк закладок
tools/resolver-probe/  CLI: живая проверка резолва и источников
```

`core-resolve` и `core-source` — чистый Kotlin/JVM. Это не стилистика: резолв —
самая хрупкая и самая проверяемая часть, и она обязана гоняться в обычных
JVM-тестах и в CLI, без эмулятора. Прямое следствие разбора anion-dl, где
`attempt_resolve` требовал `AppHandle`.

Зависимости идут в одну сторону:
`app → core-player → core-data → core-source → core-resolve`.

## Сборка

Wrapper на месте (Gradle 9.7.0), отдельный `gradle` в системе не нужен —
ни brew, ни PATH. Первый запуск сам скачает дистрибутив.

```bash
./gradlew assembleDebug
```

Проверено: `assembleDebug` проходит по всем модулям, `app-debug.apk` собирается.
Главная должна открываться; иконка и баннер — заглушки. D-pad и 30 fps — только
на боксе / ТВ-образе.

Перед первой сборкой: `cp local.properties.example local.properties` и поправить `sdk.dir`.

### Если враппер не может скачать дистрибутив

`java.net.SocketException: Unexpected end of file from server` — Java-загрузчик
враппера спотыкается на редиректе `services.gradle.org` → GitHub Releases,
хотя сеть и URL живые. Лечится подсовыванием архива в кэш мимо враппера:

```bash
curl -L -o ~/.gradle/wrapper/dists/gradle-9.7.0-bin/*/gradle-9.7.0-bin.zip https://services.gradle.org/distributions/gradle-9.7.0-bin.zip
```

Перед этим удалить недокачанный `.part` и `.lck` из того же каталога, иначе
враппер продолжит считать архив битым.

JDK отдельная не нужна: Gradle 9.x работает на JDK 17–26, и сборка проверена
и на системной Microsoft OpenJDK 17, и на embedded JBR 25 из Android Studio.
Чистые Kotlin-модули пользуются `jvmToolchain(17)`, поэтому байткод получается
одинаковый независимо от того, на какой JDK запущен Gradle.

Если Студия пишет **«Invalid Gradle JDK configuration found»** — у проекта
Gradle JDK стоит `#JAVA_HOME`, а переменной в системе нет. Любая из двух кнопок
в её подсказке рабочая; ради одного демона вместо двух удобнее выбрать ту же
JDK 17, на которой идёт сборка из терминала:

```
Settings → Build Tools → Gradle → Gradle JDK → ms-17.0.18
```

### Про версии

Стек стоит на актуальном: AGP 9.3.0 (июль 2026), Gradle 9.7.0, Kotlin 2.3.20,
compose-bom 2026.06.01, tv-material 1.1.0. Kotlin намеренно не 2.4.0 — та вышла
11.08.2026, на день раньше этого скелета и на месяц позже AGP 9.3.

Следствия AGP 9, из-за которых build-файлы выглядят непривычно:

- **`org.jetbrains.kotlin.android` не применяется.** Kotlin в Android-модулях
  встроен в AGP. В `plugins {}` у `app`, `core-player`, `core-data` — только
  android-плагин, и `kotlinOptions` там больше нет.
- **А вот compose-плагин по-прежнему обязателен.** Встроенный Kotlin его не
  заменяет: без `org.jetbrains.kotlin.plugin.compose` конфигурация падает с
  «Compose Compiler Gradle plugin is required when compose is enabled».
- **`tv-foundation` не существует.** При стабилизации Compose for TV его
  содержимое уехало в обычный `androidx.compose.foundation`.
- **Hilt не подключён** — вместо него ручной `AppContainer`. На пять модулей
  кодогенератор даёт немного, а его совместимость со встроенным Kotlin не
  проверялась.

Версии библиотек, помеченные в [libs.versions.toml](gradle/libs.versions.toml)
без «проверено» (media3, room, okhttp, coil, coroutines), заведомо рабочие, но,
скорее всего, устаревшие: Студия подсветит их прямо в каталоге после первой
синхронизации. Обновлять по одной — media3 и room ломают API между минорами.

## Что дальше

Э0–Э5 закрыты кодом. Э5: Room v1, восстановление позиции, «продолжить смотреть»,
история поиска, офлайн-кэш каталога и sync Kodik-закладок с сайтом. Живой sync с
реальным аккаунтом и прогон UI на боксе ещё впереди. Следующий этап — Э6,
дистрибуция.

---

# Что понадобится для тестирования

## Уже есть

- Android Studio с профайлером и Device Manager. Wrapper сгенерирован её же
  Gradle-дистрибутивом, системный `gradle` не нужен.
- JDK: подойдёт любая из двух — системная 17 или JBR 25 из Студии.
- Android SDK: platform-tools, build-tools 36.1.0, платформы 31 и 36.1.
- `ffmpeg`/`ffplay` — критерий готовности Э0 (проверка m3u8 без плеера).

## Ставится

**Платформа android-36** — в проекте `compileSdk = 36`, а установлена 36.1.
Студия предложит доустановить на первой синхронизации, соглашаться.
`cmdline-tools` не установлены, поэтому `sdkmanager` из терминала пока нет;
если он нужен, ставится в SDK Manager → SDK Tools.

**adb в PATH** (лежит, но не подхватывается) — в `~/.zshrc`:

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"; export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator"
```

**ТВ-образ для эмулятора.** Установлен только телефонный `google_apis_playstore`
— на нём не воспроизводится ни leanback-launcher, ни поведение D-pad:

Проще всего через Device Manager в Android Studio: Create Device → категория TV →
Television (1080p) → образ Android TV. Из терминала то же самое, если поставить
`cmdline-tools`:

```bash
sdkmanager "system-images;android-34;android-tv;arm64-v8a"
```

```bash
avdmanager create avd -n tv34 -k "system-images;android-34;android-tv;arm64-v8a" -d tv_1080p
```

## Железо

- **Тот самый дешёвый бокс, 1.5–2 ГБ RAM.** Эмулятор на M-процессоре не покажет
  ни просадок прокрутки, ни выедания памяти постерами — а это два из шести
  подводных камней плана. Замеры Э4 и Э6 имеют смысл только на нём.
- **Пульт от этого бокса.** Раскладка кнопок у боксов разъезжается; `RemoteKeyMap`
  вынесен отдельно именно поэтому.
- Бокс и ноутбук в одной сети — `adb connect <ip>:5555`, иначе каждый прогон
  через USB-кабель к телевизору.

## Данные для тестов

- **Фикстуры** — реальные ответы API в `core-source/src/test/resources/fixtures/`,
  сняты 12.08.2026. Тесты в сеть не ходят.
- **Доступ к бэку** проверяется одной командой; путь именно `/proxy/api`, и без
  заголовка обхода тот же запрос вернёт 429:

```bash
curl -sS -o /dev/null -w '%{http_code}\n' -H 'X-Anion-Client: anion-dl' https://anion.online/proxy/api/anime/feed
```

## Как тестируется, по слоям

Таблица по слоям и команды пробника — в [AGENT.md](AGENT.md), чтобы не расходились
две копии. Коротко: сеть в юнит-тестах не трогается нигде, единственное
исключение — `resolver-probe`, и он не часть CI.
