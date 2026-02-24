# Sync Integration

When submissions are synced from KoboToolbox, the app automatically:

1. **Matches drafts to submissions**: Links local draft plots to their synced submissions by `instanceName`, updating `isDraft = false`

2. **Extracts plots from synced data**: Creates `PlotEntity` records from synced submissions by parsing `rawData` JSON for polygon and farmer information

This ensures overlap detection works against **all plots** (both local drafts and synced submissions from other users/devices).

## Field Mapping Configuration

Edit `assets/plot_extraction_config.json` to customize which fields are extracted:

```json
{
  "polygonFields": ["boundary_mapping/Open_Area_GeoMapping", "manual_boundary"],
  "plotNameFields": ["First_Name", "Father_s_Name", "Grandfather_s_Name"],
  "regionField": "woreda",
  "subRegionField": "kebele"
}
```

## Sync Operational Considerations

### Draft Lifecycle

Drafts persist indefinitely until matched to a synced submission. There is no automatic cleanup of orphaned drafts.

| Draft State | Description |
|-------------|-------------|
| `isDraft=true, submissionUuid=null` | Newly created, awaiting sync |
| `isDraft=false, submissionUuid=<uuid>` | Matched to synced submission |
| `isDraft=true` (stuck) | Form never submitted to server, or `instanceName` mismatch |

**Clearing orphaned drafts**: Logout (`Menu → Logout`) clears all local data including drafts.

### Conflict Resolution

The app uses **last-write-wins** strategy with no merge logic:
- When syncing, if a submission already exists locally, it is replaced with server data
- Local modifications are overwritten without warning
- No audit trail of changes

**Implication**: If two users modify the same plot, the last synced version wins.

### Concurrency

Sync operations are serialized through Android's ViewModelScope:
- Only one sync can run at a time per app instance
- No explicit database locks; Room handles SQLite transactions automatically
- Safe for single-device use; concurrent syncs from multiple devices handled by last-write-wins

### Performance Considerations

| Dataset Size | Sync Behavior |
|--------------|---------------|
| < 1,000 submissions | Fast, no issues |
| 1,000 - 10,000 submissions | Acceptable; API pagination (300/page) limits memory |
| > 10,000 submissions | May be slow; plot extraction uses O(n) queries |

**Optimizations in use**:
- Pagination: API fetches 300 submissions per request
- Delta sync: Only fetches submissions newer than last sync timestamp
- Bounding box indexes: Fast spatial queries for overlap detection

### Troubleshooting Plot Extraction

Plot extraction failures are logged but not shown to users. Common issues:

| Symptom | Cause | Solution |
|---------|-------|----------|
| Plots not appearing after sync | Field paths in config don't match form structure | Check `plot_extraction_config.json` field names against form JSON |
| Some submissions missing plots | Invalid polygon data (self-intersection, too few points) | Review `adb logcat` for `PlotExtractor` errors |
| Zero plots extracted | Polygon field path incorrect | Verify `polygonFields` array in config matches your form |

**Viewing extraction logs**:
```bash
adb logcat -s PlotExtractor:E KoboRepository:E
```

**Verifying config fields**: Export a submission from KoboToolbox and compare JSON keys with `plot_extraction_config.json`.
