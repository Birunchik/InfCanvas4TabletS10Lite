plugins {
    id("infcanvas.android.library")
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.kover)
}

android {
    namespace = "com.infinitecanvas.board.data"

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
}
