package com.infinitecanvas.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InfraInstrumentedSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun composeTestRuleWorks() {
        compose.setContent {
            Box(modifier = Modifier.fillMaxSize())
        }
        compose.waitForIdle()
        assertTrue(true) // если дошли сюда — Rule откомпилирован и работает
    }
}