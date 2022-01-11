package com.wevois.fenavigation.viewmodels;

import static android.content.Context.MODE_PRIVATE;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.ViewModel;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.wevois.fenavigation.CommonMethods;
import com.wevois.fenavigation.repository.SplashRepository;
import com.wevois.fenavigation.views.DutyIn;
import com.wevois.fenavigation.views.HomeMapsActivity;
import com.wevois.fenavigation.views.LoginScreen;
import com.wevois.fenavigation.views.SplashScreen;

public class SplashViewModel extends ViewModel {
    String[] PERMISSION = {
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
    };
    Activity activity;
    SharedPreferences preferences;
    CommonMethods common = CommonMethods.getInstance();
    SplashRepository repository = new SplashRepository();
    Handler handler;

    public void init(SplashScreen activitys) {
        activity = activitys;
        preferences = activity.getSharedPreferences("FirebasePath", MODE_PRIVATE);
//        preferences.edit().putString("dbPath", "https://iejaipurgreater.firebaseio.com/").apply();
//        preferences.edit().putString("storagePath", "gs://dtdnavigator.appspot.com/Jaipur-Greater").apply();
        preferences.edit().putString("dbPath", "https://dtdnavigatortesting.firebaseio.com/").apply();
        preferences.edit().putString("storagePath", "gs://dtdnavigator.appspot.com/Test").apply();
        repository.getSettingsData(activity);
        checkPermission();
    }

    public void checkPermission() {
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, PERMISSION, 500);
            return;
        }
        callTimer();
    }

    public void callTimer() {
        new Handler().postDelayed(() -> {
            if (preferences.getString("loggedIn", " ").equals("1")) {
                checkNetwork();
            } else {
                Intent i = new Intent(activity, LoginScreen.class);
                activity.startActivity(i);
                activity.finish();
            }
        }, 3000);
    }

    private void checkNetwork() {
        common.checkNetWork(activity).observeForever(result -> {
            if (result) {
                common.getDatabaseForApplication(activity).child("Settings/LatestVersions/fENavigation").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String version = "";
                        try {
                            version = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
                        } catch (PackageManager.NameNotFoundException e) {
                            e.printStackTrace();
                        }
                        if (snapshot.getValue() != null) {
                            if (version.equals(snapshot.getValue().toString())) {
                                if (preferences.getString("dutyIn", " ").equals(common.date())) {
                                    Intent i = new Intent(activity, HomeMapsActivity.class);
                                    activity.startActivity(i);
                                    activity.finish();
                                } else {
                                    Intent i = new Intent(activity, DutyIn.class);
                                    activity.startActivity(i);
                                    activity.finish();
                                }
                            } else {
                                common.showAlertBox("Version Expired", false, activity, true);
                            }
                        } else {
                            common.showAlertBox("Version Expired", false, activity, true);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {

                    }
                });
            } else {
                if (handler == null) {
                    common.setProgressBar("No internet connection.", activity, activity);
                }
                handler = new Handler();
                handler.postDelayed(runnable, 1000);
            }
        });
    }

    public Runnable runnable = this::checkNetwork;
}