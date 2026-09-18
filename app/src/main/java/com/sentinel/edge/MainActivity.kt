package com.sentinel.edge

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.sentinel.edge.ui.theme.SentinelTheme
import kotlin.math.sqrt
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Current telemetry values
    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null
    private var currentAccuracy: Float? = null
    private var currentBatteryPercentage: Int = -1
    private var currentMotion: String = "UNKNOWN"
    private var currentNetworkStatus: String = "UNKNOWN"

    private lateinit var awsIotManager: AwsIotManager

    // Sentinel device identity
    private val deviceId = "SENTINEL-001"

    private val awsIotEndpoint =
        "a1hwvjs89dquf9-ats.iot.us-east-1.amazonaws.com"

    // Heartbeat handler
    private val heartbeatHandler =
        Handler(Looper.getMainLooper())

    private val heartbeatRunnable = object : Runnable {

        override fun run() {

            // Update heartbeat
            heartbeatText =
                "Heartbeat: ACTIVE\n" +
                        "Last signal: ${System.currentTimeMillis()}"

            // Build local telemetry packet
            telemetryText =
                """
                {
                  "deviceId": "$deviceId",
                  "timestamp": ${System.currentTimeMillis()},
                  "latitude": ${currentLatitude ?: "null"},
                  "longitude": ${currentLongitude ?: "null"},
                  "accuracy": ${currentAccuracy ?: "null"},
                  "motion": "$currentMotion",
                  "battery": $currentBatteryPercentage,
                  "networkStatus": "$currentNetworkStatus",
                  "deviceStatus": "ONLINE"
                }
                """.trimIndent()

            // Run again after 5 seconds
            heartbeatHandler.postDelayed(
                this,
                5000L
            )
        }
    }

    // Automatic AWS telemetry handler
    private val telemetryHandler =
        Handler(Looper.getMainLooper())

    private val telemetryRunnable = object : Runnable {

        override fun run() {

            val payload =
                """
                {
                  "deviceId": "$deviceId",
                  "timestamp": ${System.currentTimeMillis()},
                  "latitude": ${currentLatitude ?: "null"},
                  "longitude": ${currentLongitude ?: "null"},
                  "accuracy": ${currentAccuracy ?: "null"},
                  "motion": "$currentMotion",
                  "battery": $currentBatteryPercentage,
                  "networkStatus": "$currentNetworkStatus",
                  "deviceStatus": "ONLINE"
                }
                """.trimIndent()

            // Send normal telemetry to AWS IoT
            awsIotManager.publishTelemetry(
                payload
            )

            // Update AWS IoT Device Shadow
            awsIotManager.updateDeviceShadow(
                latitude = currentLatitude,
                longitude = currentLongitude,
                battery = currentBatteryPercentage,
                motion = currentMotion,
                networkStatus = currentNetworkStatus
            )

            // Run again after 5 seconds
            telemetryHandler.postDelayed(
                this,
                5000L
            )
        }
    }

    // Battery monitoring
    private val batteryReceiver =
        object : android.content.BroadcastReceiver() {

            override fun onReceive(
                context: android.content.Context?,
                intent: Intent?
            ) {

                val level =
                    intent?.getIntExtra(
                        "level",
                        -1
                    ) ?: -1

                val scale =
                    intent?.getIntExtra(
                        "scale",
                        100
                    ) ?: 100

                if (level >= 0) {

                    val batteryPercentage =
                        (level * 100) / scale

                    currentBatteryPercentage =
                        batteryPercentage

                    batteryText =
                        "Battery: $batteryPercentage%"
                }
            }
        }

    // GPS update configuration
    private val locationRequest =
        LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L
        ).apply {

            setMinUpdateIntervalMillis(
                2000L
            )

        }.build()

    // GPS callback
    private val locationCallback =
        object : LocationCallback() {

            override fun onLocationResult(
                locationResult: LocationResult
            ) {

                val location: Location? =
                    locationResult.lastLocation

                if (location != null) {

                    // Store telemetry values
                    currentLatitude =
                        location.latitude

                    currentLongitude =
                        location.longitude

                    currentAccuracy =
                        location.accuracy

                    // Display GPS information
                    locationText =
                        "Latitude: ${location.latitude}\n" +
                                "Longitude: ${location.longitude}\n" +
                                "Accuracy: ${location.accuracy} m"
                }
            }
        }

    // Location permission request
    private val locationPermissionRequest =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val locationGranted =
                permissions[
                    Manifest.permission.ACCESS_FINE_LOCATION
                ] == true ||
                        permissions[
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ] == true

            if (locationGranted) {
                startLocationUpdates()
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        awsIotManager =
            AwsIotManager(this)

        // Start heartbeat
        heartbeatHandler.post(
            heartbeatRunnable
        )

        // Check network
        checkNetworkStatus()

        // Register battery receiver
        registerReceiver(
            batteryReceiver,
            android.content.IntentFilter(
                Intent.ACTION_BATTERY_CHANGED
            )
        )

        // Initialize GPS
        fusedLocationClient =
            LocationServices
                .getFusedLocationProviderClient(this)

        // Initialize accelerometer
        sensorManager =
            getSystemService(
                SENSOR_SERVICE
            ) as SensorManager

        accelerometer =
            sensorManager.getDefaultSensor(
                Sensor.TYPE_ACCELEROMETER
            )

        if (accelerometer != null) {

            sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }

        // Start Compose UI
        setContent {

            SentinelTheme {

                SentinelScreen(

                    onStartLocation = {
                        requestLocationPermission()
                    },

                    onConnectAws = {

                        awsIotManager.connect()

                        telemetryHandler.removeCallbacks(
                            telemetryRunnable
                        )

                        telemetryHandler.postDelayed(
                            telemetryRunnable,
                            5000L
                        )
                    },

                    onStopTelemetry = {

                        telemetryHandler.removeCallbacks(
                            telemetryRunnable
                        )

                        awsStatusText =
                            "AWS IoT: CONNECTED\nTelemetry: STOPPED"
                    },

                    onSendTelemetry = {

                        val payload =
                            """
                            {
                              "deviceId": "$deviceId",
                              "timestamp": ${System.currentTimeMillis()},
                              "latitude": ${currentLatitude ?: "null"},
                              "longitude": ${currentLongitude ?: "null"},
                              "accuracy": ${currentAccuracy ?: "null"},
                              "motion": "$currentMotion",
                              "battery": $currentBatteryPercentage,
                              "networkStatus": "$currentNetworkStatus",
                              "deviceStatus": "ONLINE"
                            }
                            """.trimIndent()

                        awsIotManager.publishTelemetry(
                            payload
                        )
                    }
                )
            }
        }
    }

    // Request GPS permission
    private fun requestLocationPermission() {

        val fineGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {

            startLocationUpdates()

        } else {

            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Start continuous GPS updates
    private fun startLocationUpdates() {

        try {

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            locationText =
                "GPS monitoring: ACTIVE"

        } catch (e: SecurityException) {

            locationText =
                "Location permission required"
        }
    }

    // Check network status
    private fun checkNetworkStatus() {

        val connectivityManager =
            getSystemService(
                ConnectivityManager::class.java
            )

        val network =
            connectivityManager.activeNetwork

        val capabilities =
            connectivityManager.getNetworkCapabilities(
                network
            )

        val isOnline =
            capabilities?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            ) == true

        currentNetworkStatus =
            if (isOnline) {
                "ONLINE"
            } else {
                "OFFLINE"
            }

        networkText =
            "Network: $currentNetworkStatus"
    }

    // Accelerometer monitoring
    override fun onSensorChanged(
        event: SensorEvent?
    ) {

        if (
            event?.sensor?.type ==
            Sensor.TYPE_ACCELEROMETER
        ) {

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val acceleration =
                sqrt(
                    x * x +
                            y * y +
                            z * z
                )

            currentMotion =
                if (acceleration > 12f) {
                    "MOVING"
                } else {
                    "STATIONARY"
                }

            motionText =
                "Motion: $currentMotion"
        }
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int
    ) {
        // Sensor accuracy changes are not needed for this test.
    }

    override fun onDestroy() {

        super.onDestroy()

        // Stop GPS updates
        fusedLocationClient.removeLocationUpdates(
            locationCallback
        )

        // Stop accelerometer
        sensorManager.unregisterListener(
            this
        )

        // Unregister battery receiver
        unregisterReceiver(
            batteryReceiver
        )

        // Stop heartbeat
        heartbeatHandler.removeCallbacks(
            heartbeatRunnable
        )

        // Stop automatic AWS telemetry
        telemetryHandler.removeCallbacks(
            telemetryRunnable
        )

        // Disconnect AWS IoT
        awsIotManager.disconnect()
    }

    companion object {

        var locationText by mutableStateOf(
            "GPS monitoring: INACTIVE"
        )

        var motionText by mutableStateOf(
            "Motion: UNKNOWN"
        )

        var batteryText by mutableStateOf(
            "Battery: UNKNOWN"
        )

        var networkText by mutableStateOf(
            "Network: UNKNOWN"
        )

        var heartbeatText by mutableStateOf(
            "Heartbeat: NOT STARTED"
        )

        var telemetryText by mutableStateOf(
            "Telemetry: NOT READY"
        )

        var awsStatusText by mutableStateOf(
            "AWS IoT: NOT CONNECTED"
        )
    }
}

