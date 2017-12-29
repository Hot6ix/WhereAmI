package com.simples.j.whereami

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.IBinder
import android.support.v4.app.ActivityCompat
import android.widget.Toast
import com.google.android.gms.location.*
import java.util.*

class TrackingService : Service() {

    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    override fun onBind(intent: Intent): IBinder? {
        // TODO: Return the communication channel to the service.
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return Service.START_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)

        locationCallback = object: LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                super.onLocationResult(locationResult)

                Toast.makeText(applicationContext, "", Toast.LENGTH_SHORT).show()
            }
        }

        enableLocationUpdate()
    }

    override fun onDestroy() {
        super.onDestroy()

        disableLocationUpdate()
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
