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

private const val PERMISSION_REQUEST_CODE_LOCATION = 1
private const val PERMISSION_REQUEST_CODE_STORAGE = 2
private const val DEFAULT_CAMERA_ZOOM = 15.0f
private const val MAX_CAMERA_ZOOM = 10.0f
private const val ADDRESS_ANIM_DURATION: Long = 1000
private const val MENU_EXPAND_DURATION: Long = 250
private const val LINE_NAME = "Line"
private const val MARKER_NAME = "Marker"
const val MY_LOCATION_NAME = "My Location"

class MapActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnCameraIdleListener, GoogleMap.OnCameraMoveStartedListener, GoogleMap.OnMapLongClickListener, GoogleMap.OnMarkerClickListener, GoogleMap.OnPolylineClickListener, GoogleMap.OnPolygonClickListener,  View.OnClickListener {

    private lateinit var mMap: GoogleMap
    private lateinit var mFusedLocationSingleton: FusedLocationSingleton
    private lateinit var locationCallback: LocationCallback
    private lateinit var sharedPref: SharedPreferences

    private var zoomLevel: Float = 17.0f
    private var currentLocation: Location? = null
    private var startLatLng: LatLng? = null
    private var previousLatLng: LatLng? = null
    private var interval: Long = 1000
    private var currentMarker: Marker? = null
    private var tempLine: Polyline? = null
    private var lineNumber = 1
    private var markerNumber = 1

    private var isMyLocationEnabled = false
    private var isAddressViewLocked = false
    private var isShowDistanceEnabled = true
    private var isCameraMoving = false
    private var isLinkMode = false
    private var isDeleteMode = false

    private var isInfoViewCollapsed = true
    private var isMenuLayoutExpanded = false
    private var isMarkerOptionExpanded = false
    private var infoViewWidth = 0

    private var markerList: ArrayList<Marker> = ArrayList()
    private var lineList: ArrayList<Polyline> = ArrayList()
    private var lineDistanceList: ArrayList<Marker> = ArrayList()
    private var polygonList: ArrayList<Polygon> = ArrayList()
    private var selectedMarker: Marker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        infoView.post { infoViewWidth = infoView.measuredWidth }

        myLocation.setOnClickListener(this)
        address.setOnClickListener(this)
        item_more.setOnClickListener(this)
        item_delete.setOnClickListener(this)
        item_markers.setOnClickListener(this)
        item_setting.setOnClickListener(this)
        marker_customize.setOnClickListener(this)
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

                    if(isMyLocationEnabled && !isCameraMoving) {
                        animateCamera(myLocation, zoomLevel, 0.toFloat())

                        if(currentMarker == null) {
                            currentMarker = mMap.addMarker(MarkerOptions().position(myLocation))
                            currentMarker!!.tag = MY_LOCATION_NAME
                            markerList.add(currentMarker!!)
                            selectedMarker = currentMarker
                        }
                        currentMarker!!.position = myLocation
                        address.text = currentMarker!!.tag.toString()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isAddressViewLocked = sharedPref.getBoolean(resources.getString(R.string.pref_address_lock_id), false)
        if(isAddressViewLocked) {
            if(isInfoViewCollapsed) {
                expandInfoView()
            }
        }
        isShowDistanceEnabled = sharedPref.getBoolean(resources.getString(R.string.pref_show_distance_id), true)
        if(isShowDistanceEnabled) lineDistanceList.filter { !it.isVisible }.map { it.isVisible = true }
        else lineDistanceList.filter { it.isVisible }.map { it.isVisible = false }
    }

    override fun onStop() {
        super.onStop()
        mFusedLocationSingleton.disableLocationUpdate(locationCallback)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.setOnCameraIdleListener(this)
        mMap.setOnCameraMoveStartedListener(this)
        mMap.setOnMapLongClickListener(this)
        mMap.setOnMarkerClickListener(this)
        mMap.setOnPolylineClickListener(this)
        mMap.setOnPolygonClickListener(this)
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
        when (reason) {
            GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE -> {
                disableMyLocation()
                if(!isAddressViewLocked) {
                    if(!isInfoViewCollapsed && !isLinkMode) {
                        collapseInfoView()
                    }
                }
                if(!isDeleteMode){
                    if(isMenuLayoutExpanded) switchMenuLayout(false)
                }
                if(!isLinkMode){
                    if(isMarkerOptionExpanded) switchMarkerOption(false)
                }
            }
        }
    }

