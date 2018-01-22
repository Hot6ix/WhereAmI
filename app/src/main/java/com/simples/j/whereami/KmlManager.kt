package com.simples.j.whereami

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.Polyline
import com.google.maps.android.kml.KmlContainer
import com.google.maps.android.kml.KmlLayer
import com.google.maps.android.kml.KmlPlacemark
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Created by james on 2018-01-20.
 *
 */
class KmlManager(var context: Context) {

    fun checkStorageState(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    fun saveKmlToExternal(markers: ArrayList<Marker>, lines: ArrayList<Polyline>, polygons: ArrayList<Polygon>) {
        val file = File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "a.kml")
        val output = FileOutputStream(file)
        KmlSerializer(context, markers, lines, polygons).serialize(output)
    }

    fun loadKmlFromExternal(googleMap: GoogleMap) {
        val file = File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "a.kml")
        if(file.exists()) {
            val inputStream = FileInputStream(file)
            val layer = KmlLayer(googleMap, inputStream, context)
            layer.addLayerToMap()

            a(layer.containers)
        }
    }

    private fun a(main: Iterable<KmlContainer>): ArrayList<KmlPlacemark> {
        val list = ArrayList<KmlPlacemark>()
        for(item in main) {
//            list.addAll(item.placemarks)
            item.placemarks.map { Log.e("aaaaaaaaaa", it.properties.toString()) }
            item.placemarks.map { Log.e("zzzzzzzzzz", it.geometry.toString()) }

            if(item.hasContainers()) a(item.containers)
        }

        return list
    }
}