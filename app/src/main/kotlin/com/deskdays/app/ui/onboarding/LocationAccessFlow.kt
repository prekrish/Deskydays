package com.deskdays.app.ui.onboarding

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.deskdays.app.R
import com.deskdays.app.location.LocationAccess
import com.deskdays.app.location.LocationPermissions
import com.deskdays.app.ui.components.DeskCard
import com.deskdays.app.ui.components.LinkButton
import com.deskdays.app.ui.components.PrimaryButton
import com.deskdays.app.ui.components.QuietButton
import com.deskdays.app.ui.components.SecondaryButton
import com.deskdays.app.ui.theme.DeskColors

private enum class Step { DISCLOSURE, BACKGROUND, NEEDS_PRECISE, DENIED, BACKGROUND_DENIED }

/**
 * Google Play's required flow for background location:
 * 1. Prominent in-app disclosure (before any permission prompt).
 * 2. Foreground (precise) location request.
 * 3. Separate background request ("Allow all the time"), with its own explanation.
 * Declining at any point offers manual logging instead.
 *
 * @param onAutomatic called once precise "all the time" access is granted.
 * @param onManual called when the user chooses to log days manually.
 */
@Composable
fun LocationAccessFlow(
    onAutomatic: () -> Unit,
    onManual: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(Step.DISCLOSURE) }
    // The permission result and the on-resume check can both see the grant; finish only once.
    var finished by remember { mutableStateOf(false) }
    val finish = {
        if (!finished) {
            finished = true
            onAutomatic()
        }
    }

    fun route(after: LocationAccess) {
        step = when (after) {
            LocationAccess.PRECISE_ALWAYS -> { finish(); return }
            LocationAccess.PRECISE_WHILE_IN_USE ->
                if (step == Step.BACKGROUND || step == Step.BACKGROUND_DENIED) Step.BACKGROUND_DENIED else Step.BACKGROUND
            LocationAccess.APPROXIMATE_ONLY -> Step.NEEDS_PRECISE
            LocationAccess.NONE -> Step.DENIED
        }
    }

    val foregroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        route(LocationPermissions.current(context))
    }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        route(LocationPermissions.current(context))
    }

    // The user may grant access in system settings and come back.
    LifecycleResumeEffect(step) {
        if (step != Step.DISCLOSURE && LocationPermissions.current(context) == LocationAccess.PRECISE_ALWAYS) finish()
        onPauseOrDispose { }
    }

    BackHandler(enabled = step != Step.DISCLOSURE || onBack != null) {
        if (step == Step.DISCLOSURE) onBack?.invoke() else step = Step.DISCLOSURE
    }

    fun requestForeground() = foregroundLauncher.launch(LocationPermissions.FOREGROUND)
    fun requestBackground() = backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    fun openSettings() = context.startActivity(LocationPermissions.appSettingsIntent(context))

    when (step) {
        Step.DISCLOSURE -> DisclosureScreen(
            onContinue = {
                when (LocationPermissions.current(context)) {
                    LocationAccess.PRECISE_ALWAYS -> finish()
                    LocationAccess.PRECISE_WHILE_IN_USE -> step = Step.BACKGROUND
                    else -> requestForeground()
                }
            },
            onManual = onManual,
            onOpenPrivacyPolicy = onOpenPrivacyPolicy,
        )

        Step.BACKGROUND -> InfoScreen(
            icon = R.drawable.ic_tab_office,
            title = "One more step: allow all the time",
            body = if (LocationPermissions.backgroundOpensSettings) {
                "Android will open Desk Days' location settings. Choose \"Allow all the time\", then come back."
            } else {
                "On the next prompt, choose \"Allow all the time\"."
            },
            note = "Without this, Desk Days can only see you at the office while the app is open.",
            primary = (if (LocationPermissions.backgroundOpensSettings) "Open location settings" else "Continue") to ::requestBackground,
            onManual = onManual,
        )

        Step.NEEDS_PRECISE -> InfoScreen(
            icon = R.drawable.ic_tab_office,
            title = "Turn on precise location",
            body = "Approximate location can be off by a few kilometres, which is too rough to tell whether you're at your office.",
            note = "Turn on \"Use precise location\" for Desk Days.",
            primary = "Try again" to ::requestForeground,
            secondary = "Open app settings" to ::openSettings,
            onManual = onManual,
        )

        Step.DENIED -> InfoScreen(
            icon = R.drawable.ic_tab_office,
            title = "Location access is off",
            body = "That's fine. You can log office days yourself, or allow location later from the Office tab.",
            note = "If Android doesn't ask again, allow location in app settings.",
            primary = "Try again" to ::requestForeground,
            secondary = "Open app settings" to ::openSettings,
            onManual = onManual,
        )

        Step.BACKGROUND_DENIED -> InfoScreen(
            icon = R.drawable.ic_tab_office,
            title = "\"Allow all the time\" wasn't chosen",
            body = "Desk Days needs it to notice when you arrive at and leave the office while the app is closed.",
            note = "Open app settings → Permissions → Location → Allow all the time.",
            primary = "Open app settings" to ::openSettings,
            secondary = "Try again" to ::requestBackground,
            onManual = onManual,
        )
    }
}

/** The prominent disclosure. Wording follows the mockup and Play's disclosure requirements. */
@Composable
private fun DisclosureScreen(onContinue: () -> Unit, onManual: () -> Unit, onOpenPrivacyPolicy: () -> Unit) {
    OnboardingScaffold(
        icon = R.drawable.ic_tab_office,
        title = "Let Desk Days know when you're at the office",
        body = "Desk Days uses your location in the background, even when the app is closed, to log when you " +
            "arrive at and leave your office. That's how it counts your office days.",
        content = {
            DeskCard(padding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
                Bullet("Only arrival and departure times at your office are saved, not where else you go")
                Bullet("Stays on this phone, unless you turn on backup to your own Google Drive")
                Bullet("Turn it off anytime in Settings and log days manually instead")
            }
            Text(
                buildAnnotatedString {
                    append("On the next screen, choose ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Allow all the time") }
                    append(" so tracking works when the app is closed.")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = DeskColors.TextBody,
            )
        },
        actions = {
            PrimaryButton("Continue", onContinue)
            QuietButton("Not now, I'll log days manually", onManual)
            LinkButton("Privacy policy", onOpenPrivacyPolicy, Modifier.align(Alignment.CenterHorizontally))
        },
    )
}

@Composable
private fun InfoScreen(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    note: String,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>? = null,
    onManual: () -> Unit,
) {
    OnboardingScaffold(
        icon = icon,
        title = title,
        body = body,
        content = {
            DeskCard { Text(note, style = MaterialTheme.typography.bodyLarge) }
        },
        actions = {
            PrimaryButton(primary.first, primary.second)
            secondary?.let { SecondaryButton(it.first, it.second) }
            QuietButton("Log days manually instead", onManual)
        },
    )
}

@Composable
internal fun OnboardingScaffold(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    content: @Composable () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(DeskColors.Background).systemBarsPadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(
                Modifier.size(72.dp).background(DeskColors.AccentContainer, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), contentDescription = null, tint = DeskColors.Accent, modifier = Modifier.size(36.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 34.sp))
                Text(body, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp), color = DeskColors.TextBody)
            }
            content()
        }
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = actions,
        )
    }
}

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = DeskColors.Accent, modifier = Modifier.size(22.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp))
    }
}
