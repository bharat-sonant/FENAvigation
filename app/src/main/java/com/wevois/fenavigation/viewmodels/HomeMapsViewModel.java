package com.wevois.fenavigation.viewmodels;

import static android.content.Context.MODE_PRIVATE;

import static com.google.firebase.crashlytics.internal.Logger.TAG;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.util.Rational;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.databinding.ObservableField;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModel;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.maps.android.PolyUtil;
import com.wevois.fenavigation.CommonMethods;
import com.wevois.fenavigation.Model;
import com.wevois.fenavigation.R;
import com.wevois.fenavigation.model.BoundaryLatLngModel;
import com.wevois.fenavigation.repository.MapsRepository;
import com.wevois.fenavigation.views.HomeMapsActivity;
import com.wevois.fenavigation.MyService;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

public class HomeMapsViewModel extends ViewModel {
    GoogleMap mMap;
    Activity activity;
    SupportMapFragment mapFragment;
    CommonMethods common = CommonMethods.getInstance();
    boolean isAlertBoxOpen = false, firstTime = true, isCall = true, isCallGetZoneWard = true, isDutyOff = true,isPictureInPictureMode=false;
    CountDownTimer countDownTimerLocation, countDownTimerGetZoneAndWard, onPauseTimer;
    LatLng latLng, previousLat;
    float[] moveDistance = new float[1];
    Marker markerCurrent, markerMove;
    MapsRepository repository = new MapsRepository();
    ArrayList<BoundaryLatLngModel> boundaryLatLngModels=new ArrayList<>();
    public ObservableField<String> ward = new ObservableField<>("fetching..");
    public ObservableField<String> zone = new ObservableField<>("fetching..");
    public ObservableField<String> totalDistance = new ObservableField<>("fetching..");
    public ObservableField<String> totalHalt = new ObservableField<>("fetching..");
    public ObservableField<String> totalPhoto = new ObservableField<>("fetching..");
    public ObservableField<String> speedTv = new ObservableField<>("0");
    public ObservableField<String> timeDate = new ObservableField<>("0");
    public ObservableField<String> textTv = new ObservableField<>("Gray color me previous day ka data show ho rha hai.");

    SharedPreferences preferences;
    public static JSONObject jsonObjectLocationHistory = new JSONObject(), jsonObjectHalt = new JSONObject();
    ArrayList<LatLng> directionPositionList = new ArrayList<>();
    Polyline polyline, polyLine1;
    HashMap<String, Marker> haltMarker = new HashMap<>();
    private View mCustomMarkerView;
    private ImageView mMarkerImageView;
    TextView markerTV;
    String today = common.date();
    HashMap<String, Marker> imageMarker = new HashMap<>();
    boolean isOpen = true;

    public HomeMapsViewModel(Activity activity, SupportMapFragment fragment) {
        this.activity = activity;
        mapFragment = fragment;
        preferences = activity.getSharedPreferences("FirebasePath", MODE_PRIVATE);
        common.checkWhetherLocationSettingsAreAvailable(activity).observe((LifecycleOwner) activity, response -> {
        });
        repository.fetchWardBoundariesData(activity).observe((LifecycleOwner) activity,response->{
            repository.wardFromAvailableLatLng(activity).observe((LifecycleOwner) activity,result->{
                boundaryLatLngModels = result;
                setWardAndZone();
            });
        });
        Log.d(TAG, "HomeMapsViewModel: check "+preferences.getString("PreviousDayMessage",""));
        textTv.set(preferences.getString("PreviousDayMessage",""));
        timeDate.set(today);
        initViews();
        setMap();
        onRefreshClick();
        callMethodAfterDutyOff();
        if (!preferences.getString("dutyOff", "").equalsIgnoreCase(common.date()) && preferences.getString("dutyIn", "").equalsIgnoreCase(common.date())) {
            repository.checkDutyOff(activity, preferences.getString("uid", "")).observeForever(response -> {
                if (response != null) {
                    if (response.getValue() != null) {
                        preferences.edit().putString("dutyOff", common.date()).apply();
                    } else {
                        callLocationService();
                    }
                } else {
                    callLocationService();
                }
            });
        }
        previousTrackingLine();
    }

