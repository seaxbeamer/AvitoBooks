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
import com.google.firebase.auth.FirebaseAuth
import com.example.avitobooks.ui.screens.BookReaderScreen

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
    val isLoggedIn = FirebaseAuth.getInstance().currentUser != null

    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) "main" else "auth"
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
            AvitoBooksApp(
                onLogout = {
                    navController.navigate("auth") {
                        popUpTo("main") { inclusive = true }
                    }
                },
                onOpenBook = { title, localPath ->
                    val encodedTitle = android.net.Uri.encode(title)
                    val encodedPath = android.net.Uri.encode(localPath)

                    navController.navigate(
                        "reader?title=$encodedTitle&path=$encodedPath"
                    )
                }
            )
        }

        composable(
            route = "reader?title={title}&path={path}",
            arguments = listOf(
                androidx.navigation.navArgument("title") {
                    type = androidx.navigation.NavType.StringType
                },
                androidx.navigation.navArgument("path") {
                    type = androidx.navigation.NavType.StringType
                }
            )
        ) { backStackEntry ->
            val bookTitle = backStackEntry.arguments?.getString("title") ?: "Книга"
            val localPath = backStackEntry.arguments?.getString("path").orEmpty()

            BookReaderScreen(
                title = bookTitle,
                localFilePath = localPath,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun AvitoBooksApp(
    onLogout: () -> Unit,
    onOpenBook: (title: String, localPath: String) -> Unit
) {
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
            AppDestinations.LIBRARY -> LibraryScreen(
                onOpenBook = onOpenBook
            )
            AppDestinations.UPLOAD -> UploadBookScreen()
            AppDestinations.PROFILE -> ProfileScreen(onLogout = onLogout)
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
        AvitoBooksApp(
            onLogout = {},
            onOpenBook = { _, _ -> }
        )
    }
}