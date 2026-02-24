# Map Visualization

The app includes map visualization for viewing plot overlaps on an interactive **satellite map** powered by Mapbox.

## Features

- **Satellite Imagery**: High-resolution satellite view for accurate field boundary verification
- **Overlap Preview**: When validation fails due to overlap, tap "View on Map" to see both polygons
- **Color Coding**: Current plot (cyan fill), overlapping plots (red fill)
- **Offline Maps**: Download satellite tiles for field use without internet connectivity
- **Tile Preview**: Verify downloaded tiles by previewing regions on an interactive satellite map
- **Interactive**: Pinch to zoom, pan to navigate, tap polygon to see plot name
- **Google Maps Fallback**: Floating button to open location in Google Maps for fresher satellite imagery (visible when online)
- **Imagery Disclaimer**: Banner warns users that satellite imagery may be outdated

> **Note**: Mapbox satellite imagery may be several years old in some regions. Use the Google Maps button to check for more recent imagery when needed.

## Mapbox Setup

The app uses Mapbox Maps SDK which requires authentication tokens:

1. **Create Mapbox Account**: Sign up at [account.mapbox.com](https://account.mapbox.com/)

2. **Configure Tokens** in `local.properties` (gitignored):
   ```properties
   # Secret token for downloading SDK (Downloads:Read scope)
   MAPBOX_DOWNLOADS_TOKEN=sk.eyJ1...your_secret_token
   ```

3. **Public Token** in `app/src/main/res/values/mapbox_access_token.xml` (gitignored):
   ```xml
   <string name="mapbox_access_token">pk.eyJ1...your_public_token</string>
   ```

> **Free Tier**: Mapbox offers 25,000 monthly active users for free, sufficient for most deployments.

## Offline Map Downloads

Access offline maps via **Menu → Offline Maps** from the home screen.

**How it works:**
1. Select a Woreda/region from the list (tap to highlight)
2. Tap **Download** in the bottom footer to download satellite tiles
3. After download completes, tap **Preview** to verify tiles on a satellite map
4. The Download button is automatically disabled when the device is offline

**Predefined Regions**: Configured in `assets/offline_regions.json`:
```json
{
  "regions": [
    {
      "name": "Addis Ababa",
      "north": 9.1,
      "east": 38.9,
      "south": 8.8,
      "west": 38.6
    }
  ]
}
```

**Download Settings**:
- Style: Satellite Streets (satellite imagery with road labels)
- Zoom levels: 15-18 (suitable for plot-level detail)
- Storage: Mapbox TileStore (managed automatically)

**Tile Preview**: After downloading, use the Preview button to open an interactive satellite map centered on the region. The Mapbox SDK automatically uses cached tiles, so if the map renders correctly offline, the download was successful.

## Adding Custom Regions

Edit `app/src/main/assets/offline_regions.json` to add regions for your deployment:

```json
{
  "regions": [
    {
      "name": "Your Region Name",
      "north": <max_latitude>,
      "east": <max_longitude>,
      "south": <min_latitude>,
      "west": <min_longitude>
    }
  ]
}
```

Use [bboxfinder.com](http://bboxfinder.com/) to find bounding box coordinates for your area.
