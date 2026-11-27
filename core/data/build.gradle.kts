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
    // domain остаётся api: публичные типы core:data реализуют его интерфейсы
    // (BoardRepositoryImpl : BoardRepository), и потребителю они нужны.
    api(project(":core:domain"))

    // Room — implementation: снаружи модуля его типы не используются. DAO и
    // BoardRepositoryImpl internal, наружу отдаётся только доменный BoardRepository
    // через createBoardRepository(), поэтому Room не протекает в classpath.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.common)
    // room-ktx не подключаем: с Room 2.7 Flow/suspend-поддержка переехала в room-runtime,
    // а Dependency Analysis помечал room-ktx как неиспользуемую зависимость.
    ksp(libs.room.compiler)

    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.bundles.unitTest)
}
