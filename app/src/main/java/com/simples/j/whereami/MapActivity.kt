package com.simples.j.whereami

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import com.google.maps.android.SphericalUtil
import com.google.maps.android.ui.IconGenerator
import kotlinx.android.synthetic.main.activity_map.*
import java.text.DateFormat
import java.util.*
import kotlin.collections.ArrayList

private const val PERMISSION_REQUEST_CODE = 1
private const val DEFAULT_CAMERA_ZOOM = 15.0f
private const val MAX_CAMERA_ZOOM = 10.0f
private const val ADDRESS_ANIM_DURATION: Long = 1500
private const val MENU_EXPAND_DURATION: Long = 250

class MapActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnCameraIdleListener, GoogleMap.OnCameraMoveStartedListener, GoogleMap.OnMapLongClickListener, GoogleMap.OnMarkerClickListener, GoogleMap.OnPolylineClickListener, View.OnClickListener {

    private lateinit var mMap: GoogleMap
    private lateinit var mFusedLocationSingleton: FusedLocationSingleton
    private lateinit var locationCallback: LocationCallback
    private lateinit var sharedPref: SharedPreferences

    private var zoomLevel: Float = 17.0f
    private var currentLocation: Location? = null
    private var previousLatLng: LatLng? = null
    private var interval: Long = 1000
    private var currentMarker: Marker? = null
    private var startMarkerLatLng: LatLng? = null
    private var endMarkerLatLng: LatLng? = null

    private var isFirstScanned = false
    private var isMyLocationEnabled = true
    private var isAddressViewLocked = false
    private var isCameraMoving = false
    private var isLinkMode = false
    private var isUnlinkMode = false

    private var isInfoViewCollapsed = false
    private var isMenuLayoutExpanded = false
    private var isMarkerOptionExpanded = false
    private var infoViewWidth = 0

    private var markerList: ArrayList<Marker> = ArrayList()
    private var lineList: ArrayList<Polyline> = ArrayList()
    private var lineDistanceList: ArrayList<Marker> = ArrayList()
    private var selectedMarker: Marker? = null

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
        item_clearAll.setOnClickListener(this)
        item_markers.setOnClickListener(this)
        item_share.setOnClickListener(this)
        item_setting.setOnClickListener(this)
        marker_delete.setOnClickListener(this)
        marker_unlink.setOnClickListener(this)
        marker_link.setOnClickListener(this)
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
                    if(zoomLevel < MAX_CAMERA_ZOOM) zoomLevel = DEFAULT_CAMERA_ZOOM

                    val myLocation = LatLng(locationResult.lastLocation.latitude, locationResult.lastLocation.longitude)

                    if((!isFirstScanned || isMyLocationEnabled) && !isCameraMoving) {
                        if(!isFirstScanned) {
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(myLocation, zoomLevel))
                            isFirstScanned = true
                        }
                        else animateCamera(myLocation, zoomLevel, 0.toFloat())

                        if(currentMarker == null) {
                            currentMarker = mMap.addMarker(MarkerOptions().position(myLocation))
                            markerList.add(currentMarker!!)
                        }
                        currentMarker!!.position = myLocation
                        address.text = mFusedLocationSingleton.getAddressFromCoordinate(applicationContext, myLocation)

