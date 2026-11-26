plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.infinitecanvas.testing"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 31
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":core:domain"))
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(libs.mockk)
}
