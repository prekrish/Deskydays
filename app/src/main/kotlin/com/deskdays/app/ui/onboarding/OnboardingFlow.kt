package com.deskdays.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import com.deskdays.app.data.SettingsRepository
import com.deskdays.app.data.TrackingMode
import com.deskdays.app.ui.office.OfficeSetupMode
import com.deskdays.app.ui.office.OfficeSetupScreen
import com.deskdays.app.ui.privacy.PrivacyPolicyScreen
import kotlinx.coroutines.launch

private enum class OnboardingStep { ACCESS, OFFICE }

/** First run: location disclosure + permissions, then office setup. */
@Composable
fun OnboardingFlow(settings: SettingsRepository) {
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(OnboardingStep.ACCESS) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }

    if (showPrivacy) {
        PrivacyPolicyScreen(onBack = { showPrivacy = false })
        return
    }

    when (step) {
        OnboardingStep.ACCESS -> LocationAccessFlow(
            onAutomatic = {
                scope.launch { settings.setTrackingMode(TrackingMode.AUTOMATIC) }
                step = OnboardingStep.OFFICE
            },
            onManual = {
                scope.launch { settings.setTrackingMode(TrackingMode.MANUAL) }
                step = OnboardingStep.OFFICE
            },
            onOpenPrivacyPolicy = { showPrivacy = true },
        )

        OnboardingStep.OFFICE -> {
            BackHandler { step = OnboardingStep.ACCESS }
            Box(Modifier.systemBarsPadding()) {
                OfficeSetupScreen(
                    mode = OfficeSetupMode.ONBOARDING,
                    // Finishing onboarding flips Settings.onboardingDone, and the root swaps to the main tabs.
                    onDone = { scope.launch { settings.completeOnboarding() } },
                    onChangeTracking = { step = OnboardingStep.ACCESS },
                    onOpenPrivacyPolicy = { showPrivacy = true },
                )
            }
        }
    }
}
