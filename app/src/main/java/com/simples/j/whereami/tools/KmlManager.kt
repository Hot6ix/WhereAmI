package com.simples.j.whereami.tools

import android.content.Context
import android.os.Environment
import android.support.v4.content.ContextCompat
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import com.simples.j.whereami.MapActivity
import com.simples.j.whereami.R
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Created by james on 2018-01-20.
 *
 */

class KmlManager(private var context: Context, private var googleMap: GoogleMap) {

    var itemList: ArrayList<KmlPlacemark> = ArrayList()
    var itemInfoList: ArrayList<KmlInfo> = ArrayList()
    var lineDistanceList: ArrayList<Marker> = ArrayList()
    var polygonAreaList: ArrayList<Marker> = ArrayList()
    var isLoadedFromFile = false

    fun checkStorageState(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    fun saveKmlToExternal(items: ArrayList<KmlPlacemark>) {
        val file = File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "footprint.kml")
        val output = FileOutputStream(file)
        KmlSerializer(context, items).serialize(output)
    }

    fun loadKmlFromExternal(): Boolean {
        val file = File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "footprint.kml")
        if(file.exists()) {
            val inputStream = FileInputStream(file)
            itemInfoList = KmlParser().parse(inputStream)
            addItemsToMap()
            isLoadedFromFile = true
            return true
        }
        isLoadedFromFile = false
        return false
    }

    private fun addItemsToMap() {
        for(item in itemInfoList) {
            when(item.type) {
                KmlPlacemark.TYPE_POINT -> {
                    val marker = googleMap.addMarker(MarkerOptions()
                            .draggable(true)
                            .position(item.coordinates[0])
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)))
                    itemList.add(KmlPlacemark(marker, item.name, item.description, item.styleUrl, item.coordinates, item.type))
                }
                KmlPlacemark.TYPE_LINE -> {
                    val line = googleMap.addPolyline(PolylineOptions()
                            .clickable(true)
                            .color(ContextCompat.getColor(context, R.color.colorPrimary))
                            .addAll(item.coordinates))
                    line.tag = item.name
                    itemList.add(KmlPlacemark(line, item.name, item.description, item.styleUrl, item.coordinates, item.type))

                    item.coordinates.mapIndexed { index, latLng ->
                        if(index+1 < item.coordinates.size) {
                            val distanceMarker = googleMap.addMarker(MarkerOptions()
                                    .position(Utils.getPointsBound(arrayListOf(latLng, item.coordinates[index+1])).center)
                                    .icon(BitmapDescriptorFactory.fromBitmap(Utils.getDistanceIcon(latLng, item.coordinates[index+1], context))))
                            distanceMarker.title = MapActivity.TAG_DISTANCE
                            distanceMarker.tag = line.id
                            lineDistanceList.add(distanceMarker)
                        }
                    }
                }
                KmlPlacemark.TYPE_POLYGON -> {
                    val polygon = googleMap.addPolygon(PolygonOptions()
                            .fillColor(ContextCompat.getColor(context, R.color.colorPrimary30))
                            .clickable(true)
                            .addAll(item.coordinates))
                    itemList.add(KmlPlacemark(polygon, item.name, item.description, item.styleUrl, item.coordinates, item.type))

                    val areaMarker = googleMap.addMarker(MarkerOptions()
                            .position(Utils.getCenterOfPoints(item.coordinates))
                            .icon(BitmapDescriptorFactory.fromBitmap(Utils.getAreaIcon(item.coordinates, context))))
                    areaMarker.title = MapActivity.TAG_AREA
                    areaMarker.tag = polygon.id
                    polygonAreaList.add(areaMarker)
                }
            }
        }
    }
}