plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.zameenhisab.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.zameenhisab.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    // Fixed keystore so every build is signed with the same key (updates install over each other).
    // Move these passwords to GitHub secrets before publishing to Play Store.
    signingConfigs {
        create("zh") {
            storeFile = file("zameenhisab.jks")
            storePassword = "zameenhisab123"
            keyAlias = "zameenhisab"
            keyPassword = "zameenhisab123"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("zh")
        }
        debug {
            signingConfig = signingConfigs.getByName("zh")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
