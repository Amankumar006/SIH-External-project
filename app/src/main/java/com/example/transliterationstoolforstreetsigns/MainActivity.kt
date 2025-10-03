package com.example.transliterationstoolforstreetsigns

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
                Log.d("CameraWithTransliteration", "Model for $sourceLang -> $targetLang downloaded.")
                if (recognizedText != "No text recognized yet" && recognizedText.isNotBlank()) {
                    translator.translate(recognizedText)
                        .addOnSuccessListener { transliteratedText = it }
                        .addOnFailureListener { transliteratedText = "Ready to translate." }
                } else {
                    transliteratedText = "Language model ready."
                }
            }
            .addOnFailureListener { e ->
                Log.e("CameraWithTransliteration", "Model download failed for $sourceLang -> $targetLang.", e)
                transliteratedText = "Failed to download model for $sourceLang -> $targetLang."
            }
    }

    fun performTransliteration(text: String) {
        if (sourceLang == targetLang) {
            transliteratedText = text
            return
        }
        translator.translate(text)
            .addOnSuccessListener { translated -> transliteratedText = translated }
            .addOnFailureListener { e ->
                Log.e("CameraWithTransliteration", "Translation failed", e)
                transliteratedText = "Translation failed."
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
                                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    textRecognizer.process(image)
                                        .addOnSuccessListener { visionText ->
                                            val fullText = visionText.text
                                            if (fullText.isNotBlank()) {
                                                recognizedText = fullText
                                                performTransliteration(fullText)
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

        // UI elements overlaid on top
        Column(modifier = Modifier.fillMaxSize()) {
            // Language selectors at the top
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)) // Semi-transparent background
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExposedDropdownMenuBox(
                    expanded = sourceDropdownExpanded,
                    onExpandedChange = { sourceDropdownExpanded = !sourceDropdownExpanded },
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                ) {
                    TextField(
                        value = sourceLang,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Source") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceDropdownExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = sourceDropdownExpanded, onDismissRequest = { sourceDropdownExpanded = false }) {
                        availableLanguages.forEach { langCode ->
                            DropdownMenuItem(
                                text = { Text(TranslateLanguage.zza(langCode) ?: langCode) },
                                onClick = {
                                    sourceLang = langCode
                                    sourceDropdownExpanded = false
                                    transliteratedText = "..."
                                }
                            )
                        }
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = targetDropdownExpanded,
                    onExpandedChange = { targetDropdownExpanded = !targetDropdownExpanded },
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                ) {
                    TextField(
                        value = targetLang,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetDropdownExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = targetDropdownExpanded, onDismissRequest = { targetDropdownExpanded = false }) {
                        availableLanguages.forEach { langCode ->
                            DropdownMenuItem(
                                text = { Text(TranslateLanguage.zza(langCode) ?: langCode) },
                                onClick = {
                                    targetLang = langCode
                                    targetDropdownExpanded = false
                                    transliteratedText = "..."
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f)) // Pushes the results card to the bottom

            // Results card at the bottom
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Original:", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
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
                                val shareIntent = Intent.createChooser(sendIntent, null)
                                context.startActivity(shareIntent)
                            }) {
                                Icon(Icons.Filled.Share, contentDescription = "Share Original Text")
                            }
                        }
                    }

                    Text("Transliterated:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
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
                                val shareIntent = Intent.createChooser(sendIntent, null)
                                context.startActivity(shareIntent)
                            }) {
                                Icon(Icons.Filled.Share, contentDescription = "Share Transliterated Text")
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
