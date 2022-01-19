package com.wevois.fenavigation.repository;

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.wevois.fenavigation.CommonMethods;

import java.nio.charset.StandardCharsets;

public class SplashRepository {
    CommonMethods common = CommonMethods.getInstance();

    public void getSettingsData(Activity activity) {
        SharedPreferences preferences = activity.getSharedPreferences("FirebasePath", MODE_PRIVATE);
        new Thread(() -> {
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
        }).start();
        new Thread(() -> {
            getFENavigationData(activity,preferences);
        }).start();
        new Thread(() -> {
            Log.d("TAG", "getSettingsData: check data ");
            common.getStoRef(activity).child("Defaults/PreviousDayMessage.json").getMetadata().addOnSuccessListener(storageMetadata -> {
                Log.d("TAG", "getSettingsData: check data A ");
                long fileCreationTime = storageMetadata.getCreationTimeMillis();
                long fileDownloadTime = preferences.getLong("PreviousDayMessageDownloadTime", 0);
                Log.d("TAG", "getSettingsData: check data A1 "+fileCreationTime+"   "+fileDownloadTime);
                if (fileDownloadTime != fileCreationTime) {
                    Log.d("TAG", "getSettingsData: check data A2 "+fileCreationTime+"   "+fileDownloadTime);
                    common.getStoRef(activity).child("Defaults/PreviousDayMessage.json").getBytes(10000000).addOnSuccessListener(taskSnapshot -> {
                        try {
                            String str = new String(taskSnapshot, StandardCharsets.UTF_8);
                            preferences.edit().putString("PreviousDayMessage", str).apply();
                            Log.d("TAG", "getSettingsData: check data A3 "+fileCreationTime+"   "+fileDownloadTime+"   "+str);
                            preferences.edit().putLong("PreviousDayMessageDownloadTime", fileCreationTime).apply();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                }
            });
        }).start();
    }

    public void getFENavigationData(Activity activity, SharedPreferences preferences) {
        common.getDatabaseForApplication(activity).child("WastebinMonitor/FieldExecutive/" + preferences.getString("uid", "")).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    if (dataSnapshot.hasChild("isAppOpen")) {
                        preferences.edit().putString("isAppOpen", dataSnapshot.child("isAppOpen").getValue().toString()).apply();
                    }
                    if (dataSnapshot.hasChild("isPictureInPictureAllow")) {
                        preferences.edit().putString("isPictureInPictureAllow", dataSnapshot.child("isPictureInPictureAllow").getValue().toString()).apply();
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }
}
