plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.thatscodeguy.phonelocation"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.thatscodeguy.phonelocation"
        minSdk = 29
        targetSdk = 34
        versionCode = 6
        versionName = "2.4.2"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../signing/app.p12")
            storePassword = "phonelocation"
            keyAlias = "phonelocation"
            keyPassword = "phonelocation"
            storeType = "PKCS12"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // AGP 8+ 默认不生成 BuildConfig, MainActivity 状态页需要 VERSION_NAME/VERSION_CODE
    buildFeatures {
        buildConfig = true
    }
}
