package com.simples.j.whereami

import android.location.Location
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import kotlinx.android.synthetic.main.activity_detail.*
import java.text.DateFormat
import java.util.*

class DetailActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var location: Location

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        locationAddress.text = intent.getStringExtra(BUNDLE_ADDRESS)
        location = intent.getParcelableExtra<Location>(BUNDLE_LOCATION)

        locationDetailContent.text =
                "LatLng (Degrees) : ${Location.convert(location.latitude, Location.FORMAT_DEGREES)}, ${Location.convert(location.longitude, Location.FORMAT_DEGREES)}" +
                "\nLatLng (Minutes) : ${Location.convert(location.latitude, Location.FORMAT_MINUTES)}, ${Location.convert(location.longitude, Location.FORMAT_MINUTES)}" +
                "\nLatLng (Seconds) : ${Location.convert(location.latitude, Location.FORMAT_SECONDS)}, ${Location.convert(location.longitude, Location.FORMAT_SECONDS)}"
        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        supportFinishAfterTransition()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 17.0f))
    }

    companion object {
        const val BUNDLE_ADDRESS = "ADDRESS"
        const val BUNDLE_LOCATION = "LOCATION"
    }

}
