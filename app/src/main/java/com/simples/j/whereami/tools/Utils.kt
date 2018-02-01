package com.simples.j.whereami.tools

import android.content.Context
import android.graphics.Bitmap
import android.preference.PreferenceManager
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.SphericalUtil
import com.google.maps.android.ui.IconGenerator
import com.simples.j.whereami.R

/**
 * Created by j on 24/01/2018.
 *
 */
class Utils() {

    companion object {
        fun getPointsBound(points: List<LatLng>): LatLngBounds {
            val bounds = LatLngBounds.builder()
            for(item in points) {
                bounds.include(item)
            }

            return bounds.build()
        }

        fun getCenterOfPoints(points: List<LatLng>): LatLng {
            var latitude = 0.0
            var longitude = 0.0
            for(item in points) {
                latitude += item.latitude
                longitude += item.longitude
            }
            latitude /= points.size
            longitude /= points.size

            return LatLng(latitude, longitude)
        }

        fun getDistance(list: List<LatLng>, context: Context): String {
            val pref = PreferenceManager.getDefaultSharedPreferences(context)
            val measure = pref.getString(context.resources.getString(R.string.pref_distance_action_id), "0")
            val distance = SphericalUtil.computeLength(list)
            return when(measure) {
                MEASURE_INCH -> "%.2f in".format(distance * 39.370078)
                MEASURE_FEET -> "%.2f ft".format(distance * 3.28084)
                MEASURE_YARD -> "%.2f yd".format(distance * 1.093613)
                MEASURE_MILE -> "%.2f mi".format(distance * 0.000621)
                else -> {
                    if(distance >= 1000) "%.2f km".format(distance * 0.001)
                    else distance.toInt().toString() + " m"
                }
            }
        }

        fun getDistanceIcon(start: LatLng, end: LatLng, applicationContext: Context): Bitmap {
            return IconGenerator(applicationContext).makeIcon(getDistance(arrayListOf(start, end), applicationContext))
        }

        fun getAreaOfPolygon(points: List<LatLng>, context: Context): String {
            val pref = PreferenceManager.getDefaultSharedPreferences(context)
            val measure = pref.getString(context.resources.getString(R.string.pref_area_action_id), "0")
            return when(measure) {
                MEASURE_HECTARE -> "%.2f hr".format(SphericalUtil.computeArea(points) * 0.0001)
                MEASURE_ACRE -> "%.2f ac".format(SphericalUtil.computeArea(points) * 0.000247)
                MEASURE_ARE -> "%.2f a".format(SphericalUtil.computeArea(points) * 0.01)
                MEASURE_PYONG -> "%.2f pyong".format(SphericalUtil.computeArea(points) * 0.3025)
                else -> "%.2f m\u00B2".format(SphericalUtil.computeArea(points))
            }
        }

        fun getAreaIcon(points: List<LatLng>, applicationContext: Context): Bitmap {
            return IconGenerator(applicationContext).makeIcon(getAreaOfPolygon(points, applicationContext))
        }

        fun getDisplayResolution(context: Context): Array<Int> {
            return arrayOf(context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels)
        }

        const val MEASURE_INCH = "1"
        const val MEASURE_FEET = "2"
        const val MEASURE_YARD = "3"
        const val MEASURE_MILE = "4"
        const val MEASURE_HECTARE = "1"
        const val MEASURE_ACRE = "2"
        const val MEASURE_ARE = "3"
        const val MEASURE_PYONG = "4"
    }
}