@Composable
fun SentinelScreen(
    onStartLocation: () -> Unit,
    onConnectAws: () -> Unit,
    onSendTelemetry: () -> Unit,
    onStopTelemetry: () -> Unit
) {

    val location by remember {
        derivedStateOf {
            MainActivity.locationText
        }
    }

    val motion by remember {
        derivedStateOf {
            MainActivity.motionText
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),

        horizontalAlignment =
            Alignment.CenterHorizontally,

        verticalArrangement =
            Arrangement.Center
    ) {

        Text(
            text = "SENTINEL",
            fontSize = 28.sp
        )

        Text(
            text = "Secure Android Edge Device",
            fontSize = 15.sp
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text = "GPS Status",
            fontSize = 17.sp
        )

        Text(
            text = location,
            fontSize = 13.sp
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = "Motion",
            fontSize = 17.sp
        )

        Text(
            text = motion,
            fontSize = 15.sp
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = "Battery",
            fontSize = 17.sp
        )

        Text(
            text = MainActivity.batteryText,
            fontSize = 15.sp
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = "Network",
            fontSize = 17.sp
        )

        Text(
            text = MainActivity.networkText,
            fontSize = 15.sp
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = "Heartbeat",
            fontSize = 17.sp
        )

        Text(
            text = MainActivity.heartbeatText,
            fontSize = 14.sp
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = "AWS IoT",
            fontSize = 17.sp
        )

        Text(
            text = MainActivity.awsStatusText,
            fontSize = 14.sp
        )

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Button(
            onClick = onStartLocation
        ) {

            Text(
                text = "Start GPS Monitoring",
                fontSize = 13.sp
            )
        }

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        Button(
            onClick = onConnectAws
        ) {

            Text(
                text = "Connect to AWS IoT",
                fontSize = 13.sp
            )
        }

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        Button(
            onClick = onSendTelemetry
        ) {

            Text(
                text = "Send Test Telemetry",
                fontSize = 13.sp
            )
        }

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        Button(
            onClick = onStopTelemetry
        ) {

            Text(
                text = "Stop Automatic Telemetry",
                fontSize = 13.sp
            )
        }
    }
}
