package com.aion.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.rememberNavController
import com.aion.agent.ui.navigation.AionNavHost
import com.aion.agent.ui.theme.AionTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single activity. All screens are Compose destinations inside [AionNavHost].
 *
 * The activity is intentionally thin — no business logic, no data, no DI lookup.
 * It exists to host the Compose tree and survive configuration changes.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AionRoot() }
    }
}

@Composable
private fun AionRoot() {
    AionTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent,
        ) {
            val navController = rememberNavController()
            AionNavHost(navController = navController)
        }
    }
}
