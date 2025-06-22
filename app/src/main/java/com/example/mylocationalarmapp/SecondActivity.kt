package com.example.mylocationalarmapp

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.annotation.RequiresApi
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class SecondActivity : AppCompatActivity() {

    private lateinit var textCoordinates: TextView
    private lateinit var btnSelectTone: Button
    private lateinit var textSelectedTone: TextView
    private lateinit var btnTestTone: Button
    private lateinit var sharedPreferences: SharedPreferences
    private var selectedToneUri: Uri? = null
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnCancel: Button
    private lateinit var textStatus: TextView
    private var destinationLatitude = 0.0
    private var destinationLongitude = 0.0


    private val audioPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = if (result.data?.hasExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) == true) {
                // Handle ringtone picker result
                result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            } else {
                // Handle file picker result
                result.data?.data
            }

            uri?.let { handleSelectedTone(it) }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }

        if (granted) {
            openAudioPicker()
        } else {
            Toast.makeText(this, "Permission required to select audio files", Toast.LENGTH_SHORT).show()
        }
    }

//    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second)

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("AlarmSettings", MODE_PRIVATE)

        // Initialize views
        textCoordinates = findViewById(R.id.textCoordinates)
        btnSelectTone = findViewById(R.id.btnSelectTone)
        textSelectedTone = findViewById(R.id.textSelectedTone)
        btnTestTone = findViewById(R.id.btnTestTone)

        // Get coordinates from intent
        val latitude = intent.getDoubleExtra("latitude", 0.0)
        val longitude = intent.getDoubleExtra("longitude", 0.0)

        // Display coordinates
        textCoordinates.text = "Final Destination Coordinates:\nLatitude: $latitude\nLongitude: $longitude"

        // Load saved tone
        loadSavedTone()

        // Set click listeners
        btnSelectTone.setOnClickListener {
            checkPermissionAndSelectTone()
        }

        btnTestTone.setOnClickListener {
            testSelectedTone()
        }

        // Get coordinates and store them
        destinationLatitude = latitude
        destinationLongitude = longitude

        // Initialize control buttons
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnCancel = findViewById(R.id.btnCancel)
        textStatus = findViewById(R.id.textStatus)

        // Control button listeners
        btnStart.setOnClickListener {
            startLocationMonitoring()
        }

        btnStop.setOnClickListener {
            stopLocationMonitoring()
        }

        btnCancel.setOnClickListener {
            cancelAndReturn()
        }
    }

    private fun loadSavedTone() {
        val savedToneUriString = sharedPreferences.getString("selected_tone_uri", null)
        val savedToneName = sharedPreferences.getString("selected_tone_name", "No tone selected")

        if (savedToneUriString != null) {
            selectedToneUri = Uri.parse(savedToneUriString)
            textSelectedTone.text = savedToneName
        } else {
            textSelectedTone.text = "No tone selected"
        }
    }

    private fun checkPermissionAndSelectTone() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val permissionToCheck = permissions[0]

        if (ContextCompat.checkSelfPermission(this, permissionToCheck) == PackageManager.PERMISSION_GRANTED) {
            openAudioPicker()
        } else {
            permissionLauncher.launch(permissions)
        }
    }


    private fun openAudioPicker() {
        // Try ringtone picker first
        val ringtoneIntent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Tone")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            selectedToneUri?.let {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, it)
            }
        }

        // Check if ringtone picker is available
        if (ringtoneIntent.resolveActivity(packageManager) != null) {
            audioPickerLauncher.launch(ringtoneIntent)
        } else {
            // Fallback to file picker
            val fileIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "audio/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }

            if (fileIntent.resolveActivity(packageManager) != null) {
                audioPickerLauncher.launch(fileIntent)
            } else {
                // Final fallback to media picker
                val mediaIntent = Intent(Intent.ACTION_PICK, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
                if (mediaIntent.resolveActivity(packageManager) != null) {
                    audioPickerLauncher.launch(mediaIntent)
                } else {
                    Toast.makeText(this, "No audio picker available on this device", Toast.LENGTH_LONG).show()
                }
            }
        }
    }



    private fun handleSelectedTone(uri: Uri) {
        selectedToneUri = uri
        val toneName = getAudioFileName(uri)

        // Save to SharedPreferences
        with(sharedPreferences.edit()) {
            putString("selected_tone_uri", uri.toString())
            putString("selected_tone_name", toneName)
            apply()
        }

        // Update UI
        textSelectedTone.text = toneName
        Toast.makeText(this, "Tone selected and saved", Toast.LENGTH_SHORT).show()

        Log.d("AlarmTone", "Selected tone: $toneName, URI: $uri")
    }

    private fun getAudioFileName(uri: Uri): String {
        var fileName = "Unknown"
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = it.getString(nameIndex)
                }
            }
        }
        return fileName
    }

    private fun testSelectedTone() {
        selectedToneUri?.let { uri ->
            try {
                // Stop any currently playing media
                mediaPlayer?.release()

                // Create new MediaPlayer and play the selected tone
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@SecondActivity, uri)
                    prepare()
                    start()

                    setOnCompletionListener {
                        it.release()
                        mediaPlayer = null
                    }
                }

                Toast.makeText(this, "Playing selected tone...", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Log.e("AlarmTone", "Error playing tone", e)
                Toast.makeText(this, "Error playing tone: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, "No tone selected", Toast.LENGTH_SHORT).show()
        }
    }


    private fun startLocationMonitoring() {
        if (selectedToneUri == null) {
            Toast.makeText(this, "Please select an alarm tone first", Toast.LENGTH_SHORT).show()
            return
        }

        val serviceIntent = Intent(this, LocationAlarmService::class.java).apply {
            action = LocationAlarmService.ACTION_START
            putExtra("latitude", destinationLatitude)
            putExtra("longitude", destinationLongitude)
        }

        // Use appropriate method based on API level
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        btnStart.isEnabled = false
        btnStop.isEnabled = true
        textStatus.text = "Status: Monitoring location..."

        Toast.makeText(this, "Location monitoring started", Toast.LENGTH_SHORT).show()
    }

    private fun stopLocationMonitoring() {
        val serviceIntent = Intent(this, LocationAlarmService::class.java).apply {
            action = LocationAlarmService.ACTION_STOP
        }
        startService(serviceIntent)

        btnStart.isEnabled = true
        btnStop.isEnabled = false
        textStatus.text = "Status: Stopped"

        Toast.makeText(this, "Location monitoring stopped", Toast.LENGTH_SHORT).show()
    }



    private fun cancelAndReturn() {
        // Stop service if running
        stopLocationMonitoring()

        // Return to MainActivity and clear previous destination
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        intent.putExtra("clear_destination", true)
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onPause() {
        super.onPause()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}