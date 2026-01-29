# ExternalODK

An Android client application for KoboToolbox API integration. ExternalODK enables users to download, view, and manage form submissions from KoboToolbox servers with offline validation capabilities.

## Features

- Connect to KoboToolbox servers (EU, Global, or custom instances)
- Download and browse form submissions
- Search submissions by user, UUID, or date
- Lazy loading pagination for large datasets
- Sync status tracking for submissions
- Material 3 design with Jetpack Compose
- **ODK External App**: Polygon validation for geoshape fields

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

### Quick Start

```bash
# Run all tests (unit + instrumented)
./gradlew test

# Run only unit tests (fastest, uses Robolectric)
./gradlew testDebugUnitTest

# Run with detailed output
./gradlew test --info

# Run tests and generate coverage report
./gradlew test jacocoTestReport
```

### Test Categories

#### 1. Unit Tests (No Emulator Required)

**Database Tests** - Room DAO operations with in-memory database:
```bash
# All database tests
./gradlew test --tests "com.akvo.externalodk.data.*"

# Specific DAO tests
./gradlew test --tests "com.akvo.externalodk.data.dao.SubmissionDaoTest"
./gradlew test --tests "com.akvo.externalodk.data.dao.FormMetadataDaoTest"

# TypeConverter tests
./gradlew test --tests "com.akvo.externalodk.data.database.ConvertersTest"
```

#### 2. Instrumented Tests (Requires Device/Emulator)

```bash
# Connect device or start emulator first
adb devices

# Run all instrumented tests
./gradlew connectedAndroidTest

# Run specific instrumented test
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.akvo.externalodk.ExampleInstrumentedTest
```

### Test Structure

```
app/src/test/                          # Unit tests (JVM, fast)
├── java/com/akvo/externalodk/
│   ├── data/
│   │   ├── dao/
│   │   │   ├── SubmissionDaoTest.kt   # 11 tests: CRUD, Flow, pagination
│   │   │   └── FormMetadataDaoTest.kt # 7 tests: metadata operations
│   │   └── database/
│   │       ├── DatabaseTest.kt        # Base test class
│   │       └── ConvertersTest.kt      # 11 tests: JSON serialization
│   └── ExampleUnitTest.kt
└── resources/
    └── fixtures/                       # Test data fixtures
        ├── assets-123-data-list.json  # Form 123 sample data
        └── assets-456-data-list.json  # Form 456 sample data

app/src/androidTest/                   # Instrumented tests (device)
└── java/com/akvo/externalodk/
    └── ExampleInstrumentedTest.kt
```

### Test Technologies

| Tool | Purpose | Version |
|------|---------|--------|
| **JUnit 4** | Test framework | 4.13.2 |
| **Robolectric** | Android unit tests on JVM | 4.10.3 |
| **Room Testing** | In-memory database | 2.6.1 |
| **MockK** | Kotlin mocking | 1.13.12 |
| **Turbine** | Flow testing | 1.1.0 |
| **Coroutines Test** | Async testing | 1.7.3 |

### Lint and Code Quality

```bash
# Run lint checks
./gradlew lint

# View lint report
open app/build/reports/lint-results-debug.html

# Run with strict mode (fail on warnings)
./gradlew lintDebug -Pandroid.lintOptions.abortOnError=true
```

### Continuous Integration

**CI Pipeline Commands:**
```bash
# Full CI check
./gradlew clean test lint build

# With coverage report
./gradlew clean test jacocoTestReport lint build
```

### Test Reports

After running tests, view reports at:
- **Unit Test Results:** `app/build/reports/tests/testDebugUnitTest/index.html`
- **Instrumented Test Results:** `app/build/reports/androidTests/connected/index.html`
- **Lint Report:** `app/build/reports/lint-results-debug.html`
- **Coverage Report:** `app/build/reports/jacoco/jacocoTestReport/html/index.html`

### Troubleshooting

**Issue: Tests fail with "No such file or directory"**
```bash
# Clean build directory
./gradlew clean

# Sync Gradle
./gradlew --refresh-dependencies
```

**Issue: Robolectric tests slow**
```bash
# Run with parallel execution
./gradlew test --parallel --max-workers=4
```

**Issue: Database tests fail**
```bash
# Ensure fixtures are in correct location
ls -la app/src/test/resources/fixtures/

# Should see:
# assets-123-data-list.json
# assets-456-data-list.json
```

### Writing New Tests

**Database Test Template:**
```kotlin
class MyDaoTest : DatabaseTest() {
    private val dao: MyDao by lazy { database.myDao() }
    
    @Test
    fun `test description`() = runTest {
        // Given
        val entity = MyEntity(id = 1, name = "test")
        
        // When
        dao.insert(entity)
        
        // Then
        val result = dao.getById(1)
        assertEquals(entity, result)
    }
}
```

**Flow Test Template:**
```kotlin
@Test
fun `test Flow emissions`() = runTest {
    dao.getData().test {
        // Initial emission
        val first = awaitItem()
        assertTrue(first.isEmpty())
        
        // Trigger change
        dao.insert(testData)
        
        // New emission
        val second = awaitItem()
        assertEquals(1, second.size)
        
        ensureAllEventsConsumed()
    }
}
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

## ODK External App: Polygon Validation

ExternalODK functions as an ODK external app that validates polygon/geoshape data before submission.

### Validation Checks

| Check | Description | Error Message |
|-------|-------------|---------------|
| Vertex Count | Polygon must have at least 3 distinct points | "Polygon has too few vertices" |
| Minimum Area | Polygon must be larger than 10 sq meters | "Polygon area is too small" |
| Self-Intersection | Polygon edges cannot cross each other | "Polygon lines intersect or cross each other" |

### XLSForm Configuration

Use explicit value passing when you want to validate a field from a separate trigger (e.g., a button or another field):

**survey sheet:**

| type | name | label | appearance | required |
|------|------|-------|------------|----------|
| geoshape | manual_boundary | Draw boundary | | yes |
| text | validate_trigger | Tap to validate | ex:com.akvo.externalodk.VALIDATE_POLYGON(shape=${manual_boundary}) | |

### How Blocking Works

The blocking is handled by the **return code** from the app, not by an XLSForm constraint:

1. **Validation fails**: App returns `RESULT_CANCELED` and shows an error AlertDialog. ODK Collect does NOT update the field value - the user stays on the question and must fix the polygon.

2. **Validation passes**: App returns `RESULT_OK` with the data. ODK Collect accepts the value and allows the user to proceed.

The `required=yes` column ensures the user can't skip the field entirely. The external app's `RESULT_CANCELED` ensures invalid polygons are rejected.

### Supported Input Formats

- **ODK Geoshape**: `lat lng alt acc; lat lng alt acc; ...` (semicolon-separated points)
- **WKT**: `POLYGON ((x1 y1, x2 y2, x3 y3, x1 y1))`

### Installation

1. Build the APK: `./gradlew assembleDebug`
2. Install on the same device as ODK Collect: `./gradlew installDebug`
3. Configure your XLSForm with the external app appearance
4. Deploy the form to your device

## Contributing

This project uses **Beads** for git-backed issue tracking. See available tasks:

```bash
bd ready    # Show tasks ready to work on
bd list     # View all issues
```

## License

[Add license information here]