    private void previousTrackingLine() {
        Date date1 = new Date(System.currentTimeMillis() - (1000 * 60 * 60 * 24));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String date = sdf.format(date1);
        common.getDatabaseForApplication(activity).child("LocationHistory").child(preferences.getString("uid", " ")).child("" + common.year()).child("" + common.monthName()).child("" + date).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                ArrayList<LatLng> data1 = new ArrayList<>();
                if (snapshot.getValue() != null) {
                    for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                        if (dataSnapshot.hasChild("lat-lng")) {
                            String[] data = dataSnapshot.child("lat-lng").getValue().toString().split("~");
                            for (int i = 0; i < data.length; i++) {
                                String[] latLngs = data[i].substring(1, data[i].length() - 1).split(",");
                                if (Double.parseDouble(latLngs[0].trim()) != 0.0 && Double.parseDouble(latLngs[1].trim()) != 0.0) {
                                    data1.add(new LatLng(Double.parseDouble(latLngs[0].trim()), Double.parseDouble(latLngs[1].trim())));
                                }
                            }
                        }
                    }
                    showLineOnMap(data1, false);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void callMethodAfterDutyOff() {
        String locationHistoryData = preferences.getString("LocationHistory", "");
        if (!locationHistoryData.equalsIgnoreCase("")) {
            try {
                jsonObjectLocationHistory = new JSONObject(locationHistoryData);
                setLocationOnMap();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
//        String haltData = preferences.getString("HaltHistory","");
//        if (!haltData.equalsIgnoreCase("")) {
//            try {
//                jsonObjectHalt = new JSONObject(haltData);
//                //setHaltOnMap();
//            } catch (JSONException e) {
//                e.printStackTrace();
//            }
//        }
    }

    public void setMap() {
        mapFragment.getMapAsync(googleMap -> {
            if (googleMap != null) {
                mMap = googleMap;
            }
            int LOCATION_REQUEST = 500;
            mMap = googleMap;
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST);
                return;
            }
            mMap.setMyLocationEnabled(false);
            mMap.getUiSettings().setCompassEnabled(false);
            mMap.setBuildingsEnabled(true);
            mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(activity, R.raw.style_json));
        });
    }

    private void callLocationService() {
        if (!isMyServiceRunning(MyService.class)) {
            Intent intent = new Intent(activity, MyService.class);
            ContextCompat.startForegroundService(activity, intent);
        }
        IntentFilter intentFilter = new IntentFilter("locationInfo");
        activity.registerReceiver(locReceiver, intentFilter);

    }

