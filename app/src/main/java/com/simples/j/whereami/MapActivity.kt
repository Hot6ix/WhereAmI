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
import android.support.v7.widget.LinearLayoutManager
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.Log
import android.view.Gravity
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
import com.simples.j.whereami.tools.DrawerListAdapter
import com.simples.j.whereami.tools.KmlManager
import com.simples.j.whereami.tools.OnItemClickListener
import com.simples.j.whereami.tools.Utils
import kotlinx.android.synthetic.main.activity_map.*
import java.util.*

private const val PERMISSION_REQUEST_CODE_LOCATION = 1
private const val PERMISSION_REQUEST_CODE_STORAGE = 2
private const val DEFAULT_CAMERA_ZOOM = 15.0f
private const val MAX_CAMERA_ZOOM = 10.0f
private const val ADDRESS_ANIM_DURATION: Long = 1000
private const val MENU_EXPAND_DURATION: Long = 250
private const val LINE_NAME = "Line"
private const val POLYGON_NAME = "Polygon"
private const val MARKER_NAME = "Marker"
const val MY_LOCATION_NAME = "My Location"

class MapActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnCameraIdleListener, GoogleMap.OnCameraMoveStartedListener, GoogleMap.OnMapLongClickListener, GoogleMap.OnMarkerClickListener, GoogleMap.OnMarkerDragListener, GoogleMap.OnPolylineClickListener, GoogleMap.OnPolygonClickListener,  View.OnClickListener, OnItemClickListener {

    private lateinit var mMap: GoogleMap
    private lateinit var mFusedLocationSingleton: FusedLocationSingleton
    private lateinit var locationCallback: LocationCallback
    private lateinit var sharedPref: SharedPreferences
    private lateinit var kmlManager: KmlManager

    private var currentLocation: Location? = null
    private var startLatLng: LatLng? = null
    private var previousLatLng: LatLng? = null
    private var currentMarker: Marker? = null
    private var tempLine: Polyline? = null
    private var selectedItem: Any? = null
    private var drawerListAdapter: DrawerListAdapter? = null

    private var zoomLevel: Float = 17.0f
    private var interval: Long = 1000
    private var infoViewWidth = 0

    private var isMyLocationEnabled = false
    private var isShowDistanceEnabled = true
    private var isCameraMoving = false
    private var isLinkMode = false
    private var isDeleteMode = false
    private var isInfoViewCollapsed = true
    private var isMenuLayoutExpanded = false
    private var isMarkerOptionExpanded = false

    private var itemList = ArrayList<Any>()
    private var lineDistanceList = ArrayList<Marker>()
    private var polygonAreaList = ArrayList<Marker>()

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
        marker_undo.setOnClickListener(this)
        marker_link.setOnClickListener(this)
        updateMyLocationButtonState()
        left_drawer.layoutManager = LinearLayoutManager(applicationContext)

        // Ad
        MobileAds.initialize(this, applicationContext.getString(R.string.admob_app_id))
        adView.loadAd(AdRequest.Builder().build())

