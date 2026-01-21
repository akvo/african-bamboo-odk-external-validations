# ExternalODK

An Android client application for KoboToolbox API integration. ExternalODK enables users to download, view, and manage form submissions from KoboToolbox servers with offline validation capabilities.

## Features

- Connect to KoboToolbox servers (EU, Global, or custom instances)
- Download and browse form submissions
- Search submissions by user, UUID, or date
- Lazy loading pagination for large datasets
- Sync status tracking for submissions
- Material 3 design with Jetpack Compose

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose with Material 3 |
| Architecture | MVVM with Clean Architecture |
| DI | Hilt 2.51.1 |
| Navigation | Navigation Compose 2.8.5 |
| Database | Room 2.6.1 (planned) |
| Serialization | Kotlinx Serialization 1.6.3 |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 36 |

## Prerequisites

Before you begin, ensure you have the following installed:

1. **Java Development Kit (JDK) 17 or higher**
   ```bash
   java -version
   ```

2. **Android Studio** (Ladybug or newer recommended)
   - Download from: https://developer.android.com/studio

3. **Android SDK**
   - API Level 36 (installed via Android Studio SDK Manager)
   - Build Tools 34.0.0 or higher

4. **Git**
   ```bash
   git --version
   ```

## Local Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/akvo/ExternalODK.git
   cd ExternalODK
   ```

2. **Open in Android Studio**
   - Launch Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned directory and select it
   - Wait for Gradle sync to complete

3. **Sync Gradle** (if not automatic)
   - File → Sync Project with Gradle Files

## Building the APK

### Using Command Line

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Clean and build
./gradlew clean build
```

The generated APK files will be located at:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release-unsigned.apk`

### Using Android Studio

1. **Debug APK**
   - Build → Build Bundle(s) / APK(s) → Build APK(s)

2. **Release APK**
   - Build → Generate Signed Bundle / APK
   - Follow the signing wizard

### Install on Device

```bash
# Install debug APK on connected device/emulator
./gradlew installDebug

# Or use adb directly
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Running Tests

```bash
# Run all unit tests
./gradlew test

# Run a specific test class
./gradlew test --tests "com.akvo.externalodk.ExampleUnitTest"

# Run instrumented tests (requires emulator/device)
./gradlew connectedAndroidTest

# Run lint checks
./gradlew lint
```

## Project Structure

```
app/src/main/java/com/akvo/externalodk/
├── ExternalODKApplication.kt    # Hilt Application class
├── MainActivity.kt              # Main entry point
├── navigation/
│   ├── Routes.kt                # Type-safe navigation routes
│   └── AppNavHost.kt            # Navigation host setup
├── ui/
│   ├── component/               # Reusable UI components
│   ├── model/                   # UI data models
│   ├── screen/                  # Composable screens
│   ├── theme/                   # Material 3 theming
│   └── viewmodel/               # ViewModels with StateFlow
└── docs/                        # Feature specifications
```

## Screen Flow

```
Login → Download Loading → Download Complete → Home/Dashboard
                                                    ↓
                              Sync Complete ← Resync Loading
```

## Contributing

This project uses **Beads** for git-backed issue tracking. See available tasks:

```bash
bd ready    # Show tasks ready to work on
bd list     # View all issues
```

## License

[Add license information here]
