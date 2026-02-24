# Polygon Validation

ExternalODK functions as an ODK external app that validates polygon/geoshape data before submission.

## Validation Checks

| Check | Description | Error Message |
|-------|-------------|---------------|
| Invalid Format | Polygon data cannot be parsed | "Invalid polygon format. Unable to parse the shape data." |
| Vertex Count | Polygon must have at least 3 distinct points | "Error: Polygon has too few vertices. A valid shape requires at least 3 points." |
| Minimum Area | Polygon must be larger than 10 sq meters | "Error: Polygon area is too small. Minimum required: 10 square meters." |
| Self-Intersection | Polygon edges cannot cross each other | "Error: Polygon lines intersect or cross each other. Please redraw the shape." |

## How Blocking Works

The blocking is handled by the **return value** from the app, not by an XLSForm constraint:

1. **No polygon data received**: App returns `RESULT_CANCELED`. ODK Collect does NOT update the field value.

2. **Validation fails** (invalid geometry or overlap detected): App shows an error AlertDialog, then returns `RESULT_OK` with `value = null`. This clears/resets the field in ODK Collect, preventing the user from proceeding when `required=yes` is set.

3. **Validation passes**: App returns `RESULT_OK` with the original polygon data as `value`. ODK Collect accepts the value and allows the user to proceed.

The `required=yes` column ensures the user can't skip the field entirely. The null return value on failure effectively resets the field, blocking submission until a valid polygon is provided.

## Supported Input Formats

- **ODK Geoshape**: `lat lng alt acc; lat lng alt acc; ...` (semicolon-separated points)
- **WKT**: `POLYGON ((x1 y1, x2 y2, x3 y3, x1 y1))`

## Intent Extras

Pass these extras from XLSForm to enable overlap detection:

| Extra | Description | Required |
|-------|-------------|----------|
| `shape` | Polygon data (geoshape or WKT format) | Yes |
| `plot_name` | Unique name or combination (e.g., full name) to identify the plot — shown in overlap error dialogs and map view labels | No (defaults to empty) |
| `region` | Administrative region (metadata, stored with plot) | No (defaults to empty) |
| `sub_region` | Sub-region (metadata, stored with plot) | No (defaults to empty) |
| `instance_name` | Form instance name for draft matching and re-validation dedup | No (defaults to empty) |
