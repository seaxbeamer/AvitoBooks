package com.example.avitobooks.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

private enum class ReaderThemeMode {
    LIGHT, DARK, SEPIA
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookReaderScreen(
    title: String,
    localFilePath: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var textContent by remember { mutableStateOf<String?>(null) }

    var pdfPages by remember { mutableStateOf<List<ImageBitmap>?>(null) }

    val extension = remember(localFilePath) {
        localFilePath.substringAfterLast('.', "").lowercase()
    }

    var showSettingsSheet by remember { mutableStateOf(false) }
    var fontSizeSp by rememberSaveable { mutableStateOf(18f) }
    var lineSpacingMultiplier by rememberSaveable { mutableStateOf(1.4f) }
    var readerThemeMode by rememberSaveable { mutableStateOf(ReaderThemeMode.LIGHT) }

    val backgroundColor: Color
    val textColor: Color
    when (readerThemeMode) {
        ReaderThemeMode.LIGHT -> {
            backgroundColor = Color(0xFFFFFBFE)
            textColor = Color(0xFF111111)
        }
        ReaderThemeMode.DARK -> {
            backgroundColor = Color(0xFF121212)
            textColor = Color(0xFFFFFFFF)
        }
        ReaderThemeMode.SEPIA -> {
            backgroundColor = Color(0xFFFBF0D9)
            textColor = Color(0xFF4A3A2A)
        }
    }

    val pdfOverlayColor: Color = when (readerThemeMode) {
        ReaderThemeMode.LIGHT -> Color.Transparent
        ReaderThemeMode.DARK -> Color.Black.copy(alpha = 0.25f)
        ReaderThemeMode.SEPIA -> Color(0xFF8B6B45).copy(alpha = 0.20f)
    }

    val scrollState = rememberScrollState()

    val prefs = remember {
        context.getSharedPreferences("book_reader_prefs", Context.MODE_PRIVATE)
    }
    val progressKey = remember(localFilePath) {
        "book_progress_${localFilePath.hashCode()}"
    }
    val initialProgressFraction = remember {
        prefs.getFloat(progressKey, 0f).coerceIn(0f, 0.99f)
    }

    var initialScrollApplied by remember { mutableStateOf(false) }

    var reloadToken by remember { mutableStateOf(0) }

    LaunchedEffect(localFilePath, reloadToken) {
        isLoading = true
        errorMessage = null
        textContent = null
        pdfPages = null
        initialScrollApplied = false

        try {
            val file = File(localFilePath)
            if (!file.exists()) {
                errorMessage = "Локальный файл не найден. Возможно, он был удалён."
            } else {
                when (extension) {
                    "txt" -> {
                        val content = withContext(Dispatchers.IO) {
                            file.readText()
                        }
                        textContent = content
                    }

                    "pdf" -> {
                        pdfPages = loadPdfPages(localFilePath)
                    }

                    "epub" -> {
                        val content = extractEpubText(localFilePath)
                        textContent = content
                    }

                    else -> {
                        errorMessage =
                            "Формат .$extension пока не поддерживается. " +
                                    "Доступны форматы: .txt, .pdf, .epub."
                    }
                }
            }
        } catch (e: Exception) {
            errorMessage = "Ошибка при открытии файла. Попробуйте ещё раз."
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(textContent, pdfPages, scrollState.maxValue) {
        if (!initialScrollApplied &&
            (textContent != null || pdfPages != null) &&
            scrollState.maxValue > 0
        ) {
            val target = (scrollState.maxValue * initialProgressFraction).toInt()
            scrollState.scrollTo(target)
            initialScrollApplied = true
        }
    }

    LaunchedEffect(
        scrollState.value,
        scrollState.maxValue,
        textContent,
        pdfPages,
        isLoading,
        errorMessage
    ) {
        if (scrollState.maxValue > 0) {
            val fraction = scrollState.value.toFloat() / scrollState.maxValue.toFloat()
            prefs.edit()
                .putFloat(progressKey, fraction.coerceIn(0f, 1f))
                .apply()
        } else {
            if (!isLoading && errorMessage == null &&
                (textContent != null || pdfPages != null)
            ) {
                prefs.edit()
                    .putFloat(progressKey, 1f)
                    .apply()
            }
        }
    }

    val progressFraction by remember {
        derivedStateOf {
            if (scrollState.maxValue > 0) {
                scrollState.value.toFloat() / scrollState.maxValue.toFloat()
            } else {
                if (!isLoading && errorMessage == null &&
                    (textContent != null || pdfPages != null)
                ) {
                    1f
                } else {
                    0f
                }
            }
        }
    }
    val progressPercent = (progressFraction * 100).roundToInt()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showSettingsSheet) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { showSettingsSheet = false }
        ) {
            ReaderSettingsSheetContent(
                fontSizeSp = fontSizeSp,
                onFontSizeChange = { fontSizeSp = it },
                lineSpacingMultiplier = lineSpacingMultiplier,
                onLineSpacingChange = { lineSpacingMultiplier = it },
                themeMode = readerThemeMode,
                onThemeModeChange = { readerThemeMode = it }
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = backgroundColor,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { showSettingsSheet = true }) {
                        Text("Aa")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = backgroundColor,
                    titleContentColor = textColor,
                    navigationIconContentColor = textColor,
                    actionIconContentColor = textColor
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .background(backgroundColor)
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
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
                        ErrorContent(
                            message = errorMessage!!,
                            localFilePath = localFilePath,
                            onBack = onBack,
                            onRetry = {
                                isLoading = true
                                errorMessage = null
                                textContent = null
                                pdfPages = null
                                initialScrollApplied = false
                                reloadToken++
                            },
                            context = context
                        )
                    }

                    textContent != null -> {
                        Text(
                            text = textContent!!,
                            color = textColor,
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * lineSpacingMultiplier).sp,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .verticalScroll(scrollState)
                                .fillMaxSize()
                        )
                    }

                    pdfPages != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            Column(
                                modifier = Modifier
                                    .verticalScroll(scrollState)
                                    .fillMaxSize()
                                    .background(Color.White)
                            ) {
                                pdfPages!!.forEachIndexed { index, pageBitmap ->
                                    val aspect = pageBitmap.width.toFloat() / pageBitmap.height.toFloat()

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Image(
                                            bitmap = pageBitmap,
                                            contentDescription = "Страница ${index + 1}",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(aspect)
                                        )
                                    }
                                }
                            }

