plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.alpha3.geely.diag"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.alpha3.geely.diag"
        // Deliberadamente baixo: queremos que instale em qualquer Android que a
        // multimidia possa estar rodando, justamente porque ainda nao sabemos qual e.
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
