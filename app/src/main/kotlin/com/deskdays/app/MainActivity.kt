package com.deskdays.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.deskdays.app.tracking.Tracking
import com.deskdays.app.ui.DeskDaysRoot
import com.deskdays.app.ui.theme.DeskDaysTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DeskDaysTheme {
                DeskDaysRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Permissions may have changed in system settings while we were away.
        Tracking.sync(this)
    }
}