    override fun onMapLongClick(point: LatLng) {
        val marker = mMap.addMarker(MarkerOptions()
                .position(point)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)))
        marker.tag = MARKER_NAME + " ${markerNumber++}"
        markerList.add(marker)
        Log.i("awefwefewfewwefew", "Marker created : ${marker.id}, ${marker.tag.toString()}")
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        if(marker.title == "DISTANCE") return true
        if(marker != currentMarker) {
            disableMyLocation()
        }
        if(!isAddressViewLocked) {
            if(isInfoViewCollapsed && !isDeleteMode){
                expandInfoView()
            }
        }
        selectedMarker = marker
        if(isDeleteMode) {
            if(currentMarker != null) {
                if(selectedMarker!!.id == currentMarker!!.id) {
                    currentMarker = null
                    disableMyLocation()
                }
            }
            markerList.single { it == selectedMarker }.remove()
            markerList.remove(markerList.single { it == selectedMarker })
            collapseInfoView()
            return true
        }
        else {
//            address.text = mFusedLocationSingleton.getAddressFromCoordinate(applicationContext, marker.position)
            address.text = selectedMarker!!.tag.toString()
            if(!isMarkerOptionExpanded) switchMarkerOption(true)
        }
        if(isLinkMode) {
            if(selectedMarker!!.position != previousLatLng) {
                val distanceMarker = mMap.addMarker(MarkerOptions()
                        .position(getCenterOfPoints(previousLatLng!!, selectedMarker!!.position))
                        .icon(BitmapDescriptorFactory.fromBitmap(getDistanceIcon(previousLatLng!!, selectedMarker!!.position))))
                distanceMarker.title = "DISTANCE"
                distanceMarker.tag = tempLine!!.tag
                Log.i(distanceMarker.tag.toString(), tempLine!!.tag.toString())
                distanceMarker.isVisible = isShowDistanceEnabled
                lineDistanceList.add(distanceMarker)

                val list = tempLine!!.points
                list.add(selectedMarker!!.position)
                tempLine!!.points = list
                previousLatLng = marker.position
                // Polygon
                if(selectedMarker!!.position == startLatLng) {
                    val option = PolygonOptions()
                            .fillColor(ContextCompat.getColor(applicationContext, R.color.colorPrimary))
                            .clickable(true)
                            .addAll(list)
                    polygonList.add(mMap.addPolygon(option))
                    for(point in list) {
                        markerList.filter { point == it.position }.map {
                            it.remove()
                            markerList.remove(it)
                        }
                    }
                    lineDistanceList.filter { tempLine!!.tag == it.tag }.map {
                        it.remove()
                        lineDistanceList.remove(it)
                    }
                    tempLine!!.remove()
                    lineList.remove(tempLine!!)

                    currentMarker = null
                    collapseInfoView()
                    isLinkMode = false
                    updateLinkButtonImage()
                    switchMarkerOption(false)
                }
            }
        }

        return false
    }

    override fun onPolylineClick(polyline: Polyline) {
        if(isDeleteMode) {
            lineList.filter { it == polyline }.map {
                val tag = it.tag
                lineDistanceList.filter { tag == it.tag }.map {
                    it.remove()
                    lineDistanceList.remove(it)
                }
                it.remove()
                lineList.remove(polyline)
            }
        }
    }

    override fun onPolygonClick(polygon: Polygon?) {
        if(isDeleteMode) {
            polygonList.filter { it == polygon }.map {
                it.remove()
                polygonList.remove(polygon)
            }
        }
    }

    override fun onClick(view: View?) {
        if(view != null) {
            when (view.id) {
                R.id.myLocation -> {
                    if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_CODE_LOCATION)
                    }
                    else {
                        isMyLocationEnabled = !isMyLocationEnabled
                        setLastLocation()
                        updateMyLocationButtonImage()
                        if(isMyLocationEnabled) {
                            mFusedLocationSingleton.enableLocationUpdate(applicationContext, interval, interval, LocationRequest.PRIORITY_HIGH_ACCURACY, locationCallback)
                            selectedMarker = currentMarker
                            if(zoomLevel < MAX_CAMERA_ZOOM) zoomLevel = DEFAULT_CAMERA_ZOOM
                            if(!isAddressViewLocked) {
                                if(isInfoViewCollapsed) {
                                    expandInfoView()
                                }
                            }
                            isLinkMode = false
                            updateLinkButtonImage()
                            if(!isMarkerOptionExpanded) switchMarkerOption(true)
                        }
                    }
                }
                R.id.address -> {
                    val intent = Intent(this, DetailActivity::class.java)
                    intent.putExtra(DetailActivity.MARKER, selectedMarker!!.id)
                    startActivity(intent)
                }
                R.id.item_more -> {
                    switchMenuLayout(!isMenuLayoutExpanded)
                    if(!isMenuLayoutExpanded) {
                        isDeleteMode = false
                        updateDeleteButtonImage()
                    }
                }
                R.id.item_delete -> {
//                    markerList.map { it.remove() }
//                    markerList.clear()
//                    lineList.map { it.remove() }
//                    lineList.clear()
//                    lineDistanceList.map { it.remove() }
//                    lineDistanceList.clear()
//                    currentMarker = null
//                    isMyLocationEnabled = false
//                    isLinkMode = false
//                    isUnlinkMode = false
//                    updateMyLocationButtonImage()
//                    updateLinkButtonImage()
//                    updateUnlinkButtonImage()
//                    switchMarkerOption(false)
//                    Toast.makeText(applicationContext, "All items removed.", Toast.LENGTH_SHORT).show()
                    isDeleteMode = !isDeleteMode
                    if(isDeleteMode) {
                        isLinkMode = false
                        updateLinkButtonImage()
                        switchMarkerOption(false)
                        Toast.makeText(applicationContext, "Select item to remove.", Toast.LENGTH_SHORT).show()
                    }
                    updateDeleteButtonImage()
                }
                R.id.item_setting -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                R.id.item_markers -> {
//                    var inputStream = FileInputStream(File("${Environment.getExternalStorageDirectory()}${File.separator}KakaoTalkDownload${File.separator}A.kml.xml"))
//                    var layer = KmlLayer(mMap, inputStream, applicationContext)
//                    layer.addLayerToMap()

                    KmlSerializer(applicationContext, markerList, lineList, polygonList).serialize()
                }
                R.id.marker_link -> {
                    isLinkMode = !isLinkMode
                    if(isLinkMode) {
                        isDeleteMode = false
                        updateDeleteButtonImage()
                        Toast.makeText(applicationContext, "Select another marker to connect.", Toast.LENGTH_SHORT).show()

                        startLatLng = selectedMarker!!.position
                        previousLatLng = selectedMarker!!.position
                        tempLine = mMap.addPolyline(PolylineOptions()
                                .clickable(true)
                                .color(ContextCompat.getColor(applicationContext, R.color.colorPrimary))
                                .add(selectedMarker!!.position))
                        tempLine!!.tag = LINE_NAME + " ${lineNumber++}"
                        lineList.add(tempLine!!)
                    }
                    updateLinkButtonImage()
                }
                R.id.marker_customize -> {
                    selectedMarker!!.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when(requestCode) {
            PERMISSION_REQUEST_CODE_LOCATION -> {
                if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    isMyLocationEnabled = true
                    if(zoomLevel < MAX_CAMERA_ZOOM) zoomLevel = DEFAULT_CAMERA_ZOOM
                    mFusedLocationSingleton.enableLocationUpdate(applicationContext, interval, interval, LocationRequest.PRIORITY_HIGH_ACCURACY, locationCallback)
                    if(isInfoViewCollapsed) expandInfoView()
                    updateMyLocationButtonImage()
                }
                else {
                    Toast.makeText(this, "Need permission for service.", Toast.LENGTH_SHORT).show()
                    collapseInfoView()
                }
                return
            }
            PERMISSION_REQUEST_CODE_STORAGE -> {

            }
        }
    }

    private fun setLastLocation() {
        val l: Location? = mFusedLocationSingleton.getLastLocation(applicationContext)
        var ll: LatLng? = null
        if(l != null) ll = LatLng(l.latitude, l.longitude)
        else if(currentLocation != null) ll = LatLng(currentLocation!!.latitude, currentLocation!!.longitude)

        if(ll != null) {
            if(currentMarker != null) currentMarker!!.position = ll
            else {
                currentMarker = mMap.addMarker(MarkerOptions().position(ll))
                markerList.add(currentMarker!!)
            }

            currentMarker!!.tag = MY_LOCATION_NAME
            address.text = currentMarker!!.tag.toString()
            animateCamera(ll, zoomLevel, 0.toFloat())
            Log.i(applicationContext.packageName, "Set camera to last known location")
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
            // Delete
            constraintSet.clear(menu_item_delete.id, ConstraintSet.BOTTOM)
            constraintSet.connect(menu_item_delete.id, ConstraintSet.TOP, menu_item_more.id, ConstraintSet.BOTTOM, 30)
            // MarkerList
            constraintSet.clear(menu_item_markers.id, ConstraintSet.BOTTOM)
            constraintSet.connect(menu_item_markers.id, ConstraintSet.TOP, menu_item_delete.id, ConstraintSet.BOTTOM, 30)
            // Setting
            constraintSet.clear(menu_item_setting.id, ConstraintSet.BOTTOM)
            constraintSet.connect(menu_item_setting.id, ConstraintSet.TOP, menu_item_markers.id, ConstraintSet.BOTTOM, 30)

            item_more.setImageDrawable(getDrawable(R.drawable.ic_action_clear))
        }
        else { // Collapse
            // Delete
            constraintSet.clear(menu_item_delete.id, ConstraintSet.TOP)
            constraintSet.connect(menu_item_delete.id, ConstraintSet.TOP, menu_item_more.id, ConstraintSet.TOP)
            constraintSet.connect(menu_item_delete.id, ConstraintSet.BOTTOM, menu_item_more.id, ConstraintSet.BOTTOM)
            // MarkerList
            constraintSet.clear(menu_item_markers.id, ConstraintSet.TOP)
            constraintSet.connect(menu_item_markers.id, ConstraintSet.TOP, menu_item_more.id, ConstraintSet.TOP)
            constraintSet.connect(menu_item_markers.id, ConstraintSet.BOTTOM, menu_item_more.id, ConstraintSet.BOTTOM)
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
            // Link
            constraintSet.clear(marker_item_link.id, ConstraintSet.START)
            constraintSet.connect(marker_item_link.id, ConstraintSet.END, menu_item_more.id, ConstraintSet.START, 30)
            // Customize
            constraintSet.clear(marker_item_customize.id, ConstraintSet.START)
            constraintSet.connect(marker_item_customize.id, ConstraintSet.END, marker_item_link.id, ConstraintSet.START, 30)
        }
        else {
            // Link
            constraintSet.clear(marker_item_link.id, ConstraintSet.END)
            constraintSet.connect(marker_item_link.id, ConstraintSet.START, menu_item_more.id, ConstraintSet.START)
            constraintSet.connect(marker_item_link.id, ConstraintSet.END, menu_item_more.id, ConstraintSet.END)
            // Customize
            constraintSet.clear(marker_item_customize.id, ConstraintSet.END)
            constraintSet.connect(marker_item_customize.id, ConstraintSet.START, menu_item_more.id, ConstraintSet.START)
            constraintSet.connect(marker_item_customize.id, ConstraintSet.END, menu_item_more.id, ConstraintSet.END)
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

    private fun updateDeleteButtonImage() {
        if(isDeleteMode) {
            item_delete.setImageDrawable(getDrawable(R.drawable.ic_action_delete_light))
            item_delete.drawable.mutate().setColorFilter(ContextCompat.getColor(applicationContext, R.color.colorPrimary), PorterDuff.Mode.MULTIPLY)
        }
        else {
            item_delete.setImageDrawable(getDrawable(R.drawable.ic_action_delete_dark))
        }
    }

    private fun getCenterOfPoints(start: LatLng, end: LatLng): LatLng {
        val bounds = LatLngBounds.builder().include(start).include(end).build()
        val startLocation = Location("start")
        startLocation.latitude = start.latitude
        startLocation.longitude = start.longitude
        val endLocation = Location("end")
        endLocation.latitude = end.latitude
        endLocation.longitude = end.longitude

        return bounds.center
    }

    private fun getDistanceBetween(start: LatLng, end: LatLng): String {
        val distance = SphericalUtil.computeDistanceBetween(start, end)
        if(distance >= 1000) return "%.2fkm".format(distance * 0.001)
        else return distance.toInt().toString() + "m"
    }

    private fun getDistanceIcon(start: LatLng, end: LatLng): Bitmap {
        return IconGenerator(applicationContext).makeIcon(getDistanceBetween(start, end))
    }

    private fun disableMyLocation() {
        isMyLocationEnabled = false
        updateMyLocationButtonImage()
    }
}
