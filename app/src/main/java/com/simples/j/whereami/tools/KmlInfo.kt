package com.simples.j.whereami.tools

import android.os.Parcel
import android.os.Parcelable
import com.google.android.gms.maps.model.LatLng

/**
 * Created by j on 29/01/2018.
 *
 */

data class KmlInfo(var name: String, var description: String?, var styleUrl: String?, var coordinates: List<LatLng>, val type: String) : Parcelable {

    override fun toString(): String {
        return "name : ${name}, description : ${description}, styleUrl : ${styleUrl}, points : ${coordinates.toString()}, type : ${type}"
    }

    constructor(source: Parcel) : this(
            source.readString(),
            source.readString(),
            source.readString(),
            ArrayList<LatLng>().apply { source.readList(this, LatLng::class.java.classLoader) },
            source.readString()
    )

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = with(dest) {
        writeString(name)
        writeString(description)
        writeString(styleUrl)
        writeList(coordinates)
        writeString(type)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<KmlInfo> = object : Parcelable.Creator<KmlInfo> {
            override fun createFromParcel(source: Parcel): KmlInfo = KmlInfo(source)
            override fun newArray(size: Int): Array<KmlInfo?> = arrayOfNulls(size)
        }
    }
}