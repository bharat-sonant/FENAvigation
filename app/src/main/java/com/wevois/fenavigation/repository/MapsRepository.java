package com.wevois.fenavigation.repository;

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.maps.android.PolyUtil;
import com.wevois.fenavigation.CommonMethods;
import com.wevois.fenavigation.Model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

public class MapsRepository {

    CommonMethods common = CommonMethods.getInstance();

    public void fetchWardBoundariesData(Activity activity) {
        SharedPreferences preferences = activity.getSharedPreferences("FirebasePath", MODE_PRIVATE);
        common.setProgressBar("Please Wait", activity, activity);
        common.getStoRef(activity).child("/Defaults/BoundariesLatLng.json").getMetadata().addOnSuccessListener(storageMetadata -> {
            long fileCreationTime = storageMetadata.getCreationTimeMillis();
            long fileDownloadTime = preferences.getLong("BoundariesLatLngDownloadTime", 0);
            if (fileDownloadTime != fileCreationTime) {
                try {
                    File localFile = File.createTempFile("images", "jpg");
                    common.getStoRef(activity).child("/Defaults/BoundariesLatLng.json").getFile(localFile)
                            .addOnCompleteListener(task -> {
                                try {
                                    BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(localFile)));
                                    StringBuilder sb = new StringBuilder();
                                    String str;
                                    while ((str = br.readLine()) != null) {
                                        sb.append(str);
                                    }
                                    preferences.edit().putString("BoundariesLatLng", sb.toString()).apply();
                                    preferences.edit().putLong("BoundariesLatLngDownloadTime", fileCreationTime).apply();
                                    common.closeDialog(activity);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @SuppressLint("StaticFieldLeak")
    public LiveData<String[]> wardFromAvailableLatLng(LatLng finalLocation, Activity activity) {
        MutableLiveData<String[]> response = new MutableLiveData<>();
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @SuppressLint("WrongThread")
            @Override
            protected Boolean doInBackground(Void... p) {
                SharedPreferences preferences = activity.getSharedPreferences("FirebasePath", MODE_PRIVATE);
                Iterator<String> iterator = null;
                try {
                    iterator = new JSONObject(preferences.getString("BoundariesLatLng", "")).keys();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                boolean isIterate = false;
                while (iterator.hasNext()) {
                    try {
                        String key = iterator.next();
                        JSONArray tempLatLngArray = new JSONArray(String.valueOf(new JSONObject(preferences.getString("BoundariesLatLng", "")).get(key)));
                        ArrayList<LatLng> latLngOfBoundaryArrayList = new ArrayList<>();
                        for (int i = 0; i <= tempLatLngArray.length() - 1; i++) {
                            String[] latlngArray = String.valueOf(tempLatLngArray.get(i)).split(",");
                            latLngOfBoundaryArrayList.add(new LatLng(Double.parseDouble(latlngArray[1].trim()), Double.parseDouble(latlngArray[0].trim())));
                            if (i == tempLatLngArray.length() - 1) {
                                if (PolyUtil.containsLocation(new LatLng(finalLocation.latitude, finalLocation.longitude), latLngOfBoundaryArrayList, true)) {
                                    isIterate = true;
                                    response.postValue(key.split("_"));
                                    break;
                                }
                            }
                        }
                        if (isIterate) break;
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                return null;
            }
        }.execute();

        return response;
    }

    @SuppressLint("StaticFieldLeak")
    public LiveData<ArrayList<Model>> getTotalPhoto(Activity activity, String uid, String year, String month, String date) {
        ArrayList<Model> models = new ArrayList<>();
        MutableLiveData<ArrayList<Model>> response = new MutableLiveData<>();
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @SuppressLint("WrongThread")
            @Override
            protected Boolean doInBackground(Void... p) {
                for (int i = 1; i <= 3; i++) {
                    int finalI = i;
                    activity.runOnUiThread(() -> {
                        int count = finalI;
                        common.getDatabaseForApplication(activity).child("WastebinMonitor/ImagesData/" + year + "/" + month + "/" + date + "/" + count).orderByChild("user").equalTo(uid).addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                if (dataSnapshot.getValue() != null) {
                                    int counts = 0;
                                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                        counts++;
                                        models.add(new Model(snapshot.child("time").getValue().toString(),
                                                snapshot.child("latLng").getValue().toString()));
                                        if (count == 3 && counts == dataSnapshot.getChildrenCount()) {
                                            SimpleDateFormat formatter = new SimpleDateFormat("HH:mm");
                                            Collections.sort(models, (obj1, obj2) -> {
                                                Date a = null, b = null;
                                                try {
                                                    a = formatter.parse(obj1.getTime());
                                                    b = formatter.parse(obj2.getTime());
                                                } catch (Exception e) {
                                                }
                                                return a.compareTo(b);
                                            });
                                            response.setValue(models);
                                        }
                                    }
                                }else {
                                    response.setValue(models);
                                }
                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {

                            }
                        });
                    });
                }

                return null;
            }
        }.execute();
        return response;
    }

    @SuppressLint("StaticFieldLeak")
    public LiveData<DataSnapshot> checkDutyOff(Activity activity, String uid) {
        MutableLiveData<DataSnapshot> response = new MutableLiveData<>();
        common.getDatabaseForApplication(activity).child("FEAttendance").child(uid).child(common.year()).child(common.monthName()).child(common.date()).child("outDetails").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                response.setValue(dataSnapshot);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
        return response;
    }

    @SuppressLint("StaticFieldLeak")
    public LiveData<String> dutyOff(Activity activity, String uid, LatLng latLng, String address) {
        MutableLiveData<String> response = new MutableLiveData<>();
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @SuppressLint("WrongThread")
            @Override
            protected Boolean doInBackground(Void... p) {
                if (address.length() > 0) {
                    common.getDatabaseForApplication(activity).child("FEAttendance/" + uid + "/" + common.year() + "/" + common.monthName() + "/" + common.date() + "/outDetails").addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            if (dataSnapshot.getValue() != null) {
                                common.showAlertBox("Already duty off.", true, activity, false);
                            } else {
                                HashMap<String, String> map = new HashMap<>();
                                map.put("address", address);
                                map.put("location", latLng.latitude + "," + latLng.longitude);
                                map.put("time", common.currentTime());
                                map.put("status", "0");
                                common.getDatabaseForApplication(activity).child("FEAttendance/" + uid + "/" + common.year() + "/" + common.monthName() + "/" + common.date() + "/outDetails").setValue(map)
                                        .addOnCompleteListener(task2 -> {
                                            response.setValue("success");
                                        });
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {

                        }
                    });
                } else {
                    response.setValue("fail");
                    common.showAlertBox("please Refresh Location", false, activity, false);
                }
                return null;
            }
        }.execute();
        return response;
    }

    @SuppressLint("StaticFieldLeak")
    public LiveData<DataSnapshot> getLocationData(String year, String month, String date, Activity activity, String uid) {
        MutableLiveData<DataSnapshot> response = new MutableLiveData<>();
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @SuppressLint("WrongThread")
            @Override
            protected Boolean doInBackground(Void... p) {
                common.getDatabaseForApplication(activity).child("LocationHistory/" + uid + "/" + year + "/" + month + "/" + date).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        response.setValue(dataSnapshot);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
                return null;
            }
        }.execute();

        return response;
    }

    @SuppressLint("StaticFieldLeak")
    public LiveData<DataSnapshot> getHaltData(String year, String month, String date, Activity activity, String uid) {
        MutableLiveData<DataSnapshot> response = new MutableLiveData<>();
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @SuppressLint("WrongThread")
            @Override
            protected Boolean doInBackground(Void... p) {
                common.getDatabaseForApplication(activity).child("HaltInfo/" + uid + "/" + year + "/" + month + "/" + date).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        response.setValue(dataSnapshot);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
                return null;
            }
        }.execute();
        return response;
    }
}
