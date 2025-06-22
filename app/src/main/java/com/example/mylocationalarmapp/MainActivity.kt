package com.example.mylocationalarmapp

import android.Manifest
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.Locale
import android.widget.AdapterView
import android.content.Intent
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.location.LocationServices
import android.content.BroadcastReceiver
import android.content.IntentFilter

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var myLocationOverlay: MyLocationNewOverlay
    private lateinit var autoCompleteLocation: AutoCompleteTextView
    lateinit var destinationMarker: Marker
    private var selectedDestination: GeoPoint? = null
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private lateinit var btnNext: Button
    private var finalDestination: GeoPoint? = null
    private lateinit var locationReceiver: BroadcastReceiver


    private val locationSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Location settings satisfied, proceed with location updates
            setupLocationAfterPermission()
        } else {
            // User declined to turn on location - close app
            Toast.makeText(this, "Location is required. App will close.", Toast.LENGTH_SHORT).show()
            finishAffinity() // This closes the app completely
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        // osmdroid config
        Configuration.getInstance().load(applicationContext, androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext))

        setContentView(R.layout.activity_main)

        // Initialize the clear button
        val btnClear = findViewById<Button>(R.id.btnClear)

        // Clear button click listener
        btnClear.setOnClickListener {
            clearDestination()
        }

        fun clearDestinationFromSecondActivity() {
            clearDestination()
        }

        // Initialize Next button
        btnNext = findViewById(R.id.btnNext)

        // Next button click listener
        btnNext.setOnClickListener {
            finalDestination?.let { destination ->
                val intent = Intent(this, SecondActivity::class.java)
                intent.putExtra("latitude", destination.latitude)
                intent.putExtra("longitude", destination.longitude)
                startActivity(intent)
            }
        }

        // Initialize views
        mapView = findViewById(R.id.mapView)
        val btnConfirm = findViewById<Button>(R.id.btnConfirm)
        autoCompleteLocation = findViewById(R.id.editTextLocation)

        // Debug: Check if button is found
        Log.d("GeoDebug", "Button found: ${btnConfirm != null}")
        Log.d("GeoDebug", "AutoComplete found: ${autoCompleteLocation != null}")
        Log.d("GeoDebug", "MapView found: ${mapView != null}")

        // Set up autocomplete
        setupAutoComplete()

        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(15.0)

        // Show user location
        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView)
        myLocationOverlay.enableMyLocation()
        mapView.overlays.add(myLocationOverlay)



        // Set up location tracking if we have permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            // Check if location services are enabled
            checkLocationSettings()
        } else {
            // Ask for location permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                1
            )
        }

        // When user clicks Set Destination
        btnConfirm.setOnClickListener {
            Log.d("GeoDebug", "🔥 BUTTON CLICKED! 🔥")
            Log.d("GeoDebug", "Confirm button clicked")

            val locationName = autoCompleteLocation.text.toString()
            Log.d("GeoDebug", "User input: '$locationName'")

            if (locationName.isNotEmpty()) {
                Log.d("GeoDebug", "Starting geocoding for: $locationName")
                geocodeLocation(locationName)
            } else {
                Log.d("GeoDebug", "Empty location name")
                Toast.makeText(this, "Please enter a location", Toast.LENGTH_SHORT).show()
            }
        }
        handleIntentExtras(intent)
    }

