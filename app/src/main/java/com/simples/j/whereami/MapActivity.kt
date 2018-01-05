package com.simples.j.whereami

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.android.synthetic.main.activity_map.*

private const val PERMISSION_REQUEST_CODE = 1
private const val DEFAULT_ZOOM_LEVEL = 15.0f

class MapActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnCameraIdleListener, GoogleMap.OnCameraMoveStartedListener {

    private lateinit var mMap: GoogleMap
    private lateinit var mFusedLocationSingleton: FusedLocationSingleton
    private lateinit var locationCallback: LocationCallback

    private var zoomLevel: Float = 17.0f
    private var currentLocation: Location? = null
    private var interval: Long = 1000
    private var isFirstScanned = false // Only for first scan
    private var isMyLocationEnabled = false

    private var requestCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Request permission
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_CODE)
        }

        // Ad
        MobileAds.initialize(this, applicationContext.getString(R.string.admob_app_id))
        adView.loadAd(AdRequest.Builder().build())

        // Get services
        mFusedLocationSingleton = FusedLocationSingleton.getInstance(applicationContext)

        locationCallback = object: LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                super.onLocationResult(locationResult)

                Log.i(applicationContext.packageName, "Read user location")

                /* Location
                *   - Latitude, Longitude, Altitude, Time */
                if(locationResult != null) {
                    currentLocation = locationResult.lastLocation

                    val myLocation = LatLng(locationResult.lastLocation.latitude, locationResult.lastLocation.longitude)

                    if(!isFirstScanned || isMyLocationEnabled) {
                        animateCamera(myLocation, zoomLevel, 0.toFloat())
                        mMap.addMarker(MarkerOptions().title("sefssefew").snippet("efwefwew").position(myLocation))
                        address.text = mFusedLocationSingleton.getAddrFromCoordinate(applicationContext, myLocation)
                        isFirstScanned = true
                    }
                }
            }
        }
    }
    override fun onStart() {
        super.onStart()
        mFusedLocationSingleton.enableLocationUpdate(applicationContext, interval, interval, LocationRequest.PRIORITY_HIGH_ACCURACY, locationCallback)
    }

    override fun onStop() {
        super.onStop()
        mFusedLocationSingleton.disableLocationUpdate(locationCallback)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item!!.itemId) {
            R.id.menu_share -> {
                val intent = Intent()
                intent.action = Intent.ACTION_SEND
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_TEXT, currentLocation.toString())
//                intent.putExtra(Intent.EXTRA_TEXT, "${currentLocation!!.latitude}, ${currentLocation!!.longitude} \n${mFusedLocationSingleton.getAddrFromCoordinate(applicationContext, currentLocation!!.latitude, currentLocation!!.longitude)}")
                startActivity(Intent.createChooser(intent, resources.getText(R.string.send_to)))
            }
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setOnCameraIdleListener(this)
        mMap.setOnCameraMoveStartedListener(this)
        mMap.uiSettings.isCompassEnabled = true
        mMap.uiSettings.isMapToolbarEnabled = true
        mMap.uiSettings.isIndoorLevelPickerEnabled = true
        mMap.uiSettings.setAllGesturesEnabled(true)
        mMap.setInfoWindowAdapter(InfoWindow(applicationContext))

//        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
//            mMap.isMyLocationEnabled = true
//            mMap.uiSettings.isMyLocationButtonEnabled = true
//        }
    }

    override fun onCameraIdle() {
        zoomLevel = mMap.cameraPosition.zoom
    }

    override fun onCameraMoveStarted(reason: Int) {
        when (reason) {
            GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE -> {
                isMyLocationEnabled = false
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when(requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) { }
                else {
                    // Permiision denied
                    if(requestCount < 2) {
                        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_CODE)
                        requestCount++
                    }
                    else Toast.makeText(this, "Need permission for service.", Toast.LENGTH_SHORT).show()
                }
                return
            }
        }
    }

    private fun setLastLocation() {
        val l: Location? = mFusedLocationSingleton.getLastLocation(applicationContext)
        if(l != null) {
            val ll: LatLng? = LatLng(l.latitude, l.longitude)
            if(ll != null) {
                Log.i(applicationContext.packageName, "Set camera to last known location")
                animateCamera(ll, zoomLevel, 0.toFloat())
            }
        }
    }

    private fun animateCamera(myLocation: LatLng, zoom: Float, bearing: Float) {
        val cam = CameraPosition.builder()
                .target(myLocation)
                .zoom(zoom)
                .bearing(bearing)
                .build()
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cam))
    }
}
