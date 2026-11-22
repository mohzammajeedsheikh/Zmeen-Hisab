plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
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
    // Fixed keystore so the SHA-1 stays the same on every build (needed for Google sign-in
    // and phone auth). Move these passwords to GitHub secrets before publishing to Play Store.
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

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
}
