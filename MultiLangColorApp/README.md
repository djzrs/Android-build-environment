# MultiLang Color App

A simple Android application built with Kotlin that demonstrates:
- Multi-language support (Chinese, English, Japanese, Korean)
- Dynamic background color switching with random RGB generation
- Touch interaction (tap to change color, long press for details)

## Project Structure

```
MultiLangColorApp/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/example/multilangcolorapp/
│   │   │   └── MainActivity.kt
│   │   └── res/
│   │       ├── layout/activity_main.xml
│   │       ├── values/strings.xml (default English)
│   │       ├── values-zh/strings.xml (中文)
│   │       ├── values-en/strings.xml (English)
│   │       ├── values-ja/strings.xml (日本語)
│   │       ├── values-ko/strings.xml (한국어)
│   │       ├── drawable/info_background.xml
│   │       └── xml/data_extraction_rules.xml, backup_rules.xml
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

## Features

- **Multi-language Welcome Text**: Displays welcome message in Chinese, English, Japanese, Korean based on device locale
- **Random Color Generation**: Uses Random.nextInt() to generate RGB values, no predefined color list
- **Touch Interaction**: Tap anywhere to change background color, long press to see RGB details
- **Color Details**: Shows hex code and RGB values on long press

## Requirements

- Android SDK 24+ (minSdk 24, targetSdk 34)
- Android Studio Hedgehog or later
- Kotlin 1.9.24
- Gradle 8.5+

## Building

### Local Build
```bash
./gradlew assembleDebug
```

### GitHub Actions CI/CD
The repository includes automated build and release pipeline:
1. Push code to main branch
2. GitHub Actions builds the APK
3. Release is automatically created on GitHub Releases

## Author
- Created by 小O (AI Assistant)
- Repository: https://github.com/djzrs/Android-build-environment
# Build trigger Tue Oct  6 03:44:59 UTC 2026
# Build trigger 2 Tue Oct  6 03:50:45 UTC 2026
# Build check Tue Oct  6 04:18:05 UTC 2026
