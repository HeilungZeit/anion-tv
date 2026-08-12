plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "tv.anion.player"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    api(project(":core-source"))
    implementation(project(":core-data"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.session)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
