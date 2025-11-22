package com.example.avitobooks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.example.avitobooks.ui.screens.AuthScreen
import com.example.avitobooks.ui.screens.LibraryScreen
import com.example.avitobooks.ui.screens.ProfileScreen
import com.example.avitobooks.ui.screens.UploadBookScreen
import com.example.avitobooks.ui.theme.AvitoBooksTheme
import androidx.navigation.compose.composable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AvitoBooksTheme {
                RootNavigation()
            }
        }
    }
}

@Composable
fun RootNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "auth"
    ) {
        composable("auth") {
            AuthScreen(
                onAuthSuccess = {
                    navController.navigate("main") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }
        composable("main") {
            AvitoBooksApp()
        }
    }
}

@Composable
fun AvitoBooksApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.LIBRARY) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach { destination ->
                item(
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label
                        )
                    },
                    label = { Text(destination.label) },
                    selected = destination == currentDestination,
                    onClick = { currentDestination = destination }
                )
            }
        }
    ) {
        when (currentDestination) {
            AppDestinations.LIBRARY -> LibraryScreen()
            AppDestinations.UPLOAD -> UploadBookScreen()
            AppDestinations.PROFILE -> ProfileScreen()
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector
) {
    LIBRARY("Мои книги", Icons.Filled.Home),
    UPLOAD("Загрузка", Icons.Filled.CloudUpload),
    PROFILE("Профиль", Icons.Filled.AccountBox)
}

@PreviewScreenSizes
@Preview(showBackground = true)
@Composable
private fun AvitoBooksAppPreview() {
    AvitoBooksTheme {
        AvitoBooksApp()
    }
}