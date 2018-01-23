package com.simples.j.whereami.tools

import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * Created by james on 2018-01-22.
 *
 */

private const val a = "TAG"

class KmlParser {

    var isPlacemarkStart = false
    var isFolderStart = false

    fun parse(input: InputStream) {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        parser.nextTag()

        var placemarkName = ""
        var placemarkDescription = ""
        var placemarkStyleId = ""
        var placemarkCoordinates = ""

        while(parser.next() != XmlPullParser.END_DOCUMENT) {
            val event = parser.eventType
            val name = parser.name

//            if(event == XmlPullParser.START_TAG) Log.d(name, "Start Tag")
//            if(parser.next() == XmlPullParser.TEXT) Log.d(name, parser.text)
//            if(parser.next() == XmlPullParser.END_TAG) Log.d(name, "End Tag")
        }
    }

    private fun readText(parser: XmlPullParser): String {
        var text = ""
        if(parser.next() == XmlPullParser.TEXT) {
            text = parser.text
            parser.nextTag()
        }
        return text
    }

}