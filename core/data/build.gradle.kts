plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.kover)
}

android {
    namespace = "com.infinitecanvas.board.data"
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
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kover {
    reports {
        variant("debug")   { html { }; xml { } }
        variant("release") { html { }; xml { } }
    }
}

dependencies {
    api(project(":core:domain"))

    api(libs.androidx.room.runtime)
    api(libs.androidx.room.common)
    api(libs.androidx.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.bundles.unitTest)
    testImplementation(libs.androidx.arch.core.testing)
}