package com.simples.j.whereami.tools

/**
 * Created by james on 2018-01-23.
 *
 */
data class KmlPlacemark(var name: String, var description: String, var styleUrl: String, var coordinates: String, val type: String) {
    companion object {
        const val TYPE_POINT = "Point"
        const val TYPE_LINE = "LineString"
        const val TYPE_POLYGON = "Polygon"
    }
}