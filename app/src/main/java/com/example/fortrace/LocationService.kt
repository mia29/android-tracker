package com.example.fortrace;
import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.ServiceConnection
import android.icu.text.SimpleDateFormat
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket
import java.util.Date
import java.util.Locale

class LocationService : Service() {
    private var host: String? = null
    private var port: Int = 0

    override fun onBind(intent: Intent?): IBinder? {

        Log.v("TAG", "ON BindLocation Start")
        TODO("Not yet implemented")
    }

    override fun bindService(
        service: Intent,
        conn: ServiceConnection,
        flags: BindServiceFlags
    ): Boolean {
        Log.v("TAG", "On Bind Service Location Start")
        return super.bindService(service, conn, flags)
    }



    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    override fun onCreate() {
        super.onCreate()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        Log.d("Device Id", androidId)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location: Location in locationResult.locations) {
                    Log.d("GPSDevice Id", androidId)
                    Log.d("GPSLocationService", "Lat: ${location.latitude}, Lon: ${location.longitude}")
                    // ✅ Send location to UI using LocalBroadcast
                    val intent = Intent("LOCATION_UPDATE")
                    intent.putExtra("latitude", location.latitude)
                    intent.putExtra("longitude", location.longitude)
                    updatLatLong(location)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        host = intent?.getStringExtra("host") ?: "10.0.2.2"
        val portString = intent?.getStringExtra("port")?: "12345"
        port = portString.toInt()

        startForeground(1, createNotification())
        startLocationUpdates()
        return START_STICKY
    }


    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(10000) // 10 seconds interval
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)  // Best accuracy
            .setMinUpdateIntervalMillis(5000)  // Fastest interval: 5 seconds
            .setMaxUpdateDelayMillis(15000) // Maximum delay: 15 seconds
            .setWaitForAccurateLocation(true)  // Wait for more accurate location, if needed
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
            val channel = NotificationChannel(
                channelId, channelName, NotificationManager.IMPORTANCE_LOW
            )
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

    fun updatLatLong(location: Location) {

        // ✅ Send location to UI using LocalBroadcast
        val intent = Intent("LOCATION_UPDATE")

        intent.putExtra("latitude", location.latitude)
        intent.putExtra("longitude", location.longitude)
        intent.putExtra("altitude", location.altitude)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        Log.d("Device Id", androidId)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Socket(host, port).use { socket ->
                    val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream()), true)
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val formatted = sdf.format(Date())

                    // ✅ send JSON string (or any protocol you want)
                    val json = """ {  "android":1, "id":"${androidId}", "date":"${formatted}", "latitude": ${location.latitude}, "longitude": ${location.longitude}, "altitude": ${location.altitude} } """.trimIndent()

                    Log.d("JSONMessage", json)

                    writer.println(json)   // ✅ send line to server
                    writer.flush()

                    Log.d("LocationService", "📡 Sent location to TCP server")

                    // ✅ (Optional) Read response back from server
                    val response = socket.getInputStream().bufferedReader().readLine();

                    updateResponseMessage(response);

                }
            } catch (e: Exception) {
                Log.e("LocationService", "❌ TCP send failed: ${e.message}")
                val responseMessage = "❌ TCP send failed: ${e.message}";
                updateResponseMessage(responseMessage)
            }
        }

    }

    private fun updateResponseMessage(responseMessage: String) {
        val intent = Intent("SERVER_RESPONSE")

        intent.putExtra("response_message", responseMessage)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)


    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)

    }





}