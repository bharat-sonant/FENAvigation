package com.wevois.fenavigation.repository;

import static android.content.Context.MODE_PRIVATE;

import android.app.Activity;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.wevois.fenavigation.CommonMethods;

public class SplashRepository {
    CommonMethods common = CommonMethods.getInstance();

    public void getSettingsData(Activity activity) {
        SharedPreferences preferences = activity.getSharedPreferences("FirebasePath", MODE_PRIVATE);
        common.getDatabaseForApplication(activity).child("Settings/FENavigationApplicationSettings").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    if (dataSnapshot.hasChild("current-location-capture-time")) {
                        preferences.edit().putInt("currentLocationCaptureTime", Integer.parseInt(dataSnapshot.child("current-location-capture-time").getValue().toString()) * 1000).apply();
                    }
                    if (dataSnapshot.hasChild("max-distance-covered-per-second")) {
                        preferences.edit().putInt("maxDistanceCoveredPerSecond", Integer.parseInt(dataSnapshot.child("max-distance-covered-per-second").getValue().toString())).apply();
                    }
                    if (dataSnapshot.hasChild("screenOffIntervalTimeInSecond")) {
                        preferences.edit().putInt("appClosedTimerTime", Integer.parseInt(dataSnapshot.child("screenOffIntervalTimeInSecond").getValue().toString()) * 1000).apply();
                    }
                    if (dataSnapshot.hasChild("path-traverse-capture-time")) {
                        preferences.edit().putInt("pathTraverseCaptureTime", Integer.parseInt(dataSnapshot.child("path-traverse-capture-time").getValue().toString()) * 1000).apply();
                    }
                    if (dataSnapshot.hasChild("path-traverse-array-time")) {
                        preferences.edit().putInt("pathTraverseArrayTime", (int) Math.round(Double.parseDouble(dataSnapshot.child("path-traverse-array-time").getValue().toString()) * 1000)).apply();
                    }
                    if (dataSnapshot.hasChild("speed-capture-time-in-seconds")) {
                        preferences.edit().putInt("speedCaptureTime", Integer.parseInt(dataSnapshot.child("speed-capture-time-in-seconds").getValue().toString()) * 1000).apply();
                    }
                    if (dataSnapshot.hasChild("work-stopped-halt-allowed-in-minutes")) {
                        preferences.edit().putInt("maxHaltAllowed", Integer.parseInt(dataSnapshot.child("work-stopped-halt-allowed-in-minutes").getValue().toString())).apply();
                    }
                    if (dataSnapshot.hasChild("halt-allowed-range-in-meter")) {
                        preferences.edit().putInt("haltAllowedRange", Integer.parseInt(dataSnapshot.child("halt-allowed-range-in-meter").getValue().toString())).apply();
                    }
                    if (dataSnapshot.hasChild("halt-distance-covered-check-interval-in-second")) {
                        preferences.edit().putInt("distanceCoveredCheckInterval", Integer.parseInt(dataSnapshot.child("halt-distance-covered-check-interval-in-second").getValue().toString())).apply();
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        common.getDatabaseForApplication(activity).child("WastebinMonitor/FieldExecutive/"+preferences.getString("uid","")+"/isAppOpen").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    preferences.edit().putString("isAppOpen", dataSnapshot.getValue().toString()).apply();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }
}
