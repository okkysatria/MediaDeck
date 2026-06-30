package com.gojektracker.app.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.mutableStateListOf
import com.gojektracker.app.data.*
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

enum class WorkflowState {
    IDLE,
    STARTED,
    PICKED_UP
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private var activeInstanceRef: java.lang.ref.WeakReference<MainViewModel>? = null

        var activeInstance: MainViewModel?
            get() = activeInstanceRef?.get()
            set(value) {
                activeInstanceRef = java.lang.ref.WeakReference(value)
            }
    }

    private val repository: OrderRepository
    val allOrders: StateFlow<List<OrderRecord>>

    // GPS Settings & States
    var trackingIntervalSec by mutableStateOf(5) // Default 5 seconds
    var useGpsSimulator by mutableStateOf(true)  // Enable smart simulator by default in sandbox context to prevent AppOps errors
    var smartHeatmapEnabled by mutableStateOf(true)
    var holidayAnalysisEnabled by mutableStateOf(true)
    var weatherAnalysisEnabled by mutableStateOf(true)
    var currentWeatherCondition by mutableStateOf("Cerah")
    
    // Theme state (true = dark/mode malam, false = light/mode siang)
    var isDarkMode by mutableStateOf(true) // Default to Gojek dark theme for style

    // Map Source states ("osm", "google_maps", "manual_pbf")
    var mapSource by mutableStateOf("osm")
    var importedPbfFileName by mutableStateOf<String?>(null)
    var importedPbfFileSize by mutableStateOf<String?>(null)
    var importedPbfUri by mutableStateOf<String?>(null) // Persisted URI of the imported map file

    // ONNX ML Model states loaded from external sources
    var importedOnnxFileName by mutableStateOf<String?>(null)
    var importedOnnxFileSize by mutableStateOf<String?>(null)
    var importedOnnxUri by mutableStateOf<String?>(null)
    var useOnnxModelPrediction by mutableStateOf(true)
    var onnxInputLayerName by mutableStateOf("lat_lng_time_features")
    var onnxOutputLayerName by mutableStateOf("predicted_hotspot_intensity_reg")
    var onnxModelTypeInfo by mutableStateOf("ResNet-DeepRegressor (ONNX v1.4)")

    // Navigation and screen state
    var currentScreen by mutableStateOf("rekam_order")

    // Real Location tracking states
    var lastKnownLatitude by mutableStateOf(-7.2754) // Default to Surabaya (ITS)
    var lastKnownLongitude by mutableStateOf(112.7938)
    var currentAddress by mutableStateOf("ITS Campus, Sukolilo, Surabaya")

    // Active order tracking variables
    var workflowState by mutableStateOf(WorkflowState.IDLE)
    
    var currentOrderDate by mutableStateOf("")
    var currentOrderDay by mutableStateOf("")
    var currentOrderStartTime by mutableStateOf("")
    var currentOrderPickupTime by mutableStateOf<String?>(null)
    var currentOrderStopTime by mutableStateOf<String?>(null)
    
    var startLat by mutableStateOf(0.0)
    var startLng by mutableStateOf(0.0)
    var startAddress by mutableStateOf("")
    
    var pickupLat by mutableStateOf<Double?>(null)
    var pickupLng by mutableStateOf<Double?>(null)
    var pickupAddress by mutableStateOf<String?>(null)
    
    var stopLat by mutableStateOf<Double?>(null)
    var stopLng by mutableStateOf<Double?>(null)
    var stopAddress by mutableStateOf<String?>(null)

    // Running values
    var activeTrackPoints = mutableStateListOf<TrackPoint>()
    var activeDistanceKm by mutableStateOf(0.0)
    var activeJarakKePickup by mutableStateOf(0.0)
    var activeJarakKeTujuan by mutableStateOf(0.0)
    var activeDurationMinutes by mutableStateOf(0L)
    var showSaveDialogDirectly by mutableStateOf(false)
    
    private var trackingStartTimeMillis = 0L
    private var timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    // Location engine
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(application)
    private var locationCallback: LocationCallback? = null
    private var nativeLocationListener: android.location.LocationListener? = null

    init {
        activeInstance = this
        val database = AppDatabase.getDatabase(application)
        repository = OrderRepository(database.orderDao())
        
        allOrders = repository.allOrders.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        loadLocalSettings()
        
        // Post first standby notification
        updateNotification()
    }

    private fun loadLocalSettings() {
        val prefs = getApplication<Application>().getSharedPreferences("driver_tracker_prefs", Context.MODE_PRIVATE)
        trackingIntervalSec = prefs.getInt("tracking_interval", 5)
        val defaultSim = isRunningOnEmulatorOrSandbox()
        useGpsSimulator = prefs.getBoolean("use_gps_simulator", defaultSim)
        smartHeatmapEnabled = prefs.getBoolean("smart_heatmap", true)
        holidayAnalysisEnabled = prefs.getBoolean("holiday_analysis", true)
        weatherAnalysisEnabled = prefs.getBoolean("weather_analysis", true)
        currentWeatherCondition = prefs.getString("weather_condition", "Cerah") ?: "Cerah"
        isDarkMode = prefs.getBoolean("is_dark_mode", true)
        mapSource = prefs.getString("map_source", "osm") ?: "osm"
        if (mapSource == "google_maps") {
            mapSource = "osm"
        }
        importedPbfFileName = prefs.getString("imported_pbf_file_name", null)
        importedPbfFileSize = prefs.getString("imported_pbf_file_size", null)
        importedPbfUri = prefs.getString("imported_pbf_uri", null)
        importedOnnxFileName = prefs.getString("imported_onnx_file_name", null)
        importedOnnxFileSize = prefs.getString("imported_onnx_file_size", null)
        importedOnnxUri = prefs.getString("imported_onnx_uri", null)
        useOnnxModelPrediction = prefs.getBoolean("use_onnx_model_prediction", true)
        onnxInputLayerName = prefs.getString("onnx_input_layer", "lat_lng_time_features") ?: "lat_lng_time_features"
        onnxOutputLayerName = prefs.getString("onnx_output_layer", "predicted_hotspot_intensity_reg") ?: "predicted_hotspot_intensity_reg"
        onnxModelTypeInfo = prefs.getString("onnx_model_type", "ResNet-DeepRegressor (ONNX v1.4)") ?: "ResNet-DeepRegressor (ONNX v1.4)"
    }

    fun saveLocalSettings() {
        val prefs = getApplication<Application>().getSharedPreferences("driver_tracker_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("tracking_interval", trackingIntervalSec)
            putBoolean("use_gps_simulator", useGpsSimulator)
            putBoolean("smart_heatmap", smartHeatmapEnabled)
            putBoolean("holiday_analysis", holidayAnalysisEnabled)
            putBoolean("weather_analysis", weatherAnalysisEnabled)
            putString("weather_condition", currentWeatherCondition)
            putBoolean("is_dark_mode", isDarkMode)
            putString("map_source", mapSource)
            putString("imported_pbf_file_name", importedPbfFileName)
            putString("imported_pbf_file_size", importedPbfFileSize)
            putString("imported_pbf_uri", importedPbfUri)
            putString("imported_onnx_file_name", importedOnnxFileName)
            putString("imported_onnx_file_size", importedOnnxFileSize)
            putString("imported_onnx_uri", importedOnnxUri)
            putBoolean("use_onnx_model_prediction", useOnnxModelPrediction)
            putString("onnx_input_layer", onnxInputLayerName)
            putString("onnx_output_layer", onnxOutputLayerName)
            putString("onnx_model_type", onnxModelTypeInfo)
            apply()
        }
        // Re-setup GPS with new interval
        setupLocationUpdates()
    }

    fun removeOnnxModel() {
        importedOnnxFileName = null
        importedOnnxFileSize = null
        importedOnnxUri = null
        useOnnxModelPrediction = true
        saveLocalSettings()
    }

    private fun disableEmulatorFallback() {
        driftRunnable?.let { driftHandler.removeCallbacks(it) }
        driftRunnable = null
    }

    @SuppressLint("MissingPermission")
    fun stopLocationUpdates() {
        locationCallback?.let {
            try {
                fusedLocationClient.removeLocationUpdates(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            locationCallback = null
        }
        nativeLocationListener?.let { listener ->
            try {
                val locationManager = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                locationManager?.removeUpdates(listener)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            nativeLocationListener = null
        }
        disableEmulatorFallback()
    }

    @SuppressLint("MissingPermission")
    fun setupLocationUpdates() {
        val hasFine = ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        // Stop any current listeners active
        stopLocationUpdates()

        if (useGpsSimulator) {
            // Attempt to fetch real last known location first to center the simulated drift around the real user
            val hasPermission = hasFine || hasCoarse
            if (hasPermission) {
                try {
                    fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
                        if (loc != null) {
                            updateCurrentLocation(loc)
                        }
                        startEmulatorDriftFallback()
                    }.addOnFailureListener {
                        startEmulatorDriftFallback()
                    }
                } catch (e: Exception) {
                    startEmulatorDriftFallback()
                }
            } else {
                startEmulatorDriftFallback()
            }
            return
        }

        if (!hasFine && !hasCoarse) {
            // Permissions are NOT granted yet, safely run simulator/emulator drift instead of requesting fused or native location updates
            startEmulatorDriftFallback()
            return
        }

        val locationManager = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager

        // Instantly query last known location to center on real driver position
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
                if (loc != null) {
                    disableEmulatorFallback()
                    updateCurrentLocation(loc)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            if (locationManager != null) {
                val lastGps = if (hasFine) locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER) else null
                val lastNet = if (hasCoarse) locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER) else null
                val bestLast = if (lastGps != null && lastNet != null) {
                    if (lastGps.time > lastNet.time) lastGps else lastNet
                } else lastGps ?: lastNet
                if (bestLast != null) {
                    disableEmulatorFallback()
                    updateCurrentLocation(bestLast)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Try FusedLocationProviderClient first as standard high accuracy provider
        var fusedStarted = false
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            trackingIntervalSec * 1000L
        ).apply {
            setMinUpdateIntervalMillis(trackingIntervalSec * 500L)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                disableEmulatorFallback()
                val loc = locationResult.lastLocation ?: return
                updateCurrentLocation(loc)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            fusedStarted = true
        } catch (e: Exception) {
            e.printStackTrace()
            locationCallback = null
        }

        // Setup native LocationManager only if Fused location requested updates did not start (failsafe native fallback)
        if (!fusedStarted) {
            try {
                if (locationManager != null) {
                    nativeLocationListener = object : android.location.LocationListener {
                        override fun onLocationChanged(loc: Location) {
                            disableEmulatorFallback()
                            updateCurrentLocation(loc)
                        }
                        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }

                    var nativeStarted = false
                    if (hasFine && locationManager.allProviders.contains(android.location.LocationManager.GPS_PROVIDER)) {
                        locationManager.requestLocationUpdates(
                            android.location.LocationManager.GPS_PROVIDER,
                            trackingIntervalSec * 1000L,
                            0f,
                            nativeLocationListener!!,
                            Looper.getMainLooper()
                        )
                        nativeStarted = true
                    }
                    if (hasCoarse && locationManager.allProviders.contains(android.location.LocationManager.NETWORK_PROVIDER)) {
                        locationManager.requestLocationUpdates(
                            android.location.LocationManager.NETWORK_PROVIDER,
                            trackingIntervalSec * 1000L,
                            0f,
                            nativeLocationListener!!,
                            Looper.getMainLooper()
                        )
                        nativeStarted = true
                    }
                    if (!nativeStarted) {
                        nativeLocationListener = null
                        startEmulatorDriftFallback()
                    }
                } else {
                    startEmulatorDriftFallback()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                nativeLocationListener = null
                startEmulatorDriftFallback()
            }
        }
    }

    private var driftHandler = Handler(Looper.getMainLooper())
    private var driftRunnable: Runnable? = null

    private fun startEmulatorDriftFallback() {
        driftRunnable?.let { driftHandler.removeCallbacks(it) }
        driftRunnable = object : Runnable {
            override fun run() {
                // If workflow is active, simulate a slow movement/drift
                if (workflowState != WorkflowState.IDLE) {
                    val driftLat = (Random().nextDouble() - 0.5) * 0.0003
                    val driftLng = (Random().nextDouble() - 0.5) * 0.0003
                    val newLat = lastKnownLatitude + driftLat
                    val newLng = lastKnownLongitude + driftLng
                    
                    val dummyLoc = Location("simulated").apply {
                        latitude = newLat
                        longitude = newLng
                        time = System.currentTimeMillis()
                    }
                    updateCurrentLocation(dummyLoc)
                }
                driftHandler.postDelayed(this, 4000)
            }
        }
        driftHandler.post(driftRunnable!!)
    }

    private fun updateCurrentLocation(location: Location) {
        lastKnownLatitude = location.latitude
        lastKnownLongitude = location.longitude

        // Update active coordinates and distance if tracking is on
        if (workflowState != WorkflowState.IDLE) {
            val lastPoint = if (activeTrackPoints.isNotEmpty()) activeTrackPoints.last() else null
            val newPt = TrackPoint(location.latitude, location.longitude, System.currentTimeMillis())
            activeTrackPoints.add(newPt)

            if (lastPoint != null) {
                val results = FloatArray(1)
                Location.distanceBetween(
                    lastPoint.lat, lastPoint.lng,
                    newPt.lat, newPt.lng,
                    results
                )
                val distKm = results[0] / 1000.0
                activeDistanceKm += distKm
                if (workflowState == WorkflowState.STARTED) {
                    activeJarakKePickup += distKm
                } else if (workflowState == WorkflowState.PICKED_UP) {
                    activeJarakKeTujuan += distKm
                }
            }
        }

        // Fetch location address in background
        viewModelScope.launch {
            val addr = getAddressFromCoords(location.latitude, location.longitude)
            currentAddress = addr
            if (workflowState != WorkflowState.IDLE) {
                updateNotification()
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun getAddressFromCoords(lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(getApplication(), Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val fullAddr = addr.getAddressLine(0)
                if (!fullAddr.isNullOrEmpty()) {
                    return@withContext fullAddr
                }
                val street = addr.thoroughfare ?: addr.subLocality ?: addr.locality
                if (!street.isNullOrEmpty()) {
                    return@withContext "$street, ${addr.subAdminArea ?: addr.adminArea ?: ""}"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Smart, deterministic Indonesian address lookups & procedural fallbacks for emulator/offline GPS
        val dLat = lat
        val dLng = lng

        // Closeness thresholds to key landmarks in Surabaya (MERR, ITS, Galaxy Mall, TP, etc)
        val itsDist = kotlin.math.hypot(dLat - (-7.2754), dLng - 112.7938)
        if (itsDist < 0.005) {
            return@withContext "Kampus ITS, Jl. Raya Sukolilo, Sukolilo, Surabaya"
        }

        if (dLat in -7.28..-7.26 && dLng in 112.78..112.80) {
            return@withContext "Jl. Raya MERR No. 88, Keputih, Sukolilo, Surabaya"
        }
        if (dLat in -7.30..-7.28 && dLng in 112.73..112.76) {
            return@withContext "Galaxy Mall 3, Jl. Dharmahusada Indah, Mulyorejo, Surabaya"
        }
        if (dLat in -7.26..-7.24 && dLng in 112.73..112.75) {
            return@withContext "Tunjungan Plaza 6, Jl. Basuki Rahmat, Genteng, Surabaya"
        }
        if (dLat in -7.31..-7.29 && dLng in 112.76..112.78) {
            return@withContext "Pakuwon City Mall, Kejawan Putih Tambak, Mulyorejo, Surabaya"
        }
        if (dLat in -7.27..-7.25 && dLng in 112.74..112.76) {
            return@withContext "Stasiun Surabaya Gubeng, Pacar Keling, Tambaksari, Surabaya"
        }
        if (dLat in -7.32..-7.30 && dLng in 112.72..112.74) {
            return@withContext "Raya Jemursari No. 102, Wonocolo, Surabaya"
        }
        if (dLat in -7.35..-7.33 && dLng in 112.71..112.73) {
            return@withContext "City of Tomorrow Mall, Jl. Ahmad Yani, Sidoarjo"
        }

        // Clean deterministic address generator based on coordinates to avoid any coordinate numbers
        val hash = (kotlin.math.abs(lat * 1000).toInt() + kotlin.math.abs(lng * 1000).toInt())
        val numberCode = (kotlin.math.abs(lat * 10000).toInt() % 190) + 1

        val streetNames = listOf(
            "Jl. Raya MERR Kulon", "Jl. Dharmahusada Indah Timur", "Jl. Basuki Rahmat Raya", "Jl. Raya Kalirungkut Tengah",
            "Jl. Jemursari Utama", "Jl. Ahmad Yani Indah", "Jl. Manyar Kertoarjo", "Jl. Kertajaya Raya",
            "Jl. HR Muhammad", "Jl. Mayjen Sungkono Selatan", "Jl. Pregolan Baru", "Jl. Raya Darmo",
            "Jl. Kusuma Bangsa", "Jl. Gubeng Pojok", "Jl. Ngagel Jaya Selatan", "Jl. Rungkut Industri Raya"
        )
        val districts = listOf(
            "Sukolilo, Surabaya", "Mulyorejo, Surabaya", "Genteng, Surabaya", "Rungkut, Surabaya",
            "Wonocolo, Surabaya", "Waru, Sidoarjo", "Manyar, Surabaya", "Mulyorejo, Surabaya",
            "Dukuh Pakis, Surabaya", "Sawahan, Surabaya", "Tegalsari, Surabaya", "Darmo, Surabaya",
            "Tambaksari, Surabaya", "Krembangan, Surabaya", "Ngagel, Surabaya", "Tenggilis Mejoyo, Surabaya"
        )

        val selectedStreet = streetNames[hash % streetNames.size]
        val selectedDistrict = districts[hash % districts.size]

        return@withContext "$selectedStreet No. $numberCode, $selectedDistrict"
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    // WORKFLOW COMMANDS
    fun startOrder() {
        val sDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val sDay = SimpleDateFormat("EEEE", Locale.forLanguageTag("id-ID")).format(Date()) // Localized day in Indonesian
        val sTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        currentOrderDate = sDate
        currentOrderDay = sDay
        currentOrderStartTime = sTime
        
        startLat = lastKnownLatitude
        startLng = lastKnownLongitude
        startAddress = currentAddress

        activeDistanceKm = 0.0
        activeJarakKePickup = 0.0
        activeJarakKeTujuan = 0.0
        activeDurationMinutes = 0L
        activeTrackPoints.clear()
        // Add start point
        activeTrackPoints.add(TrackPoint(startLat, startLng, System.currentTimeMillis()))

        trackingStartTimeMillis = System.currentTimeMillis()
        workflowState = WorkflowState.STARTED
        startTimer()
        updateNotification()
    }

    fun pickupOrder() {
        val sTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        currentOrderPickupTime = sTime
        pickupLat = lastKnownLatitude
        pickupLng = lastKnownLongitude
        pickupAddress = currentAddress
        
        // Ensure starting tracker records new points
        activeTrackPoints.add(TrackPoint(lastKnownLatitude, lastKnownLongitude, System.currentTimeMillis()))
        workflowState = WorkflowState.PICKED_UP
        updateNotification()
    }

    fun completeOrder() {
        val sTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        currentOrderStopTime = sTime
        stopLat = lastKnownLatitude
        stopLng = lastKnownLongitude
        stopAddress = currentAddress

        stopTimer()
        // Duration in minutes
        val elapsed = System.currentTimeMillis() - trackingStartTimeMillis
        activeDurationMinutes = (elapsed / 60000).coerceAtLeast(1)
        showSaveDialogDirectly = true
    }

    fun saveCompletedOrder(jenis: String, pendapatan: Double, catatan: String) {
        viewModelScope.launch {
            val record = OrderRecord(
                tanggal = currentOrderDate,
                hari = currentOrderDay,
                jamMulai = currentOrderStartTime,
                jamPickup = currentOrderPickupTime,
                jamSelesai = currentOrderStopTime,
                jenisOrder = jenis,
                pendapatan = pendapatan,
                durasi = activeDurationMinutes,
                jarakTempuh = activeDistanceKm,
                jarakKePickup = activeJarakKePickup,
                jarakKeTujuan = activeJarakKeTujuan,
                latitudeAwal = startLat,
                longitudeAwal = startLng,
                alamatAwal = startAddress,
                latitudePickup = pickupLat,
                longitudePickup = pickupLng,
                alamatPickup = pickupAddress ?: "",
                latitudeAkhir = stopLat,
                longitudeAkhir = stopLng,
                alamatAkhir = stopAddress ?: "",
                catatan = catatan,
                trackGps = activeTrackPoints.toList().toJsonString()
            )
            repository.insert(record)
            resetWorkflow()
            showSaveDialogDirectly = false
        }
    }

    fun cancelActiveOrder() {
        resetWorkflow()
    }

    private fun resetWorkflow() {
        stopTimer()
        workflowState = WorkflowState.IDLE
        activeTrackPoints.clear()
        activeDistanceKm = 0.0
        activeJarakKePickup = 0.0
        activeJarakKeTujuan = 0.0
        activeDurationMinutes = 0L
        currentOrderPickupTime = null
        currentOrderStopTime = null
        pickupLat = null
        pickupLng = null
        pickupAddress = null
        stopLat = null
        stopLng = null
        stopAddress = null
        showSaveDialogDirectly = false
        
        // Return to standby notification state
        updateNotification()
    }

    fun updateNotification() {
        com.gojektracker.app.ui.components.OrderNotificationManager.showTrackingNotification(
            getApplication(),
            workflowState,
            activeDistanceKm,
            activeDurationMinutes,
            currentAddress
        )
    }

    private fun startTimer() {
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        var lastMinute = -1L
        timerRunnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - trackingStartTimeMillis
                val currentMinutes = elapsed / 60000
                activeDurationMinutes = currentMinutes
                if (currentMinutes != lastMinute) {
                    lastMinute = currentMinutes
                    updateNotification()
                }
                timerHandler.postDelayed(this, 1000)
            }
        }
        timerHandler.post(timerRunnable!!)
    }

    private fun stopTimer() {
        timerRunnable?.let {
            timerHandler.removeCallbacks(it)
            timerRunnable = null
        }
    }

    // RESET ALL
    fun deleteOrder(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.deleteAll()
        }
    }

    // EXPORT & IMPORT UTILITIES
    fun exportDataAsJson(includeTrackGps: Boolean = true): String {
        val list = allOrders.value
        val array = JSONArray()
        for (o in list) {
            val obj = JSONObject().apply {
                put("id", o.id)
                put("tanggal", o.tanggal)
                put("hari", o.hari)
                put("jamMulai", o.jamMulai)
                put("jamPickup", o.jamPickup ?: "")
                put("jamSelesai", o.jamSelesai ?: "")
                put("jenisOrder", o.jenisOrder)
                put("pendapatan", o.pendapatan)
                put("durasi", o.durasi)
                put("jarakTempuh", o.jarakTempuh)
                put("jarakKePickup", o.jarakKePickup)
                put("jarakKeTujuan", o.jarakKeTujuan)
                put("latitudeAwal", o.latitudeAwal)
                put("longitudeAwal", o.longitudeAwal)
                put("alamatAwal", o.alamatAwal)
                put("latitudePickup", o.latitudePickup ?: 0.0)
                put("longitudePickup", o.longitudePickup ?: 0.0)
                put("alamatPickup", o.alamatPickup ?: "")
                put("latitudeAkhir", o.latitudeAkhir ?: 0.0)
                put("longitudeAkhir", o.longitudeAkhir ?: 0.0)
                put("alamatAkhir", o.alamatAkhir ?: "")
                put("catatan", o.catatan ?: "")
                if (includeTrackGps) {
                    val gpsArray = try {
                        if (!o.trackGps.isNullOrEmpty() && o.trackGps != "[]") {
                            JSONArray(o.trackGps)
                        } else {
                            JSONArray()
                        }
                    } catch (e: Exception) {
                        JSONArray()
                    }
                    put("trackGps", gpsArray)
                }
            }
            array.put(obj)
        }
        return array.toString(2)
    }

    fun exportDataAsCsv(): String {
        val list = allOrders.value
        val csv = java.lang.StringBuilder()
        csv.append("ID,Tanggal,Hari,JamMulai,JamPickup,JamSelesai,JenisOrder,Pendapatan,DurasiMenit,JarakKm,AlamatAwal,AlamatAkhir,Catatan\n")
        for (o in list) {
            csv.append("${o.id},\"${o.tanggal}\",\"${o.hari}\",\"${o.jamMulai}\",\"${o.jamPickup ?: ""}\",\"${o.jamSelesai ?: ""}\",\"${o.jenisOrder}\",${o.pendapatan},${o.durasi},${o.jarakTempuh.format(2)},\"${o.alamatAwal.replace("\"", "'")}\",\"${o.alamatAkhir?.replace("\"", "'") ?: ""}\",\"${o.catatan?.replace("\"", "'") ?: ""}\"\n")
        }
        return csv.toString()
    }

    fun exportMonthlyTaxReport(driverName: String, year: Int, monthNumber: Int): String {
        val list = allOrders.value
        val prefix = String.format(Locale.US, "%04d-%02d", year, monthNumber)
        val filtered = list.filter { it.tanggal.startsWith(prefix) }.sortedBy { it.tanggal }
        
        val months = listOf(
            "Januari", "Februari", "Maret", "April", "Mei", "Juni", 
            "Juli", "Agustus", "September", "Oktober", "November", "Desember"
        )
        val monthLabel = if (monthNumber in 1..12) months[monthNumber - 1] else "Bulan-$monthNumber"
        
        val rpt = java.lang.StringBuilder()
        rpt.append("=========================================================================\n")
        rpt.append("             LAPORAN REKAPITULASI PENDAPATAN BULANAN DRIVER              \n")
        rpt.append("=========================================================================\n\n")
        
        rpt.append("INFORMASI MITRA PENGENDARA (DRIVER):\n")
        rpt.append("-------------------------------------------------------------------------\n")
        rpt.append("Nama Lengkap Pengendara  : ${driverName.ifBlank { "DRIVER UTAMA" }.uppercase()}\n")
        rpt.append("Periode Laporan Bulanan  : $monthLabel $year\n")
        rpt.append("Tanggal Dokumen Dibuat   : ${SimpleDateFormat("dd MMMM yyyy HH:mm", Locale("id", "ID")).format(Date())}\n")
        rpt.append("Sifat Dokumen            : Bukti Pencatatan Pendapatan Mandiri (In-App)\n")
        rpt.append("-------------------------------------------------------------------------\n\n")
        
        val totalTrips = filtered.size
        val totalGross = filtered.sumOf { it.pendapatan }
        val totalDistance = filtered.sumOf { it.jarakTempuh }
        val totalDuration = filtered.sumOf { it.durasi }
        
        val distinctDays = filtered.map { it.tanggal }.distinct().size
        
        // Estimasi biaya operasional realistik bensin/BBM & makan sekitar 25% dari bruto
        val estOperasional = totalGross * 0.25
        val netIncome = totalGross - estOperasional
        
        rpt.append("RINGKASAN KEUANGAN DAN ESTIMASI BERSIH:\n")
        rpt.append("-------------------------------------------------------------------------\n")
        rpt.append("Jumlah Hari Aktif On-Bid : $distinctDays hari\n")
        rpt.append("Total Order Selesai      : $totalTrips order\n")
        rpt.append("Total Jarak Tempuh Rute  : ${String.format(Locale.US, "%.2f", totalDistance)} km\n")
        rpt.append("Total Durasi Mengemudi   : $totalDuration menit\n")
        rpt.append("-------------------------------------------------------------------------\n")
        rpt.append("1. TOTAL PENDAPATAN KOTOR (Bruto)   : Rp ${String.format(Locale.US, "%,.0f", totalGross)}\n")
        rpt.append("2. Estimasi Pemakaian BBM & Makan    : Rp ${String.format(Locale.US, "%,.0f", estOperasional)} (Potongan 25% Mandiri)\n")
        rpt.append("3. ESTIMASI TOTAL PENDAPATAN BERSIH : Rp ${String.format(Locale.US, "%,.0f", netIncome)}\n")
        rpt.append("-------------------------------------------------------------------------\n")
        rpt.append("Rata-rata Pendapatan / Order        : Rp ${if (totalTrips > 0) String.format(Locale.US, "%,.0f", totalGross / totalTrips) else "0"}\n")
        rpt.append("Rata-rata Pendapatan / Hari Aktif   : Rp ${if (distinctDays > 0) String.format(Locale.US, "%,.0f", totalGross / distinctDays) else "0"}\n")
        rpt.append("=========================================================================\n\n")
        
        rpt.append("IKHTISAR REKAPITULASI AKTIVITAS HARIAN:\n")
        rpt.append("-------------------------------------------------------------------------\n")
        rpt.append("Tanggal     | Order | Jarak (km) | Pendapatan Bruto (Rp)\n")
        rpt.append("-------------------------------------------------------------------------\n")
        
        val dayGrouped = filtered.groupBy { it.tanggal }
        for ((date, orders) in dayGrouped) {
            val dSum = orders.sumOf { it.pendapatan }
            val dDist = orders.sumOf { it.jarakTempuh }
            rpt.append(String.format(Locale.US, "%-11s | %5d | %10.1f | Rp %s\n", date, orders.size, dDist, String.format(Locale.US, "%,.0f", dSum)))
        }
        if (dayGrouped.isEmpty()) {
            rpt.append("(Tidak ada catatan perjalanan dalam periode terpilih)\n")
        }
        rpt.append("-------------------------------------------------------------------------\n\n")
        
        rpt.append("RINCIAN DETIL PERJALANAN SELESAI:\n")
        rpt.append("---------------------------------------------------------------------------------------------\n")
        rpt.append("Id Order | Tanggal     | Jam   | Layanan     | Jarak (km) | Pendapatan Bruto | Catatan / Notes\n")
        rpt.append("---------------------------------------------------------------------------------------------\n")
        for (o in filtered) {
            rpt.append(
                String.format(
                    Locale.US,
                    "%-8d | %-11s | %-5s | %-11s | %10s | Rp %-12s | %s\n",
                    o.id,
                    o.tanggal,
                    o.jamMulai,
                    o.jenisOrder.take(11),
                    String.format(Locale.US, "%.1f", o.jarakTempuh),
                    String.format(Locale.US, "%,.0f", o.pendapatan),
                    o.catatan?.replace("\n", " ")?.take(20) ?: "-"
                )
            )
        }
        if (filtered.isEmpty()) {
            rpt.append("(Tidak ada riwayat transaksi order dalam periode ini)\n")
        }
        rpt.append("---------------------------------------------------------------------------------------------\n")
        rpt.append("Data di atas dicatat mandiri melalui Aplikasi Driver GPS Tracker.\n")
        rpt.append("=============================================================================================\n")
        
        return rpt.toString()
    }

    fun importDataFromJson(
        jsonStr: String,
        appendMode: Boolean,
        skipDuplicates: Boolean,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val array = JSONArray(jsonStr)
                var count = 0
                var skippedCount = 0
                val existingList = allOrders.value

                if (!appendMode) {
                    repository.deleteAll()
                }

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val tanggal = obj.optString("tanggal", "2026-06-19")
                    val jamMulai = obj.optString("jamMulai", "12:00")
                    val jenisOrder = obj.optString("jenisOrder", "Penumpang")
                    val pendapatan = obj.optDouble("pendapatan", 0.0)

                    if (appendMode && skipDuplicates) {
                        val isDuplicate = existingList.any { existing ->
                            existing.tanggal == tanggal &&
                            existing.jamMulai == jamMulai &&
                            existing.jenisOrder == jenisOrder &&
                            kotlin.math.abs(existing.pendapatan - pendapatan) < 0.01
                        }
                        if (isDuplicate) {
                            skippedCount++
                            continue
                        }
                    }

                    val jTempuh = obj.optDouble("jarakTempuh", 0.0)
                    val jKePickup = if (obj.has("jarakKePickup")) obj.optDouble("jarakKePickup") else (jTempuh * 0.3)
                    val jKeTujuan = if (obj.has("jarakKeTujuan")) obj.optDouble("jarakKeTujuan") else (jTempuh * 0.7)

                    val record = OrderRecord(
                        tanggal = tanggal,
                        hari = obj.optString("hari", "Jumat"),
                        jamMulai = jamMulai,
                        jamPickup = if (obj.has("jamPickup") && obj.getString("jamPickup").isNotEmpty()) obj.getString("jamPickup") else null,
                        jamSelesai = if (obj.has("jamSelesai") && obj.getString("jamSelesai").isNotEmpty()) obj.getString("jamSelesai") else null,
                        jenisOrder = jenisOrder,
                        pendapatan = pendapatan,
                        durasi = obj.optLong("durasi", 0),
                        jarakTempuh = jTempuh,
                        jarakKePickup = jKePickup,
                        jarakKeTujuan = jKeTujuan,
                        latitudeAwal = obj.optDouble("latitudeAwal", 0.0),
                        longitudeAwal = obj.optDouble("longitudeAwal", 0.0),
                        alamatAwal = obj.optString("alamatAwal", "Lokasi"),
                        latitudePickup = if (obj.has("latitudePickup")) obj.optDouble("latitudePickup") else null,
                        longitudePickup = if (obj.has("longitudePickup")) obj.optDouble("longitudePickup") else null,
                        alamatPickup = if (obj.has("alamatPickup")) obj.optString("alamatPickup") else null,
                        latitudeAkhir = if (obj.has("latitudeAkhir")) obj.optDouble("latitudeAkhir") else null,
                        longitudeAkhir = if (obj.has("longitudeAkhir")) obj.optDouble("longitudeAkhir") else null,
                        alamatAkhir = if (obj.has("alamatAkhir")) obj.optString("alamatAkhir") else null,
                        catatan = if (obj.has("catatan")) obj.optString("catatan") else null,
                        trackGps = if (obj.has("trackGps")) {
                            val trackVal = obj.get("trackGps")
                            if (trackVal is JSONArray) {
                                trackVal.toString()
                            } else {
                                trackVal?.toString() ?: "[]"
                            }
                        } else {
                            "[]"
                        }
                    )
                    repository.insert(record)
                    count++
                }

                val statusMsg = if (appendMode) {
                    if (skippedCount > 0) {
                        "Berhasil menggabungkan $count order (melewati $skippedCount data duplikat)!"
                    } else {
                        "Berhasil menggabungkan $count order baru!"
                    }
                } else {
                    "Berhasil mengimpor $count order (data lama ditumpuk/dihapus)!"
                }
                onSuccess(statusMsg)
            } catch (e: Exception) {
                e.printStackTrace()
                onError("Format berkas JSON tidak sesuai atau rusak.")
            }
        }
    }

    private fun generateSimulatedTrack(
        startLat: Double,
        startLng: Double,
        pickLat: Double?,
        pickLng: Double?,
        endLat: Double,
        endLng: Double
    ): String {
        val points = mutableListOf<TrackPoint>()
        val startTime = System.currentTimeMillis() - 3600 * 1000
        
        // Add start
        points.add(TrackPoint(startLat, startLng, startTime))
        
        val midpoints = mutableListOf<Pair<Double, Double>>()
        if (pickLat != null && pickLng != null && pickLat != 0.0 && pickLng != 0.0) {
            midpoints.add(Pair(pickLat, pickLng))
        }
        midpoints.add(Pair(endLat, endLng))
        
        var currentLat = startLat
        var currentLng = startLng
        var currentTime = startTime
        val r = Random()
        
        for (target in midpoints) {
            val targetLat = target.first
            val targetLng = target.second
            
            val steps = 12
            for (i in 1..steps) {
                val fraction = i.toDouble() / steps
                var nextLat = currentLat + (targetLat - currentLat) * fraction
                var nextLng = currentLng + (targetLng - currentLng) * fraction
                
                // Add minor noise to simulate turns/roads
                if (i < steps) {
                    nextLat += (r.nextDouble() - 0.5) * 0.0006
                    nextLng += (r.nextDouble() - 0.5) * 0.0006
                }
                
                currentTime += 20 * 1000 // 20 seconds interval
                points.add(TrackPoint(nextLat, nextLng, currentTime))
            }
            currentLat = targetLat
            currentLng = targetLng
        }
        return points.toJsonString()
    }

    // SEED INITIAL MOCK DATA FOR THE DEMO SO FIRST-TIMERS CAN INSTANTLY VIEW RADAR AND DASHBOARD STATS
    fun seedSampleData() {
        viewModelScope.launch {
            val list = mutableListOf<OrderRecord>()
            val c = Calendar.getInstance()
            val formatStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dayFormat = SimpleDateFormat("EEEE", Locale.forLanguageTag("id-ID"))
            
            // Generate historical orders across the last 15 days
            val types = listOf("Penumpang", "Food", "Paket")
            val locations = listOf(
                Pair(-7.2754, 112.7938) to "ITS Campus",
                Pair(-7.2622, 112.7483) to "Tunjungan Plaza",
                Pair(-7.3201, 112.7678) to "Rungkut Industri",
                Pair(-7.2512, 112.7845) to "Merr Sukolilo",
                Pair(-7.2655, 112.7812) to "Galaxy Mall"
            )

            for (i in 1..25) {
                c.add(Calendar.HOUR_OF_DAY, -18) // scatter backward in time
                val curSecDate = c.time
                val sDate = formatStr.format(curSecDate)
                val sDay = dayFormat.format(curSecDate)
                val type = types[Random().nextInt(types.size)]
                val price = (Random().nextInt(4) + 1) * 15000.0 + 9000.0
                val duration = Random().nextInt(30) + 12L
                val dist = (Random().nextDouble() * 8.0) + 2.0
                
                val locStart = locations[Random().nextInt(locations.size)]
                val locEnd = locations[Random().nextInt(locations.size)]

                val pLat = locStart.first.first + 0.001
                val pLng = locStart.first.second + 0.001

                list.add(
                    OrderRecord(
                        tanggal = sDate,
                        hari = sDay,
                        jamMulai = "1${Random().nextInt(8)}:30",
                        jamPickup = "1${Random().nextInt(8)}:40",
                        jamSelesai = "1${Random().nextInt(8)}:55",
                        jenisOrder = type,
                        pendapatan = price,
                        durasi = duration,
                        jarakTempuh = dist,
                        jarakKePickup = dist * 0.25,
                        jarakKeTujuan = dist * 0.75,
                        latitudeAwal = locStart.first.first,
                        longitudeAwal = locStart.first.second,
                        alamatAwal = locStart.second,
                        latitudePickup = pLat,
                        longitudePickup = pLng,
                        alamatPickup = locStart.second + " Gate",
                        latitudeAkhir = locEnd.first.first,
                        longitudeAkhir = locEnd.first.second,
                        alamatAkhir = locEnd.second + " Lobby",
                        catatan = "Sukses lancar jaya",
                        trackGps = generateSimulatedTrack(
                            startLat = locStart.first.first,
                            startLng = locStart.first.second,
                            pickLat = pLat,
                            pickLng = pLng,
                            endLat = locEnd.first.first,
                            endLng = locEnd.first.second
                        )
                    )
                )
            }
            for (order in list) {
                repository.insert(order)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
        stopLocationUpdates()
        driftRunnable?.let { driftHandler.removeCallbacks(it) }
    }

    private fun isRunningOnEmulatorOrSandbox(): Boolean {
        val fingerprint = android.os.Build.FINGERPRINT
        val model = android.os.Build.MODEL
        val hardware = android.os.Build.HARDWARE
        return fingerprint.contains("generic") || 
               fingerprint.contains("vbox") || 
               model.contains("google_sdk") || 
               model.contains("Emulator") || 
               model.contains("Android SDK built for x86") ||
               hardware.contains("goldfish") || 
               hardware.contains("ranchu") || 
               hardware.contains("vbox86")
    }
}
