# User AC

As a field enumerator, I want to detect plot overlaps as I collect data so that I can fix them before submission.

## Scenario 1: Overlap with downloaded data
**Given** I have downloaded the already collected data for the form I am working in before I lose signal  
**When** I collect a new plot that overlaps with an existing plot in the downloaded data in the form  
**And** I click "launch" in the kobo app to trigger the validation  
**Then** I should see the overlap in the validation app overlaid on a high resolution satellite view of the area  
**And** I should be able to select overlapping plots to see the farmer name for each and how it differs from the farmer I am working on  
**And** I should see an error in the kobo form describing the overlap in the format:  
`New plot for farmer <name> <father's name> <grandfather's name> overlaps with plot for <name> <father's name> <grandfather's name>`  
**And** I should be prevented from continuing until the problem is resolved  

**When** I go back in the kobo form to fix the overlap  
**Then** I should be able to switch back and forth from the validation app to see the latest overlap as I fix.

## Scenario 2: Overlap with unsubmitted local data
**Given** I have already collected data for a new plot which has not yet been submitted to kobo cloud  
**When** I begin a new form and collect another plot that overlaps with that existing unsubmitted plot  
**And** I click "launch" in the kobo app to trigger the validation  
**Then** I should see the overlap in the validation app overlaid on a high resolution satellite view of the area  
**And** I should be able to select overlapping plots to see the farmer name for each and how it differs from the farmer I am working on  
**And** I should see an error in the kobo form describing the overlap in the format:  
`New plot for farmer <name> <father's name> <grandfather's name> overlaps with plot for <name> <father's name> <grandfather's name>`  
**And** I should be prevented from continuing until the problem is resolved  

**When** I go back in the kobo form to fix the overlap  
**Then** I should be able to switch back and forth from the validation app to see the latest overlap as I fix  

**When** I navigate to the original form which the new record overlaps with  
**And** I return to the page of the form with the validation app "launch" button  
**And** I click the button  
**Then** I should receive a validation error  
**And** I should be able to return to the polygon input to fix the error.

## Scenario 3: Overlap resolved
**Given** I have fixed the overlap  
**When** I click launch to re-run the validation  
**Then** I should see no error  
**And** I should be able to continue with the form.

# Tech AC

## Data Storage

- Create `plots` table with:
  - `uuid` (PK)
  - `plotName` - farmer full name for display ("Abebe Kebede Tadesse")
  - `instanceName` - submission identifier for draft matching
  - `polygonWkt` - WKT format geometry
  - `woreda`, `kebele` - regional filters
  - Bounding box: `minLat`, `maxLat`, `minLon`, `maxLon`
  - `isDraft` (true until matched with synced submission)
  - `formId`, `createdAt`
  - `submissionUuid` (null for drafts, links to `submissions._uuid` after sync)
- Save plot as draft (`isDraft=true`) every time validation launch is used
- Match draft to submission by `instanceName` after sync → set `isDraft=false`

## Intent Contract

Update XLSForm appearance to pass required extras:
```
ex:org.akvo.afribamodkvalidator.VALIDATE_POLYGON(
  shape=${Open_Area_GeoMapping},
  plot_name=${First_Name} ${Father_s_Name} ${Grandfather_s_Name},
  woreda=${woreda},
  kebele=${kebele}
)
```

Keep XLSForm settings `instance_name` unchanged:
```
concat(${enumerator_id}, '-', ${woreda}, '-', today())
```

## Overlap Detection

- Block on overlaps >= 5% of smaller polygon area
- Calculate: intersection area / min(newPlotArea, existingPlotArea)
- Bounding box pre-filter in SQL for performance (indexed columns)
- JTS `intersects()` for precise geometry check
- **Region is metadata only** - not used for filtering to prevent false negatives when same plot is registered with different region label
- Expected performance: <500ms for 10,000 plots

## Error Message Format

```
New plot for <plotName> overlaps with plot for <plotName>
```

Example:
```
New plot for Abebe Kebede Tadesse overlaps with plot for Girma Tesfaye Hailu
```

## Map Visualization (MVP Required)

- Mapbox SDK with offline satellite imagery
- Pre-download by woreda (zoom 15-18, ~50-100 MB per woreda)
- Display: current plot (blue), overlapping plots (red)
- Tap polygon to show `plotName` in bottom sheet

## Sync Integration

- Extract from synced submissions' `rawData`:
  - Polygon: `Open_Area_GeoMapping` (geotrace) or `manual_boundary` (geoshape)
  - Plot name: `First_Name`, `Father_s_Name`, `Grandfather_s_Name`
- Match to existing draft by `instanceName`
- Create new plot record if no draft exists