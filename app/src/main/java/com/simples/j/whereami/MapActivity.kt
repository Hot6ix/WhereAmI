package com.simples.j.whereami

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.OnSuccessListener
import kotlinx.android.synthetic.main.activity_map.*
import java.util.*

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private val PERMISSION_REQUEST_CODE = 1
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var zoomLevel = 18
    private lateinit var locationCallback: LocationCallback
    private var currentLat = 0.0
    private var currentLng = 0.0

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
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object: LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                super.onLocationResult(locationResult)

                Log.i(applicationContext.packageName, "Read user location")
                currentLat = locationResult!!.lastLocation.latitude
                currentLng = locationResult!!.lastLocation.longitude
                address.text = locationResult!!.lastLocation.latitude.toString() + ", " + locationResult!!.lastLocation.longitude.toString() + "\n"
                address.append(getAddrFromCoordinate(locationResult!!.lastLocation.latitude, locationResult!!.lastLocation.longitude))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enableLocationUpdate()
    }

    override fun onStop() {
        super.onStop()
        disableLocationUpdate()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item!!.itemId) {
            R.id.menu_share -> {
//                var intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${currentLat},${currentLng}?q=${currentLat},${currentLng}"))
//                startActivity(Intent.createChooser(intent, resources.getText(R.string.send_to)))
                var intent = Intent()
                intent.action = Intent.ACTION_SEND
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_TEXT, "${currentLat}, ${currentLng} \n${getAddrFromCoordinate(currentLat, currentLng)}")
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
        getCurrentLocation()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when(requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission was granted
                    getCurrentLocation()
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

    private fun getCurrentLocation() {
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mFusedLocationClient.lastLocation.addOnSuccessListener(this, OnSuccessListener { location ->
                if (mMap != null) {
                    var myLocation = LatLng(location.latitude, location.longitude)
                    mMap.addMarker(MarkerOptions().position(myLocation).title("Your Location"))
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(myLocation, zoomLevel.toFloat()))

                    getAddrFromCoordinate(location.latitude, location.longitude)
                }
            })
        }
    }

    private fun enableLocationUpdate() {
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            var locationRequest = LocationRequest()
            locationRequest.interval = 5000
            locationRequest.fastestInterval = 3000
            locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

            mFusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        }
    }

    private fun disableLocationUpdate() {
        mFusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun getAddrFromCoordinate(lat: Double, lng: Double): String {
        var addr = ""
        var geoCoder = Geocoder(applicationContext, Locale.getDefault())
        var addresses: List<Address> = geoCoder.getFromLocation(lat, lng, 1)
        if(addresses.size != null && addresses.isNotEmpty()) {
            for(i in 0..addresses[0].maxAddressLineIndex) { // 0 = Country
                addr += addresses[0].getAddressLine(i)
            }
        }

        return addr
    }
}