        // Get services
        mFusedLocationSingleton = FusedLocationSingleton.getInstance(applicationContext)
        sharedPref = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        // Request permission
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), PERMISSION_REQUEST_CODE_STORAGE)
        }

        locationCallback = object: LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                super.onLocationResult(locationResult)

                Log.i(applicationContext.packageName, "Read user location")

                /* Location
                *   - Latitude, Longitude, Altitude, Time */
                if(locationResult != null) {
                    currentLocation = locationResult.lastLocation
                    val myLocation = LatLng(locationResult.lastLocation.latitude, locationResult.lastLocation.longitude)

                    if(isMyLocationEnabled && !isCameraMoving) {
                        animateCamera(myLocation, zoomLevel, 0.toFloat())

                        if(currentMarker == null) {
                            currentMarker = mMap.addMarker(MarkerOptions().position(myLocation))
                            currentMarker!!.tag = MY_LOCATION_NAME
                            itemList.add(currentMarker!!)
                            selectedItem = currentMarker
                        }
                        else {
                            currentMarker!!.position = myLocation
                        }
                        address.text = currentMarker!!.tag.toString()
                        drawerListAdapter?.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        isShowDistanceEnabled = sharedPref.getBoolean(resources.getString(R.string.pref_show_distance_id), true)
        lineDistanceList.map { it.isVisible = isShowDistanceEnabled }
        polygonAreaList.map { it.isVisible = isShowDistanceEnabled }
    }

    override fun onStop() {
        super.onStop()
        if(kmlManager.checkStorageState()) kmlManager.saveKmlToExternal(itemList)
    }

    override fun onDestroy() {
        super.onDestroy()
        mFusedLocationSingleton.disableLocationUpdate(locationCallback)
    }

    override fun onBackPressed() {
        if(isLinkMode) {
            isLinkMode = false
            updateLinkButtonState()
        }
        else if(isDeleteMode) {
            isDeleteMode = false
            updateDeleteButtonState()
        }
        else
            super.onBackPressed()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.setOnCameraIdleListener(this)
        mMap.setOnCameraMoveStartedListener(this)
        mMap.setOnMapLongClickListener(this)
        mMap.setOnMarkerClickListener(this)
        mMap.setOnMarkerDragListener(this)
        mMap.setOnPolylineClickListener(this)
        mMap.setOnPolygonClickListener(this)
        mMap.uiSettings.setAllGesturesEnabled(true)
        mMap.setPadding(0, 80, 0, 150)

        kmlManager = KmlManager(applicationContext, mMap)
        if(kmlManager.loadKmlFromExternal()) {
            itemList = kmlManager.itemList
            lineDistanceList = kmlManager.lineDistanceList
            polygonAreaList = kmlManager.polygonAreaList

            drawerListAdapter = DrawerListAdapter(itemList, applicationContext)
            drawerListAdapter?.setOnItemClickListener(this)
            left_drawer.adapter = drawerListAdapter

            lineDistanceList.map { it.isVisible = isShowDistanceEnabled }
            polygonAreaList.map { it.isVisible = isShowDistanceEnabled }

            val allItemPoints = ArrayList<LatLng>()
            kmlManager.placemarkList!!.map {
                allItemPoints.addAll(it.coordinates)
            }
            if(allItemPoints.size > 0) mMap.moveCamera(CameraUpdateFactory.newLatLng(Utils.getCenterOfPoints(allItemPoints)))
        }
    }

    override fun onDrawerItemClick(item: Any, view: View) {
        drawer_layout.closeDrawers()
        if(zoomLevel < MAX_CAMERA_ZOOM) zoomLevel = DEFAULT_CAMERA_ZOOM
        itemList.filter { it == item }.map {
            when(it) {
                is Marker -> {
                    animateCamera(it.position, zoomLevel, mMap.cameraPosition.bearing)
                    selectedItem = it
                    address.text = it.tag.toString()
                    if(!isMarkerOptionExpanded) switchMarkerOption(true)
                }
                is Polyline -> {
                    animateCamera(it.points[0], zoomLevel, mMap.cameraPosition.bearing)
                }
                is Polygon -> {
                    animateCamera(Utils.getCenterOfPoints(it.points), zoomLevel, mMap.cameraPosition.bearing)
                }
            }
        }
        if(isInfoViewCollapsed) expandInfoView()
    }

    override fun onCameraIdle() {
        isCameraMoving = false
        if(mMap.cameraPosition.zoom <= 17)
            zoomLevel = mMap.cameraPosition.zoom
        Log.i("zzoommmmm", zoomLevel.toString())
    }

    override fun onCameraMoveStarted(reason: Int) {
        isCameraMoving = true
        when (reason) {
            GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE -> {
                disableMyLocation()
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
                .draggable(true)
                .position(point)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)))
        marker.tag = MARKER_NAME + " ${UUID.randomUUID().toString()}"
        itemList.add(marker)
        drawerListAdapter?.notifyDataSetChanged()
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        if(marker.title == "DISTANCE" || marker.title == "AREA") return true
        if(marker != currentMarker) disableMyLocation()
        if(isInfoViewCollapsed && !isDeleteMode) expandInfoView()

        if(isDeleteMode) {
            if(currentMarker != null) {
                if(marker == currentMarker) {
                    currentMarker = null
                    disableMyLocation()
                }
            }
            (itemList.single { it == marker } as Marker).remove()
            itemList.remove(itemList.single { it == marker })
            if(selectedItem == marker && !isInfoViewCollapsed) collapseInfoView()
            if(!isMarkerOptionExpanded) switchMarkerOption(false)
            drawerListAdapter?.notifyDataSetChanged()
            return true
        }
        else {
            selectedItem = marker
            address.text = marker.tag.toString()
            if(!isMarkerOptionExpanded) switchMarkerOption(true)
        }
        if(isLinkMode) {
            if(marker.position != previousLatLng) {
                var list = ArrayList<LatLng>()
                if(tempLine == null) {
                    tempLine = mMap.addPolyline(PolylineOptions()
                            .clickable(true)
                            .color(ContextCompat.getColor(applicationContext, R.color.colorPrimary))
                            .add(startLatLng)
                            .add((selectedItem as Marker).position))
                    tempLine!!.tag = LINE_NAME + " ${UUID.randomUUID().toString()}"
                    itemList.add(tempLine!!)
                }
                else {
                    list = ArrayList(tempLine!!.points)
                    list.add(marker.position)
                    tempLine!!.points = list
                }
                val distanceMarker = mMap.addMarker(MarkerOptions()
                        .position(Utils.getCenterOfPoints(arrayListOf(previousLatLng!!, marker.position)))
                        .icon(BitmapDescriptorFactory.fromBitmap(Utils.getDistanceIcon(previousLatLng!!, marker.position, applicationContext))))
                distanceMarker.title = "DISTANCE"
                distanceMarker.tag = tempLine!!.id
                distanceMarker.isVisible = isShowDistanceEnabled
                lineDistanceList.add(distanceMarker)

                previousLatLng = marker.position

                // Polygon
                if (marker.position == startLatLng) {
                    val polygon = mMap.addPolygon(PolygonOptions()
                            .fillColor(ContextCompat.getColor(applicationContext, R.color.colorPrimary))
                            .clickable(true)
                            .addAll(list))
                    polygon.tag = POLYGON_NAME + " ${UUID.randomUUID().toString()}"
                    itemList.add(polygon)
                    address.text = polygon.tag.toString()
                    selectedItem = polygon

                    val areaMarker = mMap.addMarker(MarkerOptions()
                            .position(Utils.getCenterOfPoints(list))
                            .icon(BitmapDescriptorFactory.fromBitmap(Utils.getAreaIcon(list, applicationContext))))
                    areaMarker.title = "AREA"
                    areaMarker.tag = polygon.id
                    areaMarker.isVisible = isShowDistanceEnabled
                    polygonAreaList.add(areaMarker)

                    for (point in list) {
                        itemList.filter { it is Marker }.filter { point == (it as Marker).position }.map {
                            (it as Marker).remove()
                            itemList.remove(it)
                        }
                    }
                    lineDistanceList.filter { tempLine!!.id == it.tag }.map {
                        it.remove()
                        lineDistanceList.remove(it)
                    }
                    tempLine!!.remove()
                    itemList.remove(tempLine!!)

                    currentMarker = null
                    isLinkMode = false
                    updateLinkButtonState()
                    switchMarkerOption(false)
                }
            }
        }

        drawerListAdapter?.notifyDataSetChanged()
        return false
    }

    override fun onMarkerDrag(p0: Marker?) {
    }

    override fun onMarkerDragStart(p0: Marker?) {
    }

    override fun onMarkerDragEnd(marker: Marker) {
        if(isLinkMode) selectedItem = marker
    }

    override fun onPolylineClick(polyline: Polyline) {
        if(isInfoViewCollapsed && !isDeleteMode) expandInfoView()

        if(isDeleteMode) {
            itemList.filter { it == polyline }.map {
                val id = (it as Polyline).id
                lineDistanceList.filter { id == it.tag }.map {
                    it.remove()
                    lineDistanceList.remove(it)
                }
                it.remove()
                itemList.remove(polyline)
            }
            if(selectedItem == polyline && !isInfoViewCollapsed) collapseInfoView()
        }
        else {
            selectedItem = polyline
            address.text = polyline.tag.toString()
            animateCamera(polyline.points[0],zoomLevel, mMap.cameraPosition.bearing)
        }
        drawerListAdapter?.notifyDataSetChanged()
    }

    override fun onPolygonClick(polygon: Polygon) {
        if(isInfoViewCollapsed && !isDeleteMode) expandInfoView()

        if(isDeleteMode) {
            itemList.filter { it == polygon }.map {
                val id = (it as Polygon).id
                polygonAreaList.filter { id == it.tag }.map {
                    it.remove()
                    polygonAreaList.remove(it)
                }
                it.remove()
                itemList.remove(polygon)
            }
            if(selectedItem == polygon && !isInfoViewCollapsed) collapseInfoView()
        }
        else {
            selectedItem = polygon
            address.text = polygon.tag.toString()
            animateCamera(Utils.getCenterOfPoints(polygon.points),zoomLevel, mMap.cameraPosition.bearing)
        }
        drawerListAdapter?.notifyDataSetChanged()
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
                        updateMyLocationButtonState()
                        if(isMyLocationEnabled) {
                            mFusedLocationSingleton.enableLocationUpdate(applicationContext, interval, interval, LocationRequest.PRIORITY_HIGH_ACCURACY, locationCallback)
                            selectedItem = currentMarker
                            if(zoomLevel < MAX_CAMERA_ZOOM) zoomLevel = DEFAULT_CAMERA_ZOOM
                            if(isInfoViewCollapsed) expandInfoView()
                            isLinkMode = false
                            updateLinkButtonState()
                            if(!isMarkerOptionExpanded) switchMarkerOption(true)
                        }
                    }
                }
                R.id.address -> {
                    if(selectedItem is Marker) {
                        animateCamera((selectedItem as Marker).position, zoomLevel, mMap.cameraPosition.bearing)
                        if(!isMarkerOptionExpanded) switchMarkerOption(true)
                    }
                    if(selectedItem is Polyline) {
                        animateCamera((selectedItem as Polyline).points[0], zoomLevel, mMap.cameraPosition.bearing)
                    }
                    if(selectedItem is Polygon) {
                        animateCamera(Utils.getCenterOfPoints((selectedItem as Polygon).points), zoomLevel, mMap.cameraPosition.bearing)
                    }
                }
                R.id.item_more -> {
                    switchMenuLayout(!isMenuLayoutExpanded)
                    if(!isMenuLayoutExpanded) {
                        isDeleteMode = false
                        updateDeleteButtonState()
                    }
                }
                R.id.item_delete -> {
                    isDeleteMode = !isDeleteMode
                    if(isDeleteMode) {
                        isLinkMode = false
                        updateLinkButtonState()
                        switchMarkerOption(false)
                        Toast.makeText(applicationContext, "Select item to remove.", Toast.LENGTH_SHORT).show()
                    }
                    updateDeleteButtonState()
                }
                R.id.item_setting -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                R.id.item_markers -> drawer_layout.openDrawer(Gravity.START)
                R.id.marker_link -> {
                    isLinkMode = !isLinkMode
                    if(isLinkMode) {
                        isDeleteMode = false
                        updateDeleteButtonState()
                        Toast.makeText(applicationContext, "Select another marker to connect.", Toast.LENGTH_SHORT).show()

                        startLatLng = (selectedItem as Marker).position
                        previousLatLng = (selectedItem as Marker).position
                    }
                    updateLinkButtonState()
                }
                R.id.marker_undo -> {
//                    val intent = Intent(this, DetailActivity::class.java)
//                    intent.putExtra(DetailActivity.MARKER, (selectedItem as Marker).id)
//                    startActivity(intent)

                    if(tempLine != null && tempLine!!.points.size > 1)  {
                        val list = tempLine!!.points
                        animateCamera(list[list.lastIndex-1], zoomLevel, mMap.cameraPosition.bearing)
                        previousLatLng = list[list.lastIndex-1]
                        list.removeAt(list.lastIndex)
                        lineDistanceList.last().remove()
                        lineDistanceList.removeAt(lineDistanceList.lastIndex)
                        tempLine!!.points = list
                    }
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
                    updateMyLocationButtonState()
                }
                else {
                    Toast.makeText(this, "Need permission for service.", Toast.LENGTH_SHORT).show()
                    collapseInfoView()
                }
                return
            }
            PERMISSION_REQUEST_CODE_STORAGE -> {
                if(grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Need permission for service.", Toast.LENGTH_SHORT).show()
                    finish()
                }
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
                itemList.add(currentMarker!!)
            }

            currentMarker!!.tag = MY_LOCATION_NAME
            address.text = currentMarker!!.tag.toString()
            if(zoomLevel < MAX_CAMERA_ZOOM) zoomLevel = DEFAULT_CAMERA_ZOOM
            animateCamera(ll, zoomLevel, 0.toFloat())
            Log.i(applicationContext.packageName, "Set camera to last known location")
        }
    }

    private fun animateCamera(location: LatLng, zoom: Float, bearing: Float) {
        val cam = CameraPosition.builder()
                .target(location)
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
        updateMyLocationButtonState()
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
        updateMyLocationButtonState()
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
            constraintSet.clear(marker_item_undo.id, ConstraintSet.START)
            constraintSet.connect(marker_item_undo.id, ConstraintSet.END, marker_item_link.id, ConstraintSet.START, 30)
        }
        else {
            // Link
            constraintSet.clear(marker_item_link.id, ConstraintSet.END)
            constraintSet.connect(marker_item_link.id, ConstraintSet.START, menu_item_more.id, ConstraintSet.START)
            constraintSet.connect(marker_item_link.id, ConstraintSet.END, menu_item_more.id, ConstraintSet.END)
            // Customize
            constraintSet.clear(marker_item_undo.id, ConstraintSet.END)
            constraintSet.connect(marker_item_undo.id, ConstraintSet.START, menu_item_more.id, ConstraintSet.START)
            constraintSet.connect(marker_item_undo.id, ConstraintSet.END, menu_item_more.id, ConstraintSet.END)
        }
        val transition = AutoTransition()
        transition.duration = MENU_EXPAND_DURATION
        transition.interpolator = AccelerateDecelerateInterpolator()

        TransitionManager.beginDelayedTransition(main_layout, transition)
        constraintSet.applyTo(main_layout)
        isMarkerOptionExpanded = switch
    }

    private fun updateMyLocationButtonState() {
        if(isMyLocationEnabled) {
            myLocation.setImageDrawable(getDrawable(R.drawable.ic_menu_mylocation_light))
            myLocation.drawable.mutate().setColorFilter(ContextCompat.getColor(applicationContext, R.color.colorPrimary), PorterDuff.Mode.MULTIPLY)
        }
        else {
            myLocation.setImageDrawable(getDrawable(R.drawable.ic_menu_mylocation_dark))
        }
    }

    private fun updateLinkButtonState() {
        if(isLinkMode) {
            marker_link.setImageDrawable(getDrawable(R.drawable.ic_action_link_light))
            marker_link.drawable.mutate().setColorFilter(ContextCompat.getColor(applicationContext, R.color.colorPrimary), PorterDuff.Mode.MULTIPLY)
        }
        else {
            marker_link.setImageDrawable(getDrawable(R.drawable.ic_action_link_dark))
            tempLine = null
            startLatLng = null
            previousLatLng = null
            itemList.filter { it is Polyline }.filter { (it as Polyline).points.size < 2 }.map {
                (it as Polyline).remove()
                itemList.remove(it)
            }
        }
        drawerListAdapter?.notifyDataSetChanged()
    }

    private fun updateDeleteButtonState() {
        if(isDeleteMode) {
            item_delete.setImageDrawable(getDrawable(R.drawable.ic_action_delete_light))
            item_delete.drawable.mutate().setColorFilter(ContextCompat.getColor(applicationContext, R.color.colorPrimary), PorterDuff.Mode.MULTIPLY)
        }
        else {
            item_delete.setImageDrawable(getDrawable(R.drawable.ic_action_delete_dark))
        }
    }

    private fun disableMyLocation() {
        isMyLocationEnabled = false
        updateMyLocationButtonState()
    }

}
