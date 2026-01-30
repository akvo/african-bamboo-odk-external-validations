package org.akvo.afribamodkvalidator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import org.akvo.afribamodkvalidator.data.session.SessionManager
import org.akvo.afribamodkvalidator.navigation.AppNavHost
import org.akvo.afribamodkvalidator.ui.theme.AfriBamODKValidatorTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val isLoggedIn = sessionManager.isLoggedIn()

        setContent {
            AfriBamODKValidatorTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavHost(
                        navController = navController,
                        isLoggedIn = isLoggedIn,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
