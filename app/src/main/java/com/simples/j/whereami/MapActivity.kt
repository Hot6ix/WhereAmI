package com.simples.j.whereami

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.location.Location
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.constraint.ConstraintSet
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.RelativeLayout
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
import com.google.android.gms.maps.model.*
import kotlinx.android.synthetic.main.activity_map.*
import java.text.DateFormat
import java.util.*
import kotlin.collections.ArrayList

private const val PERMISSION_REQUEST_CODE = 1
private const val DEFAULT_CAMERA_ZOOM = 15.0f
private const val MAX_CAMERA_ZOOM = 10.0f
private const val ADDRESS_ANIM_DURATION: Long = 1500
private const val MENU_EXPAND_DURATION: Long = 250

class MapActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnCameraIdleListener, GoogleMap.OnCameraMoveStartedListener, GoogleMap.OnMapLongClickListener, View.OnClickListener {

    private lateinit var mMap: GoogleMap
    private lateinit var mFusedLocationSingleton: FusedLocationSingleton
    private lateinit var locationCallback: LocationCallback
    private lateinit var sharedPref: SharedPreferences

    private var zoomLevel: Float = 17.0f
    private var currentLocation: Location? = null
    private var interval: Long = 1000
    private var currentMarker: Marker? = null
    private var isFirstScanned = false
    private var isMyLocationEnabled = true
    private var isAddressViewLocked = false
    private var isCameraMoving = false
    private var isInfoViewCollapsed = false
    private var isMenuLayoutExpanded = false
    private var infoViewWidth = 0
    private var markerList: ArrayList<Marker> = ArrayList()

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
        infoView.post { infoViewWidth = infoView.measuredWidth }

        myLocation.setOnClickListener(this)
        address.setOnClickListener(this)
        item_more.setOnClickListener(this)
        item_share.setOnClickListener(this)
        item_setting.setOnClickListener(this)
        updateMyLocationButtonImage()

        // Ad
        MobileAds.initialize(this, applicationContext.getString(R.string.admob_app_id))
        adView.loadAd(AdRequest.Builder().build())

        // Get services
        mFusedLocationSingleton = FusedLocationSingleton.getInstance(applicationContext)
        sharedPref = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        locationCallback = object: LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                super.onLocationResult(locationResult)

                Log.i(applicationContext.packageName, "Read user location")

