# Technical Acceptance Criteria: ODK External Polygon Validator

## 1. Technology Stack & Dependencies
*   **Language**: Kotlin
*   **Minimum SDK**: Android 5.0 (Lollipop) or higher (aligned with modern ODK Collect).
*   **Geometry Library**: **JTS Topology Suite** (`org.locationtech.jts:jts-core`) to handle geometry validation (equivalent to Python's `shapely`).
    *   *Rationale*: JTS is the industry standard for Java/Kotlin spatial operations and provides `isValid`, `area`, and `coordinate` methods.

## 2. Intent & Data Handling
The app must function as an ODK External App:
*   **Intent Filter**: The App must handle the `ACTION_VIEW` intent or the specific action string defined in the ODK `intent` widget of the XForm.
*   **Input Parsing**:
    *   The app MUST accept the polygon data via the Intent Extras (specifically the value string passed from the ODK form field).
    *   The app MUST parse the incoming string into a JTS `Polygon` object.
    *   *Supported Formats*: The app MUST support parsing standard GeoJSON or WKT (Well-Known Text). **(Decision Point: Specify exactly which format ODK passes in your XForm)**.

## 3. Geometry Validation Logic
The app must implement the following validation checks in order. If any check fails, the specific error message (see Section 4) must be triggered, and the validation process must halt.

### 3.1 Vertex Count (Too Few Vertices)
*   **Tech Spec**: Extract the `Coordinate` array from the `Polygon` exterior ring.
*   **Criteria**: Validate that `coordinates.size >= 4`.
    *   *Note*: A polygon requires 4 points to form a triangle (3 distinct points + 1 closing point that duplicates the first).
*   **Threshold**: Define a constant `MIN_VERTICES = 4`.

### 3.2 Minimum Area (Polygon Too Small)
*   **Tech Spec**: Calculate the area of the polygon using `geometry.getArea`.
*   **Criteria**: Validate that `geometry.getArea > MIN_AREA_THRESHOLD`.
    *   *Note on Units*: Since GPS coordinates are in degrees, `getArea` returns square degrees.
    *   **Requirement**: The app MUST convert square degrees to square meters (or hectares) using a Geodetic calculation (e.g., JTS `GeodeticCalculator`) OR the ODK form must pass pre-projected data. **(Decision Point: Prefer on-device conversion)**.
*   **Threshold**: Define a configurable `MIN_AREA_METERS` (e.g., 10 sq meters).

### 3.3 Topology (Self-Intersecting Lines)
*   **Tech Spec**: Use the native JTS validation method.
*   **Criteria**: Validate that `geometry.isValid` returns `true`.
*   **Logic**:
    *   If `!geometry.isValid`, the polygon likely has self-intersecting edges, bow-ties, or other topological errors.

## 4. User Interface & Error Handling
The app must strictly enforce the User AC regarding blocking and error messages.

*   **Error Display**:
    *   Do **NOT** use a simple Toast for blocking errors.
    *   Display an **AlertDialog** that cannot be dismissed by clicking outside the box (Force Cancellation).
    *   **Error Messages** (Must map 1:1 to the logic in Section 3):
        1.  *Vertex Error*: "Error: Polygon has too few vertices. A valid shape requires at least 3 points."
        2.  *Area Error*: "Error: Polygon area is too small. Minimum required: [X] square meters."
        3.  *Intersection Error*: "Error: Polygon lines intersect or cross each other. Please redraw the shape."
*   **Blocking Behavior**:
    *   If validation fails, the AlertDialog must display an "OK" button.
    *   Clicking "OK" must simply close the dialog and return the user to the app state (allowing them to cancel or edit in ODK).
    *   **CRITICAL**: The app **MUST NOT** call `setResult(RESULT_OK)` or `finish()` successfully if validation fails.

## 5. Successful Validation & Return
*   **Success Scenario**: If all checks in Section 3 pass:
    1.  Display a brief success message (Toast) or simply proceed.
    2.  Return `RESULT_OK` to ODK.
    3.  Return the validated Polygon string back to ODK via the Intent data URI (so the form field updates).
    4.  Call `finish()`.

## 6. Code Implementation Reference (Kotlin/JTS)

```kotlin
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.io.ParseException
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.operation.valid.IsValidOp

// Constants for validation thresholds
private const val MIN_VERTICES = 4
private const val MIN_AREA_SQ_METERS = 10.0 // Example threshold

class ValidationActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Get Input from ODK Intent
        // Assuming ODK passes the WKT string in a specific extra, e.g., "value"
        val wktString = intent.getStringExtra("value") ?: run {
            showErrorAndBlock("No data received from form.")
            return
        }

        if (validatePolygon(wktString)) {
            returnSuccess(wktString)
        }
        // If false, the validatePolygon method already showed the error dialog
    }

    private fun validatePolygon(wkt: String): Boolean {
        val geometryFactory = GeometryFactory()
        val reader = WKTReader(geometryFactory)
        
        val polygon: Polygon
        try {
            polygon = reader.read(wkt) as Polygon
        } catch (e: ParseException) {
            showErrorAndBlock("Invalid format.")
            return false
        }

        // Check 1: Vertices
        val coords = polygon.exteriorRing.coordinates
        if (coords.size < MIN_VERTICES) {
            showErrorAndBlock("Error: Polygon has too few vertices. Minimum is 3.")
            return false
        }

        // Check 2: Area (Simplified for demo - requires Geodetic calc for real GPS)
        // Note: For high precision on GPS coordinates, use a GeodeticCalculator
        val area = polygon.area 
        // *Logic to convert degree area to meters squared goes here*
        if (area < 0.000001) { // Placeholder threshold
            showErrorAndBlock("Error: Polygon area is too small.")
            return false
        }

        // Check 3: Self-Intersection
        val isValidOp = IsValidOp(polygon)
        if (!polygon.isValid) {
            val errorMsg = isValidOp.validationError.message
            showErrorAndBlock("Error: Invalid shape (Lines intersect). $errorMsg")
            return false
        }

        return true
    }

    private fun showErrorAndBlock(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Validation Failed")
            .setMessage(message)
            .setCancelable(false) // Block user
            .setPositiveButton("OK") { dialog, _ ->
                // Return to ODK but do NOT set RESULT_OK
                setResult(RESULT_CANCELED)
                dialog.dismiss()
                finish()
            }
            .show()
    }

    private fun returnSuccess(data: String) {
        val resultIntent = Intent()
        // Prepare data to pass back to ODK if necessary
        // resultIntent.data = Uri.parse(data) 
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}
```

## 7. Testing Scenarios (QA Criteria)
1.  **Scenario A: Small Polygon**
    *   Input: A tiny triangle (e.g., 1 sq meter).
    *   Expected Result: App opens, displays "Area too small", blocks return. User fixes data in ODK, relaunches, App validates successfully and closes.
2.  **Scenario B: Self-Intersecting (Bowtie)**
    *   Input: Coordinates `0,0`, `10,0`, `0,10`, `10,10`, `0,0`.
    *   Expected Result: App opens, displays "Invalid shape/Lines intersect", blocks return.
3.  **Scenario C: Line (Too few vertices)**
    *   Input: Coordinates `0,0`, `10,10`, `0,10` (open line or insufficient closing).
    *   Expected Result: App opens, displays "Too few vertices", blocks return.
4.  **Scenario D: Valid Polygon**
    *   Input: A standard square meeting minimum area.
    *   Expected Result: App opens, immediately closes (or flashes success), and returns control to ODK allowing the user to proceed.
