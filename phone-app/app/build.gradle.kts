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
        versionCode = 2
        versionName = "2.3.1"
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
}
