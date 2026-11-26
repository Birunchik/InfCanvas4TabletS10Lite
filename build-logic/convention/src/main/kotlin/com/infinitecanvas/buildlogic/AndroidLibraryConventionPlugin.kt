package com.infinitecanvas.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Базовая конфигурация Android-библиотек InfCanvas.
 *
 * Модуль задаёт только `namespace` и зависимости; compileSdk/minSdk/Java/тесты
 * приходят отсюда, чтобы новые модули не расползались по версиям.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> {
                compileSdk = 37

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
                    animationsDisabled = true
                }
            }
        }
    }
}
