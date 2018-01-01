package com.simples.j.whereami

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.android.synthetic.main.activity_map.*

class MapActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnInfoWindowClickListener {

    private lateinit var mMap: GoogleMap
    private val PERMISSION_REQUEST_CODE = 1
    private lateinit var mFusedLocationSingleton: FusedLocationSingleton
    private lateinit var locationCallback: LocationCallback

    private var zoomLevel = 17
    private var currentLat = 0.0
    private var currentLng = 0.0
    private var interval: Long = 5000
    private var currentMarker: Marker? = null

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

        // Location service
        mFusedLocationSingleton = FusedLocationSingleton.getInstance(applicationContext)

        locationCallback = object: LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                super.onLocationResult(locationResult)

                Log.i(applicationContext.packageName, "Read user location")
                currentLat = locationResult!!.lastLocation.latitude
                currentLng = locationResult!!.lastLocation.longitude

                var myLocation = LatLng(currentLat, currentLng)

                if(currentMarker != null) {
                    currentMarker?.remove()
                }
                currentMarker = mMap.addMarker(
                        MarkerOptions().position(myLocation)
                                .title(locationResult!!.lastLocation.latitude.toString() + ", " + locationResult!!.lastLocation.longitude.toString())
                                .snippet(mFusedLocationSingleton.getAddrFromCoordinate(applicationContext, locationResult!!.lastLocation.latitude, locationResult!!.lastLocation.longitude)))
                currentMarker?.showInfoWindow()

                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(myLocation, zoomLevel.toFloat()))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mFusedLocationSingleton.enableLocationUpdate(applicationContext, interval, interval, LocationRequest.PRIORITY_HIGH_ACCURACY, locationCallback)
    }

    override fun onPause() {
        super.onPause()
        mFusedLocationSingleton.disableLocationUpdate(locationCallback)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item!!.itemId) {
            R.id.menu_copy -> {
                var clipboardManager: ClipboardManager = applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                var clipData = ClipData.newPlainText("location", "${currentLat}, ${currentLng} \n${mFusedLocationSingleton.getAddrFromCoordinate(applicationContext, currentLat, currentLng)}")
                clipboardManager.primaryClip = clipData
                Toast.makeText(this, getString(R.string.copy_message), Toast.LENGTH_SHORT).show()
            }
            R.id.menu_share -> {
//                var intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${currentLat},${currentLng}?q=${currentLat},${currentLng}"))
//                startActivity(Intent.createChooser(intent, resources.getText(R.string.send_to)))
                var intent = Intent()
                intent.action = Intent.ACTION_SEND
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_TEXT, "${currentLat}, ${currentLng} \n${mFusedLocationSingleton.getAddrFromCoordinate(applicationContext, currentLat, currentLng)}")
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

//        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) mMap.isMyLocationEnabled = true
//        mMap.uiSettings.isMyLocationButtonEnabled = true

        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isCompassEnabled = true
        mMap.uiSettings.isMapToolbarEnabled = true
        mMap.uiSettings.isIndoorLevelPickerEnabled = true
        mMap.uiSettings.setAllGesturesEnabled(true)
    }

    override fun onInfoWindowClick(p0: Marker?) {
        var clipboardManager: ClipboardManager = applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        var clipData = ClipData.newPlainText("location", "${currentLat}, ${currentLng} \n${mFusedLocationSingleton.getAddrFromCoordinate(applicationContext, currentLat, currentLng)}")
        clipboardManager.primaryClip = clipData
        Toast.makeText(this, getString(R.string.copy_message), Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when(requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission was granted
//                    getCurrentLocation()
                }
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
}
