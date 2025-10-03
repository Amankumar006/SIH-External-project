package com.example.transliterationstoolforstreetsigns

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.text.Transliterator
import android.os.Bundle
import android.util.Log
import android.widget.Toast
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (hasCamPermission) {
                        CameraWithTransliteration(
                            modifier = Modifier.padding(innerPadding),
                            cameraExecutor = cameraExecutor,
                            textRecognizer = textRecognizer
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
    return Locale.forLanguageTag(langCode).displayName
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraWithTransliteration(
    modifier: Modifier = Modifier,
    cameraExecutor: ExecutorService,
    textRecognizer: com.google.mlkit.vision.text.TextRecognizer
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var recognizedText by remember { mutableStateOf("No text recognized yet") }
    var translatedText by remember { mutableStateOf("Translation will appear here") }
    var transliteratedText by remember { mutableStateOf("Transliteration will appear here") }

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

    val translatorOptions = remember(sourceLang, targetLang) {
        TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(targetLang)
            .build()
    }
    val translator = remember(translatorOptions) { Translation.getClient(translatorOptions) }

    DisposableEffect(translator) {
        onDispose {
            translator.close()
        }
    }

    LaunchedEffect(translator) {
        val conditions = DownloadConditions.Builder().requireWifi().build()
        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                Log.d("CameraWithTransliteration", "Translation model for $sourceLang -> $targetLang downloaded.")
                translatedText = "Language model ready."
            }
            .addOnFailureListener { e ->
                Log.e("CameraWithTransliteration", "Model download failed for $sourceLang -> $targetLang.", e)
                translatedText = "Failed to download model."
            }
    }

    fun performTranslation(text: String) {
        if (sourceLang == targetLang) {
            translatedText = text
            return
        }
        translator.translate(text)
            .addOnSuccessListener { translated -> translatedText = translated }
            .addOnFailureListener { e ->
                Log.e("CameraWithTransliteration", "Translation failed", e)
                translatedText = "Translation failed."
            }
    }

    fun performTransliteration(text: String) {
        val sourceScript = langToScript(sourceLang)
        val targetScript = langToScript(targetLang)

        if (sourceScript == targetScript) {
            transliteratedText = text
            return
        }

        try {
            val transliteratorId = "$sourceScript-$targetScript"
            val transliterator = Transliterator.getInstance(transliteratorId)
            transliteratedText = transliterator.transliterate(text)
        } catch (e: Exception) {
            Log.e("CameraWithTransliteration", "Transliteration failed", e)
            transliteratedText = "Transliteration not supported."
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Camera Preview in the background
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val rotation = imageProxy.imageInfo.rotationDegrees

                                    // Define the focus area relative to the image's dimensions
                                    val imageWidth = if (rotation % 180 == 0) imageProxy.width else imageProxy.height
                                    val imageHeight = if (rotation % 180 == 0) imageProxy.height else imageProxy.width

                                    val focusBoxTop = imageHeight * 0.35f
                                    val focusBoxBottom = imageHeight * 0.65f
                                    val focusBoxLeft = imageWidth * 0.05f
                                    val focusBoxRight = imageWidth * 0.95f

                                    val image = InputImage.fromMediaImage(mediaImage, rotation)
                                    textRecognizer.process(image)
                                        .addOnSuccessListener { visionText ->
                                            val recognizedTextInFocus = visionText.textBlocks
                                                .filter { block ->
                                                    val box = block.boundingBox ?: return@filter false
                                                    // Check if the center of the text block is within our defined focus area
                                                    box.exactCenterY() > focusBoxTop && box.exactCenterY() < focusBoxBottom &&
                                                            box.exactCenterX() > focusBoxLeft && box.exactCenterX() < focusBoxRight
                                                }
                                                .joinToString(separator = "\n") { it.text }

                                            if (recognizedTextInFocus.isNotBlank() && recognizedTextInFocus != recognizedText) {
                                                recognizedText = recognizedTextInFocus
                                                translatedText = "..."
                                                transliteratedText = "..."
                                                performTranslation(recognizedTextInFocus)
                                                performTransliteration(recognizedTextInFocus)
                                            }
                                            imageProxy.close()
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e("CameraWithTransliteration", "Text recognition failed", e)
                                            imageProxy.close()
                                        }
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

        // Focus area UI overlay
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.3f)
                .align(Alignment.Center)
                .border(2.dp, Color.White.copy(alpha = 0.7f))
        )

        // UI elements overlaid on top
        Column(modifier = Modifier.fillMaxSize()) {
            // Language selectors at the top
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Source Language Dropdown
                ExposedDropdownMenuBox(
                    expanded = sourceDropdownExpanded,
                    onExpandedChange = { sourceDropdownExpanded = !sourceDropdownExpanded },
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                ) {
                    TextField(
                        value = getLanguageDisplayName(sourceLang),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Source") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceDropdownExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = sourceDropdownExpanded, onDismissRequest = { sourceDropdownExpanded = false }) {
                        availableLanguages.forEach { langCode ->
                            DropdownMenuItem(
                                text = { Text(getLanguageDisplayName(langCode)) },
                                onClick = {
                                    sourceLang = langCode
                                    sourceDropdownExpanded = false
                                    translatedText = "..."
                                    transliteratedText = "..."
                                }
                            )
                        }
                    }
                }
                // Target Language Dropdown
                ExposedDropdownMenuBox(
                    expanded = targetDropdownExpanded,
                    onExpandedChange = { targetDropdownExpanded = !targetDropdownExpanded },
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                ) {
                    TextField(
                        value = getLanguageDisplayName(targetLang),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetDropdownExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = targetDropdownExpanded, onDismissRequest = { targetDropdownExpanded = false }) {
                        availableLanguages.forEach { langCode ->
                            DropdownMenuItem(
                                text = { Text(getLanguageDisplayName(langCode)) },
                                onClick = {
                                    targetLang = langCode
                                    targetDropdownExpanded = false
                                    translatedText = "..."
                                    transliteratedText = "..."
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Results card at the bottom
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 1. Original Text
                    Text("Original:", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = recognizedText, modifier = Modifier.weight(1f))
                        Row {
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(recognizedText))
                                Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Original Text")
                            }
                            IconButton(onClick = {
                                val sendIntent: Intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, recognizedText)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, null))
                            }) {
                                Icon(Icons.Filled.Share, contentDescription = "Share Original Text")
                            }
                        }
                    }

                    // 2. Transliterated Text
                    Text("Transliterated:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = transliteratedText, modifier = Modifier.weight(1f))
                        Row {
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(transliteratedText))
                                Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Transliterated Text")
                            }
                            IconButton(onClick = {
                                val sendIntent: Intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, transliteratedText)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, null))
                            }) {
                                Icon(Icons.Filled.Share, contentDescription = "Share Transliterated Text")
                            }
                        }
                    }

                    // 3. Translated Text
                    Text("Translated:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = translatedText, modifier = Modifier.weight(1f))
                        Row {
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(translatedText))
                                Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Translated Text")
                            }
                            IconButton(onClick = {
                                val sendIntent: Intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, translatedText)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, null))
                            }) {
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
