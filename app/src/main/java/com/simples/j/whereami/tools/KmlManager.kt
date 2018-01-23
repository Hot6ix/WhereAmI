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
    var markerList: ArrayList<Marker> = ArrayList()
    var lineList: ArrayList<Polyline> = ArrayList()
    var polygonList: ArrayList<Polygon> = ArrayList()

    fun checkStorageState(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    fun saveKmlToExternal(markers: ArrayList<Marker>, lines: ArrayList<Polyline>, polygons: ArrayList<Polygon>) {
        val file = File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "a.kml")
        val output = FileOutputStream(file)
        KmlSerializer(context, markers, lines, polygons).serialize(output)
    }

    fun loadKmlFromExternal() {
        val file = File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "a.kml")
        if(file.exists()) {
            val inputStream = FileInputStream(file)
            placemarkList = KmlParser().parse(inputStream)
            addItemsToMap()
        }
    }

    private fun addItemsToMap() {
        for(item in placemarkList!!) {
            when(item.type) {
                KmlPlacemark.TYPE_POINT -> {
                    val point = item.coordinates.split(",")
                    val marker = googleMap.addMarker(MarkerOptions()
                            .draggable(true)
                            .position(LatLng(point[1].toDouble(), point[0].toDouble()))
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)))
                    marker.tag = item.name
                    markerList.add(marker)
                }
                KmlPlacemark.TYPE_LINE -> {
                    val points = item.coordinates.split("\n")
                    val pointList = ArrayList<LatLng>()
                    points.map {
                        val a = it.split(",")
                        if(a.size > 2)
                            pointList.add(LatLng(a[1].toDouble(), a[0].toDouble()))
                    }

                    val line = googleMap.addPolyline(PolylineOptions()
                            .clickable(true)
                            .color(ContextCompat.getColor(context, R.color.colorPrimary))
                            .addAll(pointList))
                    line.tag = item.name
                    lineList.add(line)
                }
                KmlPlacemark.TYPE_POLYGON -> {
                    val points = item.coordinates.split(" ")
                    val pointList = ArrayList<LatLng>()
                    points.map {
                        val a = it.split(",")
                        if(a.size > 2)
                            pointList.add(LatLng(a[1].toDouble(), a[0].toDouble()))
                    }

                    val polygon = googleMap.addPolygon(PolygonOptions()
                            .fillColor(ContextCompat.getColor(context, R.color.colorPrimary))
                            .clickable(true)
                            .addAll(pointList))
                    polygon.tag = item.name
                    polygonList.add(polygon)
                }
            }
        }
    }
}