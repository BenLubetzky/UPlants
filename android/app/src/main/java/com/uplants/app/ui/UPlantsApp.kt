package com.uplants.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.uplants.app.ui.auth.login.LoginScreen
import com.uplants.app.ui.auth.signup.SignUpScreen
import com.uplants.app.ui.home.HomeScreen
import kotlinx.serialization.Serializable

@Serializable
internal object LoginRoute

@Serializable
internal object SignUpRoute

@Composable
fun UPlantsApp(viewModel: AppViewModel = viewModel(factory = AppViewModel.Factory)) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    when (val state = authState) {
        AuthState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        AuthState.SignedOut -> AuthNavHost(onContinueAsGuest = viewModel::continueAsGuest)
        AuthState.Guest -> HomeScreen(username = null, onSignOut = viewModel::signOut)
        is AuthState.SignedIn -> HomeScreen(username = state.session.username, onSignOut = viewModel::signOut)
    }
}

@Composable
private fun AuthNavHost(onContinueAsGuest: () -> Unit) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = LoginRoute) {
        composable<LoginRoute> {
            LoginScreen(
                onNavigateToSignUp = { navController.navigate(SignUpRoute) },
                onContinueAsGuest = onContinueAsGuest,
            )
        }
        composable<SignUpRoute> {
            SignUpScreen(onNavigateToLogin = { navController.popBackStack() })
        }
    }
}
