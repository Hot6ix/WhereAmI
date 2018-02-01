package com.simples.j.whereami

import android.Manifest
import android.app.Activity
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
import com.simples.j.whereami.tools.*
import kotlinx.android.synthetic.main.activity_map.*

class MapActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnCameraIdleListener, GoogleMap.OnCameraMoveStartedListener, GoogleMap.OnMapLongClickListener, GoogleMap.OnMarkerClickListener, GoogleMap.OnMarkerDragListener, GoogleMap.OnPolylineClickListener, GoogleMap.OnPolygonClickListener,  View.OnClickListener, DrawerListAdapter.OnItemClickListener {

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
    private var markerIndex = 1
    private var lineIndex = 1
    private var polygonIndex = 1
    private var distanceMeasureType: String? = null
    private var areaMeasureType: String? = null

    private var isMyLocationEnabled = false
    private var isShowDistanceEnabled = false
    private var isShowAreaEnabled = false
    private var isCameraMoving = false
    private var isLinkMode = false
    private var isDeleteMode = false
    private var isInfoViewCollapsed = true
    private var isMenuLayoutExpanded = false
    private var isMarkerOptionExpanded = false

    private var itemList = ArrayList<KmlPlacemark>()
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

        val layoutManager = LinearLayoutManager(applicationContext)
        left_drawer.layoutManager = layoutManager

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
                            itemList.add(KmlPlacemark(currentMarker!!, MY_LOCATION_NAME, null, null, arrayListOf(myLocation), KmlPlacemark.TYPE_POINT))
                            selectedItem = currentMarker
                        }
                        else {
                            currentMarker!!.position = myLocation
                        }
                        itemList.filter { it.item == currentMarker }.map { setAddressName(it.name) }
                        drawerListAdapter?.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        isShowDistanceEnabled = sharedPref.getBoolean(resources.getString(R.string.pref_show_distance_id), false)
        isShowAreaEnabled = sharedPref.getBoolean(resources.getString(R.string.pref_show_area_id), false)
        lineDistanceList.map { it.isVisible = isShowDistanceEnabled }
        polygonAreaList.map { it.isVisible = isShowAreaEnabled }

        if(isShowDistanceEnabled) {
            if(distanceMeasureType != null && distanceMeasureType != sharedPref.getString(resources.getString(R.string.pref_distance_action_id), "0")) {
                Log.i(applicationContext.packageName, "Because measureType changed, redraw distance markers")
                lineDistanceList.map {it.remove() }
                lineDistanceList.clear()
                itemList.map {
                    if(it.type == KmlPlacemark.TYPE_LINE) {
                        val item = it.item as Polyline
                        it.coordinates.mapIndexed { index, latLng ->
                            if(index+1 < it.coordinates.size) {
                                val distanceMarker = mMap.addMarker(MarkerOptions()
                                        .position(Utils.getPointsBound(arrayListOf(latLng, it.coordinates[index+1])).center)
                                        .icon(BitmapDescriptorFactory.fromBitmap(Utils.getDistanceIcon(latLng, it.coordinates[index+1], applicationContext))))
                                distanceMarker.title = "DISTANCE"
                                distanceMarker.tag = item.id
                                lineDistanceList.add(distanceMarker)
                            }
                        }
                    }
                }
            }
        }
        if(isShowAreaEnabled) {
            if(areaMeasureType != null && areaMeasureType != sharedPref.getString(resources.getString(R.string.pref_area_action_id), "0")) {
                Log.i(applicationContext.packageName, "Because measureType changed, redraw area markers")
                polygonAreaList.map {it.remove() }
                polygonAreaList.clear()
                itemList.map {
                    if(it.type == KmlPlacemark.TYPE_POLYGON) {
                        val item = it.item as Polygon
                        val areaMarker = mMap.addMarker(MarkerOptions()
                                .position(Utils.getCenterOfPoints(it.coordinates))
                                .icon(BitmapDescriptorFactory.fromBitmap(Utils.getAreaIcon(it.coordinates, applicationContext))))
                        areaMarker.title = "AREA"
                        areaMarker.tag = item.id
                        polygonAreaList.add(areaMarker)
                    }
                }
            }
        }

        distanceMeasureType = sharedPref.getString(resources.getString(R.string.pref_distance_action_id), "0")
        areaMeasureType = sharedPref.getString(resources.getString(R.string.pref_area_action_id), "0")
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
        when {
            isLinkMode -> {
                isLinkMode = false
                updateLinkButtonState()
            }
            isDeleteMode -> {
                isDeleteMode = false
                updateDeleteButtonState()
            }
            drawer_layout.isDrawerOpen(left_drawer) -> drawer_layout.closeDrawers()
            !isInfoViewCollapsed -> {
                collapseInfoView()
                if(isMarkerOptionExpanded) switchMarkerOption(false)
            }
            else -> super.onBackPressed()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(resultCode) {
            Activity.RESULT_OK -> {
                if(data != null) {
                    itemList.filter { it.item == selectedItem }.map {
                        val resultItem = data.extras[DetailActivity.ITEM] as KmlInfo
                        it.name = resultItem.name
                        it.description = resultItem.description
                        setAddressName(it.name)
                        drawerListAdapter?.notifyDataSetChanged()
                    }
                }
            }
        }
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
        mMap.uiSettings.isMapToolbarEnabled = false
        mMap.setPadding(0, 200, 0, 0)

        kmlManager = KmlManager(applicationContext, mMap)
        if(kmlManager.loadKmlFromExternal()) {
            itemList = kmlManager.itemList
            lineDistanceList = kmlManager.lineDistanceList
            polygonAreaList = kmlManager.polygonAreaList

            drawerListAdapter = DrawerListAdapter(itemList, applicationContext)
            drawerListAdapter?.setOnItemClickListener(this)
            left_drawer.adapter = drawerListAdapter

            lineDistanceList.map { it.isVisible = isShowDistanceEnabled }
            polygonAreaList.map { it.isVisible = isShowAreaEnabled }

            val allItemPoints = ArrayList<LatLng>()
            itemList.map {
                allItemPoints.addAll(it.coordinates)
            }
            val display = Utils.getDisplayResolution(applicationContext)
            if(allItemPoints.size > 0) mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(Utils.getPointsBound(allItemPoints), display[0], display[1], (display[0] * 0.1).toInt()))
        }
    }

    override fun onDrawerItemClick(item: Any, view: View) {
        drawer_layout.closeDrawers()
        if(zoomLevel < MAX_CAMERA_ZOOM) zoomLevel = DEFAULT_CAMERA_ZOOM
        itemList.filter { it.item == item }.map {
            val kmlItem = it.item
            setAddressName(it.name)
            when(kmlItem) {
                is Marker -> {
                    animateCamera(kmlItem.position, zoomLevel, mMap.cameraPosition.bearing)
                    if(!isMarkerOptionExpanded) switchMarkerOption(true)
                    selectedItem = kmlItem
                }
                is Polyline -> {
                    animateCamera(kmlItem.points[0], zoomLevel, mMap.cameraPosition.bearing)
                    if(isMarkerOptionExpanded) switchMarkerOption(false)
                    isLinkMode = false
                    removeEmptyLine()
                    selectedItem = kmlItem
                }
                is Polygon -> {
                    animateCamera(Utils.getPointsBound(kmlItem.points).center, zoomLevel, mMap.cameraPosition.bearing)
                    if(isMarkerOptionExpanded) switchMarkerOption(false)
                    isLinkMode = false
                    removeEmptyLine()
                    selectedItem = kmlItem
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
        itemList.add(KmlPlacemark(marker, MARKER_NAME + " ${markerIndex++}", null, null, arrayListOf(point), KmlPlacemark.TYPE_POINT))
        drawerListAdapter?.notifyDataSetChanged()
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        if(marker.title == TAG_DISTANCE || marker.title == TAG_AREA) {
            return true
        }
        if(marker != currentMarker) disableMyLocation()
        if(isInfoViewCollapsed && !isDeleteMode) expandInfoView()

        if(isDeleteMode) {
            if(currentMarker != null) {
                if(marker == currentMarker) {
                    currentMarker = null
                    disableMyLocation()
                }
            }
            itemList.filter { it.item == marker }.map {
                (it.item as Marker).remove()
            }
            itemList.remove(itemList.single { it.item == marker })
            if(selectedItem == marker && !isInfoViewCollapsed) collapseInfoView()
            if(!isMarkerOptionExpanded) switchMarkerOption(false)
            drawerListAdapter?.notifyDataSetChanged()
            return true
        }
        else {
            selectedItem = marker
            itemList.filter { it.item == marker }.map { setAddressName(it.name) }
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
                    itemList.add(KmlPlacemark(tempLine!!, LINE_NAME + " ${lineIndex++}", null, null, tempLine!!.points, KmlPlacemark.TYPE_LINE))
                }
                else {
                    list = ArrayList(tempLine!!.points)
                    list.add(marker.position)
                    tempLine!!.points = list
                    itemList.single { it.item == tempLine }.coordinates = list
                }
                val distanceMarker = mMap.addMarker(MarkerOptions()
                        .position(Utils.getPointsBound(arrayListOf(previousLatLng!!, marker.position)).center)
                        .icon(BitmapDescriptorFactory.fromBitmap(Utils.getDistanceIcon(previousLatLng!!, marker.position, applicationContext))))
                distanceMarker.title = TAG_DISTANCE
                distanceMarker.tag = tempLine!!.id
                distanceMarker.isVisible = isShowDistanceEnabled
                lineDistanceList.add(distanceMarker)

                previousLatLng = marker.position

                // Polygon
                if (marker.position == startLatLng) {
                    val name = POLYGON_NAME + " ${polygonIndex++}"
                    val polygon = mMap.addPolygon(PolygonOptions()
                            .fillColor(ContextCompat.getColor(applicationContext, R.color.colorPrimary))
                            .clickable(true)
                            .addAll(list))
                    itemList.add(KmlPlacemark(polygon, name, null, null, list, KmlPlacemark.TYPE_POLYGON))
                    setAddressName(name)
                    selectedItem = polygon

                    val areaMarker = mMap.addMarker(MarkerOptions()
                            .position(Utils.getPointsBound(list).center)
                            .icon(BitmapDescriptorFactory.fromBitmap(Utils.getAreaIcon(list, applicationContext))))
                    areaMarker.title = TAG_AREA
                    areaMarker.tag = polygon.id
                    areaMarker.isVisible = isShowAreaEnabled
                    polygonAreaList.add(areaMarker)

                    for (point in list) {
                        itemList.filter { it.item is Marker }.filter { point == (it.item as Marker).position }.map {
                            (it.item as Marker).remove()
                            itemList.remove(it)
                        }
                    }
                    lineDistanceList.filter { tempLine!!.id == it.tag }.map {
                        it.remove()
                        lineDistanceList.remove(it)
                    }
                    tempLine!!.remove()
                    itemList.remove(itemList.single { it.item == tempLine })

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
        if(isMarkerOptionExpanded) switchMarkerOption(false)

        if(isDeleteMode) {
            itemList.filter { it.item == polyline }.map {
                val kmlItem = it.item as Polyline
                lineDistanceList.filter { kmlItem.id == it.tag }.map {
                    it.remove()
                    lineDistanceList.remove(it)
                }
                kmlItem.remove()
                itemList.remove(itemList.single { it.item == polyline })
            }
            if(selectedItem == polyline && !isInfoViewCollapsed) collapseInfoView()
        }
        else {
            selectedItem = polyline
            itemList.filter { it.item == polyline }.map { setAddressName(it.name) }
            animateCamera(polyline.points[0],zoomLevel, mMap.cameraPosition.bearing)
        }
        drawerListAdapter?.notifyDataSetChanged()
    }

    override fun onPolygonClick(polygon: Polygon) {
        if(isInfoViewCollapsed && !isDeleteMode) expandInfoView()
        if(isMarkerOptionExpanded) switchMarkerOption(false)

        if(isDeleteMode) {
            itemList.filter { it.item == polygon }.map {
                val kmlItem = it.item as Polygon
                polygonAreaList.filter { kmlItem.id == it.tag }.map {
                    it.remove()
                    polygonAreaList.remove(it)
                }
                kmlItem.remove()
                itemList.remove(itemList.single { it.item == polygon })
            }
            if(selectedItem == polygon && !isInfoViewCollapsed) collapseInfoView()
        }
        else {
            selectedItem = polygon
            itemList.filter { it.item == polygon }.map { setAddressName(it.name) }
            animateCamera(Utils.getPointsBound(polygon.points).center,zoomLevel, mMap.cameraPosition.bearing)
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
                    val intent = Intent(this, DetailActivity::class.java)
                    val item = itemList.single { it.item == selectedItem }
                    val kmlItem = item.item
                    when(kmlItem) {
                        is Marker -> {
                            intent.putExtra(DetailActivity.ITEM_ID, kmlItem.id)
                        }
                        is Polyline -> {
                            intent.putExtra(DetailActivity.ITEM_ID, kmlItem.id)
                        }
                        is Polygon -> {
                            intent.putExtra(DetailActivity.ITEM_ID, kmlItem.id)
                        }
                    }
                    intent.putExtra(DetailActivity.ITEM, KmlInfo(item.name, item.description, item.styleUrl, item.coordinates, item.type))
                    startActivityForResult(intent, 5)
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
            val name = MY_LOCATION_NAME
            if(currentMarker != null) currentMarker!!.position = ll
            else {
                currentMarker = mMap.addMarker(MarkerOptions().position(ll))
                itemList.add(KmlPlacemark(currentMarker!!, name, null, null, arrayListOf(ll), KmlPlacemark.TYPE_POINT))
            }

            setAddressName(name)
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
            removeEmptyLine()
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

    private fun removeEmptyLine() {
        itemList.filter { it.item is Polyline }.filter { (it.item as Polyline).points.size < 2 }.map {
            (it.item as Polyline).remove()
            itemList.remove(it)
        }
    }

    private fun setAddressName(name: String) {
        if(name.isNotEmpty()) address.text = name
        else address.text = getString(R.string.untitled)
    }

    companion object {
        const val PERMISSION_REQUEST_CODE_LOCATION = 1
        const val PERMISSION_REQUEST_CODE_STORAGE = 2
        const val DEFAULT_CAMERA_ZOOM = 15.0f
        const val MAX_CAMERA_ZOOM = 10.0f
        const val ADDRESS_ANIM_DURATION: Long = 1000
        const val MENU_EXPAND_DURATION: Long = 250
        const val LINE_NAME = "Line"
        const val POLYGON_NAME = "Polygon"
        const val MARKER_NAME = "Marker"
        const val MY_LOCATION_NAME = "My Location"
        const val TAG_DISTANCE = "DISTANCE"
        const val TAG_AREA = "AREA"
    }

}
