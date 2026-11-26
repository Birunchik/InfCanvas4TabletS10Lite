plugins {
    id("infcanvas.android.library")
}

android {
    namespace = "com.infinitecanvas.testing"
}

dependencies {
    api(project(":core:domain"))
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(libs.mockk)
}
