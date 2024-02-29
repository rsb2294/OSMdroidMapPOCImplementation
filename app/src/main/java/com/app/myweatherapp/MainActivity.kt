package com.app.myweatherapp

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Rect
import android.location.Address
import android.location.Geocoder
import android.location.GpsStatus
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.app.myweatherapp.databinding.ActivityMainBinding
import com.app.myweatherapp.model.weatherData.WeatherData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration.getInstance
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import retrofit2.await
import java.util.Locale


class MainActivity : AppCompatActivity(), MapListener, GpsStatus.Listener {
    private lateinit var binding: ActivityMainBinding
    private val REQUEST_PERMISSIONS_REQUEST_CODE = 1
    private var openWeatherMapService: OpenWeatherMapService? = null
    private val apiKey = "30ed732483377ef562163c418a24dbfb"

    lateinit var mMap: MapView
    lateinit var controller: IMapController
    lateinit var mMyLocationOverlay: MyLocationNewOverlay

    private var mHandler: Handler? = null
    private var marker: Marker? = null
    private var mIsScrolling = false
    val token: String? = null

    private lateinit var geocoder: Geocoder

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 123
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        getInstance().load(
            this,
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        )
        setContentView(binding.root)
        mMap = binding.map
        openWeatherMapService = ApiClient.createService(
            "https://api.openweathermap.org/data/2.5/", token,
            OpenWeatherMapService::class.java
        )
        if (allPermissionsGranted()) {
            initializeMap()
        } else {
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }

    }

    private fun allPermissionsGranted() =
        REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(
                applicationContext, it
            ) == PackageManager.PERMISSION_GRANTED
        }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                initializeMap()
            } else {
                Toast.makeText(
                    applicationContext,
                    "Permissions denied by the user.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Initialize the map
    private fun initializeMap() {
        mMap.setTileSource(TileSourceFactory.MAPNIK)
        mMap.mapCenter
        mMap.setMultiTouchControls(true)
        mMap.getLocalVisibleRect(Rect())
        controller = mMap.controller
        mHandler = Handler()
        mMyLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mMap)
        setCurrentLocationMap()
        controller.setZoom(6.0)

        mMap.overlays.add(mMyLocationOverlay)
        mMap.addMapListener(this)
    }

    private fun loadWeatherData(location: String?, tempUnit: String) {
        coroutineScope.launch {
            try {
                val response = openWeatherMapService!!.getCurrentWeatherData(location, tempUnit, apiKey).await()
                if (response.cod == 200) {
                    updateUI(response) ?: showErrorToast()
                } else {
                    showErrorToast()
                }
            } catch (e: Exception) {
                showErrorToast()
            }
        }
    }

    private fun updateUI(weatherData: WeatherData) {
        Log.e("Data: ", "" + weatherData.name)
        binding.cityNameTextView.text = weatherData.name
        val temp = weatherData.main.temp.toString() + " °C"
        binding.temperatureTextView.text = temp
        binding.weatherDescriptionTextView.text = weatherData.weather[0].description
    }

    private fun showErrorToast() {
        Toast.makeText(this@MainActivity, "Failed to load weather data", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        var prefs: SharedPreferences =
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        getInstance().load(
            this,
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        )
        binding.map.onResume()
    }

    override fun onPause() {
        super.onPause()
        var prefs: SharedPreferences =
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        getInstance().load(
            this,
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        )
        binding.map.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }

    override fun onScroll(event: ScrollEvent?): Boolean {
        mIsScrolling = true
        val lat = event?.source?.mapCenter?.latitude
        val lon = event?.source?.mapCenter?.longitude
        mMyLocationOverlay.disableMyLocation()
        mHandler!!.removeCallbacksAndMessages(null)
        mHandler!!.postDelayed({
            if (mIsScrolling) {
                mIsScrolling = false
                marker?.position = GeoPoint(lat!!, lon!!)
                marker?.title = getCityName(this, lat, lon)
                mMap.invalidate()
                loadWeatherData(marker?.title, "metric")
            }
        }, 200)

        return true
    }

    override fun onZoom(event: ZoomEvent?): Boolean {
//        Log.e("TAG", "onZoom zoom level: ${event?.zoomLevel}   source:  ${event?.source}")
        return false
    }

    override fun onGpsStatusChanged(event: Int) {
//        Log.e("TAG", "onGpsStatusChanged : ${event}")
    }

    private fun getCityName(context: Context, lat: Double, long: Double): String {
        var cityName: String = ""
        geocoder = Geocoder(this, Locale.getDefault())
        val address = geocoder.getFromLocation(lat, long, 3)
        if (!address.isNullOrEmpty()) {
            if (address[0].locality != null) {
                cityName = address[0].locality
            }
            if (cityName.isNullOrEmpty()) {
                if (address[0].adminArea != null) {
                    cityName = address[0].adminArea
                }
                if (cityName.isNullOrEmpty()) {
                    if (address[0].subAdminArea != null) {
                        cityName = address[0].subAdminArea
                    }
                }
            }
        }
        return cityName ?: ""
    }

    private fun clearMarkers(mapView: MapView) {
        val overlayManager = mapView.overlays
        val markersToRemove = mutableListOf<Marker>()
        for (overlay in overlayManager) {
            if (overlay is Marker) {
                markersToRemove.add(overlay)
            }
        }

        for (removeMarker in markersToRemove) {
            overlayManager.remove(removeMarker)
        }

        mapView.invalidate()
    }

    fun showMyCurrentLocation(view: View) {
        setCurrentLocationMap()
    }

    private fun setCurrentLocationMap() {
        clearMarkers(mMap)
        marker = Marker(mMap)
        var lat = 0.00
        var log = 0.00
        if (mMyLocationOverlay.myLocation != null) {
            lat = mMyLocationOverlay.myLocation.latitude
        }
        if (mMyLocationOverlay.myLocation != null) {
            log = mMyLocationOverlay.myLocation.longitude
        }
        mMyLocationOverlay.enableMyLocation()
        mMyLocationOverlay.enableFollowLocation()
        marker?.position = GeoPoint(lat, log)
        marker?.title = getCityName(this, lat, log)
        mMap.overlays.add(marker)
        mMap.invalidate()
        loadWeatherData(marker?.title, "metric")
    }

    fun searchLocation(view: View) {
        val searchText = binding.searchET.text.toString().trim()
        if (searchText.isNotEmpty()) {
            GlobalScope.launch(Dispatchers.Main) {
                try {
                    val searchedLocation = withContext(Dispatchers.IO) {
                        val addresses: MutableList<Address>? = geocoder.getFromLocationName(searchText, 1)
                        addresses?.firstOrNull()?.let {
                            GeoPoint(it.latitude, it.longitude)
                        }
                    }
                    searchedLocation?.let {
                        marker?.position = it
                        mMap.controller.animateTo(it)
                        loadWeatherData(marker?.title, "metric")
                    } ?: run {
                        Toast.makeText(this@MainActivity, "Location not found", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Something went wrong", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }
        }
    }

}