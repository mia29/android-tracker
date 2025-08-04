package com.example.fortrace

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*

class LocationService : Service() {

    private var host: String? = null
    private var port: Int = 0
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null // 🚀 No binding needed

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        Log.d("Device Id", androidId)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location: Location in locationResult.locations) {
                    Log.d("GPSLocationService", "Lat: ${location.latitude}, Lon: ${location.longitude}")
                    updatLatLong(location)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = this.getSharedPreferences("fortrace_prefs", Context.MODE_PRIVATE)
        val savedHost = prefs.getString("last_host", "10.0.2.2")
        val savedPort = prefs.getString("last_port", "12345")

        host = savedHost
        val portString = savedPort ?: "12345"
        port = portString.toInt()

        startForeground(1, createNotification())
        startLocationUpdates()
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(10000) // every 10 seconds
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(5000)
            .setMaxUpdateDelayMillis(15000)
            .setWaitForAccurateLocation(true)
            .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun createNotification(): Notification {
        val channelId = "location_channel"
        val channelName = "GPS Location Service"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Location Service Running")
            .setContentText("Getting your GPS location")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updatLatLong(location: Location) {
        // ✅ Send to UI
        val intent = Intent("LOCATION_UPDATE")
        intent.putExtra("latitude", location.latitude)
        intent.putExtra("longitude", location.longitude)
        intent.putExtra("altitude", location.altitude)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

        val prefs = this.getSharedPreferences("fortrace_loc_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("last_latitude", location.latitude.toString())
            .putString("last_longitude", location.longitude.toString())
            .putString("last_altitude", location.altitude.toString())
            .apply()

        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        serviceScope.launch {
            try {
                Socket(host, port).use { socket ->
                    PrintWriter(OutputStreamWriter(socket.getOutputStream()), true).use { writer ->
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        val formatted = sdf.format(Date())

                        val json = """{
                          "android":1,
                          "id":"$androidId",
                          "date":"$formatted",
                          "latitude":${location.latitude},
                          "longitude":${location.longitude},
                          "altitude":${location.altitude}
                        }""".trimIndent()

                        writer.println(json)
                        writer.flush()
                        Log.d("LocationService", "📡 Sent location to TCP server")

                        // ✅ Read response from server
                        val response = socket.getInputStream().bufferedReader().readLine()
                        updateResponseMessage(response ?: "✅ Sent successfully")
                    }
                }
            } catch (e: Exception) {
                Log.e("LocationService", "❌ TCP send failed: ${e.message}")
                updateResponseMessage("❌ TCP send failed: ${e.message}")
            }
        }
    }

    private fun updateResponseMessage(responseMessage: String) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val formatted = sdf.format(Date())

        val intent = Intent("SERVER_RESPONSE")
        intent.putExtra("response_message", responseMessage)
        intent.putExtra("updated_at", formatted )
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

        val prefs = this.getSharedPreferences("fortrace_response_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("last_response", responseMessage)
            .putString("last_update", formatted)
            .apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        updateResponseMessage("🛑 Location service stopped")
        serviceScope.cancel()   // ✅ also cancel any coroutines still running
        stopForeground(STOP_FOREGROUND_REMOVE) // ✅ remove the notification
        Log.d("LocationService", "✅ Service fully destroyed")
    }

    override fun stopService(name: Intent?): Boolean {
        return super.stopService(name)
    }
}
