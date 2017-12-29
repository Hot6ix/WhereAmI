package com.simples.j.whereami

import android.Manifest
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.IBinder
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat
import android.support.v4.app.NotificationCompat
import android.util.Log
import android.widget.Toast
import com.google.android.gms.location.*
import java.util.*

class TrackingService : Service() {

    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var notificationManager: NotificationManager
    private val notificationId = 124097
    private lateinit var sharedPref: SharedPreferences

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
        notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        sharedPref = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        locationCallback = object: LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                super.onLocationResult(locationResult)

                notificationManager.notify(notificationId, getNotificationBuilder(applicationContext, "test", "${locationResult!!.lastLocation.latitude}, ${locationResult!!.lastLocation.longitude}").build())
            }
        }

        enableLocationUpdate()
    }

    override fun onDestroy() {
        super.onDestroy()

        disableLocationUpdate()
        notificationManager.cancel(notificationId)
    }

    private fun enableLocationUpdate() {
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            var locationRequest = LocationRequest()
            locationRequest.interval = sharedPref.getString(applicationContext.getString(R.string.pref_interval_id), "5000").toLong()
            locationRequest.fastestInterval = sharedPref.getString(applicationContext.getString(R.string.pref_interval_id), "5000").toLong()
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

    private fun getNotificationBuilder(context: Context, title: String, content: String): NotificationCompat.Builder {
        var notificationBuilder = NotificationCompat.Builder(context, context.packageName)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle(title)
                .setContentText(content)

        return notificationBuilder
    }
}
