package com.simples.j.whereami;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Created by j on 30/12/2017.
 *
 */

public class FusedLocationSingleton {

    private static FusedLocationSingleton mInstance;
    private FusedLocationProviderClient mFusedLocationClient;
    private Location lastLocation;

    private FusedLocationSingleton(Context context) {
        initialize(context);
    }

    public static FusedLocationSingleton getInstance(Context context) {
        if(mInstance == null) {
            mInstance = new FusedLocationSingleton(context);
            Log.i(context.getPackageName(), "Create new instance");
        }
        else Log.i(context.getPackageName(), "Return exist instance");

        return mInstance;
    }

    private void initialize(Context context) {
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    public void enableLocationUpdate(Context context, long interval, long fastInterval, int priority, LocationCallback callback) {
        if(ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationRequest request = new LocationRequest();
            request.setInterval(interval);
            request.setFastestInterval(fastInterval);
            request.setPriority(priority);

            mFusedLocationClient.requestLocationUpdates(request, callback, null);
        }
    }

    public void disableLocationUpdate(LocationCallback callback) {
        mFusedLocationClient.removeLocationUpdates(callback);
    }

    public String getAddrFromCoordinate(Context context, double lat, double lng) {

        StringBuilder builder = new StringBuilder();
        try{
            Geocoder coder = new Geocoder(context, Locale.getDefault());
            List<Address> addresses = coder.getFromLocation(lat, lng, 1);
            if(addresses != null) {
                if(addresses.size() > 0) {
                    for(int i = 0; i <= addresses.get(0).getMaxAddressLineIndex(); i++) {
                        builder.append(addresses.get(0).getAddressLine(i));
                    }
                }
            }

        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return builder.toString();
    }

    public Location getLastLocation(Context context){
        if(ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mFusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    lastLocation = location;
                }
            });
        }
        return lastLocation;
    }
}
