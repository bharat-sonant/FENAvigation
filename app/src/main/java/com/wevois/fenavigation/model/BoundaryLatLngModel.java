package com.wevois.fenavigation.model;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;

public class BoundaryLatLngModel {
    String key;
    ArrayList<LatLng> latLngArrayList;

    public BoundaryLatLngModel(String key, ArrayList<LatLng> latLngArrayList) {
        this.key = key;
        this.latLngArrayList = latLngArrayList;
    }

    public String getKey() {
        return key;
    }

    public ArrayList<LatLng> getLatLngArrayList() {
        return latLngArrayList;
    }

    @Override
    public String toString() {
        return "BoundaryLatLngModel{" +
                "key='" + key + '\'' +
                ", latLngArrayList=" + latLngArrayList +
                '}';
    }
}
