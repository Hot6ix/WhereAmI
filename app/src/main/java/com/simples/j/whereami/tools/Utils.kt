package com.simples.j.whereami.tools

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.SphericalUtil
import com.google.maps.android.ui.IconGenerator

/**
 * Created by j on 24/01/2018.
 *
 */
class Utils() {

    companion object {
        fun getCenterOfPoints(points: List<LatLng>): LatLng {
            val bounds = LatLngBounds.builder()
            for(item in points) {
                bounds.include(item)
            }
            return bounds.build().center
        }

        fun getDistanceBetween(start: LatLng, end: LatLng): String {
            val distance = SphericalUtil.computeDistanceBetween(start, end)
            if(distance >= 1000) return "%.2fkm".format(distance * 0.001)
            else return distance.toInt().toString() + "m"
        }

        fun getDistanceIcon(start: LatLng, end: LatLng, applicationContext: Context): Bitmap {
            return IconGenerator(applicationContext).makeIcon(getDistanceBetween(start, end))
        }

        fun getAreaOfPolygon(points: List<LatLng>): String {
            return "%.2fm\u00B2".format(SphericalUtil.computeArea(points))
        }

        fun getAreaIcon(points: List<LatLng>, applicationContext: Context): Bitmap {
            return IconGenerator(applicationContext).makeIcon(getAreaOfPolygon(points))
        }
    }
}