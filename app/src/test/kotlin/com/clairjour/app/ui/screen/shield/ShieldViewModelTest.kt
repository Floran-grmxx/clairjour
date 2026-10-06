package com.clairjour.app.ui.screen.shield

import com.clairjour.app.data.prefs.SettingsRepository
import com.clairjour.app.shield.ShieldRule
import com.clairjour.app.shield.ShieldSettings
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShieldViewModelTest {

    private var now = 100_000L
    private val repository = mockk<SettingsRepository>(relaxed = true) {
        every { shieldSettingsFlow } returns flowOf(ShieldSettings())
    }
    private lateinit var viewModel: ShieldViewModel

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = ShieldViewModel(repository) { now }
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `countdown is based on a deadline and survives a new dialog`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        viewModel.onRuleChange(ShieldRule.REELS_TAB, enabled = false)
        val request = requireNotNull(viewModel.state.value.pendingDisable)
        assertEquals(ShieldSettings.DISABLE_DELAY_SECONDS, viewModel.secondsBeforeConfirm(request))

        // Rotating recreates the dialog: it reads the same deadline, so time keeps going down.
        now += 4_000
        val afterRotation = requireNotNull(viewModel.state.value.pendingDisable)
        assertEquals(ShieldSettings.DISABLE_DELAY_SECONDS - 4, viewModel.secondsBeforeConfirm(afterRotation))
    }

    @Test
    fun `confirming before the deadline does nothing`() {
        viewModel.onRuleChange(ShieldRule.REELS_TAB, enabled = false)
        viewModel.confirmDisable()
        coVerify(exactly = 0) { repository.setShieldRule(any(), any()) }

        now += ShieldSettings.DISABLE_DELAY_SECONDS * 1_000L
        viewModel.confirmDisable()
        coVerify(exactly = 1) { repository.setShieldRule(ShieldRule.REELS_TAB, false) }
    }

    @Test
    fun `switching on is immediate`() {
        viewModel.onRuleChange(ShieldRule.STORIES, enabled = true)
        coVerify(exactly = 1) { repository.setShieldRule(ShieldRule.STORIES, true) }
    }
}
