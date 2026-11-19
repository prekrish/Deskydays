package com.deskdays.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
}
