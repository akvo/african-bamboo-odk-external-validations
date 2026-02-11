# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build the project
./gradlew build

# Clean build
./gradlew clean build

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run lint checks
./gradlew lint

# Run all unit tests
./gradlew test

# Run a specific unit test class
./gradlew test --tests "org.akvo.afribamodkvalidator.ExampleUnitTest"

# Run instrumented tests (requires emulator/device)
./gradlew connectedAndroidTest

# Install debug APK on connected device
./gradlew installDebug
```

## Architecture

### Tech Stack
- **Language**: Kotlin 2.0.21
- **UI**: Jetpack Compose with Material 3
- **Min SDK**: 24 (Android 7.0) / **Target SDK**: 36
- **Architecture Pattern**: MVVM with Clean Architecture
- **Planned Dependencies**: Room (database), Retrofit (API), Hilt (DI), Kotlinx Serialization

### Project Purpose
AfriBamODKValidator is a client application for KoboToolbox API integration. It handles multiple forms with varying schemas using a hybrid storage approach.

### Core Design Patterns

**Generic Form Storage**: Instead of creating specific database columns for each form type, the app uses:
- Standard Kobo fields in typed columns: `_uuid`, `submissionTime`, `submittedBy`
- Dynamic form content stored as JSON string in `rawData` column
- Detail screens parse `rawData` as key-value pairs for display

**UI First Strategy**: Build screens with mock data before database implementation is ready. This allows parallel development of UI and data layers.

### Screen Flow (6 screens)
1. **Login Screen** → 2. **Download Loading** → 3. **Download Complete** → 4. **Home/Dashboard** (main list)
5. **Resync Loading** and 6. **Sync Complete** (for data refresh flows)

### API Integration Pattern
- KoboToolbox API with Basic Auth via OkHttp Interceptor
- Pagination with `limit=300`
- Dynamic JSON parsing with `ignoreUnknownKeys = true`
- Retrofit service/repository pattern

## Issue Tracking

This project uses **Beads** for git-backed issue tracking:

```bash
bd ready              # Find tasks ready to work on
bd list               # View all issues
bd show <id>          # View issue details
bd create --title="..." --type=task --priority=2  # Create issue
bd update <id> --status=in_progress              # Claim work
bd close <id>         # Complete issue
bd sync               # Sync with git remote
```

## Code Review Requirement

**After completing any beads task**, always run a code review before committing:

1. Use `/code-reviewer` skill to review local changes
2. Use `/reviewing-changes` skill for Android-specific pattern validation (MVVM, Compose, etc.)

This ensures code quality and catches issues before they're committed.

## Session Completion Protocol

Before completing any session, run:
```bash
# 1. Review your changes first
/code-reviewer        # Review local changes for correctness
/reviewing-changes    # Android-specific pattern validation

# 2. Then commit and sync
git status            # Check changes
git add <files>       # Stage code
bd sync               # Sync beads changes
git commit -m "..."   # Commit code
git push              # Push to remote
```

## Key Directories

- `app/src/main/java/com/akvo/externalodk/` - Main source code
- `app/src/main/java/com/akvo/externalodk/ui/theme/` - Material 3 theming
- `docs/` - Feature specifications and UI flow documentation
- `.claude/skills/` - Claude AI skill templates for UI, Room DB, and API sync patterns
