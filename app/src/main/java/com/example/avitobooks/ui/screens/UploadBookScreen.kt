package com.example.avitobooks.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.avitobooks.ui.theme.AvitoBooksTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadBookScreen(
    modifier: Modifier = Modifier,
    onUploadSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val firestore = remember { FirebaseFirestore.getInstance() }
    val storage = remember { FirebaseStorage.getInstance() }
    val scope = rememberCoroutineScope()

    var title by rememberSaveable { mutableStateOf("") }
    var author by rememberSaveable { mutableStateOf("") }
    var fileName by rememberSaveable { mutableStateOf<String?>(null) }
    var fileUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            fileUri = uri
            fileName = queryDisplayName(context, uri)
            errorMessage = null
            successMessage = null
        }
    }

    fun startUpload() {
        val currentUser = auth.currentUser
        val uri = fileUri

        if (currentUser == null) {
            errorMessage = "Пользователь не авторизован"
            return
        }
        if (uri == null) {
            errorMessage = "Выберите файл книги"
            return
        }
        if (title.isBlank() || author.isBlank()) {
            errorMessage = "Введите название и автора"
            return
        }

        scope.launch {
            isUploading = true
            errorMessage = null
            successMessage = null

            try {
                val ext = resolveExtension(context, uri, fileName)

                val booksCollection = firestore.collection("books")
                val docRef = booksCollection.document()

                val storagePath = "books/${currentUser.uid}/${docRef.id}.$ext"
                val storageRef = storage.reference.child(storagePath)

                val uploadTaskSnapshot = storageRef.putFile(uri).await()
                if (uploadTaskSnapshot.error != null) {
                    throw uploadTaskSnapshot.error!!
                }

                val downloadUrl = storageRef.downloadUrl.await()

                val bookData = mapOf(
                    "title" to title.trim(),
                    "author" to author.trim(),
                    "userId" to currentUser.uid,
                    "extension" to ext,
                    "storagePath" to storagePath,
                    "fileUrl" to downloadUrl.toString()
                )

                docRef.set(bookData).await()

                successMessage = "Книга загружена в аккаунт"
                onUploadSuccess()
            } catch (e: Exception) {
                Log.e("UploadBookScreen", "startUpload error", e)
                errorMessage = "Ошибка загрузки: ${e.localizedMessage ?: "повторите попытку"}"
            } finally {
                isUploading = false
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Загрузить книгу") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxSize()
        ) {
            Text(
                text = "Файл будет сохранён в облако. " + "Локальную копию можно скачать во вкладке «Мои книги».",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { filePickerLauncher.launch("*/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = fileName ?: "Выбрать файл")
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Название книги") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Автор") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (successMessage != null) {
                Text(
                    text = successMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { startUpload() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isUploading &&
                        title.isNotBlank() &&
                        author.isNotBlank() &&
                        fileUri != null
            ) {
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(20.dp)
                            .padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text("Загрузить")
            }
        }
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
    cursor.use {
        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1 && it.moveToFirst()) {
            return it.getString(nameIndex)
        }
    }
    return null
}

private fun resolveExtension(
    context: Context,
    uri: Uri,
    fileName: String?
): String {
    val fromName = fileName
        ?.substringAfterLast('.', missingDelimiterValue = "")
        .orEmpty()
    if (fromName.isNotBlank()) return fromName.lowercase()

    val mime = context.contentResolver.getType(uri)
    val fromMime = mime
        ?.substringAfterLast('/', missingDelimiterValue = "")
        .orEmpty()
    if (fromMime.isNotBlank()) return fromMime.lowercase()

    return "pdf"
}

@Preview(showBackground = true)
@Composable
private fun UploadBookScreenPreview() {
    AvitoBooksTheme {
        UploadBookScreen()
    }
}