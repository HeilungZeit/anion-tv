/**
 * Пропатченные версии транзитивных зависимостей плагинов сборки.
 *
 * Dependabot заводит алерты на классpath сборки так же, как на код приложения,
 * хотя ничего из этого в APK не попадает: BouncyCastle приходит с apksig,
 * jdom2 — со слиянием манифестов, jose4j и commons-lang3 — с аналитикой AGP.
 * Обновить их иначе нельзя — версии задаёт сам AGP, и до его следующего
 * релиза алерты висели бы открытыми.
 *
 * Все четыре — патч-релизы своих же веток, API не трогают. Строки временные:
 * когда AGP подтянет их сам, `force` перестанет что-либо менять, и блок можно
 * удалить целиком — `./gradlew buildEnvironment` покажет, так ли это.
 */
buildscript {
    configurations.classpath {
        resolutionStrategy {
            force("org.bouncycastle:bcprov-jdk18on:1.84")
            force("org.bouncycastle:bcpkix-jdk18on:1.84")
            force("org.bouncycastle:bcutil-jdk18on:1.84")
            force("org.bitbucket.b_c:jose4j:0.9.6")
            force("org.jdom:jdom2:2.0.6.1")
            force("org.apache.commons:commons-lang3:3.18.0")
        }
    }
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
