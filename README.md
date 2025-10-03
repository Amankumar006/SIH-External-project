# Transliterations tool for street signs

A lightweight Android app that lets users point the camera at printed/handwritten text, extract it instantly, and transliterate/convert it into another script (e.g., English → Hindi, Kannada → English) without requiring internet.

## Features

*   **Real-time Text Recognition**: Uses the device's camera to recognize text in real-time.
*   **Focus Area**: A dedicated focus area on the screen allows for selective text capture, improving accuracy.
*   **Offline First**: All core features work without an internet connection.
*   **Multi-language Support**: Supports multiple languages for translation and transliteration.
*   **Translation and Transliteration**: Provides both meaning-for-meaning translation and script-to-script transliteration.
*   **Copy and Share**: Easily copy or share the recognized, translated, or transliterated text.
*   **Clean, Modern UI**: A simple and intuitive user interface built with Jetpack Compose.

## Technology Used

*   **UI**: [Jetpack Compose](https://developer.android.com/jetpack/compose) (with Material 3) for building the user interface.
*   **Camera**: [CameraX](https://developer.android.com/training/camerax) for accessing the camera and displaying a live preview.
*   **Text Recognition**: [ML Kit Text Recognition](https://developers.google.com/ml-kit/vision/text-recognition/android) for recognizing text from the camera feed.
*   **Translation**: [ML Kit Translation](https://developers.google.com/ml-kit/language/translation/android) for providing meaning-for-meaning translations between languages.
*   **Transliteration**: Android's built-in [ICU Transliterator](https://developer.android.com/reference/android/icu/text/Transliterator) for converting text from one script to another.
*   **Language**: [Kotlin](https://kotlinlang.org/) as the primary programming language.
*   **Asynchronous Operations**: Kotlin Coroutines and `LaunchedEffect` for managing background tasks and asynchronous operations.

## How to Build and Run

1.  Clone the repository.
2.  Open the project in Android Studio.
3.  Connect a device or start an emulator.
4.  Click the **Run 'app'** button.
