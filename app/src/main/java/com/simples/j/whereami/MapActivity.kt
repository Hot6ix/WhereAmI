package com.simples.j.whereami

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.*
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
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.android.synthetic.main.activity_map.*

private const val PERMISSION_REQUEST_CODE = 1

class MapActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnInfoWindowClickListener, SensorEventListener {

    private lateinit var mMap: GoogleMap
    private lateinit var mFusedLocationSingleton: FusedLocationSingleton
    private lateinit var locationCallback: LocationCallback
    private lateinit var mClipboardManager: ClipboardManager
    private lateinit var mSensorManager: SensorManager
    private lateinit var mSensor: Sensor

    private var zoomLevel = 17
    private var currentLocation: Location? = null
    private var interval: Long = 3000
    private var currentMarker: Marker? = null

    private var requestCount = 0

    var mRotationMatrix = FloatArray(16)

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
        mClipboardManager = applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        mSensorManager = applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        // Location service
        mFusedLocationSingleton = FusedLocationSingleton.getInstance(applicationContext)

        locationCallback = object: LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                super.onLocationResult(locationResult)

                Log.i(applicationContext.packageName, "Read user location")

                /* Location
                *   - Latitude, Longitude, Altitude
                *   - Bearing, Speed, Time */
                currentLocation = locationResult!!.lastLocation

                val myLocation = LatLng(locationResult!!.lastLocation.latitude, locationResult.lastLocation.longitude)

                if(currentMarker != null) {
                    currentMarker?.remove()
                }
                currentMarker = mMap.addMarker(
                        MarkerOptions().position(myLocation)
                                .title(mFusedLocationSingleton.getAddrFromCoordinate(applicationContext, locationResult.lastLocation.latitude, locationResult.lastLocation.longitude)))
                currentMarker?.showInfoWindow()

//                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myLocation, zoomLevel.toFloat()))
//                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition(myLocation, zoomLevel.toFloat(), 0f, locationResult.lastLocation.bearing)))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_STATUS_ACCURACY_LOW)
        mFusedLocationSingleton.enableLocationUpdate(applicationContext, interval, interval, LocationRequest.PRIORITY_HIGH_ACCURACY, locationCallback)
    }

    override fun onPause() {
        super.onPause()
        mSensorManager.unregisterListener(this)
        mFusedLocationSingleton.disableLocationUpdate(locationCallback)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item!!.itemId) {
            R.id.menu_copy -> {
                val clipData = ClipData.newPlainText("location", "${currentLocation!!.latitude}, ${currentLocation!!.longitude} \n${mFusedLocationSingleton.getAddrFromCoordinate(applicationContext, currentLocation!!.latitude, currentLocation!!.longitude)}")
                mClipboardManager.primaryClip = clipData
                Toast.makeText(this, getString(R.string.copy_message), Toast.LENGTH_SHORT).show()
            }
            R.id.menu_share -> {
//                var intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${currentLat},${currentLng}?q=${currentLat},${currentLng}"))
//                startActivity(Intent.createChooser(intent, resources.getText(R.string.send_to)))
                val intent = Intent()
                intent.action = Intent.ACTION_SEND
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_TEXT, "${currentLocation!!.latitude}, ${currentLocation!!.longitude} \n${mFusedLocationSingleton.getAddrFromCoordinate(applicationContext, currentLocation!!.latitude, currentLocation!!.longitude)}")
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
        mMap.setOnInfoWindowClickListener(this)
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isCompassEnabled = true
        mMap.uiSettings.isMapToolbarEnabled = true
        mMap.uiSettings.isIndoorLevelPickerEnabled = true
        mMap.uiSettings.setAllGesturesEnabled(true)
    }

    override fun onInfoWindowClick(p0: Marker?) {
//        val clipData = ClipData.newPlainText("location", "${currentLat}, ${currentLng} \n${mFusedLocationSingleton.getAddrFromCoordinate(applicationContext, currentLat, currentLng)}")
//        clipboardManager.primaryClip = clipData
//        Toast.makeText(this, getString(R.string.copy_message), Toast.LENGTH_SHORT).show()
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

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) { }

    override fun onSensorChanged(event: SensorEvent?) {
        if(event!!.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {

            SensorManager.getRotationMatrixFromVector(mRotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(mRotationMatrix, orientation)
            if(currentLocation != null) {
                val bearing = Math.toDegrees(orientation[0].toDouble()) +  GeomagneticField(currentLocation!!.latitude.toFloat(), currentLocation!!.longitude.toFloat(), currentLocation!!.altitude.toFloat(), System.currentTimeMillis()).declination
                var cam = CameraPosition.builder()
                        .target(LatLng(currentLocation!!.latitude, currentLocation!!.longitude))
                        .zoom(zoomLevel.toFloat())
                        .bearing(bearing.toFloat())
                        .build()
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cam))
            }
        }
    }
}