    BroadcastReceiver locReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("locationInfo")) {
                double latB = intent.getDoubleExtra("lat", 0);
                double lngB = intent.getDoubleExtra("lng", 0);
                String alertBox = intent.getStringExtra("alertBox");
                float speed = intent.getFloatExtra("speed", 0);
                String locationString = intent.getStringExtra("locationHistoryJson");
                //String haltString = intent.getStringExtra("haltJson");
                try {
                    jsonObjectLocationHistory = new JSONObject(locationString);
                    //jsonObjectHalt = new JSONObject(haltString);
                } catch (Exception e) {
                }

                speedTv.set("" + (int) (speed * 3.6));
                latLng = new LatLng(latB, lngB);
                if (preferences.getString("BoundariesLatLng", "").length() > 0) {
                    if (isCallGetZoneWard) {
                        isCallGetZoneWard = false;
                        if (!(countDownTimerGetZoneAndWard == null)) {
                            countDownTimerGetZoneAndWard.cancel();
                            countDownTimerGetZoneAndWard = null;
                        }
                        new getZoneAndWard().execute();
                    }
                }
                if (isCall) {
                    isCall = false;
                    firstTime = true;
                    if (!(countDownTimerLocation == null)) {
                        countDownTimerLocation.cancel();
                        countDownTimerLocation = null;
                    }
                    new currentLocationShow().execute();
                }
                if (alertBox.equals("yes")) {
                    if (!isAlertBoxOpen) {
                        isAlertBoxOpen = true;
                        if (isPictureInPictureMode){
                            reOpenActivity();
                        }
                        common.checkWhetherLocationSettingsAreAvailable(activity).observeForever(response -> {
                        });
                    }
                }
            }
        }
    };

    public void pictureInPictureMode(boolean isPictureInPictureModes) {
        if (isPictureInPictureModes) {
            isPictureInPictureMode = isPictureInPictureModes;
        } else {
            isPictureInPictureMode = isPictureInPictureModes;
        }
    }

    private class currentLocationShow extends AsyncTask<Void, String, String> {
        @Override
        protected String doInBackground(Void... voids) {
            activity.runOnUiThread(() -> {
                countDownTimerLocation = new CountDownTimer(2000, 1000) {
                    public void onTick(long millisUntilFinished) {

                    }

                    public void onFinish() {

                        if (firstTime) {
                            firstTime = false;
                            previousLat = latLng;
                            new setMovingMarker().execute();
                        }
                        Location.distanceBetween(latLng.latitude, latLng.longitude, previousLat.latitude, previousLat.longitude, moveDistance);
                        if (moveDistance[0] > 2) {
                            final LatLng startPosition = previousLat;
                            final LatLng finalPosition = latLng;
                            final Handler handler = new Handler();
                            final long start = SystemClock.uptimeMillis();
                            final Interpolator interpolator = new AccelerateDecelerateInterpolator();
                            final float durationInMs = 500;

                            handler.post(new Runnable() {
                                long elapsed;
                                float t;
                                float v;

                                @Override
                                public void run() {
                                    elapsed = SystemClock.uptimeMillis() - start;
                                    t = elapsed / durationInMs;
                                    v = interpolator.getInterpolation(t);
                                    LatLng currentPosition = new LatLng(
                                            startPosition.latitude * (1 - t) + finalPosition.latitude * t,
                                            startPosition.longitude * (1 - t) + finalPosition.longitude * t);
                                    markerCurrent.setPosition(currentPosition);
                                    markerMove.setPosition(currentPosition);
                                    if (t < 1) {
                                        markerCurrent.setVisible(false);
                                        markerMove.setVisible(true);
                                        handler.postDelayed(this, 10);
                                    } else {
                                        markerMove.setVisible(false);
                                        markerCurrent.setVisible(true);
                                    }
                                }
                            });
                            previousLat = latLng;
                        }
                        if (!preferences.getString("dutyOff", "").equalsIgnoreCase(common.date()) && preferences.getString("dutyIn", "").equalsIgnoreCase(common.date())) {
                            new currentLocationShow().execute();
                        } else {
                            activity.stopService(new Intent(activity, MyService.class));
                        }
                    }
                }.start();
            });
            return null;
        }
    }

    private class getZoneAndWard extends AsyncTask<Void, String, String> {
        @Override
        protected String doInBackground(Void... voids) {
            activity.runOnUiThread(() -> {

                countDownTimerGetZoneAndWard = new CountDownTimer(60000, 10000) {
                    public void onTick(long millisUntilFinished) {

                    }

                    public void onFinish() {
                        if (!preferences.getString("dutyOff", "").equalsIgnoreCase(common.date()) && preferences.getString("dutyIn", "").equalsIgnoreCase(common.date())) {
                            if (today.equalsIgnoreCase(common.date())) {
                                Log.d("TAG", "onReceive: check A " + jsonObjectLocationHistory);
                                setWardAndZone();
                                if (jsonObjectLocationHistory != null)
                                    setLocationOnMap();
//                                if (jsonObjectHalt != null)
//                                    setHaltOnMap();
                            }
                            if (!preferences.getString("dutyOff", "").equalsIgnoreCase(common.date()) && preferences.getString("dutyIn", "").equalsIgnoreCase(common.date())) {
                                new getZoneAndWard().execute();
                            }
                        }
                    }
                }.start();
            });
            return null;
        }
    }

