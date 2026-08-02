package com.crosstrainer.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.crosstrainer.companion.ui.dashboard.DashboardRoute
import com.crosstrainer.companion.ui.theme.CrosstrainerCompanionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CrosstrainerCompanionTheme {
                DashboardRoute()
            }
        }
    }
}

