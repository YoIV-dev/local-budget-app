package com.localbudget.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.localbudget.app.ui.navigation.AppNavGraph
import com.localbudget.app.ui.navigation.Screen
import com.localbudget.app.ui.theme.LocalBudgetTheme

class MainActivity : ComponentActivity() {

    /** Screen a notification asked to open; cleared once the nav graph has shown it. */
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as LocalBudgetApplication).container
        // Only on a fresh start: after a config change the intent would reopen the screen again.
        // Reopening from Recents also replays the launching intent, which may be an old notification tap.
        val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromHistory) pendingRoute.value = routeOf(intent)

        setContent {
            LocalBudgetTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavGraph(
                        appContainer = appContainer,
                        pendingRoute = pendingRoute.value,
                        onPendingRouteHandled = { pendingRoute.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        routeOf(intent)?.let { pendingRoute.value = it }
    }

    private fun routeOf(intent: Intent?): String? = when (intent?.getStringExtra(EXTRA_OPEN_ROUTE)) {
        ROUTE_FRIDGE -> Screen.Fridge.route
        else -> null
    }

    companion object {
        const val EXTRA_OPEN_ROUTE = "open_route"
        const val ROUTE_FRIDGE = "fridge"
    }
}
