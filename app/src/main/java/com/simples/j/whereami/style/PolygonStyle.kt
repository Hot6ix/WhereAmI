package com.simples.j.whereami.style

import java.io.Serializable

/**
 * Created by j on 05/02/2018.
 *
 */
data class PolygonStyle(
        override var id: String,
        override var color: Int,
        override var colorMode: String = "normal",
        var width: Int,
        var fill: Int,
        var fillColor: Int
): ItemStyle(), Serializable