plugins {
    id("com.android.application")
}

android {
    namespace = "dev.omnitrix.watchface"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.omnitrix.watchface"
        // Watch Face Format v4 needs Wear OS 6 (API 36); the Galaxy Watch8 ships with it.
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}
