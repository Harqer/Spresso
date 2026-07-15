package com.meta.wearable.retail.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.meta.wearable.retail.RetailSessionManager
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testWelcomeMessageIsDisplayed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sessionManager = RetailSessionManager(context)

        composeTestRule.setContent {
            RetailMobileApp(
                userToken = "test_token",
                sessionManager = sessionManager,
                onCompletePurchase = { _, _ -> },
            )
        }

        composeTestRule.onNodeWithText("Spresso").assertIsDisplayed()
    }
}
