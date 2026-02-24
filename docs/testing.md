# Testing

## Test Categories

### 1. Unit Tests (No Emulator Required)

**Database Tests** - Room DAO operations with in-memory database:
```bash
# All database tests
./gradlew test --tests "org.akvo.afribamodkvalidator.data.*"

# Specific DAO tests
./gradlew test --tests "org.akvo.afribamodkvalidator.data.dao.SubmissionDaoTest"
./gradlew test --tests "org.akvo.afribamodkvalidator.data.dao.FormMetadataDaoTest"

# TypeConverter tests
./gradlew test --tests "org.akvo.afribamodkvalidator.data.database.ConvertersTest"
```

### 2. Instrumented Tests (Requires Device/Emulator)

```bash
# Connect device or start emulator first
adb devices

# Run all instrumented tests
./gradlew connectedAndroidTest

# Run specific instrumented test
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.akvo.afribamodkvalidator.ExampleInstrumentedTest
```

## Test Structure

```
app/src/test/                          # Unit tests (JVM, fast)
├── java/org/akvo/afribamodkvalidator/
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
└── java/org/akvo/afribamodkvalidator/
    └── ExampleInstrumentedTest.kt
```

## Test Technologies

| Tool | Purpose | Version |
|------|---------|--------|
| **JUnit 4** | Test framework | 4.13.2 |
| **Robolectric** | Android unit tests on JVM | 4.10.3 |
| **Room Testing** | In-memory database | 2.6.1 |
| **MockK** | Kotlin mocking | 1.13.12 |
| **Turbine** | Flow testing | 1.1.0 |
| **Coroutines Test** | Async testing | 1.7.3 |

## Lint and Code Quality

```bash
# Run lint checks
./gradlew lint

# View lint report
open app/build/reports/lint-results-debug.html

# Run with strict mode (fail on warnings)
./gradlew lintDebug -Pandroid.lintOptions.abortOnError=true
```

## CI Pipeline Commands

```bash
# Full CI check
./gradlew clean test lint build

# With coverage report
./gradlew clean test jacocoTestReport lint build
```

## Test Reports

After running tests, view reports at:
- **Unit Test Results:** `app/build/reports/tests/testDebugUnitTest/index.html`
- **Instrumented Test Results:** `app/build/reports/androidTests/connected/index.html`
- **Lint Report:** `app/build/reports/lint-results-debug.html`
- **Coverage Report:** `app/build/reports/jacoco/jacocoTestReport/html/index.html`

## Troubleshooting

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

## Writing New Tests

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
