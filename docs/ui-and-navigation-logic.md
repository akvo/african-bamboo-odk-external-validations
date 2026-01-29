### Task 1: UI & Navigation Logic
**Focus:** Jetpack Compose UI Components, State Management, and User Flows.

#### Screen 1: Login
*   **[UI Component]** Implement `LoginScreen` Composable.
*   **[Input Fields]** Create four `OutlinedTextField` components:
    1.  KOBO Username (Text)
    2.  KOBO Password (Password transformation)
    3.  KOBO Server URL (Text, default value provided)
    4.  Form ID / Asset URI (Text)
*   **[Validation Logic]** The "Download Data" button must be **disabled** if any of the 4 fields are empty.
*   **[Button Label]** Button text must explicitly read **"Download Data"**.
*   **[Action]** On button click, invoke `ViewModel.startLoginAndDownloadProcess()` with the input values.

#### Screen 2: Download Loading
*   **[UI Component]** Implement `DownloadLoadingScreen` Composable.
*   **[Visuals]** Display a centered `CircularProgressIndicator` (Indeterminate).
*   **[Text]** Display text "Downloading data..." below the spinner.
*   **[State Handling]** UI must automatically dismiss and navigate to Screen 3 upon receiving `UiState.Success`.

#### Screen 3: Download Complete
*   **[UI Component]** Implement `DownloadCompleteScreen` Composable.
*   **[Data Display]** Display two Text views:
    1.  "Total form entries downloaded: {count}"
    2.  "Latest submission date: {date}"
*   **[Actions]** Implement two buttons:
    1.  **"View Data"** (Primary): Navigates to Home.
    2.  **"Resync Data"** (Secondary): Triggers Resync Flow (Screen 5).

#### Screen 4: Home / Dashboard
*   **[UI Component]** Implement `HomeDashboardScreen` Composable.
*   **[List]** Implement a `LazyColumn` to render the list of downloaded forms.
*   **[List Item]** Each item must display: Submission Name, ID, and a Status Badge.
*   **[Action]** Implement a Floating Action Button (FAB) or Top Bar button labeled **"Resync Data"**.

#### Screen 5: Resync Loading
*   **[UI Component]** Implement `ResyncLoadingScreen` Composable.
*   **[Visuals]** Display `CircularProgressIndicator` with text "Syncing data...".
*   **[State Handling]** UI must automatically dismiss and navigate to Screen 6 upon success.

#### Screen 6: Sync Complete
*   **[UI Component]** Implement `SyncCompleteScreen` Composable.
*   **[Data Display]** Display:
    1.  "Added Records: {number}"
    2.  "Updated Records: {number}"
    3.  "Latest Record Timestamp: {time}"
*   **[Action]** Implement "Return to Dashboard" button.


