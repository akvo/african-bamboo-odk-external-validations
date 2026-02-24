# AfriBamODKValidator
> African Bamboo Plot Validator
An Android client application for KoboToolbox API integration. AfriBamODKValidator enables users to download, view, and manage form submissions from KoboToolbox servers with offline validation capabilities.

## Features

- Connect to KoboToolbox servers (EU, Global, or custom instances)
- Download and browse form submissions
- Search submissions by user, UUID, or date
- Lazy loading pagination for large datasets
- Sync status tracking for submissions
- Material 3 design with Jetpack Compose
- **ODK External App**: Polygon validation for geoshape fields
- **Plot Overlap Detection**: Detect and block overlapping plots (>= 5% threshold)
- **Map Visualization**: View overlapping plots on interactive map with offline tile support

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose with Material 3 |
| Architecture | MVVM with Clean Architecture |
| DI | Hilt 2.51.1 |
| Navigation | Navigation Compose 2.8.5 |
| Database | Room 2.6.1 |
| Geometry | JTS Topology Suite 1.19.0 |
| Maps | Mapbox Maps SDK 11.18.1 |
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
   git clone https://github.com/akvo/african-bamboo-odk-external-validations.git AfriBamODKValidator
   cd AfriBamODKValidator
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

# Build release APK (signed)
./gradlew assembleRelease

# Clean and build
./gradlew clean build
```

The generated APK files will be located at:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

### Release Signing Setup

> **Important**: Android requires all APKs to be digitally signed before installation. An unsigned APK (`app-release-unsigned.apk`) **cannot be installed** on any Android device — this applies to all versions from Android 7.0 (minSdk 24) through Android 15+. If users report "app not installed" errors, verify the APK is signed (the filename should be `app-release.apk`, not `app-release-unsigned.apk`).

Release builds require a signing keystore. Create a `keystore.properties` file in the project root (gitignored):

```properties
storeFile=../release-keystore.jks
storePassword=your_store_password
keyAlias=release
keyPassword=your_key_password
```

To generate a new keystore:
```bash
keytool -genkey -v \
  -keystore release-keystore.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias release
```

### Install on Device

```bash
# Install debug APK on connected device/emulator
./gradlew installDebug

# Or use adb directly
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Releasing

The `release.sh` script automates the full release process: version bump, build, sign, tag, push, and publish to GitHub Releases.

### Prerequisites

- [GitHub CLI](https://cli.github.com) (`gh`) installed and authenticated
- `keystore.properties` configured (see [Release Signing Setup](#release-signing-setup))

### Usage

```bash
# Bump minor version (default): 1.1 → 1.2
./release.sh

# Bump minor version (explicit): 1.1 → 1.2
./release.sh minor

# Bump major version: 1.1 → 2.0
./release.sh major

# Bump patch version: 1.1 → 1.1.1
./release.sh patch

# Set exact version
./release.sh 2.5

# Replace APK on existing release (no version bump)
./release.sh --update
```

### What It Does

1. Reads current version from `app/build.gradle.kts`
2. Bumps `versionCode` and `versionName`
3. Builds a signed release APK (`./gradlew clean assembleRelease`)
4. Verifies the APK signature
5. Generates release notes from git commits since the last tag
6. Shows a preview and asks for confirmation
7. Commits the version bump, creates a git tag, and pushes
8. Creates a GitHub Release with the APK attached

### Update Mode

Use `--update` to rebuild and replace the APK on an existing release without changing the version. This is useful for hotfixes:

```bash
# Fix code, commit, then:
./release.sh --update
```

## ODK External App: Polygon Validation

ExternalODK functions as an ODK external app that validates polygon/geoshape data before submission. It checks geometry validity (vertex count, area, self-intersection) and optionally detects plot overlaps.

### XLSForm Configuration

All validation uses the same external app intent (`VALIDATE_POLYGON`). Pass only `shape` for geometry-only validation, or include additional extras to enable overlap detection.

> **Important**: The validation app will **not** be triggered unless the XLSForm `appearance` column contains the correct package name: `ex:org.akvo.afribamodkvalidator.VALIDATE_POLYGON(...)`. A typo or missing package name means ODK Collect will not launch the validation app, and polygon data will be accepted without any checks.

**Geometry-only validation (survey sheet):**

| type | name | label | appearance | required |
|------|------|-------|------------|----------|
| geoshape | manual_boundary | Draw boundary | | yes |
| text | validate_trigger | Tap to validate | ex:org.akvo.afribamodkvalidator.VALIDATE_POLYGON(shape=${manual_boundary}) | yes |

**With overlap detection (survey sheet):**

| type | name | label | appearance | required |
|------|------|-------|------------|----------|
| geoshape | plot_boundary | Draw plot boundary | | yes |
| text | first_name | First Name | | yes |
| text | father_name | Father's Name | | yes |
| text | grandfather_name | Grandfather's Name | | yes |
| select_one regions | region | Select Region | | yes |
| select_one sub_regions | sub_region | Select Sub-Region | | |
| calculate | full_name | | | |
| calculate | instance_id | | | |
| text | validate_plot | Validate Plot | ex:org.akvo.afribamodkvalidator.VALIDATE_POLYGON(shape=${plot_boundary},plot_name=${full_name},region=${region},sub_region=${sub_region},instance_name=${instance_id}) | yes |

**calculations sheet (for overlap detection):**

| name | calculation |
|------|-------------|
| full_name | concat(${first_name}, ' ', ${father_name}, ' ', ${grandfather_name}) |
| instance_id | concat(${enumerator_id}, '-', ${region}, '-', today()) |

> **Note**: `instance_id` duplicates the logic from `instance_name` in the settings sheet because settings fields cannot be referenced directly in survey expressions.

### Installation

1. Build the APK: `./gradlew assembleDebug`
2. Install on the same device as ODK Collect: `./gradlew installDebug`
3. Configure your XLSForm with the external app appearance
4. Deploy the form to your device

For validation checks, blocking mechanics, supported formats, and intent extras, see [docs/polygon-validation.md](docs/polygon-validation.md).

## Plot Overlap Detection

The app detects overlapping plots to prevent duplicate land registrations. When a new plot overlaps with an existing plot by 5% or more of the smaller polygon's area, validation fails. Overlap detection works fully offline — draft plots are stored locally and checked immediately without server sync.

For the full detection pipeline, threshold details, error messages, and draft storage, see [docs/plot-overlap-detection.md](docs/plot-overlap-detection.md).

## Sync Integration

When submissions are synced from KoboToolbox, the app matches local draft plots to synced submissions and extracts plot data from synced records. This ensures overlap detection works against all plots across devices.

For field mapping configuration, draft lifecycle, conflict resolution, and troubleshooting, see [docs/sync-integration.md](docs/sync-integration.md).

## Map Visualization

Interactive satellite map powered by Mapbox for viewing plot overlaps with color-coded polygons, offline tile downloads, and Google Maps fallback for fresher imagery.

For Mapbox setup, offline downloads, and custom regions, see [docs/map-visualization.md](docs/map-visualization.md).

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

For test categories, structure, CI commands, troubleshooting, and test templates, see [docs/testing.md](docs/testing.md).

## Project Structure

```
app/src/main/java/org/akvo/afribamodkvalidator/
├── AfriBamODKValidatorApplication.kt    # Hilt Application class
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

This project is licensed under the [GNU Affero General Public License v3.0](LICENSE).
