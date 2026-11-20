package com.deskdays.app.ui.privacy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.deskdays.app.R
import com.deskdays.app.ui.components.LinkButton
import com.deskdays.app.ui.theme.DeskColors

/**
 * Shows res/raw/privacy_policy.md. That file is also the text to host publicly for the
 * Play Console privacy policy URL, so keep the two identical.
 */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val lines = remember {
        context.resources.openRawResource(R.raw.privacy_policy).bufferedReader().use { it.readLines() }
    }
    Column(
        Modifier.fillMaxSize().background(DeskColors.Background).systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LinkButton("‹ Back", onBack)
        lines.forEach { line ->
            when {
                line.startsWith("# ") -> Text(line.removePrefix("# "), style = MaterialTheme.typography.headlineMedium)
                line.startsWith("## ") -> Text(
                    line.removePrefix("## "),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                line.startsWith("- ") -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•", style = MaterialTheme.typography.bodyLarge)
                    Text(line.removePrefix("- "), style = MaterialTheme.typography.bodyLarge, color = DeskColors.TextBody)
                }
                line.isNotBlank() -> Text(line, style = MaterialTheme.typography.bodyLarge, color = DeskColors.TextBody)
            }
        }
    }
}
