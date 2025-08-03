package com.example.fortrace

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [FirstFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class FirstFragment : Fragment() {

    private lateinit var deviceIdText: TextView
    private lateinit var locationText: TextView
    private lateinit var messageText: TextView
    private lateinit var responseMessageText: TextView
    private lateinit var host: EditText
    private lateinit var port: EditText
    private lateinit var toggleBtn: Button

    private var isTracking = false

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            when (intent?.action) {
                "LOCATION_UPDATE" -> {
                    val lat = intent.getDoubleExtra("latitude", 0.0)
                    val lon = intent.getDoubleExtra("longitude", 0.0)
                    val alt = intent.getDoubleExtra("altitude", 0.0)
                    locationText.text = "Lat: $lat\nLon: $lon\n Al: $alt"
                }

                "SERVER_RESPONSE" -> {
                    val status = intent.getStringExtra("response_message")
                    responseMessageText.text = "Service: $status"
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate your layout
        return inflater.inflate(R.layout.fragment_first, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ Find views
        deviceIdText = view.findViewById(R.id.deviceId)
        locationText = view.findViewById(R.id.location)
        messageText = view.findViewById(R.id.message)
        responseMessageText = view.findViewById(R.id.responseMessage)
        toggleBtn = view.findViewById(R.id.button2)

        // ✅ Show Device ID
        val deviceId = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
        deviceIdText.text = "📱 Device ID:\n$deviceId"
        Log.d("FORTRACE", "Device ID: $deviceId")

        host = view.findViewById(R.id.host)
        port = view.findViewById(R.id.port)

        toggleBtn.setOnClickListener {
            if  (!isTracking) {
                val host = host.text.toString().trim()
                val port = port.text.toString().trim()
                if (host.isEmpty() || port.isEmpty()) {
                    Toast.makeText(requireContext(), "⚠️ Please enter a URL", Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener
                }

                val serviceIntent = Intent(requireContext(), LocationService::class.java)
                serviceIntent.putExtra("host", host)   // ✅ send to service
                serviceIntent.putExtra("port", port)   // ✅ send to service
                requireContext().startService(serviceIntent)

                Toast.makeText(
                    requireContext(),
                    "🚀 Service Started. Sending to:\n$host:$port",
                    Toast.LENGTH_SHORT
                ).show()

                isTracking = true
                toggleBtn.text = "⏹ Stop Tracking"
                messageText.text = "📡 Tracking location..."
            } else {
                // ✅ Stop service
                val serviceIntent = Intent(requireContext(), LocationService::class.java)
                requireContext().stopService(serviceIntent)

                isTracking = false
                toggleBtn.text = "▶ Start Tracking"
                messageText.text = "✅ Tracking stopped."
                Toast.makeText(requireContext(), "🛑 Service Stopped", Toast.LENGTH_SHORT).show()

            }
        }

    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction("LOCATION_UPDATE")
            addAction("SERVER_RESPONSE")
        }
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(locationReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(requireContext())
            .unregisterReceiver(locationReceiver)
    }

}