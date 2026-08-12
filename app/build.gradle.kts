import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.isFile) {
    keystorePropertiesFile.inputStream().use(keystoreProperties::load)
}

fun signingValue(propertyName: String, environmentName: String): String? =
    keystoreProperties.getProperty(propertyName)?.takeIf(String::isNotBlank)
        ?: System.getenv(environmentName)?.takeIf(String::isNotBlank)

val releaseStoreFile = signingValue("storeFile", "ANION_KEYSTORE_FILE")
val releaseStorePassword = signingValue("storePassword", "ANION_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "ANION_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "ANION_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { it != null }

if (System.getenv("ANION_REQUIRE_SIGNING") == "true" && !hasReleaseSigning) {
    error("Release signing is required, but one or more ANION_KEYSTORE_* secrets are missing")
}

android {
    namespace = "tv.anion.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "tv.anion.tv"
        minSdk = 23
        versionCode = System.getenv("ANION_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("ANION_VERSION_NAME")?.takeIf(String::isNotBlank) ?: "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(project(":core-source"))
    implementation(project(":core-resolve"))
    implementation(project(":core-player"))
    implementation(project(":core-data"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.tv.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(libs.media3.ui)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
