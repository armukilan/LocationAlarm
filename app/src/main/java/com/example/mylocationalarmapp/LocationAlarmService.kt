package com.example.mylocationalarmapp

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlin.math.*

class LocationAlarmService : Service() {

    companion object {
        const val CHANNEL_ID = "LocationAlarmChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "START_MONITORING"
        const val ACTION_STOP = "STOP_MONITORING"
        const val TARGET_DISTANCE_METERS = 500f
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var notificationManager: NotificationManager
    private lateinit var sharedPreferences: SharedPreferences

    private var targetLatitude = 0.0
    private var targetLongitude = 0.0
    private var alarmToneUri: Uri? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isMonitoring = false
    private var hasTriggeredAlarm = false

    override fun onCreate() {
        super.onCreate()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        sharedPreferences = getSharedPreferences("AlarmSettings", Context.MODE_PRIVATE)

        createNotificationChannel()
        setupLocationCallback()

        Log.d("LocationService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMonitoring(intent)
            ACTION_STOP -> stopMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring(intent: Intent) {
        targetLatitude = intent.getDoubleExtra("latitude", 0.0)
        targetLongitude = intent.getDoubleExtra("longitude", 0.0)

        val toneUriString = sharedPreferences.getString("selected_tone_uri", null)
        alarmToneUri = toneUriString?.let { Uri.parse(it) }

        if (targetLatitude == 0.0 && targetLongitude == 0.0) {
            Log.e("LocationService", "Invalid target coordinates")
            stopSelf()
            return
        }

        isMonitoring = true
        hasTriggeredAlarm = false
        startForeground(NOTIFICATION_ID, createNotification("Starting location monitoring..."))
        startLocationUpdates()

        Log.d("LocationService", "Started monitoring for target: $targetLatitude, $targetLongitude")
    }

    private fun stopMonitoring() {
        isMonitoring = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
        stopAlarm()
        stopForeground(true)
        stopSelf()

        Log.d("LocationService", "Stopped monitoring")
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (!isMonitoring) return

                val location = locationResult.lastLocation ?: return
                val distance = calculateDistance(
                    location.latitude, location.longitude,
                    targetLatitude, targetLongitude
                )

                Log.d("LocationService", "Current distance: ${distance.toInt()}m")

                // Update notification with current distance
                val notification = createNotification("Distance to destination: ${distance.toInt()}m")
                notificationManager.notify(NOTIFICATION_ID, notification)

                // Check if within target distance
                if (distance <= TARGET_DISTANCE_METERS && !hasTriggeredAlarm) {
                    triggerAlarm()
                    hasTriggeredAlarm = true
                }
            }
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 5000L // Update every 5 seconds
        ).apply {
            setMinUpdateDistanceMeters(10f) // Update if moved 10 meters
            setMinUpdateIntervalMillis(2000L) // Minimum 2 seconds between updates
        }.build()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    private fun triggerAlarm() {
        Log.d("LocationService", "🚨 ALARM TRIGGERED! Within 500m of destination")

        // Update notification
        val alarmNotification = createNotification("🚨 DESTINATION REACHED! You are within 500m")
        notificationManager.notify(NOTIFICATION_ID, alarmNotification)

        // Play alarm sound
        alarmToneUri?.let { uri ->
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@LocationAlarmService, uri)
                    isLooping = true
                    prepare()
                    start()
                }
                Log.d("LocationService", "Alarm sound started")
            } catch (e: Exception) {
                Log.e("LocationService", "Error playing alarm", e)
            }
        }
    }

    private fun stopAlarm() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
            mediaPlayer = null
            Log.d("LocationService", "Alarm sound stopped")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Alarm",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows distance to destination"
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, SecondActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Location Alarm Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.d("LocationService", "Service destroyed")
    }
}