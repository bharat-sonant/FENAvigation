package com.wevois.fenavigation;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MyService extends Service {
    Intent locationInfo;
    SharedPreferences preferences;
    boolean mobileOffDataCaptured = false, requiredDistanceCovered = false;
    CountDownTimer countDownTimerPath, countDownTimerTemp,countDownTimerLocation;
    DatabaseReference databaseReferencePath;
    CommonMethods common = CommonMethods.getInstance();
    float speed;
    String haltStartTime;
    int maxDistance = 15, maxDistanceCanCover = 0, haltDuration;
    double haltStartLat = 0.0, haltStartLng = 0.0, currentLat = 0.0, currentLng = 0.0, previousLat, previousLng;
    private LocationCallback locationCallback;
    public static JSONObject jsonObjectLocationHistory = new JSONObject(), jsonObjectHalt = new JSONObject();

    public MyService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        databaseReferencePath = common.getDatabaseForApplication(this);
        preferences = getSharedPreferences("FirebasePath", MODE_PRIVATE);

        String locationHistoryData = preferences.getString("LocationHistory","");
        if (!locationHistoryData.equalsIgnoreCase("")) {
            try {
                jsonObjectLocationHistory = new JSONObject(locationHistoryData);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

//        String haltData = preferences.getString("HaltHistory","");
//        if (!haltData.equalsIgnoreCase("")) {
//            try {
//                jsonObjectHalt = new JSONObject(haltData);
//            } catch (JSONException e) {
//                e.printStackTrace();
//            }
//        }

        startTimersAndRelatedProcesses();
        locationInfo = new Intent();
        locationInfo.setAction("locationInfo");
        mobileOffDataCaptured = false;
        startForeground(1000,getNotification());
        getLocationUpdates();
        return START_STICKY;
    }

    @SuppressLint("MissingPermission")
    public void getLocationUpdates() {
        Log.d("TAG", "onStartCommand: check calling A");

        final LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(1000);
        locationRequest.setFastestInterval(1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                if (locationResult != null && locationResult.getLocations().size() > 0) {
                    int latestLocationIndex = locationResult.getLocations().size() - 1;
                    locationResult.getLocations().get(latestLocationIndex).getAccuracy();
                    currentLat = locationResult.getLocations().get(latestLocationIndex).getLatitude();
                    currentLng = locationResult.getLocations().get(latestLocationIndex).getLongitude();
                    Log.d("TAG", "onLocationResult: check "+locationResult.getLocations().get(latestLocationIndex).getLatitude()+"    "+
                            locationResult.getLocations().get(latestLocationIndex).getLongitude());
                    speed = locationResult.getLocations().get(latestLocationIndex).getSpeed();
                    if (!isAppIsInBackground()) {
                        locationInfo.putExtra("lat", currentLat);
                        locationInfo.putExtra("lng", currentLng);
                        locationInfo.putExtra("speed", speed);
                        locationInfo.putExtra("locationHistoryJson", jsonObjectLocationHistory.toString());
                        //locationInfo.putExtra("haltJson", jsonObjectHalt.toString());
                        locationInfo.putExtra("speed", speed);
                        locationInfo.putExtra("alertBox", "no");
                        sendBroadcast(locationInfo);
                    }
                    if (preferences.getString("dutyOff", "").equalsIgnoreCase(common.date())&&preferences.getString("dutyIn", "").equalsIgnoreCase(common.date())) {
                        Log.d("TAG", "onLocationResult: check A ");
                        removeLocationListener();
                    }
                }
            }
        };
        LocationServices.getFusedLocationProviderClient(getApplicationContext()).requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    public void removeLocationListener() {
        Log.d("TAG", "onLocationResult: check B "+currentLat);
        if (locationCallback != null) {
            LocationServices.getFusedLocationProviderClient(MyService.this).removeLocationUpdates(locationCallback);
        }
    }

    private Notification getNotification() {
        NotificationCompat.Builder noticationBuilder = null;
        noticationBuilder = new NotificationCompat.Builder(getApplicationContext(),
                App.CHANNEL_ID)
                .setContentTitle("Location Notification")
                .setContentText("Location service is running in background.")
                .setAutoCancel(true);
        return noticationBuilder.build();
    }

    private void startTimersAndRelatedProcesses() {
        if (countDownTimerLocation == null) {
            setCurrentLocation();
        }
        if (countDownTimerPath == null) {
            setPathTraversal();
        }
        if (countDownTimerTemp == null) {
            haltStartTime = new SimpleDateFormat("HH:mm").format(new Date());
            haltIdentification();
        }
    }

    private void setPathTraversal() {
        Log.d("TAG", "onLocationResult: check C "+currentLat);
        StringBuilder traversalHistory = new StringBuilder();
        maxDistance = (int) Math.round(preferences.getInt("maxDistanceCoveredPerSecond", 0) * (Double.parseDouble(String.valueOf(preferences.getInt("pathTraverseArrayTime", 0))) / 1000));
        maxDistanceCanCover = maxDistance;
        countDownTimerPath = new CountDownTimer(preferences.getInt("pathTraverseCaptureTime", 60000), preferences.getInt("pathTraverseArrayTime", 500)) {
            @SuppressLint("MissingPermission")
            public void onTick(long millisUntilFinished) {
                if (currentLat != 0.0 && currentLng != 0.0) {
                    if (previousLat == 0.0) {
                        previousLat = currentLat;
                    }
                    if (previousLng == 0.0) {
                        previousLng = currentLng;
                    }
                    float[] pathDis = new float[1];
                    Location.distanceBetween(previousLat, previousLng, currentLat, currentLng, pathDis);
                    if (pathDis[0] > 0 && pathDis[0] < maxDistanceCanCover) {
                        traversalHistory.append("(" + currentLat + "," + currentLng + ")~");
                    } else if (pathDis[0] != 0) {
                        maxDistanceCanCover = maxDistanceCanCover + maxDistance;
                    }
                    previousLat = currentLat;
                    previousLng = currentLng;
                }
            }

            @SuppressLint("SimpleDateFormat")
            public void onFinish() {
                if (!preferences.getString("dutyOff", "").equalsIgnoreCase(common.date())&&preferences.getString("dutyIn", "").equalsIgnoreCase(common.date())) {
                    if (currentLat != 0.0 && currentLng != 0.0) {
                        if (traversalHistory.length() == 0) {
                            traversalHistory.append("(" + currentLat + "," + currentLng + ")~");
                        }
                        saveLocationHistory(traversalHistory);
                    }
                    if (!mobileOffDataCaptured) {
                        checkLocationHistoryGapForMobileOff();
                    }
                    setPathTraversal();
                }
            }
        }.start();
    }

    private void saveLocationHistory(StringBuilder traversalHistory) {
        String currentTime = new SimpleDateFormat("HH:mm").format(new Date());
        Double currentArrayDistance = calculateCoveredDistance(traversalHistory);
        final Double[] totalArrayDistance = {Double.parseDouble(preferences.getString("TotalCoveredDistance", "0.0")) + currentArrayDistance};
        preferences.edit().putString("TotalCoveredDistance", String.valueOf(totalArrayDistance[0])).apply();
        DatabaseReference locationDatabaseReference = databaseReferencePath.child("LocationHistory/" + preferences.getString("uid", "") + "/" + common.year() + "/" + common.monthName() + "/" + common.date());
        locationDatabaseReference.child("" + currentTime + "/lat-lng").setValue(traversalHistory.substring(0, traversalHistory.length() - 1));
        locationDatabaseReference.child("" + currentTime + "/distance-in-meter").setValue(currentArrayDistance);

        try {
            jsonObjectLocationHistory.put(currentTime, traversalHistory.substring(0, traversalHistory.length() - 1));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        preferences.edit().putString("LocationHistory",jsonObjectLocationHistory.toString()).apply();
        locationDatabaseReference.child("/last-update-time").setValue(currentTime);
        locationDatabaseReference.child("/TotalCoveredDistance").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    totalArrayDistance[0] = totalArrayDistance[0] + Double.valueOf(dataSnapshot.getValue().toString());
                }
                try {
                    jsonObjectLocationHistory.put("totalDistance", "" + new Double(totalArrayDistance[0]).intValue());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                locationDatabaseReference.child("/TotalCoveredDistance").setValue(totalArrayDistance[0]);
                preferences.edit().putString("TotalCoveredDistance", "0.0").apply();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private double calculateCoveredDistance(StringBuilder traversalHistory) {
        String[] latLngArray = traversalHistory.toString().split("~");
        double preLat = 0.0, preLng = 0.0;
        float[] distance = new float[1];
        double totalDistance = 0;
        try {
            for (int i = 0; i < latLngArray.length; i++) {
                String[] latLng = latLngArray[i].substring(1, latLngArray[i].length() - 1).split(",");
                if (Double.parseDouble(latLng[0].trim()) != 0.0 && Double.parseDouble(latLng[1].trim()) != 0.0) {
                    double latitude = Double.parseDouble(latLng[0].trim());
                    double longitude = Double.parseDouble(latLng[1].trim());
                    if (i == 0) {
                        preLat = latitude;
                        preLng = longitude;
                    }
                    if (i > 0) {
                        Location.distanceBetween(preLat, preLng, latitude, longitude, distance);
                        totalDistance = totalDistance + distance[0];
                        preLat = latitude;
                        preLng = longitude;
                    }
                }
            }
        } catch (Exception e) {
        }
        return totalDistance;
    }

    private void haltIdentification() {
        int countDownInterval = preferences.getInt("distanceCoveredCheckInterval", 0) * 1000;
        float[] distance = new float[1];
        countDownTimerTemp = new CountDownTimer(60000, countDownInterval) {
            @SuppressLint("SimpleDateFormat")
            public void onTick(long millisUntilFinished) {
                if (haltStartLat == 0.0) {
                    haltStartLat = currentLat;
                }
                if (haltStartLng == 0.0) {
                    haltStartLng = currentLng;
                }
                Location.distanceBetween(haltStartLat, haltStartLng, currentLat, currentLng, distance);
                requiredDistanceCovered = !(preferences.getInt("haltAllowedRange", 0) >= distance[0]);
            }

            @SuppressLint("SimpleDateFormat")
            public void onFinish() {
                if (!preferences.getString("dutyOff", "").equalsIgnoreCase(common.date())&&preferences.getString("dutyIn", "").equalsIgnoreCase(common.date())) {
                    if (!requiredDistanceCovered) {
                        if (isHaltAllowed(true)) {
                            String currentTime = new SimpleDateFormat("HH:mm").format(new Date());
                            haltDuration = haltDuration == 0 ? preferences.getInt("maxHaltAllowed", 0) : haltDuration + 1;
                            if (haltStartLat != 0.0 && haltStartLng != 0.0) {
                                saveHaltInfo("work-stopped", haltStartTime, currentTime, haltDuration, haltStartLat, haltStartLng);
                            }
                        }
                    } else {
                        haltStartTime = new SimpleDateFormat("HH:mm").format(new Date());
                        haltDuration = 0;
                        haltStartLat = currentLat;
                        haltStartLng = currentLng;
                    }
                    haltIdentification();
                }
            }
        }.start();
    }

    private boolean isHaltAllowed(boolean isEqual) {
        boolean isAllowed = false;
        String currentTime = new SimpleDateFormat("HH:mm").format(new Date());
        try {
            SimpleDateFormat df = new SimpleDateFormat("HH:mm");
            Calendar cal = Calendar.getInstance();
            cal.setTime(df.parse(haltStartTime));
            cal.add(Calendar.MINUTE, preferences.getInt("maxHaltAllowed", 0));
            String mayStayTillThisTime = df.format(cal.getTime());
            if (isEqual) {
                if (currentTime.compareTo(mayStayTillThisTime) >= 0) {
                    isAllowed = true;
                }
            } else {
                if (currentTime.compareTo(mayStayTillThisTime) > 0) {
                    isAllowed = true;
                }
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return isAllowed;
    }

    @SuppressLint("SimpleDateFormat")
    private void saveHaltInfo(String haltType, String startTime, String endTime, int duration, double lat, double lng) {
        Geocoder geocoder = new Geocoder(MyService.this, Locale.getDefault());
        String address = common.getAddress(geocoder, lat, lng);
        String[] locality = address.split(", Rajasthan");
        DatabaseReference databaseHalt = databaseReferencePath.child("HaltInfo/" + preferences.getString("uid", "") + "/" + common.year() + "/" + common.monthName() + "/" + common.date());
        databaseHalt.child("" + startTime + "/endTime").setValue(endTime);
        databaseHalt.child("" + startTime + "/startTime").setValue(startTime);
        databaseHalt.child("" + startTime + "/duration").setValue(duration);
        databaseHalt.child("" + startTime + "/location").setValue("lat/lng: (" + lat + "," + lng + ")");
        databaseHalt.child("" + startTime + "/haltType").setValue(haltType);
        databaseHalt.child("" + startTime + "/locality").setValue(locality[0]);

//        try {
//            JSONObject haltDetails = new JSONObject();
//            haltDetails.put("endTime", endTime);
//            haltDetails.put("startTime", startTime);
//            haltDetails.put("duration", duration);
//            haltDetails.put("location", lat + "," + lng);
//            haltDetails.put("haltType", haltType);
//            haltDetails.put("locality", locality[0]);
//            jsonObjectHalt.put(startTime, haltDetails);
//        } catch (JSONException e) {
//            e.printStackTrace();
//        }
//        preferences.edit().putString("HaltHistory",jsonObjectHalt.toString()).apply();
    }

    private void checkLocationHistoryGapForMobileOff() {
        databaseReferencePath.child("FEAttendance/" + preferences.getString("uid", "") + "/" + common.year() + "/" + common.monthName() + "/" + common.date() + "/inDetails/time").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String taskStartTime = "";
                if (dataSnapshot.getValue() != null) {
                    taskStartTime = dataSnapshot.getValue().toString();

                    String finalTaskStartTime = taskStartTime;
                    Log.d("TAG", "onDataChange: check " + finalTaskStartTime);
                    databaseReferencePath.child("LocationHistory/" + preferences.getString("uid", "") + "/" + common.year() + "/" + common.monthName() + "/" + common.date()).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            Log.d("TAG", "onDataChange: check A " + finalTaskStartTime);
                            if (dataSnapshot.getValue() != null) {
                                Log.d("TAG", "onDataChange: check B " + dataSnapshot);
                                String previousTime = "", previousLatLng = "";
                                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                    if (snapshot.hasChildren()) {
                                        String currentTime = snapshot.getKey();
                                        Log.d("TAG", "onDataChange: check BB " + currentTime);
                                        if (common.formatDate(finalTaskStartTime, currentTime) > 0) {
                                            Log.d("TAG", "onDataChange: check BBB " + currentTime);
                                            if (previousTime.equalsIgnoreCase("")) {
                                                previousTime = currentTime;
                                            }
                                            int timeGap = common.formatDate(previousTime, currentTime);

                                            Log.d("TAG", "onDataChange: check BBBB " + timeGap);
                                            if (timeGap >= preferences.getInt("maxHaltAllowed", 0)) {
                                                if (previousLatLng.length()>0) {
                                                    String[] latLng = previousLatLng.replace("(", "").replace(")", "").split(",");
                                                    if (Double.parseDouble(latLng[0]) != 0.0 && Double.parseDouble(latLng[1]) != 0.0) {
                                                        saveHaltInfo("work-stopped", previousTime, currentTime, timeGap, Double.parseDouble(latLng[0]), Double.parseDouble(latLng[1]));
                                                    }
                                                }
                                            }
                                            if (snapshot.hasChild("lat-lng") || snapshot.hasChild("latlng")) {
                                                String[] latLngArray = null;
                                                if (snapshot.hasChild("lat-lng")) {
                                                    latLngArray = snapshot.child("lat-lng").getValue().toString().split("~");
                                                }
                                                if (snapshot.hasChild("latlng")) {
                                                    latLngArray = snapshot.child("latlng").getValue().toString().split("~");
                                                }
                                                previousLatLng = latLngArray[0];
                                            }
                                            previousTime = currentTime;
                                        }
                                    }
                                }
                                String currentTime = new SimpleDateFormat("HH:mm").format(new Date());
                                if (common.formatDate(finalTaskStartTime, currentTime) > 0) {
                                    if (previousTime != "") {
                                        int timeGap = common.formatDate(previousTime, currentTime);
                                        if (timeGap >= preferences.getInt("maxHaltAllowed", 0)) {
                                            String[] latLng = previousLatLng.replace("(", "").replace(")", "").split(",");
                                            if (Double.parseDouble(latLng[0]) != 0.0 && Double.parseDouble(latLng[1]) != 0.0) {
                                                saveHaltInfo("work-stopped", previousTime, currentTime, timeGap, Double.parseDouble(latLng[0]), Double.parseDouble(latLng[1]));
                                            }
                                        }
                                    }
                                }
                            } else {
                                Log.d("TAG", "onDataChange: check C " + dataSnapshot);
                            }
                            mobileOffDataCaptured = true;
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {

                        }
                    });
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private void setCurrentLocation() {
        countDownTimerLocation = new CountDownTimer(preferences.getInt("currentLocationCaptureTime", 10000), 1000) {
            public void onTick(long millisUntilFinished) {
            }

            @RequiresApi(api = Build.VERSION_CODES.O)
            public void onFinish() {
                if (!preferences.getString("dutyOff", "").equalsIgnoreCase(common.date())&&preferences.getString("dutyIn", "").equalsIgnoreCase(common.date())) {
                    final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                    if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        if (isAppIsInBackground()) {
                            Intent i = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(i);
                        } else {
                            locationInfo.putExtra("lat", currentLat);
                            locationInfo.putExtra("lng", currentLng);
                            locationInfo.putExtra("speed", speed);
                            locationInfo.putExtra("alertBox", "yes");
                            sendBroadcast(locationInfo);
                        }
                    }
                    if (currentLat != 0.0 && currentLat != 0.0) {
                        databaseReferencePath.child("CurrentLocationInfo/" + preferences.getString("uid", "") + "/latLng").setValue("" + currentLat + "," + currentLng);
                    }
                    setCurrentLocation();
                }
            }
        }.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Intent intent = new Intent(".Restarter");
        sendBroadcast(intent);
    }

    public boolean isAppIsInBackground() {
        boolean isInBackground = true;
        try {
            ActivityManager am = (ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE);
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT_WATCH) {
                List<ActivityManager.RunningAppProcessInfo> runningProcesses = am.getRunningAppProcesses();
                for (ActivityManager.RunningAppProcessInfo processInfo : runningProcesses) {
                    if (processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                        for (String activeProcess : processInfo.pkgList) {
                            if (activeProcess.equals(this.getPackageName())) {
                                isInBackground = false;
                            }
                        }
                    }
                }
            } else {
                List<ActivityManager.RunningTaskInfo> taskInfo = am.getRunningTasks(1);
                ComponentName componentInfo = taskInfo.get(0).topActivity;
                if (componentInfo.getPackageName().equals(this.getPackageName())) {
                    isInBackground = false;
                }
            }
        } catch (Exception e) {
        }
        return isInBackground;
    }
}