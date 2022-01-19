package com.wevois.fenavigation.viewmodels;

import static android.content.Context.MODE_PRIVATE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.databinding.ObservableField;
import androidx.lifecycle.ViewModel;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.wevois.fenavigation.CommonMethods;
import com.wevois.fenavigation.MyService;
import com.wevois.fenavigation.repository.SplashRepository;
import com.wevois.fenavigation.views.DutyIn;
import com.wevois.fenavigation.views.HomeMapsActivity;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class DutyInViewModel extends ViewModel {
    Activity activity;
    public static final int REFRESH_LOCATION = 750;
    boolean isFakeLocation,isMoved=true;
    Location finalLocation;
    CommonMethods common = CommonMethods.getInstance();
    public ObservableField<String> addressTv = new ObservableField<>("");
    public ObservableField<String> userName = new ObservableField<>("");
    SharedPreferences preferences;
    public static final String TIME_SERVER = "ntp.xs4all.nl";

    public void init(DutyIn dutyIn) {
        activity = dutyIn;
        preferences = activity.getSharedPreferences("FirebasePath", MODE_PRIVATE);
        userName.set(preferences.getString("name", ""));
        getLocationPermission();
        new SplashRepository().getFENavigationData(activity,preferences);
    }

    public void dutyInClick() {
        if (isMoved) {
            isMoved = false;
            checkTime();
        }
    }

    public void refreshClick() {
        getLocationPermission();
    }

    public void getLocationPermission() {
        LocationServices.getSettingsClient(activity).checkLocationSettings(new LocationSettingsRequest.Builder()
                .addLocationRequest(new LocationRequest().setInterval(5000).setFastestInterval(1000).setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY))
                .setAlwaysShow(true).setNeedBle(true).build())
                .addOnCompleteListener(task1 -> {
                    try {
                        task1.getResult(ApiException.class);
                        getLocation();
                    } catch (ApiException e) {
                        if (e instanceof ResolvableApiException) {
                            try {
                                ResolvableApiException resolvable = (ResolvableApiException) e;
                                resolvable.startResolutionForResult(activity, REFRESH_LOCATION);
                            } catch (IntentSender.SendIntentException ex) {
                                ex.printStackTrace();
                            }
                        }
                    }
                });
    }

    public void getLocation(){
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        LocationServices.getFusedLocationProviderClient(activity).requestLocationUpdates(new LocationRequest().setInterval(5000).setFastestInterval(1000).setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY),
                new LocationCallback() {
                    @Override
                    public void onLocationResult(LocationResult locationResult) {
                        super.onLocationResult(locationResult);
                        finalLocation = locationResult.getLastLocation();
                        isFakeLocation = common.isMockLocationOn(finalLocation, activity);
                        LocationServices.getFusedLocationProviderClient(activity).removeLocationUpdates(this);
                        if (finalLocation != null) {
                            try {
                                Geocoder geocoder = new Geocoder(activity, Locale.getDefault());
                                String[] address = geocoder.getFromLocation(finalLocation.getLatitude(), finalLocation.getLongitude(), 5).get(0).getAddressLine(0).split(", " + geocoder
                                        .getFromLocation(finalLocation.getLatitude(), finalLocation.getLongitude(), 5).get(0).getLocality());
                                addressTv.set(address[0]);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }, Looper.getMainLooper());
    }

    @SuppressLint("StaticFieldLeak")
    private void checkTime() {
        new AsyncTask<Void, Void, Long>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                common.setProgressBar("Please wait...",activity,activity);
            }

            @Override
            protected Long doInBackground(Void... p) {
                long returnTime = 0;
                NTPUDPClient timeClient = new NTPUDPClient();
                timeClient.setDefaultTimeout(1000);
                for (int retries = 7; retries >= 0; retries--) {
                    try {
                        InetAddress inetAddress = InetAddress.getByName(TIME_SERVER);
                        TimeInfo timeInfo = timeClient.getTime(inetAddress);
                        returnTime = timeInfo.getMessage().getTransmitTimeStamp().getTime();
                        return returnTime;
                    } catch (IOException e) {
                    }
                }
                return returnTime;
            }

            @Override
            protected void onPostExecute(Long result) {
                if (result != 0) {
                    Date date = new Date(result);
                    Date dates = new Date(new Date().getTime());
                    int timeGap = common.timeDiff(date, dates);
                    if (timeGap < 5) {
                        timeIn();
                    } else {
                        common.getDatabaseForApplication(activity).child("ServerTimeResponse/FENavigation/ServerTimeNotMatched/"+preferences.getString("uid","")).runTransaction(new Transaction.Handler() {
                            @NonNull
                            @Override
                            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                                if (currentData.getValue() == null) {
                                    currentData.setValue(1);
                                } else {
                                    currentData.setValue(String.valueOf(Integer.parseInt(currentData.getValue().toString()) + 1));
                                }
                                return Transaction.success(currentData);
                            }

                            @Override
                            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                            }
                        });
                        isMoved=true;
                        common.closeDialog(activity);
                        common.showAlertBox("Device Time Setting are not working properly",false,activity,false);
                    }
                } else {
                    common.getDatabaseForApplication(activity).child("ServerTimeResponse/FENavigation/ServerTimeNotResponse/"+preferences.getString("uid","")).runTransaction(new Transaction.Handler() {
                        @NonNull
                        @Override
                        public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                            if (currentData.getValue() == null) {
                                currentData.setValue(1);
                            } else {
                                currentData.setValue(String.valueOf(Integer.parseInt(currentData.getValue().toString()) + 1));
                            }
                            return Transaction.success(currentData);
                        }

                        @Override
                        public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                        }
                    });
                    timeIn();
                }
            }
        }.execute();
    }

    @SuppressLint("StaticFieldLeak")
    private void timeIn() {
        common.checkNetWork(activity).observeForever(result->{
            if (result) {
                if (addressTv.get().length()>0) {
                    common.getDatabaseForApplication(activity).child("FEAttendance").child(preferences.getString("uid","")).child(common.year()).child(common.monthName()).child(common.date()).child("inDetails").addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.getValue()==null){
                                try {
                                    if (isFakeLocation) {
                                        common.getDatabaseForApplication(activity).child("FakeLocation/FENavigation/" + preferences.getString("uid","")).setValue(common.date());
                                    }
                                } catch (Exception e) {
                                }
                                HashMap<String,String> map = new HashMap<>();
                                map.put("address", addressTv.get());
                                map.put("location", finalLocation.getLatitude()+","+finalLocation.getLongitude());
                                map.put("time", common.currentTime());
                                map.put("status", "0");
                                common.getDatabaseForApplication(activity).child("FEAttendance").child(preferences.getString("uid","")).child(common.year()).child(common.monthName()).child(common.date()).child("inDetails").setValue(map)
                                        .addOnCompleteListener(task2 -> {
                                            if (task2.isSuccessful()) {
                                                activity.stopService(new Intent(activity, MyService.class));
                                                preferences.edit().putString("LocationHistory","").apply();
                                                preferences.edit().putString("HaltHistory","").apply();
                                                preferences.edit().putString("dutyIn", common.date()).apply();
                                                activity.startActivity(new Intent(activity, HomeMapsActivity.class));
                                                activity.finish();
                                                common.closeDialog(activity);
                                            }
                                        });
                            }else {
                                preferences.edit().putString("dutyIn", common.date()).apply();
                                activity.startActivity(new Intent(activity, HomeMapsActivity.class));
                                activity.finish();
                                common.closeDialog(activity);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {

                        }
                    });
                } else {
                    isMoved=true;
                    common.showAlertBox("please Refresh Location",false,activity,false);
                }
            } else {
                isMoved=true;
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setMessage("Internet not Available")
                        .setCancelable(false)
                        .setPositiveButton("Retry", (dialog, id) -> {
                            timeIn();
                            dialog.cancel();
                        })
                        .setNegativeButton("Exit", (dialog, i) -> activity.finish());
                AlertDialog alertDialog = builder.create();
                alertDialog.show();
            }
            common.closeDialog(activity);
        });
    }

    public void resume(){
        isMoved=true;
    }

    public void destroy(){
        common.closeDialog(activity);
    }
}
