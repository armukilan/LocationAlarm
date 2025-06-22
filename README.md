# Location Alarm App

A smart Android application that triggers an alarm when you're within 500 meters of your destination. Perfect for public transport commuters, travelers, or anyone who wants to be alerted when approaching their destination.

## Features

- **Location-based Alarm**: Automatically triggers when you're within 500 meters of your destination
- **Interactive Map**: Uses OpenStreetMap for location selection and visualization
- **Smart Search**: Autocomplete location search with real-time suggestions
- **Draggable Pin**: Adjust your destination precisely by dragging the pin on the map
- **Custom Alarm Tones**: Select from system ringtones or your own audio files
- **Background Monitoring**: Continues monitoring your location even when the app is minimized
- **Real-time Distance Updates**: Shows your current distance to the destination in notifications

## How It Works

### 1. Location Selection (MainActivity)
- **Search Method**: Type your destination in the search bar with autocomplete suggestions
- **Map Method**: Use the interactive map to select and adjust your destination
- **Pin Adjustment**: Drag the pin to fine-tune your exact destination
- The final destination is always determined by the pin's position

### 2. Alarm Configuration (SecondActivity)
- View your selected destination coordinates
- Choose a custom alarm tone from:
  - System ringtones
  - Your music library
  - Audio files from storage
- Test your selected tone before starting
- Start/stop location monitoring

### 3. Background Monitoring (LocationAlarmService)
- Runs as a foreground service for reliable location tracking
- Updates your location every 5 seconds with high accuracy
- Shows real-time distance in the notification
- Triggers alarm and notification when within 500 meters
- Continues playing until manually stopped

## Technical Details

### Architecture
- **MainActivity**: Location selection with OSMDroid map integration
- **SecondActivity**: Alarm configuration and service control
- **LocationAlarmService**: Background location monitoring service

### Key Technologies
- **Kotlin**: Primary programming language
- **Android Location Services**: GPS and network-based location
- **OSMDroid**: Open-source map library
- **Geocoding**: Address-to-coordinate conversion
- **Foreground Services**: Background location monitoring
- **MediaPlayer**: Custom alarm tone playback

### Location Accuracy
- Uses `PRIORITY_HIGH_ACCURACY` for GPS-based location
- Minimum update interval: 2 seconds
- Minimum distance change: 10 meters
- Trigger distance: 500 meters from destination

## Installation

### Prerequisites
- Android device with API level 22+ (Android 5.1+)
- GPS/Location services enabled
- Internet connection for map tiles and geocoding

### Install from Source
1. Clone this repository
2. Open in Android Studio
3. Build and run on your device

```bash
git clone https://github.com/yourusername/location-alarm-app.git
cd location-alarm-app
```

## Permissions Required

The app requires the following permissions:
- **ACCESS_FINE_LOCATION**: For precise GPS location
- **ACCESS_COARSE_LOCATION**: For network-based location
- **READ_EXTERNAL_STORAGE/READ_MEDIA_AUDIO**: For custom alarm tones
- **FOREGROUND_SERVICE**: For background location monitoring
- **INTERNET**: For map tiles and geocoding

## Usage Instructions

1. **Set Destination**:
   - Launch the app and allow location permissions
   - Either search for your destination or select it on the map
   - Drag the pin to adjust the exact location if needed
   - Tap "Next" to proceed

2. **Configure Alarm**:
   - Select your preferred alarm tone
   - Test the tone to ensure it works
   - Tap "Start" to begin monitoring

3. **Monitor Progress**:
   - The app runs in the background
   - Check the notification for real-time distance updates
   - The alarm will trigger automatically when you're within 500m

4. **Stop or Cancel**:
   - Use "Stop" to pause monitoring
   - Use "Cancel" to return to destination selection

## Troubleshooting

### Location Issues
- Ensure GPS is enabled in device settings
- Check that the app has location permissions
- Try restarting the app if location isn't updating

### Alarm Not Playing
- Verify that a tone is selected
- Check device volume settings
- Test the tone using the "Test Tone" button

### Service Stops
- Disable battery optimization for the app
- Ensure the app isn't being killed by the system
- Check that location services remain enabled

## Technical Implementation

### Distance Calculation
Uses the Haversine formula via Android's `Location.distanceBetween()` method for accurate distance calculations between GPS coordinates.

### Background Processing
Implements a foreground service with persistent notification to ensure reliable location monitoring even when the app is not in the foreground.

### Memory Management
Properly manages MediaPlayer instances and location callbacks to prevent memory leaks and ensure smooth operation.

## Contributing

Contributions are welcome! Please feel free to submit issues, feature requests, or pull requests.

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## Future Enhancements

- Multiple destination support
- Customizable trigger distances
- Route-based alarms
- Integration with public transport APIs
- Voice notifications
- Geofencing optimization

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.

```
Location Alarm App - Smart proximity-based alarm system
Copyright (C) 2024

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```

## Acknowledgments

- OpenStreetMap contributors for map data
- OSMDroid library developers
- Android open-source community

---

**Note**: This app is designed for convenience and should not be relied upon for critical timing or safety purposes. Always have backup plans for important journeys.