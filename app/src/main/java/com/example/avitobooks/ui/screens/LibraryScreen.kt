package com.example.avitobooks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import com.example.avitobooks.ui.theme.AvitoBooksTheme

data class UiBook(
    val id: String,
    val title: String,
    val author: String,
    val isDownloaded: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }

    // FIXME заглушка для примера
    val allBooks = remember {
        listOf(
            UiBook(
                id = "1",
                title = "Мастер и Маргарита",
                author = "М. Булгаков",
                isDownloaded = true
            ),
            UiBook(
                id = "2",
                title = "Преступление и наказание",
                author = "Ф. Достоевский",
                isDownloaded = false
            ),
        )
    }

    val filteredBooks = if (query.isBlank()) {
        allBooks
    } else {
        allBooks.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.author.contains(query, ignoreCase = true)
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
                allBooks.isEmpty() -> {
                    LibraryEmptyState(text = "У вас пока нет скачанных книг")
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
                                    // TODO переход на экран чтения книги
                                },
                                onActionClick = {
                                    // TODO либо удалить локальный файл либо скачать из облака
                                }
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
    onActionClick: () -> Unit,
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

            IconButton(onClick = onActionClick) {
                val icon = if (book.isDownloaded) {
                    Icons.Default.Delete
                } else {
                    Icons.Default.CloudDownload
                }
                val description = if (book.isDownloaded) {
                    "Удалить локальный файл"
                } else {
                    "Загрузить книгу"
                }
                Icon(
                    imageVector = icon,
                    contentDescription = description
                )
            }
        }
    }
}

@PreviewScreenSizes
@Preview(showBackground = true)
@Composable
private fun LibraryScreenPreview() {
    AvitoBooksTheme {
        LibraryScreen()
    }
}