                            if (pdfOverlayColor.alpha > 0f) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(pdfOverlayColor)
                                )
                            }
                        }
                    }

                    else -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Нет данных для отображения",
                                color = textColor
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$progressPercent% прочитано",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End,
                    color = textColor,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ReaderSettingsSheetContent(
    fontSizeSp: Float,
    onFontSizeChange: (Float) -> Unit,
    lineSpacingMultiplier: Float,
    onLineSpacingChange: (Float) -> Unit,
    themeMode: ReaderThemeMode,
    onThemeModeChange: (ReaderThemeMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Настройки отображения",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Размер шрифта")
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReaderChip(
                label = "Маленький",
                selected = fontSizeSp == 16f,
                onClick = { onFontSizeChange(16f) }
            )
            ReaderChip(
                label = "Средний",
                selected = fontSizeSp == 18f,
                onClick = { onFontSizeChange(18f) }
            )
            ReaderChip(
                label = "Крупный",
                selected = fontSizeSp == 22f,
                onClick = { onFontSizeChange(22f) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Межстрочный интервал")
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReaderChip(
                label = "Компактный",
                selected = lineSpacingMultiplier == 1.2f,
                onClick = { onLineSpacingChange(1.2f) }
            )
            ReaderChip(
                label = "Обычный",
                selected = lineSpacingMultiplier == 1.4f,
                onClick = { onLineSpacingChange(1.4f) }
            )
            ReaderChip(
                label = "Свободный",
                selected = lineSpacingMultiplier == 1.6f,
                onClick = { onLineSpacingChange(1.6f) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Тема чтения")
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReaderChip(
                label = "Светлая",
                selected = themeMode == ReaderThemeMode.LIGHT,
                onClick = { onThemeModeChange(ReaderThemeMode.LIGHT) }
            )
            ReaderChip(
                label = "Тёмная",
                selected = themeMode == ReaderThemeMode.DARK,
                onClick = { onThemeModeChange(ReaderThemeMode.DARK) }
            )
            ReaderChip(
                label = "Сепия",
                selected = themeMode == ReaderThemeMode.SEPIA,
                onClick = { onThemeModeChange(ReaderThemeMode.SEPIA) }
            )
        }
    }
}

@Composable
private fun ReaderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}

@Composable
private fun ErrorContent(
    message: String,
    localFilePath: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    context: Context
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onRetry) {
                Text("Попробовать снова")
            }
            OutlinedButton(onClick = {
                val file = File(localFilePath)
                if (file.exists()) {
                    file.delete()
                }
                onBack()
            }) {
                Text("Удалить файл")
            }
        }
    }
}

private suspend fun loadPdfPages(localFilePath: String): List<ImageBitmap> =
    withContext(Dispatchers.IO) {
        val file = File(localFilePath)
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)
        val pageCount = renderer.pageCount
        val pages = mutableListOf<ImageBitmap>()

        try {
            for (index in 0 until pageCount) {
                val page = renderer.openPage(index)
                val width = page.width
                val height = page.height
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                pages.add(bitmap.asImageBitmap())
            }
        } finally {
            renderer.close()
            fd.close()
        }
        pages
    }

private suspend fun extractEpubText(localFilePath: String): String =
    withContext(Dispatchers.IO) {
        val file = File(localFilePath)
        val zip = ZipFile(file)
        try {
            val entriesList = mutableListOf<ZipEntry>()
            val entriesEnum = zip.entries()
            while (entriesEnum.hasMoreElements()) {
                entriesList.add(entriesEnum.nextElement())
            }

            val htmlEntries = entriesList
                .filter { entry ->
                    val name = entry.name.lowercase()
                    !entry.isDirectory && (
                            name.endsWith(".xhtml") ||
                                    name.endsWith(".html") ||
                                    name.endsWith(".htm")
                            )
                }
                .sortedBy { it.name }

            if (htmlEntries.isEmpty()) {
                return@withContext "EPUB-файл не содержит читаемых HTML-страниц."
            }

            val builder = StringBuilder()
            for (entry in htmlEntries) {
                val raw = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                val withoutScripts = raw
                    .replace("(?s)<script.*?</script>".toRegex(), " ")
                    .replace("(?s)<style.*?</style>".toRegex(), " ")
                val plain = withoutScripts
                    .replace("<[^>]+>".toRegex(), " ")
                    .replace("\\s+".toRegex(), " ")
                    .trim()

                if (plain.isNotBlank()) {
                    builder.append(plain).append("\n\n")
                }
            }
            builder.toString().ifBlank { "Не удалось извлечь текст из EPUB." }
        } finally {
            zip.close()
        }
    }