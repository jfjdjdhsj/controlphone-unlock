plugins {
    id("com.android.application")
}

val signingStoreFile = System.getenv("SIGNING_STORE_FILE")

android {
    namespace = "io.github.jfjdjdhsj.controlphone"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.jfjdjdhsj.controlphone"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("MODULE_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("MODULE_VERSION_NAME") ?: "1.0.0"
    }

    if (signingStoreFile != null) {
        signingConfigs {
            create("release") {
                storeFile = file(signingStoreFile)
                storeType = "PKCS12"
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = if (signingStoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")

    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
}