                        if(previousLatLng != null) {
                            reconnectLineToMyLocation(previousLatLng!!, myLocation)
                        }
                        else previousLatLng = myLocation
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

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setOnCameraIdleListener(this)
        mMap.setOnCameraMoveStartedListener(this)
        mMap.setOnMapLongClickListener(this)
        mMap.setOnMarkerClickListener(this)
        mMap.setOnPolylineClickListener(this)
        mMap.uiSettings.setAllGesturesEnabled(true)
        mMap.setPadding(0, 80, 0, 330)
    }

    override fun onCameraIdle() {
        isCameraMoving = false
        zoomLevel = mMap.cameraPosition.zoom
        Log.i("zzoommmmm", zoomLevel.toString())
    }

    override fun onCameraMoveStarted(reason: Int) {
        isCameraMoving = true
//        controlDistanceMarker()
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
                if(!isLinkMode && !isUnlinkMode){
                    if(isMarkerOptionExpanded) switchMarkerOption(false)
                }
            }
        }
    }

    override fun onMapLongClick(point: LatLng) {
        markerList.add(mMap.addMarker(MarkerOptions()
                .position(point)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))))
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        if(marker.tag == "DISTANCE") return true
        if(marker != currentMarker) {
            isMyLocationEnabled = false
            updateMyLocationButtonImage()
        }
        if(!isAddressViewLocked) {
            if(isInfoViewCollapsed){
                expandInfoView()
            }
        }
        address.text = mFusedLocationSingleton.getAddressFromCoordinate(applicationContext, marker.position)
        selectedMarker = marker
        if(!isMarkerOptionExpanded) switchMarkerOption(true)
        if(isLinkMode) {
            endMarkerLatLng = marker.position

            if(startMarkerLatLng != endMarkerLatLng) {
                val distanceMarker = mMap.addMarker(MarkerOptions()
                        .position(getDistanceBetween(startMarkerLatLng!!, endMarkerLatLng!!))
                        .icon(BitmapDescriptorFactory.fromBitmap(getDistanceIcon(startMarkerLatLng!!, endMarkerLatLng!!))))
                distanceMarker.tag = "DISTANCE"
                distanceMarker.showInfoWindow()
                distanceMarker.snippet = SphericalUtil.computeDistanceBetween(startMarkerLatLng!!, endMarkerLatLng!!).toString()
                lineDistanceList.add(distanceMarker)

                lineList.add(mMap.addPolyline(PolylineOptions()
                        .clickable(true)
                        .color(ContextCompat.getColor(applicationContext, R.color.colorPrimary))
                        .add(startMarkerLatLng)
                        .add(endMarkerLatLng)))
                startMarkerLatLng = marker.position
            }
        }
        return false
    }

    override fun onPolylineClick(polyline: Polyline) {
        if(isUnlinkMode) {
            lineList.filter { it == polyline }.mapIndexed { index, it ->
                it.remove()
                lineList.remove(polyline)
                lineDistanceList[index].remove()
                lineDistanceList.removeAt(index)
            }
        }
    }

    override fun onClick(view: View?) {
        if(view != null) {
            when (view.id) {
                R.id.myLocation -> {
                    if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_CODE)
                    }
                    else {
                        isMyLocationEnabled = !isMyLocationEnabled
                        updateMyLocationButtonImage()
                        if(isMyLocationEnabled) {
                            selectedMarker = currentMarker
                            if(zoomLevel < MAX_CAMERA_ZOOM) zoomLevel = DEFAULT_CAMERA_ZOOM
                            if(!isAddressViewLocked) {
                                if(isInfoViewCollapsed) {
                                    expandInfoView()
                                }
                            }
                            setLastLocation()
                        }
                    }
                }
                R.id.address -> {
//                    var intent = Intent(this, DetailActivity::class.java)
//                    intent.putExtra(DetailActivity.BUNDLE_ADDRESS, mFusedLocationSingleton.getAddressFromCoordinate(applicationContext, LatLng(currentLocation!!.latitude, currentLocation!!.longitude)))
//                    intent.putExtra(DetailActivity.BUNDLE_LOCATION, currentLocation)
//                    var options = ActivityOptionsCompat.makeSceneTransitionAnimation(this,
//                            Pair<View, String>(address, ViewCompat.getTransitionName(address)))
//                    startActivity(intent, options.toBundle())
                }
                R.id.item_more -> {
                    switchMenuLayout(!isMenuLayoutExpanded)
                }
                R.id.item_clearAll -> {
                    markerList.filter { it.position != currentMarker!!.position }.map { it.remove() }
                    markerList.removeAll { it.position != currentMarker!!.position }
                    lineList.map { it.remove() }
                    lineList.clear()
                    lineDistanceList.map { it.remove() }
                    lineDistanceList.clear()
                    isLinkMode = false
                    isUnlinkMode = false
                    updateLinkButtonImage()
                    updateUnlinkButtonImage()
                    switchMarkerOption(false)
                    Toast.makeText(applicationContext, "All markers and lines removed.", Toast.LENGTH_SHORT).show()
                }
                R.id.item_share -> {
                    val selected = sharedPref.getStringSet(resources.getString(R.string.pref_share_option_id), null).sorted()
                    val text = StringBuilder()
                    for(item in selected) {
                        when(item.toInt()) {
                            0 -> text.append("${mFusedLocationSingleton.getAddressFromCoordinate(applicationContext, selectedMarker!!.position)}\n")
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
                R.id.item_markers -> {

                }
                R.id.marker_delete -> {
                    if(selectedMarker!!.id == currentMarker!!.id) {
                        Toast.makeText(applicationContext, "My location marker cannot be deleted.", Toast.LENGTH_SHORT).show()
                    }
                    else {
                        removeLineDependency(selectedMarker!!.position)
                        markerList.single { it.id == selectedMarker!!.id }.remove()
                        markerList.remove(markerList.single { it.id == selectedMarker!!.id })
                        isLinkMode = false
                        isUnlinkMode = false
                        updateLinkButtonImage()
                        updateUnlinkButtonImage()
                        switchMarkerOption(false)
                    }
                }
                R.id.marker_unlink -> {
                    isUnlinkMode = !isUnlinkMode
                    if(isUnlinkMode) {
                        isLinkMode = false
                        updateLinkButtonImage()
                    }
                    updateUnlinkButtonImage()
                    Toast.makeText(applicationContext, "Select line to remove", Toast.LENGTH_SHORT).show()
                }
                R.id.marker_link -> {
                    isLinkMode = !isLinkMode
                    if(isLinkMode) {
                        isUnlinkMode = false
                        updateUnlinkButtonImage()
                    }
                    startMarkerLatLng = selectedMarker!!.position
                    updateLinkButtonImage()
                    Toast.makeText(applicationContext, "Select other marker", Toast.LENGTH_SHORT).show()
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
        val ll: LatLng
        if(l != null) ll = LatLng(l.latitude, l.longitude)
        else ll = LatLng(currentLocation!!.latitude, currentLocation!!.longitude)
        currentMarker!!.position = ll
        if(previousLatLng != null) {
            reconnectLineToMyLocation(previousLatLng!!, ll)
        }
        else previousLatLng = ll
        address.text = mFusedLocationSingleton.getAddressFromCoordinate(applicationContext, ll)
        animateCamera(ll, zoomLevel, 0.toFloat())
        Log.i(applicationContext.packageName, "Set camera to last known location")
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
            // Clear all
            constraintSet.clear(menu_item_clearAll.id, ConstraintSet.BOTTOM)
            constraintSet.connect(menu_item_clearAll.id, ConstraintSet.TOP, menu_item_more.id, ConstraintSet.BOTTOM, 30)
            // MarkerList
            constraintSet.clear(menu_item_markers.id, ConstraintSet.BOTTOM)
            constraintSet.connect(menu_item_markers.id, ConstraintSet.TOP, menu_item_clearAll.id, ConstraintSet.BOTTOM, 30)
            // Share
            constraintSet.clear(menu_item_share.id, ConstraintSet.BOTTOM)
            constraintSet.connect(menu_item_share.id, ConstraintSet.TOP, menu_item_markers.id, ConstraintSet.BOTTOM, 30)
            // Setting
            constraintSet.clear(menu_item_setting.id, ConstraintSet.BOTTOM)
            constraintSet.connect(menu_item_setting.id, ConstraintSet.TOP, menu_item_share.id, ConstraintSet.BOTTOM, 30)

            item_more.setImageDrawable(getDrawable(R.drawable.ic_action_clear))
        }
        else { // Collapse
            // Clear all
            constraintSet.clear(menu_item_clearAll.id, ConstraintSet.TOP)
            constraintSet.connect(menu_item_clearAll.id, ConstraintSet.TOP, menu_item_more.id, ConstraintSet.TOP)
            constraintSet.connect(menu_item_clearAll.id, ConstraintSet.BOTTOM, menu_item_more.id, ConstraintSet.BOTTOM)
            // MarkerList
            constraintSet.clear(menu_item_markers.id, ConstraintSet.TOP)
            constraintSet.connect(menu_item_markers.id, ConstraintSet.TOP, menu_item_more.id, ConstraintSet.TOP)
            constraintSet.connect(menu_item_markers.id, ConstraintSet.BOTTOM, menu_item_more.id, ConstraintSet.BOTTOM)
            // Share
            constraintSet.clear(menu_item_share.id, ConstraintSet.TOP)
            constraintSet.connect(menu_item_share.id, ConstraintSet.TOP, menu_item_more.id, ConstraintSet.TOP)
            constraintSet.connect(menu_item_share.id, ConstraintSet.BOTTOM, menu_item_more.id, ConstraintSet.BOTTOM)
            // Setting
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
        isMenuLayoutExpanded = switch
    }

    private fun switchMarkerOption(switch: Boolean) {
        val constraintSet = ConstraintSet()
        constraintSet.clone(main_layout)
        if(switch) {
            // Delete marker
            constraintSet.clear(marker_item_delete.id, ConstraintSet.START)
            constraintSet.connect(marker_item_delete.id, ConstraintSet.END, menu_item_more.id, ConstraintSet.START, 30)
            // Remove line
            constraintSet.clear(marker_item_unlink.id, ConstraintSet.START)
            constraintSet.connect(marker_item_unlink.id, ConstraintSet.END, marker_item_delete.id, ConstraintSet.START, 30)
            // Link marker
            constraintSet.clear(marker_item_link.id, ConstraintSet.START)
            constraintSet.connect(marker_item_link.id, ConstraintSet.END, marker_item_unlink.id, ConstraintSet.START, 30)
        }
        else {
            // Delete marker
            constraintSet.clear(marker_item_delete.id, ConstraintSet.END)
            constraintSet.connect(marker_item_delete.id, ConstraintSet.START, menu_item_more.id, ConstraintSet.START)
            constraintSet.connect(marker_item_delete.id, ConstraintSet.END, menu_item_more.id, ConstraintSet.END)
            // Remove line
            constraintSet.clear(marker_item_unlink.id, ConstraintSet.END)
            constraintSet.connect(marker_item_unlink.id, ConstraintSet.START, menu_item_more.id, ConstraintSet.START)
            constraintSet.connect(marker_item_unlink.id, ConstraintSet.END, menu_item_more.id, ConstraintSet.END)
            // Link marker
            constraintSet.clear(marker_item_link.id, ConstraintSet.END)
            constraintSet.connect(marker_item_link.id, ConstraintSet.START, menu_item_more.id, ConstraintSet.START)
            constraintSet.connect(marker_item_link.id, ConstraintSet.END, menu_item_more.id, ConstraintSet.END)
        }
        val transition = AutoTransition()
        transition.duration = MENU_EXPAND_DURATION
        transition.interpolator = AccelerateDecelerateInterpolator()

        TransitionManager.beginDelayedTransition(main_layout, transition)
        constraintSet.applyTo(main_layout)
        isMarkerOptionExpanded = switch
    }

    private fun updateMyLocationButtonImage() {
        if(isMyLocationEnabled) {
            myLocation.setImageDrawable(getDrawable(R.drawable.ic_menu_mylocation_light))
            myLocation.drawable.mutate().setColorFilter(ContextCompat.getColor(applicationContext, R.color.colorPrimary), PorterDuff.Mode.MULTIPLY)
        }
        else {
            myLocation.setImageDrawable(getDrawable(R.drawable.ic_menu_mylocation_dark))
        }
    }

    private fun updateLinkButtonImage() {
        if(isLinkMode) {
            marker_link.setImageDrawable(getDrawable(R.drawable.ic_action_link_light))
            marker_link.drawable.mutate().setColorFilter(ContextCompat.getColor(applicationContext, R.color.colorPrimary), PorterDuff.Mode.MULTIPLY)
        }
        else {
            marker_link.setImageDrawable(getDrawable(R.drawable.ic_action_link_dark))
        }
    }

    private fun updateUnlinkButtonImage() {
        if(isUnlinkMode) {
            marker_unlink.setImageDrawable(getDrawable(R.drawable.ic_action_unlink_light))
            marker_unlink.drawable.mutate().setColorFilter(ContextCompat.getColor(applicationContext, R.color.colorPrimary), PorterDuff.Mode.MULTIPLY)
        }
        else {
            marker_unlink.setImageDrawable(getDrawable(R.drawable.ic_action_unlink_dark))
        }
    }

    private fun removeLineDependency(position: LatLng) {
        val iterator = lineList.listIterator()

        while(iterator.hasNext()) {
            val index = iterator.nextIndex()
            val item = iterator.next()
            item.points.filter { it == position }.map {
                item.remove()
                iterator.remove()
                lineDistanceList[index].remove()
                lineDistanceList.removeAt(index)
            }
        }
    }

    private fun checkLineDuplication() {

    }

    private fun reconnectLineToMyLocation(previous: LatLng, current: LatLng) {
        for((index, item) in lineList.withIndex()) {
            var isConnected = false
            var oppositePoint: LatLng? = null

            for(point in item.points) {
                if(point == previous) {
                    // This line connected to my location
                    isConnected = true
                }
                else {
                    oppositePoint = point
                }
            }

            if(isConnected) {
                val array = ArrayList<LatLng>()
                array.add(current)
                array.add(oppositePoint!!)
                item.points = array
                lineDistanceList[index].position = getDistanceBetween(current, oppositePoint)
                lineDistanceList[index].setIcon(BitmapDescriptorFactory.fromBitmap(getDistanceIcon(current, oppositePoint)))
            }
        }

        previousLatLng = current
    }

    private fun getDistanceBetween(start: LatLng, end: LatLng): LatLng {
        val bounds = LatLngBounds.builder().include(start).include(end).build()
        val startLocation = Location("start")
        startLocation.latitude = start.latitude
        startLocation.longitude = start.longitude
        val endLocation = Location("end")
        endLocation.latitude = end.latitude
        endLocation.longitude = end.longitude

        return bounds.center
    }

    private fun getDistanceIcon(start: LatLng, end: LatLng): Bitmap {
        return IconGenerator(applicationContext)
                .makeIcon(SphericalUtil.computeDistanceBetween(start, end).toInt().toString() + "m")
    }

    private fun controlDistanceMarker() {
        if(zoomLevel < 14) {
            lineDistanceList.filter { it.snippet.toFloat() < 500f }.map { it.isVisible = false }
        }
        else if(zoomLevel < 15) {
            lineDistanceList.filter { it.snippet.toFloat() < 500f }.map { it.isVisible = true }
            lineDistanceList.filter { it.snippet.toFloat() < 200f }.map { it.isVisible = false }
        }
        else if(zoomLevel < 16) {
            lineDistanceList.filter { it.snippet.toFloat() < 200f }.map { it.isVisible = false }
            lineDistanceList.filter { it.snippet.toFloat() < 100f }.map { it.isVisible = false }
        }
    }
}