//    private fun clearDestination() {
//        // Clear the text field
//        autoCompleteLocation.setText("")
//        autoCompleteLocation.clearFocus()
//
//        // Remove the destination marker from map
//        if (::destinationMarker.isInitialized) {
//            mapView.overlays.remove(destinationMarker)
//            mapView.invalidate()
//        }
//
//        // Clear the selected destination
//        selectedDestination = null
//
//        // Disable Next button
//        btnNext.isEnabled = false
//        finalDestination = null
//
//        // Show feedback to user
//        Toast.makeText(this, "Destination cleared", Toast.LENGTH_SHORT).show()
//
//        Log.d("GeoDebug", "Destination cleared")
//    }

    private fun clearDestination() {
        // Clear the text field
        autoCompleteLocation.setText("")
        autoCompleteLocation.clearFocus()

        // Remove the destination marker from map
        if (::destinationMarker.isInitialized) {
            mapView.overlays.remove(destinationMarker)
            mapView.invalidate()
        }

        // Clear the selected destination
        selectedDestination = null

        // Disable Next button
        btnNext.isEnabled = false
        finalDestination = null

        // Reset pin to user's current location
        val currentLocation = myLocationOverlay.myLocation
        if (currentLocation != null) {
            val currentPoint = GeoPoint(currentLocation.latitude, currentLocation.longitude)

            // Create new marker at current location
            destinationMarker = Marker(mapView)
            destinationMarker.position = currentPoint
            destinationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            destinationMarker.title = "Destination"
            destinationMarker.isDraggable = true

            // Add drag listener
            destinationMarker.setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDrag(marker: Marker?) {}
                override fun onMarkerDragStart(marker: Marker?) {}
                override fun onMarkerDragEnd(marker: Marker?) {
                    marker?.let {
                        finalDestination = it.position
                        btnNext.isEnabled = true
                        Log.d("GeoDebug", "Pin moved to: ${it.position.latitude}, ${it.position.longitude}")
                        Toast.makeText(this@MainActivity, "Destination updated to pin location", Toast.LENGTH_SHORT).show()
                    }
                }
            })

            mapView.overlays.add(destinationMarker)
            mapView.controller.setCenter(currentPoint)
            mapView.invalidate()

            Log.d("GeoDebug", "Pin reset to current location: ${currentLocation.latitude}, ${currentLocation.longitude}")
            Toast.makeText(this, "Pin reset to your current location. Drag to set destination.", Toast.LENGTH_LONG).show()
        } else {
            Log.d("GeoDebug", "Current location not available")
            Toast.makeText(this, "Destination cleared. Current location not available.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupAutoComplete() {
        // Set up the autocomplete with empty adapter initially
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, mutableListOf())
        autoCompleteLocation.setAdapter(adapter)
        autoCompleteLocation.threshold = 3 // Start suggesting after 3 characters

        // Add item click listener to handle dropdown selection
        autoCompleteLocation.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
            val selectedLocation = parent.getItemAtPosition(position) as String
            autoCompleteLocation.setText(selectedLocation)
            autoCompleteLocation.dismissDropDown()
            autoCompleteLocation.clearFocus()

            // Optionally, automatically geocode the selected location
            geocodeLocation(selectedLocation)
        }

        // Add text change listener for real-time suggestions
        autoCompleteLocation.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()

                // Cancel previous search
                searchRunnable?.let { searchHandler.removeCallbacks(it) }

                if (query.length >= 3) {
                    // Debounce the search - wait 500ms after user stops typing
                    searchRunnable = Runnable {
                        searchLocationSuggestions(query)
                    }
                    searchHandler.postDelayed(searchRunnable!!, 500)
                }
            }
        })
    }

    private fun searchLocationSuggestions(query: String) {
        Log.d("GeoDebug", "Searching suggestions for: $query")

        val geocoder = Geocoder(this, Locale.getDefault())

        if (!Geocoder.isPresent()) {
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocationName(query, 5) { addressList ->
                    runOnUiThread {
                        updateSuggestions(addressList)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addressList = geocoder.getFromLocationName(query, 5)
                updateSuggestions(addressList)
            }
        } catch (e: Exception) {
            Log.e("GeoDebug", "Error getting suggestions", e)
        }
    }

    private fun updateSuggestions(addressList: List<Address>?) {
        if (!addressList.isNullOrEmpty()) {
            val suggestions = addressList.map { address ->
                // Create a nice display name from the address
                buildString {
                    address.featureName?.let { append(it) }
                    address.locality?.let {
                        if (isNotEmpty()) append(", ")
                        append(it)
                    }
                    address.adminArea?.let {
                        if (isNotEmpty()) append(", ")
                        append(it)
                    }
                    address.countryName?.let {
                        if (isNotEmpty()) append(", ")
                        append(it)
                    }
                }
            }

            Log.d("GeoDebug", "Found ${suggestions.size} suggestions")

            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, suggestions)
            autoCompleteLocation.setAdapter(adapter)

            // Show dropdown if we have suggestions
            if (suggestions.isNotEmpty()) {
                autoCompleteLocation.showDropDown()
            }
        }
    }

    private fun geocodeLocation(locationName: String) {
        val geocoder = Geocoder(this, Locale.getDefault())

        // Check if Geocoder is present on device
        if (!Geocoder.isPresent()) {
            Toast.makeText(this, "Geocoder not available", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Use the new async API for Android 33+ (API level 33)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocationName(locationName, 1) { addressList ->
                    runOnUiThread {
                        handleGeocodingResult(addressList)
                    }
                }
            } else {
                // Use the old synchronous API for older versions
                @Suppress("DEPRECATION")
                val addressList = geocoder.getFromLocationName(locationName, 1)
                handleGeocodingResult(addressList)
            }
        } catch (e: Exception) {
            Log.e("GeoDebug", "Geocoding error", e)
            Toast.makeText(this, "Error getting location: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }



    private fun handleGeocodingResult(addressList: List<Address>?) {
        Log.d("GeoDebug", "Geocoder returned ${addressList?.size ?: 0} results")

        if (!addressList.isNullOrEmpty()) {
            val address = addressList[0]
            val destinationPoint = GeoPoint(address.latitude, address.longitude)
            Log.d("GeoDebug", "Address found: ${address.latitude}, ${address.longitude}")

            // Move map to location
//            mapView.controller.setCenter(destinationPoint)
//            mapView.controller.setZoom(16.0)

            // For first time, move to searched location. For subsequent times, use current location
            val targetPoint = if (myLocationOverlay.myLocation != null) {
                // Use current location if available
                val currentLocation = myLocationOverlay.myLocation
                GeoPoint(currentLocation.latitude, currentLocation.longitude)
            } else {
                // Fallback to searched location
                destinationPoint
            }

            // Move map to target location
            mapView.controller.setCenter(targetPoint)
            mapView.controller.setZoom(16.0)

            // Set marker to current location (or searched location as fallback)
            val markerPosition = targetPoint

            // Check if marker exists and is still on the map
            val markerExistsOnMap = ::destinationMarker.isInitialized && mapView.overlays.contains(destinationMarker)

            if (markerExistsOnMap) {
                // Update existing marker position
                destinationMarker.position = destinationPoint
                Log.d("GeoDebug", "Updated existing marker position")
            } else {
                // Create new marker
                destinationMarker = Marker(mapView)
//                destinationMarker.position = destinationPoint
                destinationMarker.position = markerPosition
                destinationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                destinationMarker.title = "Destination"
                mapView.overlays.add(destinationMarker)
                Log.d("GeoDebug", "Created new marker")
            }

            // Allow user to drag the pin and update final destination when moved
            destinationMarker.isDraggable = true
            destinationMarker.setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDrag(marker: Marker?) {}
                override fun onMarkerDragStart(marker: Marker?) {}
                override fun onMarkerDragEnd(marker: Marker?) {
                    marker?.let {
                        // Update final destination to pin's current position
                        finalDestination = it.position
                        Log.d("GeoDebug", "Pin moved to: ${it.position.latitude}, ${it.position.longitude}")
                        Toast.makeText(this@MainActivity, "Destination updated to pin location", Toast.LENGTH_SHORT).show()
                    }
                }
            })

            mapView.invalidate()

            Toast.makeText(this, "Pin placed. You can adjust it on the map.", Toast.LENGTH_SHORT).show()

            // Save destination and enable Next button
//            selectedDestination = destinationPoint
//            finalDestination = destinationPoint
            selectedDestination = markerPosition
            finalDestination = markerPosition
            btnNext.isEnabled = true

        } else {
            Toast.makeText(this, "Location not found. Try being more specific.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkLocationSettings() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 5000L
        ).build()

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true) // This will show the dialog even if location is disabled

        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            // Location settings are satisfied, proceed with location updates
            setupLocationAfterPermission()
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                // Location settings are not satisfied, show dialog to turn on location
                try {
                    locationSettingsLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(exception.resolution).build()
                    )
                } catch (sendEx: Exception) {
                    Log.e("LocationSettings", "Error showing location settings dialog", sendEx)
                }
            }
        }
    }

    private fun setupLocationAfterPermission() {
        myLocationOverlay.runOnFirstFix {
            runOnUiThread {
                val myLoc = myLocationOverlay.myLocation
                if (myLoc != null) {
                    mapView.controller.setCenter(GeoPoint(myLoc.latitude, myLoc.longitude))
                }
            }
        }
    }


override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)

    if (requestCode == 1) {
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Permission granted, now check location settings
            checkLocationSettings()
        } else {
            // Permission denied - close app
            Toast.makeText(this, "Location permission is required. App will close.", Toast.LENGTH_SHORT).show()
            finishAffinity() // This closes the app completely
        }
    }
}
    private fun setupLocationReceiver() {
        locationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                if (intent?.action == "android.location.PROVIDERS_CHANGED") {
                    // Location settings changed, check if location is still enabled
                    checkLocationSettings()
                }
            }
        }

        val filter = IntentFilter("android.location.PROVIDERS_CHANGED")
        registerReceiver(locationReceiver, filter)
    }

    override fun onResume() {
        super.onResume()
        setupLocationReceiver()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(locationReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }

    private fun handleIntentExtras(intent: Intent?) {
        if (intent?.getBooleanExtra("clear_destination", false) == true) {
            clearDestination()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntentExtras(intent)
    }
}