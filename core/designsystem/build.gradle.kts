plugins {
    id("infcanvas.android.compose")
}

android {
    namespace = "com.infinitecanvas.designsystem"
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.compose.material3)
}
