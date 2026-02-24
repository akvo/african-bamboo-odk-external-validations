# Architecture Overview

## Common Misconception

> "I logged in and downloaded data, so the app will automatically validate my polygons."

**This is incorrect.** The AfriBamODK app has two **independent** roles that work together but are triggered differently:

| Role | Triggered by | Purpose |
|------|-------------|---------|
| **Data Manager** | User opens the app, logs in | Downloads submissions from KoboToolbox and extracts plots into the local database |
| **External Validator** | ODK Collect launches the app via intent | Validates polygon geometry and checks for overlaps against the local database |

The download populates the database. The validation **only happens when ODK Collect calls the app** through a correctly configured XLSForm.

---

## System Overview

```mermaid
graph TB
    subgraph "KoboToolbox Server"
        API["KoboToolbox API"]
    end

    subgraph "AfriBamODK App"
        subgraph "Role 1: Data Manager"
            LOGIN["Login Screen"]
            DOWNLOAD["Download/Sync"]
            EXTRACT["Plot Extractor"]
        end

        DB[("Local Database<br/>(Room)")]

        subgraph "Role 2: External Validator"
            VALIDATE["PolygonValidationActivity"]
            GEOM["Geometry Checks"]
            OVERLAP["Overlap Checker"]
            MAP["Map Preview"]
        end
    end

    subgraph "ODK Collect"
        FORM["XLSForm with<br/>appearance config"]
    end

    %% Data Manager flow
    LOGIN -->|credentials| DOWNLOAD
    DOWNLOAD -->|"fetch submissions<br/>(API)"| API
    API -->|"JSON responses"| DOWNLOAD
    DOWNLOAD -->|"store submissions"| DB
    DOWNLOAD -->|"after sync"| EXTRACT
    EXTRACT -->|"parse rawData →<br/>extract polygons"| DB

    %% External Validator flow
    FORM -->|"intent: VALIDATE_POLYGON<br/>(shape, plot_name, region, ...)"| VALIDATE
    VALIDATE --> GEOM
    GEOM -->|"pass"| OVERLAP
    OVERLAP -->|"query candidates<br/>(bbox pre-filter)"| DB
    DB -->|"existing plots"| OVERLAP
    OVERLAP -->|"overlap detected"| MAP
    VALIDATE -->|"RESULT_OK + value<br/>or RESULT_OK + null"| FORM

    style DB fill:#f9f,stroke:#333,stroke-width:2px
    style FORM fill:#bbf,stroke:#333,stroke-width:2px
    style API fill:#bfb,stroke:#333,stroke-width:2px
```

---

## Two Independent Workflows

The following sequence diagram shows the two workflows and how the local database is the bridge between them.

```mermaid
sequenceDiagram
    box rgb(230, 245, 230) Workflow 1: Data Population
    participant User
    participant App as AfriBamODK App
    participant Kobo as KoboToolbox API
    participant DB as Local Database
    end

    box rgb(230, 230, 245) Workflow 2: Validation (triggered by ODK Collect)
    participant ODK as ODK Collect
    participant Val as PolygonValidationActivity
    end

    Note over User,DB: WORKFLOW 1 — User opens app, downloads data
    User->>App: Login (username, password, server, formId)
    App->>Kobo: GET /api/v2/assets/{uid}/data (paginated)
    Kobo-->>App: JSON submissions
    App->>DB: Store SubmissionEntities
    App->>DB: Match drafts to submissions (by instanceName)
    App->>DB: Extract plots from rawData → PlotEntities
    Note over DB: Database now contains<br/>plots from all synced submissions

    Note over ODK,DB: WORKFLOW 2 — Enumerator collects data in ODK Collect
    Note over ODK: XLSForm MUST have correct appearance:<br/>ex:org.akvo.afribamodkvalidator.VALIDATE_POLYGON(...)
    ODK->>Val: Intent: VALIDATE_POLYGON<br/>(shape, plot_name, region, instance_name)

    Val->>Val: Step 1: Geometry validation<br/>(vertices, area, self-intersection)
    alt Geometry invalid
        Val-->>ODK: RESULT_OK, value=null (field cleared)
        Note over ODK: required=yes blocks submission
    end

    Val->>DB: Step 2: Query overlap candidates (bbox pre-filter)
    DB-->>Val: Existing plots in bounding box
    Val->>Val: Step 3: JTS intersection check (≥5% threshold)

    alt Overlap detected
        Val-->>ODK: RESULT_OK, value=null (field cleared)
        Note over ODK: required=yes blocks submission
    else No overlap
        Val->>DB: Save draft plot (isDraft=true)
        Val-->>ODK: RESULT_OK, value=polygon data (accepted)
    end
```

---

## Why Both Workflows Are Needed

```mermaid
flowchart LR
    subgraph "Without Download"
        A["New plot validated"] --> B["No existing plots<br/>in database"]
        B --> C["Overlap check passes<br/>even if overlap exists<br/>on other devices"]
    end

    subgraph "With Download"
        D["Submissions synced<br/>from server"] --> E["Plots extracted<br/>to local DB"]
        E --> F["New plot validated<br/>against ALL plots"]
        F --> G["Overlaps detected<br/>across all devices"]
    end

    style C fill:#fbb,stroke:#933
    style G fill:#bfb,stroke:#393
```

**The download is not for validation itself** — it is for **populating the local plot database** so that overlap detection has comprehensive data to compare against. Without downloading, the app can only detect overlaps between plots collected on the **same device** during the **current session**.

---

## Draft Plot Lifecycle

Draft plots bridge the gap between local validation and server sync.

```mermaid
stateDiagram-v2
    [*] --> DraftCreated: Polygon passes validation<br/>(saved with isDraft=true)

    DraftCreated --> MatchedToSubmission: Server sync finds matching<br/>instanceName
    DraftCreated --> DraftCreated: More plots validated locally<br/>(each checks against all drafts + synced)
    DraftCreated --> OrphanedDraft: Form never submitted<br/>or instanceName mismatch

    MatchedToSubmission --> [*]: isDraft=false<br/>submissionUuid set
    OrphanedDraft --> [*]: Cleared on logout

    note right of DraftCreated
        Immediately available for
        overlap detection — no sync needed
    end note
```

---

## What the XLSForm Controls

The XLSForm `appearance` column determines whether validation runs at all and what type of validation is performed:

```mermaid
flowchart TD
    START["Enumerator taps<br/>validation field<br/>in ODK Collect"]

    START --> CHECK{"XLSForm appearance<br/>configured correctly?"}

    CHECK -->|"No appearance or<br/>wrong package name"| SKIP["ODK Collect accepts<br/>any polygon data<br/>⚠️ NO VALIDATION"]

    CHECK -->|"ex:org.akvo.afribamodkvalidator<br/>.VALIDATE_POLYGON(...)"| LAUNCH["Launches AfriBamODK<br/>validation app"]

    LAUNCH --> EXTRAS{"Which extras<br/>are passed?"}

    EXTRAS -->|"shape only"| GEOM["Geometry-only validation<br/>(vertices, area, self-intersection)"]

    EXTRAS -->|"shape + plot_name<br/>+ region + instance_name"| FULL["Full validation<br/>(geometry + overlap detection)"]

    GEOM --> RETURN["Returns result<br/>to ODK Collect"]
    FULL --> RETURN

    style SKIP fill:#fbb,stroke:#933
    style GEOM fill:#ffb,stroke:#993
    style FULL fill:#bfb,stroke:#393
```
