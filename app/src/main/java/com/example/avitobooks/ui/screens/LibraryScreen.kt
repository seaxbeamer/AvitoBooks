package com.example.avitobooks.ui.screens

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import com.example.avitobooks.ui.theme.AvitoBooksTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import android.util.Log
import androidx.compose.ui.platform.LocalInspectionMode
data class UiBook(
    val id: String,
    val title: String,
    val author: String,
    val isDownloaded: Boolean,
    val storagePath: String,
    val extension: String,
    val localPath: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    onOpenBook: (title: String, localPath: String) -> Unit
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current

    val auth = remember { FirebaseAuth.getInstance() }
    val firestore = remember { FirebaseFirestore.getInstance() }
    val storage = remember { FirebaseStorage.getInstance() }
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var allBooks by remember { mutableStateOf<List<UiBook>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var fileActionBookId by remember { mutableStateOf<String?>(null) }

    fun loadBooks() {
        val currentUser = auth.currentUser
        val currentUserId = currentUser?.uid

        if (currentUserId == null) {
            isLoading = false
            errorMessage = "Пользователь не авторизован"
            return
        }

        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val snapshot = firestore.collection("books")
                    .whereEqualTo("userId", currentUserId)
                    .get()
                    .await()

                val result = snapshot.documents.map { doc ->
                    val id = doc.id
                    val title = doc.getString("title") ?: "Без названия"
                    val author = doc.getString("author") ?: "Неизвестный автор"
                    val storagePath = doc.getString("storagePath") ?: ""
                    val extension = doc.getString("extension") ?: "pdf"

                    val localFile = getLocalBookFile(
                        context = context,
                        userId = currentUserId,
                        title = title,
                        author = author,
                        bookId = id,
                        extension = extension
                    )
                    val isDownloaded = localFile.exists()
                    val localPath = if (isDownloaded) localFile.absolutePath else null

                    UiBook(
                        id = id,
                        title = title,
                        author = author,
                        isDownloaded = isDownloaded,
                        storagePath = storagePath,
                        extension = extension,
                        localPath = localPath
                    )
                }

                allBooks = result
            } catch (e: Exception) {
                Log.e("LibraryScreen", "loadBooks error", e)
                errorMessage = "Не удалось загрузить список книг. Попробуйте ещё раз."
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!isPreview) {
            loadBooks()
        }
    }

    fun handleLocalCopyAction(book: UiBook) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(context, "Пользователь не авторизован", Toast.LENGTH_SHORT).show()
            return
        }

        if (fileActionBookId != null) return

        scope.launch {
            fileActionBookId = book.id
            try {
                if (book.isDownloaded) {
                    val localFile = getLocalBookFile(
                        context = context,
                        userId = currentUser.uid,
                        title = book.title,
                        author = book.author,
                        bookId = book.id,
                        extension = book.extension
                    )

                    val deleted = withContext(Dispatchers.IO) {
                        localFile.delete()
                    }

                    if (deleted) {
                        allBooks = allBooks.map {
                            if (it.id == book.id) it.copy(isDownloaded = false) else it
                        }
                        Toast.makeText(
                            context,
                            "Локальная копия удалена",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "Не удалось удалить файл",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    if (book.storagePath.isBlank()) {
                        Toast.makeText(
                            context,
                            "Не указан путь к файлу в хранилище",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }

                    val localFile = getLocalBookFile(
                        context = context,
                        userId = currentUser.uid,
                        title = book.title,
                        author = book.author,
                        bookId = book.id,
                        extension = book.extension
                    )

                    withContext(Dispatchers.IO) {
                        localFile.parentFile?.mkdirs()
                    }

                    Log.d(
                        "LibraryScreen",
                        "Downloading book '${book.title}' to: ${localFile.absolutePath}"
                    )

                    try {
                        val ref = storage.reference.child(book.storagePath)
                        ref.getFile(localFile).await()
                    } catch (e: Exception) {
                        withContext(Dispatchers.IO) {
                            if (localFile.exists() && localFile.length() == 0L) {
                                localFile.delete()
                            }
                        }
                        throw e
                    }

                    allBooks = allBooks.map {
                        if (it.id == book.id) it.copy(
                            isDownloaded = true,
                            localPath = localFile.absolutePath
                        ) else it
                    }

                    Toast.makeText(
                        context,
                        "Книга загружена на устройство",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("LibraryScreen", "handleLocalCopyAction error", e)
                Toast.makeText(
                    context,
                    "Ошибка работы с файлом: ${e.localizedMessage ?: "повторите попытку"}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                fileActionBookId = null
            }
        }
    }

    fun handleDeleteFromCloud(book: UiBook) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(context, "Пользователь не авторизован", Toast.LENGTH_SHORT).show()
            return
        }

        if (fileActionBookId != null) return

        scope.launch {
            fileActionBookId = book.id
            try {
                firestore.collection("books")
                    .document(book.id)
                    .delete()
                    .await()

                if (book.storagePath.isNotBlank()) {
                    try {
                        storage.reference.child(book.storagePath).delete().await()
                    } catch (e: Exception) {
                        Log.w("LibraryScreen", "Failed to delete file in Storage", e)
                    }
                }

                withContext(Dispatchers.IO) {
                    val localFile = getLocalBookFile(
                        context = context,
                        userId = currentUser.uid,
                        title = book.title,
                        author = book.author,
                        bookId = book.id,
                        extension = book.extension
                    )
                    if (localFile.exists()) {
                        localFile.delete()
                    }
                }

                allBooks = allBooks.filterNot { it.id == book.id }

                Toast.makeText(
                    context,
                    "Книга удалена из аккаунта",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Log.e("LibraryScreen", "handleDeleteFromCloud error", e)
                Toast.makeText(
                    context,
                    "Не удалось удалить книгу: ${e.localizedMessage ?: "повторите попытку"}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                fileActionBookId = null
            }
        }
    }

    val filteredBooks = remember(query, allBooks) {
        if (query.isBlank()) {
            allBooks
        } else {
            allBooks.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.author.contains(query, ignoreCase = true)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Мои книги") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Поиск книг…") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { loadBooks() }) {
                                Text("Повторить")
                            }
                        }
                    }
                }

                allBooks.isEmpty() -> {
                    LibraryEmptyState(text = "У вас пока нет книг")
                }

                filteredBooks.isEmpty() -> {
                    LibraryEmptyState(text = "Ничего не найдено")
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredBooks, key = { it.id }) { book ->
                            BookListItem(
                                book = book,
                                onClick = {
                                    val localPath = book.localPath
                                    if (book.isDownloaded && localPath != null) {
                                        onOpenBook(book.title, localPath)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Сначала скачайте книгу",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                onToggleLocalCopyClick = {
                                    handleLocalCopyAction(book)
                                },
                                onDeleteFromCloudClick = {
                                    handleDeleteFromCloud(book)
                                },
                                isFileActionInProgress = (fileActionBookId == book.id)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryEmptyState(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun BookListItem(
    book: UiBook,
    onClick: () -> Unit,
    onToggleLocalCopyClick: () -> Unit,
    onDeleteFromCloudClick: () -> Unit,
    isFileActionInProgress: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = book.title.firstOrNull()?.uppercase() ?: "",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onToggleLocalCopyClick,
                enabled = !isFileActionInProgress
            ) {
                if (isFileActionInProgress) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    val icon = if (book.isDownloaded) {
                        Icons.Default.Delete
                    } else {
                        Icons.Default.CloudDownload
                    }
                    val description = if (book.isDownloaded) {
                        "Удалить локальную копию"
                    } else {
                        "Загрузить книгу на устройство"
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = description
                    )
                }
            }

            IconButton(
                onClick = onDeleteFromCloudClick,
                enabled = !isFileActionInProgress
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = "Удалить книгу из аккаунта"
                )
            }
        }
    }
}

private fun sanitizeFileComponent(input: String): String =
    input.trim()
        .replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        .replace("\\s+".toRegex(), "_")
        .ifBlank { "unknown" }
        .take(50)

private fun getUserBooksDir(context: Context, userId: String): File {
    val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        ?: context.filesDir
    val dir = File(baseDir, "AvitoBooks/$userId")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    return dir
}

private fun getLocalBookFile(
    context: Context,
    userId: String,
    title: String,
    author: String,
    bookId: String,
    extension: String
): File {
    val safeAuthor = sanitizeFileComponent(author)
    val safeTitle = sanitizeFileComponent(title)
    val ext = if (extension.isNotBlank()) extension else "pdf"

    val fileName = "${safeAuthor}_${safeTitle}_${bookId}.$ext"
    val dir = getUserBooksDir(context, userId)
    return File(dir, fileName)
}

@PreviewScreenSizes
@Preview(showBackground = true)
@Composable
private fun LibraryScreenPreview() {
    AvitoBooksTheme {
        LibraryScreen(onOpenBook = { _, _ -> })
    }
}