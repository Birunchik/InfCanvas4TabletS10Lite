package com.infinitecanvas.board

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InfraSmokeTest {

    @Test
    fun junitWorks() {
        assertEquals(1, 1)
    }

    @Test
    fun coroutinesTestWorks() = runTest {
        // TestScope активен, advanceUntilIdle() не бросает
        assertEquals(2, 1 + 1)
    }

    @Test
    fun mockkWorks() {
        val mock: () -> Unit = mockk()
        every { mock() } just Runs

        mock()

        verify(exactly = 1) { mock() }
    }
}