                /* Location
                *   - Latitude, Longitude, Altitude, Time */
                if(locationResult != null) {
                    currentLocation = locationResult.lastLocation

                    val myLocation = LatLng(locationResult.lastLocation.latitude, locationResult.lastLocation.longitude)

                    if((!isFirstScanned || isMyLocationEnabled) && !isCameraMoving) {
                        if(!isFirstScanned) {
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(myLocation, zoomLevel))
                            isFirstScanned = true
                        }
                        else animateCamera(myLocation, zoomLevel, 0.toFloat())

                        if(currentMarker == null) {
                            currentMarker = mMap.addMarker(MarkerOptions().position(myLocation))
                        }
                        currentMarker!!.position = myLocation
                        address.text = mFusedLocationSingleton.getAddressFromCoordinate(applicationContext, myLocation)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mFusedLocationSingleton.enableLocationUpdate(applicationContext, interval, interval, LocationRequest.PRIORITY_HIGH_ACCURACY, locationCallback)
        isAddressViewLocked = sharedPref.getBoolean(resources.getString(R.string.pref_address_lock_id), false)
        if(isAddressViewLocked) {
            if(isInfoViewCollapsed) {
                expandInfoView()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        mFusedLocationSingleton.disableLocationUpdate(locationCallback)
    }

//    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
//        menuInflater.inflate(R.menu.menu, menu)
//        return super.onCreateOptionsMenu(menu)
//    }
//
//    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
//        when (item!!.itemId) {
//            R.id.menu_share -> {
//                val intent = Intent()
//                intent.action = Intent.ACTION_SEND
//                intent.type = "text/plain"
//                intent.putExtra(Intent.EXTRA_TEXT, currentLocation.toString())
////                intent.putExtra(Intent.EXTRA_TEXT, "${currentLocation!!.latitude}, ${currentLocation!!.longitude} \n${mFusedLocationSingleton.getAddrFromCoordinate(applicationContext, currentLocation!!.latitude, currentLocation!!.longitude)}")
//                startActivity(Intent.createChooser(intent, resources.getText(R.string.send_to)))
//            }
//            R.id.menu_settings -> {
//                startActivity(Intent(this, SettingsActivity::class.java))
//            }
//        }
//        return super.onOptionsItemSelected(item)
//    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setOnCameraIdleListener(this)
        mMap.setOnCameraMoveStartedListener(this)
        mMap.setOnMapLongClickListener(this)
        mMap.uiSettings.setAllGesturesEnabled(true)
        mMap.setPadding(0, 80, 0, 330)
    }

    override fun onCameraIdle() {
        isCameraMoving = false
        zoomLevel = mMap.cameraPosition.zoom
    }

    override fun onCameraMoveStarted(reason: Int) {
        isCameraMoving = true
        when (reason) {
            GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE -> {
                isMyLocationEnabled = false
                updateMyLocationButtonImage()
                if(!isAddressViewLocked) {
                    if(!isInfoViewCollapsed) {
                        collapseInfoView()
                    }
                }
                if(isMenuLayoutExpanded) switchMenuLayout(false)
            }
        }
    }

    override fun onMapLongClick(point: LatLng) {
        markerList.add(mMap.addMarker(MarkerOptions().position(point).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))))
    }

    override fun onClick(view: View?) {
        if(view != null) {
            when (view.id) {
                R.id.myLocation -> {
                    if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_CODE)
                    }
                    else {
                        myLocation.isSelected = !myLocation.isSelected
                        isMyLocationEnabled = !isMyLocationEnabled
                        updateMyLocationButtonImage()
                        if(isMyLocationEnabled) {
                            if(zoomLevel < MAX_CAMERA_ZOOM) zoomLevel = DEFAULT_CAMERA_ZOOM
                            setLastLocation()
                            if(!isAddressViewLocked) {
                                if(isInfoViewCollapsed) {
                                    expandInfoView()
                                }
                            }
                        }
                    }
                }
                R.id.item_more -> {
                    switchMenuLayout(!isMenuLayoutExpanded)
                }
                R.id.item_share -> {
                    val selected = sharedPref.getStringSet(resources.getString(R.string.pref_share_option_id), null).sorted()
                    val text = StringBuilder()
                    for(item in selected) {
                        when(item.toInt()) {
                            0 -> text.append("${mFusedLocationSingleton.getAddressFromCoordinate(applicationContext, LatLng(currentLocation!!.latitude, currentLocation!!.longitude))}\n")
                            1 -> text.append("${LatLng(currentLocation!!.latitude, currentLocation!!.longitude)}\n")
                            2 -> text.append(DateFormat.getDateTimeInstance().format(Date(currentLocation!!.time)))
                        }
                    }
                    Toast.makeText(applicationContext, selected.toString(), Toast.LENGTH_SHORT).show()
                    val intent = Intent()
                    intent.action = Intent.ACTION_SEND
                    intent.type = "text/plain"
                    intent.putExtra(Intent.EXTRA_TEXT, text.toString())
                    startActivity(Intent.createChooser(intent, resources.getText(R.string.send_to)))
                }
                R.id.item_setting -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                R.id.address -> {
//                    var intent = Intent(this, DetailActivity::class.java)
//                    intent.putExtra(DetailActivity.BUNDLE_ADDRESS, mFusedLocationSingleton.getAddressFromCoordinate(applicationContext, LatLng(currentLocation!!.latitude, currentLocation!!.longitude)))
//                    intent.putExtra(DetailActivity.BUNDLE_LOCATION, currentLocation)
//                    var options = ActivityOptionsCompat.makeSceneTransitionAnimation(this,
//                            Pair<View, String>(address, ViewCompat.getTransitionName(address)))
//                    startActivity(intent, options.toBundle())
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when(requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if(zoomLevel < MAX_CAMERA_ZOOM) zoomLevel = DEFAULT_CAMERA_ZOOM
                    mFusedLocationSingleton.enableLocationUpdate(applicationContext, interval, interval, LocationRequest.PRIORITY_HIGH_ACCURACY, locationCallback)
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

    private fun setLastLocation() {
        val l: Location? = mFusedLocationSingleton.getLastLocation(applicationContext)
        val ll: LatLng?
        if(l != null) {
            ll = LatLng(l.latitude, l.longitude)
        }
        else {
            ll = LatLng(currentLocation!!.latitude, currentLocation!!.longitude)
        }
        Log.i(applicationContext.packageName, "Set camera to last known location")
        currentMarker!!.position = ll
        animateCamera(ll, zoomLevel, 0.toFloat())
    }

    private fun animateCamera(myLocation: LatLng, zoom: Float, bearing: Float) {
        val cam = CameraPosition.builder()
                .target(myLocation)
                .zoom(zoom)
                .bearing(bearing)
                .build()
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cam))
    }

    private fun collapseInfoView() {
        val anim = object: Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
                infoView.layoutParams.width = ((myLocation.measuredWidth - infoView.measuredWidth) * interpolatedTime + infoView.measuredWidth).toInt()
                infoView.requestLayout()
            }
        }
        anim.duration = ADDRESS_ANIM_DURATION
        infoView.startAnimation(anim)
        isInfoViewCollapsed = true
        updateMyLocationButtonImage()
    }

    private fun expandInfoView() {
        infoView.measure(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
        val anim = object: Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
                infoView.layoutParams.width = ((infoViewWidth * interpolatedTime) + infoView.measuredWidth).toInt()
                infoView.requestLayout()
            }
        }
        anim.duration = ADDRESS_ANIM_DURATION
        infoView.startAnimation(anim)
        isInfoViewCollapsed = false
        updateMyLocationButtonImage()
    }

    private fun switchMenuLayout(switch: Boolean) {
        val constraintSet = ConstraintSet()
        constraintSet.clone(main_layout)
        if(switch) { // Expand
            // Set infp
            constraintSet.connect(menu_item_info.id, ConstraintSet.TOP, menu_item_more.id, ConstraintSet.BOTTOM, 30)
            constraintSet.clear(menu_item_info.id, ConstraintSet.BOTTOM)
            // Set share
            constraintSet.connect(menu_item_share.id, ConstraintSet.TOP, menu_item_info.id, ConstraintSet.BOTTOM, 30)
            constraintSet.clear(menu_item_share.id, ConstraintSet.BOTTOM)
            // Set setting
            constraintSet.connect(menu_item_setting.id, ConstraintSet.TOP, menu_item_share.id, ConstraintSet.BOTTOM, 30)
            constraintSet.clear(menu_item_setting.id, ConstraintSet.BOTTOM)

            item_more.setImageDrawable(getDrawable(R.drawable.ic_action_clear))
        }
        else { // Collapse
            // Set info
            constraintSet.clear(menu_item_info.id, ConstraintSet.TOP)
            constraintSet.connect(menu_item_info.id, ConstraintSet.TOP, menu_item_more.id, ConstraintSet.TOP)
            constraintSet.connect(menu_item_info.id, ConstraintSet.BOTTOM, menu_item_more.id, ConstraintSet.BOTTOM)
            // Set share
            constraintSet.clear(menu_item_share.id, ConstraintSet.TOP)
            constraintSet.connect(menu_item_share.id, ConstraintSet.TOP, menu_item_more.id, ConstraintSet.TOP)
            constraintSet.connect(menu_item_share.id, ConstraintSet.BOTTOM, menu_item_more.id, ConstraintSet.BOTTOM)
            // Set setting
            constraintSet.clear(menu_item_setting.id, ConstraintSet.TOP)
            constraintSet.connect(menu_item_setting.id, ConstraintSet.TOP, menu_item_more.id, ConstraintSet.TOP)
            constraintSet.connect(menu_item_setting.id, ConstraintSet.BOTTOM, menu_item_more.id, ConstraintSet.BOTTOM)

            item_more.setImageDrawable(getDrawable(R.drawable.ic_action_menu))
        }
        val transition = AutoTransition()
        transition.duration = MENU_EXPAND_DURATION
        transition.interpolator = AccelerateDecelerateInterpolator()

        TransitionManager.beginDelayedTransition(main_layout, transition)
        constraintSet.applyTo(main_layout)

        isMenuLayoutExpanded = !isMenuLayoutExpanded
    }

    private fun updateMyLocationButtonImage() {
        if(isMyLocationEnabled) {
            myLocation.isSelected = true
            myLocation.setImageDrawable(getDrawable(R.drawable.ic_menu_mylocation_light))
            myLocation.drawable.mutate().setColorFilter(ContextCompat.getColor(applicationContext, R.color.colorPrimary), PorterDuff.Mode.MULTIPLY)
        }
        else {
            myLocation.isSelected = false
            myLocation.setImageDrawable(getDrawable(R.drawable.ic_menu_mylocation_dark))
        }
    }
}
