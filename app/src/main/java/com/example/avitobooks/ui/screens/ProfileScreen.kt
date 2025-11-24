package com.example.avitobooks.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.avitobooks.ui.theme.AvitoBooksTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    onLogout: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val firestore = remember { FirebaseFirestore.getInstance() }
    val storage = remember { FirebaseStorage.getInstance() }

    val coroutineScope = rememberCoroutineScope()
    val user = auth.currentUser

    val scrollState = rememberScrollState()

    var displayName by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    var isInitialLoading by remember { mutableStateOf(true) }
    var isSavingProfile by remember { mutableStateOf(false) }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    val email = user?.email ?: "user@example.com"

    LaunchedEffect(user?.uid) {
        if (user == null) {
            isInitialLoading = false
            errorMessage = "Пользователь не авторизован"
            return@LaunchedEffect
        }

        isInitialLoading = true
        errorMessage = null
        successMessage = null

        try {
            val docRef = firestore.collection("users").document(user.uid)
            val snapshot = docRef.get().await()

            if (snapshot.exists()) {
                val data = snapshot.data
                val firestoreName = data?.get("displayName") as? String
                val firestoreStatus = data?.get("status") as? String
                val firestorePhoto = data?.get("photoUrl") as? String

                displayName = firestoreName ?: user.displayName.orEmpty()
                status = firestoreStatus.orEmpty()
                photoUri = firestorePhoto
                    ?.takeIf { it.isNotBlank() }
                    ?.let { Uri.parse(it) }
                    ?: user.photoUrl
            } else {
                displayName = user.displayName.orEmpty()
                status = ""
                photoUri = user.photoUrl

                val initialData = mapOf(
                    "displayName" to displayName,
                    "status" to status,
                    "photoUrl" to (photoUri?.toString() ?: ""),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(initialData, SetOptions.merge()).await()
            }
        } catch (e: Exception) {
            displayName = user.displayName.orEmpty()
            status = ""
            photoUri = user.photoUrl
            errorMessage = "Не удалось загрузить профиль. Попробуйте ещё раз."
        } finally {
            isInitialLoading = false
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { pickedUri ->
        val currentUser = auth.currentUser
        if (pickedUri == null || currentUser == null) return@rememberLauncherForActivityResult

        photoUri = pickedUri
        errorMessage = null
        successMessage = null

        coroutineScope.launch {
            isUploadingAvatar = true
            try {
                val ref = storage.reference
                    .child("avatars")
                    .child(currentUser.uid)

                ref.putFile(pickedUri).await()
                val downloadUrl = ref.downloadUrl.await()

                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setPhotoUri(downloadUrl)
                    .setDisplayName(displayName.trim().takeIf { it.isNotBlank() })
                    .build()
                currentUser.updateProfile(profileUpdates).await()

                val profileData = mapOf(
                    "displayName" to displayName.trim(),
                    "status" to status.trim(),
                    "photoUrl" to downloadUrl.toString(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                firestore.collection("users")
                    .document(currentUser.uid)
                    .set(profileData, SetOptions.merge())
                    .await()

                photoUri = downloadUrl
                successMessage = "Аватар обновлён"
            } catch (e: Exception) {
                errorMessage = "Не удалось загрузить фото. Попробуйте ещё раз."
            } finally {
                isUploadingAvatar = false
            }
        }
    }

    fun saveProfile() {
        val currentUser = auth.currentUser ?: return

        coroutineScope.launch {
            isSavingProfile = true
            errorMessage = null
            successMessage = null
            try {
                val trimmedName = displayName.trim()
                val trimmedStatus = status.trim()

                val profileData = mapOf(
                    "displayName" to trimmedName,
                    "status" to trimmedStatus,
                    "photoUrl" to (photoUri?.toString() ?: ""),
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                firestore.collection("users")
                    .document(currentUser.uid)
                    .set(profileData, SetOptions.merge())
                    .await()

                val updates = UserProfileChangeRequest.Builder()
                    .setDisplayName(trimmedName.takeIf { it.isNotBlank() })
                    .build()
                currentUser.updateProfile(updates).await()

                successMessage = "Профиль сохранён"
            } catch (e: Exception) {
                errorMessage = "Не удалось сохранить профиль. Попробуйте ещё раз."
            } finally {
                isSavingProfile = false
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Профиль") }
            )
        }
    ) { innerPadding ->
        if (isInitialLoading) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Блок аватарки
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            shape = CircleShape
                        )
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .align(Alignment.CenterHorizontally)
                        .let {
                            if (!isUploadingAvatar && user != null) {
                                it.then(
                                    Modifier
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                        .clickable { imagePickerLauncher.launch("image/*") }
                                )
                            } else it
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (photoUri != null) {
                        AsyncImage(
                            model = photoUri,
                            contentDescription = "Аватар",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountBox,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp)
                        )
                    }

                    if (isUploadingAvatar) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Настройки профиля",
                            style = MaterialTheme.typography.titleMedium
                        )

                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Никнейм") },
                            singleLine = true,
                            enabled = !isSavingProfile && !isUploadingAvatar
                        )

                        OutlinedTextField(
                            value = status,
                            onValueChange = { status = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Статус") },
                            maxLines = 3,
                            enabled = !isSavingProfile && !isUploadingAvatar
                        )

                        AnimatedVisibility(visible = errorMessage != null) {
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        AnimatedVisibility(visible = successMessage != null) {
                            Text(
                                text = successMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Button(
                            onClick = { saveProfile() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = user != null && !isSavingProfile && !isUploadingAvatar
                        ) {
                            if (isSavingProfile) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier
                                        .height(20.dp)
                                        .padding(end = 8.dp)
                                )
                            }
                            Text("Сохранить профиль")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Аккаунт",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "«Читатель проживает тысячу жизней, прежде чем умрет. Человек, который никогда не читает, переживает только одну» — Д. Мартин.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedButton(
                            onClick = {
                                auth.signOut()
                                onLogout()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = user != null && !isSavingProfile && !isUploadingAvatar
                        ) {
                            Text("Выйти из аккаунта")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    AvitoBooksTheme {
        ProfileScreen(onLogout = {})
    }
}