//    @SuppressLint("StaticFieldLeak")
//    private void setHaltOnMap() {
//        new AsyncTask<Void, Void, Boolean>() {
//            @Override
//            protected void onPreExecute() {
//                super.onPreExecute();
//            }
//
//            @SuppressLint("WrongThread")
//            @Override
//            protected Boolean doInBackground(Void... p) {
//                Iterator<String> iterator = null;
//                try {
//                    iterator = jsonObjectHalt.keys();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//                int time = 0;
//                clearPreviousHaltOnMap();
//                while (iterator.hasNext()) {
//                    try {
//                        String key = iterator.next();
//                        JSONObject haltObject = jsonObjectHalt.getJSONObject(key);
//                        String[] haltLatLng = haltObject.getString("location").split(",");
//                        String duration = haltObject.getString("duration");
//                        showHaltOnMap(haltLatLng, duration, key);
//                        time = time + Integer.parseInt(duration);
//                    } catch (JSONException e) {
//                        e.printStackTrace();
//                    }
//                }
//                totalHalt.set("" + time + " min");
//                return null;
//            }
//        }.execute();
//    }
//

    private Bitmap getMarkerBitmapFromView(View view, int background, String key) {
        mMarkerImageView.setImageResource(R.drawable.ic_capture_image_loc);
        markerTV.setText(key);
        markerTV.setBackgroundResource(background);
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        view.buildDrawingCache();
        Bitmap returnedBitmap = Bitmap.createBitmap(view.getMeasuredWidth(), view.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(returnedBitmap);
        canvas.drawColor(Color.WHITE, PorterDuff.Mode.SRC_IN);
        Drawable drawable = view.getBackground();
        if (drawable != null)
            drawable.draw(canvas);
        view.draw(canvas);
        return returnedBitmap;
    }

    private void initViews() {
        mCustomMarkerView = ((LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.view_custom_marker, null);
        mMarkerImageView = mCustomMarkerView.findViewById(R.id.profile_image);
        markerTV = mCustomMarkerView.findViewById(R.id.haltTimeTV);
    }

    @SuppressLint("StaticFieldLeak")
    private void setLocationOnMap() {
        try {
            directionPositionList.clear();
            if (jsonObjectLocationHistory.has("totalDistance")) {
                showTotalDistance(jsonObjectLocationHistory.getString("totalDistance"));
            }
        } catch (Exception e) {
        }
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @SuppressLint("WrongThread")
            @Override
            protected Boolean doInBackground(Void... p) {
                Iterator<String> iterator = null;
                try {
                    iterator = jsonObjectLocationHistory.keys();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                while (iterator.hasNext()) {
                    try {
                        String key = iterator.next();
                        if (!key.equalsIgnoreCase("totalDistance")) {
                            String[] stringLatLng = jsonObjectLocationHistory.getString(key).split("~");
                            for (int i = 0; i < stringLatLng.length; i++) {
                                String[] latLngs = stringLatLng[i].substring(1, stringLatLng[i].length() - 1).split(",");
                                if (Double.parseDouble(latLngs[0].trim()) != 0.0 && Double.parseDouble(latLngs[1].trim()) != 0.0) {
                                    directionPositionList.add(new LatLng(Double.parseDouble(latLngs[0].trim()), Double.parseDouble(latLngs[1].trim())));
                                }
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                showLineOnMap(directionPositionList, true);
                return null;
            }
        }.execute();
    }

    private class setMovingMarker extends AsyncTask<Void, String, String> {
        @Override
        protected String doInBackground(Void... voids) {
            activity.runOnUiThread(() -> {
                int height = 100;
                int width = 50;
                BitmapDrawable bitManOne = (BitmapDrawable) activity.getResources().getDrawable(R.drawable.map_men);
                BitmapDrawable bitManMove = (BitmapDrawable) activity.getResources().getDrawable(R.drawable.map_men);
                MarkerOptions markerOptions = new MarkerOptions();
                MarkerOptions markerOptionsMove = new MarkerOptions();
                markerOptions.position(latLng);
                markerOptionsMove.position(latLng);
                markerOptionsMove.icon(BitmapDescriptorFactory.fromBitmap(Bitmap.createScaledBitmap(bitManMove.getBitmap(), width, height, false)));
                markerOptions.icon(BitmapDescriptorFactory.fromBitmap(Bitmap.createScaledBitmap(bitManOne.getBitmap(), width, height, false)));
                if (markerCurrent != null) {
                    markerCurrent.remove();
                }
                markerCurrent = mMap.addMarker(markerOptions);
                markerMove = mMap.addMarker(markerOptionsMove);
                markerMove.setVisible(false);
                LatLngBounds.Builder builder = new LatLngBounds.Builder();
//                if (directionPositionList.size() > 0) {
//                    for (int a = 0; a < directionPositionList.size(); a++) {
//                        builder.include(directionPositionList.get(a));
//                    }
//                }
                if (latLng != null) {
                    builder.include(latLng);
                }
                CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(builder.build(), 100);
                try {
                    mMap.animateCamera(cu);
                } catch (Exception e) {
                }
            });
            return null;
        }
    }

    public void destroy() {
        common.closeDialog(activity);
        try {
            activity.unregisterReceiver(locReceiver);
        } catch (Exception e) {
        }
    }

    public void resume() {
        isAlertBoxOpen = false;
        isDutyOff = true;
        if (onPauseTimer != null) {
            onPauseTimer.cancel();
            onPauseTimer = null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void pause() {
        if (!isAlertBoxOpen) {
            pictureInPictureMethod();
        }
        if (!preferences.getString("dutyOff", "").equalsIgnoreCase(common.date()) && preferences.getString("dutyIn", "").equalsIgnoreCase(common.date())) {
            if (preferences.getString("isAppOpen", "").equalsIgnoreCase("yes")) {
                if (onPauseTimer != null) {
                    onPauseTimer.cancel();
                    onPauseTimer = null;
                }
                pauseTimerCall();
            }
        }
    }

    private void pauseTimerCall() {
        onPauseTimer = new CountDownTimer(preferences.getInt("appClosedTimerTime", 60000), 10000) {
            @SuppressLint("MissingPermission")
            public void onTick(long millisUntilFinished) {
            }

            @SuppressLint("SimpleDateFormat")
            public void onFinish() {
                reOpenActivity();
            }
        }.start();
    }

    public void reOpenActivity() {
        if (!preferences.getString("dutyOff", "").equalsIgnoreCase(common.date()) && preferences.getString("dutyIn", "").equalsIgnoreCase(common.date())) {
            Intent rIntent = new Intent(activity, HomeMapsActivity.class);
            rIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent intent = PendingIntent.getActivity(activity, 0, rIntent, PendingIntent.FLAG_IMMUTABLE);
            AlarmManager manager = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
            manager.set(AlarmManager.RTC, System.currentTimeMillis(), intent);
        }
    }

    public void stop() {
        reOpenActivity();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void back() {
        pictureInPictureMethod();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void pictureInPictureMethod() {
        if (!preferences.getString("dutyOff", "").equalsIgnoreCase(common.date()) && preferences.getString("dutyIn", "").equalsIgnoreCase(common.date())) {
            Display d = activity.getWindowManager().getDefaultDisplay();
            Point p = new Point();
            d.getSize(p);
            int width = p.x;
            int height = p.y;
            Rational ratio = new Rational(width, height);
            PictureInPictureParams.Builder pip_Builder = new PictureInPictureParams.Builder();
            pip_Builder.setAspectRatio(ratio).build();
            activity.enterPictureInPictureMode();
        }
    }

    public void onRefreshClick() {
        common.setProgressBar("Please wait...", activity, activity);
        getPhoto(common.year(), common.monthName(), common.date(), true);
    }

    public void onDutyOffClick() {
        if (!preferences.getString("dutyOff", "").equalsIgnoreCase(common.date()) && preferences.getString("dutyIn", "").equalsIgnoreCase(common.date())) {
            if (isDutyOff) {
                isDutyOff = false;
                common.setProgressBar("Check network...", activity, activity);
                Geocoder geocoder = new Geocoder(activity, Locale.getDefault());
                String address = common.getAddress(geocoder, latLng.latitude, latLng.longitude);
                common.checkNetWork(activity).observeForever(result -> {
                    if (result) {
                        common.closeDialog(activity);
                        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                        builder.setMessage("Are you sure?\nYou want to duty off.")
                                .setCancelable(false)
                                .setPositiveButton("Yes", (dialog, id) -> {
                                    common.setProgressBar("Uploading...", activity, activity);
                                    repository.dutyOff(activity, preferences.getString("uid", ""), latLng, address).observeForever(response -> {
                                        common.closeDialog(activity);
                                        if (response.equalsIgnoreCase("success")) {
                                            preferences.edit().putString("dutyOff", common.date()).apply();
                                            activity.stopService(new Intent(activity, MyService.class));
                                        }
                                        isDutyOff = true;
                                    });
                                    dialog.cancel();
                                }).setNegativeButton("No", (dialog, i) -> {
                                    dialog.cancel();
                                    isDutyOff = true;
                                }
                        );
                        AlertDialog alertDialog = builder.create();
                        alertDialog.show();
                    } else {
                        common.closeDialog(activity);
                        isDutyOff = true;
                        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                        builder.setMessage("Internet not Available")
                                .setCancelable(false)
                                .setPositiveButton("Retry", (dialog, id) -> {
                                    onDutyOffClick();
                                    dialog.cancel();
                                }).setNegativeButton("Exit", (dialog, i) -> activity.finish());
                        AlertDialog alertDialog = builder.create();
                        alertDialog.show();
                    }
                });
            }
        } else {
            common.showAlertBox("Already duty off.", true, activity, false);
        }
    }

    public void onClickOpen() {
        try {
            Intent i = new Intent();
            i.setAction(Intent.ACTION_VIEW);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            i.setClassName("com.wevois.wastebinmonitor", "com.wevois.wastebinmonitor.SplashActivity");
            activity.startActivity(i);
        } catch (Exception e) {
            common.showAlertBox("Wastebin app not found.", false, activity, false);
        }
    }

    @SuppressLint("StaticFieldLeak")
    public void datePicker() {
        if (isOpen) {
            isOpen = false;
            int mYear, mMonth, mDay;
            final Calendar c = Calendar.getInstance();
            mYear = c.get(Calendar.YEAR);
            mMonth = c.get(Calendar.MONTH);
            mDay = c.get(Calendar.DAY_OF_MONTH);
            DatePickerDialog datePickerDialog = new DatePickerDialog(activity,
                    (view, year, monthOfYear, dayOfMonth) -> {
                        String date_time;
                        if (monthOfYear + 1 <= 9) {
                            if (dayOfMonth <= 9) {
                                date_time = year + "-0" + (monthOfYear + 1) + "-" + "0" + dayOfMonth;
                            } else {
                                date_time = year + "-0" + (monthOfYear + 1) + "-" + dayOfMonth;
                            }
                        } else {
                            if (dayOfMonth <= 9) {
                                date_time = year + "-" + (monthOfYear + 1) + "-" + "0" + dayOfMonth;
                            } else {
                                date_time = year + "-" + (monthOfYear + 1) + "-" + dayOfMonth;
                            }
                        }
                        SimpleDateFormat fromUser = new SimpleDateFormat("yyyy-MM-dd");
                        SimpleDateFormat myYear = new SimpleDateFormat("yyyy");
                        SimpleDateFormat myMonth = new SimpleDateFormat("MMMM", Locale.US);
                        today = date_time;
                        timeDate.set(today);
                        common.setProgressBar("Please wait...", activity, activity);
                        isOpen = true;
                        if (date_time.equalsIgnoreCase(common.date())) {
                            ward.set("fetching..");
                            zone.set("fetching..");
                            setWardAndZone();
                            setLocationOnMap();
                            //setHaltOnMap();
                        } else {
                            ward.set("...");
                            zone.set("...");
                            try {
                                repository.getLocationData(myYear.format(fromUser.parse(date_time)), myMonth.format(fromUser.parse(date_time)), date_time, activity, preferences.getString("uid", "")).observeForever(result -> {
                                    if (result != null) {
                                        if (result.getValue() != null) {
                                            try {
                                                directionPositionList.clear();
                                                if (result.hasChild("TotalCoveredDistance")) {
                                                    showTotalDistance(String.valueOf(new Double(Double.parseDouble(result.child("TotalCoveredDistance").getValue().toString())).intValue()));
                                                }
                                            } catch (Exception e) {
                                            }
                                            for (DataSnapshot snapshot : result.getChildren()) {
                                                if (snapshot.hasChild("lat-lng")) {
                                                    String[] stringLatLng = snapshot.child("lat-lng").getValue().toString().split("~");
                                                    for (int i = 0; i < stringLatLng.length; i++) {
                                                        String[] latLngs = stringLatLng[i].substring(1, stringLatLng[i].length() - 1).split(",");
                                                        if (Double.parseDouble(latLngs[0].trim()) != 0.0 && Double.parseDouble(latLngs[1].trim()) != 0.0) {
                                                            directionPositionList.add(new LatLng(Double.parseDouble(latLngs[0].trim()), Double.parseDouble(latLngs[1].trim())));
                                                        }
                                                    }
                                                }
                                            }
                                            showLineOnMap(directionPositionList, true);
                                        } else {
                                            clearPreviousLocationDataOnMap();
                                        }
                                    } else {
                                        clearPreviousLocationDataOnMap();
                                    }
                                });
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                            //clearPreviousHaltOnMap();
//                        try {
//                            repository.getHaltData(myYear.format(fromUser.parse(date_time)), myMonth.format(fromUser.parse(date_time)), date_time, activity, preferences.getString("uid", "")).observeForever(result -> {
//                                if (result != null) {
//                                    if (result.getValue() != null) {
//                                        int time = 0;
//                                        for (DataSnapshot snapshot : result.getChildren()) {
//                                            String duration = "0";
//                                            if (snapshot.hasChild("duration")) {
//                                                duration = snapshot.child("duration").getValue().toString();
//                                            }
//                                            if (snapshot.hasChild("location")) {
//                                                String loc = snapshot.child("location").getValue().toString();
//                                                loc = loc.replace("lat/lng: (", "");
//                                                loc = loc.replace(")", "");
//                                                String[] locArr = loc.split(",");
//                                                //showHaltOnMap(locArr, duration, snapshot.getKey());
//                                                time = time + Integer.parseInt(duration);
//                                            }
//                                        }
//                                        totalHalt.set("" + time + " min");
//                                    } else {
//                                        totalHalt.set("0 min");
//                                    }
//                                } else {
//                                    totalHalt.set("0 min");
//                                }
//                            });
//                        } catch (ParseException e) {
//                            e.printStackTrace();
//                        }
                        }
                        try {
                            getPhoto(myYear.format(fromUser.parse(date_time)), myMonth.format(fromUser.parse(date_time)), date_time, false);
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }

                    }, mYear, mMonth, mDay);
            datePickerDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", ((dialog, which) -> {
                datePickerDialog.dismiss();
                isOpen = true;
            }));
            datePickerDialog.show();
        }
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public void showTotalDistance(String distance) {
        if (Integer.parseInt(distance) >= 1000) {
            totalDistance.set("" + Double.parseDouble(distance) / 1000 + " Km");
        } else {
            totalDistance.set("" + Integer.parseInt(distance) + " m");
        }
    }

    public void showLineOnMap(ArrayList<LatLng> distance, boolean b) {
        activity.runOnUiThread(() -> {
            if (b) {
                try {
                    polyline.remove();
                } catch (Exception e) {
                }
                polyline = mMap.addPolyline(new PolylineOptions().addAll((distance)).color(Color.GREEN));
            } else {
                polyLine1 = mMap.addPolyline(new PolylineOptions().addAll((distance)).color(Color.GRAY));
            }
            common.closeDialog(activity);
            if (preferences.getString("dutyOff", "").equalsIgnoreCase(common.date())) {
                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                if (directionPositionList.size() > 0) {
                    for (int a = 0; a < directionPositionList.size(); a++) {
                        builder.include(directionPositionList.get(a));
                    }
                }
                if (latLng != null) {
                    builder.include(latLng);
                }
                CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(builder.build(), 100);
                try {
                    mMap.animateCamera(cu);
                } catch (Exception e) {
                }
            }
        });
    }

//    public void showHaltOnMap(String[] haltLatLng, String duration, String key) {
//        activity.runOnUiThread(() -> {
//            int backColor;
//            if (Integer.parseInt(duration) < 20) {
//                backColor = R.drawable.outline_bg_map;
//            } else {
//                backColor = R.drawable.outline_bg_map3;
//            }
//            final Marker[] markers = new Marker[1];
//            markers[0] = mMap.addMarker(new MarkerOptions().position(new LatLng(Double.parseDouble(haltLatLng[0]), Double.parseDouble(haltLatLng[1]))).title(key)
//                    .icon(BitmapDescriptorFactory.fromBitmap(getMarkerBitmapFromView(mCustomMarkerView, backColor, duration))));
//            markers[0].setTag(key);
//            haltMarker.put(key, markers[0]);
//        });
//    }

    public void showImageCaptureLocationOnMap(String[] haltLatLng, String duration, String key) {
        activity.runOnUiThread(() -> {
            int backColor = R.drawable.outline_bg_map;
            final Marker[] markers = new Marker[1];
            markers[0] = mMap.addMarker(new MarkerOptions().position(new LatLng(Double.parseDouble(haltLatLng[0]), Double.parseDouble(haltLatLng[1]))).title(key)
                    .icon(BitmapDescriptorFactory.fromBitmap(getMarkerBitmapFromView(mCustomMarkerView, backColor, duration))));
            markers[0].setTag(key);
            imageMarker.put(key, markers[0]);
        });
    }

    public void clearPreviousLocationDataOnMap() {
        common.closeDialog(activity);
        totalDistance.set("0 m");
        try {
            directionPositionList.clear();
        } catch (Exception e) {
        }
        try {
            polyline.remove();
        } catch (Exception e) {
        }
    }

//    public void clearPreviousHaltOnMap() {
//        try {
//            activity.runOnUiThread(() -> {
//                int size = haltMarker.size();
//                for (int i = 0; i < size; i++) {
//                    try {
//                        Marker marker = haltMarker.get((haltMarker.keySet().toArray())[0]);
//                        marker.remove();
//                        haltMarker.remove((haltMarker.keySet().toArray())[0]);
//                    } catch (Exception e) {
//                    }
//                }
//            });
//        } catch (Exception e) {
//        }
//    }

    public void clearPreviousImageOnMap() {
        try {
            activity.runOnUiThread(() -> {
                int size = imageMarker.size();
                for (int i = 0; i < size; i++) {
                    try {
                        Marker marker = imageMarker.get((imageMarker.keySet().toArray())[0]);
                        marker.remove();
                        imageMarker.remove((imageMarker.keySet().toArray())[0]);
                    } catch (Exception e) {
                    }
                }
            });
        } catch (Exception e) {
        }
    }

    public void getPhoto(String year, String month, String date, boolean isProgressOn) {
        repository.getTotalPhoto(activity, preferences.getString("uid", ""), year, month, date).observeForever(response -> {
            if (isProgressOn) {
                common.closeDialog(activity);
            }
            clearPreviousImageOnMap();
            if (response.size() != 0) {
                int count = 1;
                for (Model model : response) {
                    String[] lng = model.getLatLng().trim().split(",");
                    showImageCaptureLocationOnMap(lng, "" + count, model.getTime());
                    count++;
                }
                totalPhoto.set("" + response.size());
            } else {
                totalPhoto.set("0");
            }
        });
    }

    public void setWardAndZone() {
        if (latLng != null) {
            Log.d(TAG, "setWardAndZone: check "+boundaryLatLngModels);
            if (!boundaryLatLngModels.isEmpty()){
                Log.d(TAG, "setWardAndZone: check A "+boundaryLatLngModels);
                for (BoundaryLatLngModel boundaryLatLngModel: boundaryLatLngModels) {
                    if (PolyUtil.containsLocation(new LatLng(latLng.latitude, latLng.longitude), boundaryLatLngModel.getLatLngArrayList(), true)) {
                        String[] data = boundaryLatLngModel.getKey().split("_");
                        ward.set(data[0]);
                        zone.set(data[1]);
                        break;
                    }
                }
            }
        }
    }
}
