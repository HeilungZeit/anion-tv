/**
 * Пропатченные версии транзитивных зависимостей инструментов сборки.
 *
 * Dependabot заводит алерты на классpath сборки так же, как на код приложения,
 * хотя ничего из этого в APK не попадает: BouncyCastle приходит с apksig,
 * jdom2 — со слиянием манифестов, jose4j и commons-lang3 — с аналитикой AGP.
 * Обновить их иначе нельзя — версии задаёт сам AGP, и до его следующего
 * релиза алерты висели бы открытыми.
 *
 * Все пять — патч-релизы своих же веток, API не трогают. Строки временные:
 * когда AGP подтянет их сам, `force` перестанет что-либо менять, и блок можно
 * удалить целиком — `./gradlew buildEnvironment` покажет, так ли это.
 *
 * Блок обязан стоять здесь: в `settings.gradle.kts` у buildscript свой
 * классpath, и `force` там молча ничего не меняет.
 */
buildscript {
    val patched = listOf(
        "org.bouncycastle:bcprov-jdk18on:1.85.2",
        "org.bouncycastle:bcpkix-jdk18on:1.85.2",
        "org.bouncycastle:bcutil-jdk18on:1.85.2",
        "org.bitbucket.b_c:jose4j:0.9.7",
        "org.jdom:jdom2:2.0.6.1",
        "org.apache.commons:commons-lang3:3.20.0",
        "org.apache.httpcomponents:httpclient:4.5.14",
    )
    extra["patchedBuildTools"] = patched
    configurations.classpath { resolutionStrategy { patched.forEach(::force) } }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.room) apply false
}

/**
 * Тот же список — для конфигураций модулей: линт резолвит свой инструмент
 * отдельно (`androidLintTool`), со своими копиями BouncyCastle, httpclient и
 * commons-lang3, и алерты держались именно на них. Классpath плагинов их не
 * покрывает, поэтому список прогоняется дважды — но объявлен один раз, иначе
 * обновление правило бы только половину.
 *
 * На зависимости приложения `force` не влияет: ни одной из этих библиотек в
 * APK нет, проверяется через
 * `:app:dependencies --configuration releaseRuntimeClasspath`.
 */
@Suppress("UNCHECKED_CAST")
val patchedBuildTools = rootProject.extra["patchedBuildTools"] as List<String>

allprojects {
    configurations.configureEach {
        resolutionStrategy { patchedBuildTools.forEach(::force) }
    }
}
