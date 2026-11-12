package com.uplants.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.uplants.app.data.notifications.DeepLinks
import com.uplants.app.ui.UPlantsApp
import com.uplants.app.ui.theme.UPlantsTheme

class MainActivity : ComponentActivity() {

    private val container get() = (application as UPlantsApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Opened by tapping a notification: remember where to go (MainScreen navigates there).
        if (savedInstanceState == null) DeepLinks.readFrom(intent)?.let { container.pendingDeepLink.value = it }
        setContent {
            UPlantsTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    UPlantsApp()
                }
            }
        }
    }

    /** A notification tapped while the app was already open. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        DeepLinks.readFrom(intent)?.let { container.pendingDeepLink.value = it }
    }

    /** Coming back to the app: look for new activity now instead of waiting for the 15-minute job. */
    override fun onStart() {
        super.onStart()
        if (container.sessions.current() != null) container.notificationScheduler.checkNow()
    }
}
