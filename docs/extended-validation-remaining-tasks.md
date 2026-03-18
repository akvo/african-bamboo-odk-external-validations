# Extended Validation Rules — Remaining Tasks

**Branch**: `feature/25-extended-validation-rules-warnings`
**Source Plan**: [extended-validation-implementation-plan.md](extended-validation-implementation-plan.md)

## What's Been Completed

### Phase 1: Core Validation Engine ✅
- `validation/GeoMath.kt` — Haversine distance + Coefficient of Variation
- `validation/PlotWarning.kt` — WarningType enum (5 types) + PlotWarning data class
- `validation/WarningRuleEngine.kt` — All 5 warning rules (W1–W5) + area calculation
- `validation/GeoMathTest.kt` — 14 unit tests
- `validation/WarningRuleEngineTest.kt` — 24 unit tests

### Phase 2: Persistence (Room) ✅
- `data/entity/PlotWarningEntity.kt` — Room entity with fieldSynced/notesSynced tracking
- `data/dao/PlotWarningDao.kt` — Full DAO (batch insert, aggregation, sync status, cleanup)
- `data/database/AppDatabase.kt` — Version 5 → 6, proper `MIGRATION_5_6` (CREATE TABLE + indexes)
- `di/DatabaseModule.kt` — Hilt provider for PlotWarningDao
- `data/repository/KoboRepository.kt` — `computeAndPersistWarnings()` + warning cleanup

### Phase 3: Kobo Write-Back ✅
- `data/network/KoboApiService.kt` — `patchSubmission()` + `addNote()` endpoints
- `data/repository/KoboRepository.kt` — Sync logic:
  - `syncWarningsToKobo()` → `syncWarningsViaField()` + `syncWarningsViaNotes()`
  - PATCH pipe-delimited string to `dcu_validation_warnings` field
  - POST each warning as `[DCU Warning]` note
  - Fire-and-forget; failures logged, retried on next sync

### Phase 4: UI ✅
- `ui/model/SubmissionUiModel.kt` — `warningCount: Int = 0`
- `ui/viewmodel/HomeViewModel.kt` — Warning counts via Flow + warning filter toggle
- `ui/viewmodel/SubmissionDetailViewModel.kt` — Loads warnings per submission
- `ui/component/SubmissionListItem.kt` — Amber warning badge
- `ui/screen/SubmissionDetailScreen.kt` — WarningsSection with amber cards
- `ui/screen/HomeDashboardScreen.kt` — Warning filter icon (amber when active)

### Phase 5: Documentation ✅ (code)
- `README.md` — Updated XLSForm Configuration section with `dcu_validation_warnings` field + explanation
- Warning flags added to Features list

### Tests ✅
- 38 validation engine tests (GeoMath + WarningRuleEngine)
- 6 sync-to-Kobo tests (PATCH success/failure, notes success/failure, grouping, skip empty)
- All passing

---

## Remaining Tasks

None — all tasks complete.

---

## Tech AC Checklist (Final)

| Area | Status |
|------|--------|
| Validation Engine | ✅ All done |
| Persistence (incl. migration) | ✅ All done |
| Kobo Sync — Primary (XLSForm field) | ✅ All done |
| Kobo Sync — Fallback (_notes) | ✅ Done |
| UI — List + Detail + Dashboard filter | ✅ All done |
| Documentation | ✅ Done |
| Integration and Safety | ✅ All done |
| Tests — Validation | ✅ 38 passing |
| Tests — Sync | ✅ 6 passing |
| DB Migration | ✅ Done |
