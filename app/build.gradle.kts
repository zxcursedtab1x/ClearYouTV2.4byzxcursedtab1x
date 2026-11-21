plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "app.clearyourtv"
    compileSdk = 34
    defaultConfig {
        applicationId = "app.clearyourtv"
        minSdk = 21
        targetSdk = 34
        versionCode = 11
        versionName = "2.4-dev"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
// Зависимостей нет намеренно: быстрый старт, маленький APK, никаких трекеров.
