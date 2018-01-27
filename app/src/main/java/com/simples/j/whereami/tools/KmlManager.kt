package com.simples.j.whereami.tools

import android.content.Context
import android.os.Environment
import android.support.v4.content.ContextCompat
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import com.simples.j.whereami.R
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Created by james on 2018-01-20.
 *
 */

class KmlManager(private var context: Context, private var googleMap: GoogleMap) {

    var placemarkList: ArrayList<KmlPlacemark>? = null
    var itemList = ArrayList<Any>()
    var lineDistanceList: ArrayList<Marker> = ArrayList()
    var polygonAreaList: ArrayList<Marker> = ArrayList()

    fun checkStorageState(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    fun saveKmlToExternal(items: ArrayList<Any>) {
        val file = File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "a.kml")
        val output = FileOutputStream(file)
        KmlSerializer(context, items).serialize(output)
    }

    fun loadKmlFromExternal(): Boolean {
        val file = File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "a.kml")
        if(file.exists()) {
            val inputStream = FileInputStream(file)
            placemarkList = KmlParser().parse(inputStream)
            addItemsToMap()
            return true
        }
        return false
    }

    private fun addItemsToMap() {
        for(item in placemarkList!!) {
            when(item.type) {
                KmlPlacemark.TYPE_POINT -> {
                    val marker = googleMap.addMarker(MarkerOptions()
                            .draggable(true)
                            .position(item.coordinates[0])
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)))
                    marker.tag = item.name
                    itemList.add(marker)
                }
                KmlPlacemark.TYPE_LINE -> {

                    val line = googleMap.addPolyline(PolylineOptions()
                            .clickable(true)
                            .color(ContextCompat.getColor(context, R.color.colorPrimary))
                            .addAll(item.coordinates))
                    line.tag = item.name
                    itemList.add(line)

                    item.coordinates.mapIndexed { index, latLng ->
                        if(index+1 < item.coordinates.size) {
                            val distanceMarker = googleMap.addMarker(MarkerOptions()
                                    .position(Utils.getCenterOfPoints(arrayListOf(latLng, item.coordinates[index+1])))
                                    .icon(BitmapDescriptorFactory.fromBitmap(Utils.getDistanceIcon(latLng, item.coordinates[index+1], context))))
                            distanceMarker.title = "DISTANCE"
                            distanceMarker.tag = line.id
                            lineDistanceList.add(distanceMarker)
                        }
                    }
                }
                KmlPlacemark.TYPE_POLYGON -> {

                    val polygon = googleMap.addPolygon(PolygonOptions()
                            .fillColor(ContextCompat.getColor(context, R.color.colorPrimary))
                            .clickable(true)
                            .addAll(item.coordinates))
                    polygon.tag = item.name
                    itemList.add(polygon)

                    val areaMarker = googleMap.addMarker(MarkerOptions()
                            .position(Utils.getCenterOfPoints(item.coordinates))
                            .icon(BitmapDescriptorFactory.fromBitmap(Utils.getAreaIcon(item.coordinates, context))))
                    areaMarker.title = "AREA"
                    areaMarker.tag = polygon.id
                    polygonAreaList.add(areaMarker)
                }
            }
        }
    }
}