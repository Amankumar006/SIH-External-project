package com.example.transliterationstoolforstreetsigns

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.text.Transliterator
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.transliterationstoolforstreetsigns.ui.theme.TransliterationsToolForStreetSignsTheme
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            TransliterationsToolForStreetSignsTheme {
                var hasCamPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { granted ->
                        hasCamPermission = granted
                    }
                )
                LaunchedEffect(key1 = true) {
                    launcher.launch(Manifest.permission.CAMERA)
                }

                val snackbarHostState = remember { SnackbarHostState() }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                ) { innerPadding ->
                    if (hasCamPermission) {
                        CameraWithTransliteration(
                            modifier = Modifier.padding(innerPadding),
                            cameraExecutor = cameraExecutor,
                            textRecognizer = textRecognizer,
                            snackbarHostState = snackbarHostState
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(innerPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Camera permission is required to use this app.")
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

// Helper to map ML Kit language to ICU script for Transliterator
private fun langToScript(langCode: String): String {
    return when (langCode) {
        TranslateLanguage.HINDI -> "Devanagari"
        TranslateLanguage.KANNADA -> "Kannada"
        TranslateLanguage.ENGLISH -> "Latin"
        else -> "Any"
    }
}

// Helper to get a displayable name for a language code
private fun getLanguageDisplayName(langCode: String): String {
    return Locale.forLanguageTag(langCode).displayLanguage
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraWithTransliteration(
    modifier: Modifier = Modifier,
    cameraExecutor: ExecutorService,
    textRecognizer: com.google.mlkit.vision.text.TextRecognizer,
    snackbarHostState: SnackbarHostState
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var recognizedText by remember { mutableStateOf("") }
    var translatedText by remember { mutableStateOf("") }
    var transliteratedText by remember { mutableStateOf("") }

    val availableLanguages = remember {
        listOf(
            TranslateLanguage.ENGLISH,
            TranslateLanguage.HINDI,
            TranslateLanguage.KANNADA
        )
    }

    var sourceLang by remember { mutableStateOf(TranslateLanguage.ENGLISH) }
    var targetLang by remember { mutableStateOf(TranslateLanguage.HINDI) }

    var sourceDropdownExpanded by remember { mutableStateOf(false) }
    var targetDropdownExpanded by remember { mutableStateOf(false) }

    val translator = remember(sourceLang, targetLang) {
        if (sourceLang == targetLang) return@remember null
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(targetLang)
            .build()
        Translation.getClient(options)
    }

    val transliterator = remember(sourceLang, targetLang) {
        val sourceScript = langToScript(sourceLang)
        val targetScript = langToScript(targetLang)
        if (sourceScript == targetScript) return@remember null
        try {
            Transliterator.getInstance("$sourceScript-$targetScript")
        } catch (e: Exception) {
            null
        }
    }

    DisposableEffect(translator) {
        onDispose { translator?.close() }
    }

    LaunchedEffect(translator) {
        translator?.downloadModelIfNeeded(DownloadConditions.Builder().requireWifi().build())
            ?.addOnSuccessListener {
                Log.d("CameraWithTransliteration", "Translation model downloaded.")
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Language model ready for $sourceLang -> $targetLang")
                }
            }
            ?.addOnFailureListener { e ->
                Log.e("CameraWithTransliteration", "Model download failed.", e)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Failed to download language model.")
                }
            }
    }

    fun processText(text: String) {
        if (translator == null) {
            translatedText = text
        } else {
            translator.translate(text)
                .addOnSuccessListener { translated -> translatedText = translated }
                .addOnFailureListener { translatedText = "Translation failed." }
        }

        if (transliterator == null) {
            transliteratedText = text
        } else {
            transliteratedText = transliterator.transliterate(text)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    this.scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    var lastAnalyzedTimestamp = 0L
                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor) { imageProxy ->
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastAnalyzedTimestamp >= 500) { // Process ~2 frames per second
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null) {
                                        val rotation = imageProxy.imageInfo.rotationDegrees
                                        val image = InputImage.fromMediaImage(mediaImage, rotation)

                                        val imageWidth = if (rotation % 180 == 0) imageProxy.width else imageProxy.height
                                        val imageHeight = if (rotation % 180 == 0) imageProxy.height else imageProxy.width

                                        val focusBoxTop = imageHeight * 0.35f
                                        val focusBoxBottom = imageHeight * 0.65f
                                        val focusBoxLeft = imageWidth * 0.05f
                                        val focusBoxRight = imageWidth * 0.95f

                                        textRecognizer.process(image)
                                            .addOnSuccessListener { visionText ->
                                                val recognizedTextInFocus = visionText.textBlocks
                                                    .filter { block ->
                                                        val box = block.boundingBox ?: return@filter false
                                                        val centerX = box.centerX().toFloat()
                                                        val centerY = box.centerY().toFloat()
                                                        centerY > focusBoxTop && centerY < focusBoxBottom && centerX > focusBoxLeft && centerX < focusBoxRight
                                                    }
                                                    .joinToString(separator = "\n") { it.text }

                                                if (recognizedTextInFocus.isNotBlank() && recognizedTextInFocus != recognizedText) {
                                                    recognizedText = recognizedTextInFocus
                                                    translatedText = "..."
                                                    transliteratedText = "..."
                                                    processText(recognizedTextInFocus)
                                                }
                                            }
                                            .addOnCompleteListener {
                                                imageProxy.close()
                                            }
                                    } else {
                                        imageProxy.close()
                                    }
                                    lastAnalyzedTimestamp = currentTime
                                } else {
                                    imageProxy.close()
                                }
                            }
                        }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalyzer)
                    } catch (exc: Exception) {
                        Log.e("CameraWithTransliteration", "Use case binding failed", exc)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.3f)
                .align(Alignment.Center)
                .border(3.dp, Color.White.copy(alpha = 0.8f))
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Source Language Dropdown
                ExposedDropdownMenuBox(expanded = sourceDropdownExpanded, onExpandedChange = { sourceDropdownExpanded = !it }, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    TextField(value = getLanguageDisplayName(sourceLang), onValueChange = {}, readOnly = true, label = { Text("Source") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceDropdownExpanded) }, modifier = Modifier.menuAnchor())
                    ExposedDropdownMenu(expanded = sourceDropdownExpanded, onDismissRequest = { sourceDropdownExpanded = false }) {
                        availableLanguages.forEach { langCode ->
                            DropdownMenuItem(text = { Text(getLanguageDisplayName(langCode)) }, onClick = { sourceLang = langCode; sourceDropdownExpanded = false })
                        }
                    }
                }
                // Target Language Dropdown
                ExposedDropdownMenuBox(expanded = targetDropdownExpanded, onExpandedChange = { targetDropdownExpanded = !it }, modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    TextField(value = getLanguageDisplayName(targetLang), onValueChange = {}, readOnly = true, label = { Text("Target") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetDropdownExpanded) }, modifier = Modifier.menuAnchor())
                    ExposedDropdownMenu(expanded = targetDropdownExpanded, onDismissRequest = { targetDropdownExpanded = false }) {
                        availableLanguages.forEach { langCode ->
                            DropdownMenuItem(text = { Text(getLanguageDisplayName(langCode)) }, onClick = { targetLang = langCode; targetDropdownExpanded = false })
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            AnimatedVisibility(
                visible = recognizedText.isNotBlank(),
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // 1. Original Text
                        Text("Original:", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = recognizedText, modifier = Modifier.weight(1f).padding(end = 8.dp))
                            IconButton(onClick = { clipboardManager.setText(AnnotatedString(recognizedText)); coroutineScope.launch { snackbarHostState.showSnackbar("Copied Original Text") } }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Original Text")
                            }
                            IconButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, recognizedText) }, "Share Text")) }) {
                                Icon(Icons.Filled.Share, contentDescription = "Share Original Text")
                            }
                        }

                        // 2. Transliterated Text
                        Text("Transliterated:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = transliteratedText, modifier = Modifier.weight(1f).padding(end = 8.dp))
                            IconButton(onClick = { clipboardManager.setText(AnnotatedString(transliteratedText)); coroutineScope.launch { snackbarHostState.showSnackbar("Copied Transliterated Text") } }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Transliterated Text")
                            }
                            IconButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, transliteratedText) }, "Share Text")) }) {
                                Icon(Icons.Filled.Share, contentDescription = "Share Transliterated Text")
                            }
                        }

                        // 3. Translated Text
                        Text("Translated:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = translatedText, modifier = Modifier.weight(1f).padding(end = 8.dp))
                            IconButton(onClick = { clipboardManager.setText(AnnotatedString(translatedText)); coroutineScope.launch { snackbarHostState.showSnackbar("Copied Translated Text") } }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Translated Text")
                            }
                            IconButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, translatedText) }, "Share Text")) }) {
                                Icon(Icons.Filled.Share, contentDescription = "Share Translated Text")
                            }
                        }
                    }
                }
            }
        }
    }
}

@ComposePreview(showBackground = true)
@Composable
fun GreetingPreview() {
    TransliterationsToolForStreetSignsTheme {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("App UI with Camera and Transliteration functionality.")
        }
    }
}
