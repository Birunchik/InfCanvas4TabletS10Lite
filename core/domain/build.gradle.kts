plugins {
    id("infcanvas.android.library")
}

android {
    namespace = "com.infinitecanvas.board.domain"
}

dependencies {
    // api, потому что BoardRepository возвращает Flow в публичном контракте
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
