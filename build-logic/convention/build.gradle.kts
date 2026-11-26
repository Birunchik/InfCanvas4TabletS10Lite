plugins {
    `kotlin-dsl`
}

group = "com.infinitecanvas.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "infcanvas.android.library"
            implementationClass = "com.infinitecanvas.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("composeLibrary") {
            id = "infcanvas.android.compose"
            implementationClass = "com.infinitecanvas.buildlogic.ComposeLibraryConventionPlugin"
        }
    }
}
