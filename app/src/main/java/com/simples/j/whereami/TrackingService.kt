package com.simples.j.whereami

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.*
import android.os.IBinder
import android.preference.PreferenceManager
import android.support.v4.app.NotificationCompat
import android.util.Log
import android.widget.Toast
import com.google.android.gms.location.*
import java.util.*

class TrackingService : Service() {

    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var mFusedLocationSingleton: FusedLocationSingleton
    private lateinit var locationCallback: LocationCallback
    private lateinit var notificationManager: NotificationManager
    private val notificationId = 124097
    private val contentId = 234798
    private var interval: Long = 5000
    private val receiverAction = ".ACTION_COPY"
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
        mFusedLocationSingleton = FusedLocationSingleton.getInstance(applicationContext)

        notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        sharedPref = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        locationCallback = object: LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                super.onLocationResult(locationResult)

                notificationManager.notify(notificationId, getNotificationBuilder(applicationContext, "test", "${locationResult!!.lastLocation.latitude}, ${locationResult!!.lastLocation.longitude} at ${Calendar.getInstance().time.toString()}").build())
            }
        }

        val broadcastReceiver = object: BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                if(p1 != null) {
                    val clipboardManager: ClipboardManager = applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clipData = ClipData.newPlainText("location", p1.extras.getString(contentId.toString()))
                    clipboardManager.primaryClip = clipData
                }

                Toast.makeText(applicationContext, getString(R.string.copy_message), Toast.LENGTH_SHORT).show()
            }
        }
        applicationContext.registerReceiver(broadcastReceiver, IntentFilter(packageName + receiverAction))

        Log.i(packageName, "Enable location update ( interval : " + interval + ", Action : " + sharedPref.getString(applicationContext.getString(R.string.pref_tracking_action_id), "0") + " )")
        mFusedLocationSingleton.enableLocationUpdate(applicationContext, interval, interval, LocationRequest.PRIORITY_HIGH_ACCURACY, locationCallback)
    }

    override fun onDestroy() {
        super.onDestroy()

        mFusedLocationSingleton.disableLocationUpdate(locationCallback)
        notificationManager.cancel(notificationId)
    }

    private fun getNotificationBuilder(context: Context, title: String, content: String): NotificationCompat.Builder {
        var notificationBuilder = NotificationCompat.Builder(context, context.packageName)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle(title)
                .setContentText(content)

        var intent = Intent()

        when (sharedPref.getString(applicationContext.getString(R.string.pref_tracking_action_id), "0").toInt()) {

            0 -> {
                // Open app
                var pIntent = PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, MapActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT)
                notificationBuilder.setContentIntent(pIntent)
            }
            1 -> {
                // Copy to clipboard
                intent = Intent(packageName + receiverAction)
                intent.putExtra(contentId.toString(), content)
                var pIntent = PendingIntent.getBroadcast(applicationContext, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT)
                notificationBuilder.setContentIntent(pIntent)
            }
            2 -> {
                // Share
                intent.action = Intent.ACTION_SEND
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_TEXT, content)

                var pIntent = PendingIntent.getActivity(applicationContext, 0, Intent.createChooser(intent, resources.getText(R.string.send_to)), PendingIntent.FLAG_UPDATE_CURRENT)
                notificationBuilder.setContentIntent(pIntent)
            }

        }
        return notificationBuilder
    }
}
