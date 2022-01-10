package com.wevois.fenavigation;

public class Model {
    String time;
    String latLng;

    public Model(String time, String latLng) {
        this.time = time;
        this.latLng = latLng;
    }

    public String getTime() {
        return time;
    }

    public String getLatLng() {
        return latLng;
    }
}
