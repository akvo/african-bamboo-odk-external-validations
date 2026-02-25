# Plot Overlap Detection

The app detects overlapping plots to prevent duplicate land registrations. When a new plot overlaps with an existing plot by 20% or more of the smaller polygon's area, validation fails.

## How It Works

1. **Single-polygon validation** runs first (vertex count, area, self-intersection)
2. **Bounding box pre-filter** queries nearby plots from the database using indexed bbox columns
3. **JTS geometry check** computes precise intersection area
4. **Threshold check**: overlap >= 20% of smaller polygon → blocked

> **Note**: Region is stored as metadata only, not used for filtering. This ensures overlaps are detected even when the same plot is registered with a different region label (wrong selection, boundary plots, fraud prevention).

## Fully Offline — No Sync Required Between Plots

Overlap detection works entirely offline without syncing between form collections. Each time the validation app is launched, the validated polygon is immediately saved as a draft (`isDraft = true`) in the local Room database — **before** the form is submitted to the server. The next validation checks against all existing plots (both drafts and synced submissions).

This means:
- **Form 1** is validated → polygon saved as draft to local DB → returned to ODK Collect
- **Form 2** is validated → overlap check queries the DB → **finds Form 1's draft** → blocks if overlap >= 20%
- No internet or server sync is needed between collecting plots on the same device

## Overlap Error Messages

When overlap is detected:
```
New plot for Abebe Kebede Tadesse overlaps with plot for Girma Tesfaye Hailu
```

## Overlap Threshold

- **Threshold**: 20% of the smaller polygon's area
- **Calculation**: `intersection_area / min(new_plot_area, existing_plot_area) * 100`
- **Rationale**: Allows minor boundary touching (GPS inaccuracy) while blocking significant overlaps

## Draft Plot Storage

On successful validation, the plot is saved as a draft in the local database:
- Stored with `isDraft = true`
- Linked to form via `instanceName`
- Used for overlap detection with subsequent plots
