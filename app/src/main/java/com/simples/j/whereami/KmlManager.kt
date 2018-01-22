package com.simples.j.whereami

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import com.google.maps.android.data.kml.KmlContainer
import com.google.maps.android.data.kml.KmlLayer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Created by james on 2018-01-20.
 *
 */

private const val TYPE_POINT = "Point"
private const val TYPE_LINE = "LineString"
private const val TYPE_POLYGON = "Polygon"

class KmlManager(var context: Context, googleMap: GoogleMap) {

    private var map = googleMap
    var a = false
    var style: KmlContainer? = null

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
            val layer = KmlLayer(map, inputStream, context)
            layer.addLayerToMap()
            a(layer.containers)
        }
    }

    private fun a(main: Iterable<KmlContainer>) {
        for(item in main) {
            if(!a) {
                style = item
                a = true
                Log.e("main", style.toString())
            }
            if(item.hasPlacemarks()) {
                item.placemarks.map {
                    Log.e("name", it.properties.toString())
                    Log.e("style-id", it.styleId)
                    if(it.styleId.contains("normal"))
                        Log.e("style", style!!.getStyle(it.styleId).toString())
                    else
                        Log.e("style", style!!.getStyle(it.styleId + "-normal").toString())
                    Log.e("geometryType", it.geometry.geometryType)
                    Log.e("geometryObject", it.geometry.geometryObject.toString())
                }
            }

            if(item.hasContainers()) a(item.containers)
        }

    }
}