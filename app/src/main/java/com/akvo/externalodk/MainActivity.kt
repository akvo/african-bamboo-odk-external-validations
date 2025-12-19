package com.akvo.externalodk

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akvo.externalodk.ui.theme.ExternalODKTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Load default GeoJSON from assets if not already set
        loadDefaultGeoJson()
        
        enableEdgeToEdge()
        setContent {
            ExternalODKTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RegionInputScreen(
                        modifier = Modifier.padding(innerPadding),
                        context = this
                    )
                }
            }
        }
    }
    
    private fun loadDefaultGeoJson() {
        val sharedPreferences = getSharedPreferences("region_prefs", Context.MODE_PRIVATE)
        if (!sharedPreferences.contains("geojson_polygon")) {
            try {
                val defaultGeoJson = assets.open("polygon.json").bufferedReader().use { it.readText() }
                sharedPreferences.edit()
                    .putString("geojson_polygon", defaultGeoJson)
                    .apply()
            } catch (e: Exception) {
                // Handle error silently
            }
        }
    }
}

@Composable
fun RegionInputScreen(modifier: Modifier = Modifier, context: Context) {
    val sharedPreferences = context.getSharedPreferences("region_prefs", Context.MODE_PRIVATE)
    var regionText by remember { mutableStateOf(sharedPreferences.getString("valid_region", "") ?: "") }
    var showRegionMessage by remember { mutableStateOf(false) }
    var showGeoJsonMessage by remember { mutableStateOf(false) }
    var geoJsonStatus by remember { 
        mutableStateOf(
            if (sharedPreferences.contains("geojson_polygon")) "GeoJSON loaded" 
            else "No GeoJSON loaded"
        ) 
    }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val geoJsonContent = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                    reader.readText()
                }
                
                if (geoJsonContent != null) {
                    sharedPreferences.edit()
                        .putString("geojson_polygon", geoJsonContent)
                        .apply()
                    geoJsonStatus = "GeoJSON loaded"
                    showGeoJsonMessage = true
                }
            } catch (e: Exception) {
                geoJsonStatus = "Error loading GeoJSON: ${e.message}"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Validation Configuration",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Region Configuration Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Region Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                OutlinedTextField(
                    value = regionText,
                    onValueChange = { 
                        regionText = it
                        showRegionMessage = false
                    },
                    label = { Text("Valid Region Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    singleLine = true
                )

                Button(
                    onClick = {
                        sharedPreferences.edit()
                            .putString("valid_region", regionText)
                            .apply()
                        showRegionMessage = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Region")
                }

                if (showRegionMessage) {
                    Text(
                        text = "Region saved successfully!",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // GeoJSON Upload Section
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Polygon GeoJSON",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    text = geoJsonStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (geoJsonStatus.startsWith("Error")) 
                        MaterialTheme.colorScheme.error 
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Button(
                    onClick = { 
                        showGeoJsonMessage = false
                        filePickerLauncher.launch("application/json")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Upload GeoJSON File")
                }

                if (showGeoJsonMessage) {
                    Text(
                        text = "GeoJSON uploaded successfully!",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                // Preview Map Button
                if (sharedPreferences.contains("geojson_polygon")) {
                    OutlinedButton(
                        onClick = {
                            val intent = android.content.Intent(context, MapPreviewActivity::class.java)
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text("Preview Polygon on Map")
                    }
                }
            }
        }
    }
}