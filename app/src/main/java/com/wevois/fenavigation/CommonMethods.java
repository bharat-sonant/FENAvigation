package com.wevois.fenavigation;

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class CommonMethods {
    HttpURLConnection urlc = null;
    ProgressDialog dialog;
    private static CommonMethods single_instance = null;

    private CommonMethods() { }

    public static CommonMethods getInstance() {
        if (single_instance == null)
            single_instance = new CommonMethods();

        return single_instance;
    }

    public DatabaseReference getDatabaseForApplication(Context context) {
        DatabaseReference databaseReferencePath = FirebaseDatabase.getInstance(getSp(context).getString("dbPath", "")).getReference();
        return databaseReferencePath;
    }

    public StorageReference getStoRef(Context context) {
        return FirebaseStorage.getInstance().getReferenceFromUrl(getSp(context).getString("storagePath", ""));
    }

    private SharedPreferences getSp(Context context) {
        SharedPreferences sp = context.getSharedPreferences("FirebasePath", MODE_PRIVATE);
        return sp;
    }

    public boolean internetIsConnected() {
        try
        {
            urlc = (HttpURLConnection) (new URL("https://google.com").openConnection());
            urlc.setRequestProperty("User-Agent", "Test");
            urlc.setRequestProperty("Connection", "close");
            urlc.setConnectTimeout(10000);
            urlc.setReadTimeout(10000);
            urlc.connect();
            if (urlc.getResponseCode() == 200) {
                closedInternetCheckMethod();
            }
            Log.d("TAG", "init: check data C3"+urlc.getResponseCode());
            return (urlc.getResponseCode() == 200);
        } catch (IOException e) {
            closedInternetCheckMethod();
            return (false);
        }
    }

    public void closedInternetCheckMethod(){
        try {
            urlc.getInputStream().close();
            urlc.getOutputStream().close();
            urlc.disconnect();
        } catch (Exception ea) {
        }
    }

    public boolean network(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnected() && internetIsConnected()) {
            return true;
        } else {
            return false;
        }
    }

    @SuppressLint("StaticFieldLeak")
    public LiveData<Boolean> checkNetWork(Activity activity) {
        MutableLiveData<Boolean> response = new MutableLiveData<>();
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected Boolean doInBackground(Void... p) {
                return network(activity);
            }

            @Override
            protected void onPostExecute(Boolean result) {
                response.setValue(result);
            }
        }.execute();
        return response;
    }

    public void showAlertBox(String message, boolean chancel, Activity activity,boolean isFinish) {
        closeDialog(activity);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setMessage(message).setCancelable(chancel).setPositiveButton("Ok", (dialog, id) -> {
            if (isFinish) {
                activity.finish();
            }
            dialog.cancel();
        });
        AlertDialog alertDAssignment = builder.create();
        if (!alertDAssignment.isShowing()) {
            alertDAssignment.show();
        }
    }

    public void setProgressBar(String title, Context context, Activity activity) {
        closeDialog(activity);
        dialog = new ProgressDialog(context);
        dialog.setCancelable(false);
        dialog.setTitle(title);
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        if (!activity.isFinishing()) {
            try {
                dialog.show();
            }catch (Exception e){}
        }
    }

    public String decrypt(String password, String outputString) throws Exception {
        SecretKeySpec key = generateKey(outputString);
        Cipher c = Cipher.getInstance("AES");
        c.init(Cipher.DECRYPT_MODE, key);
        byte[] decodeValue = Base64.decode(password, Base64.DEFAULT);
        byte[] decVal = c.doFinal(decodeValue);
        return new String(decVal);
    }

    private SecretKeySpec generateKey(String userName) throws Exception {
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = userName.getBytes("UTF-8");
        digest.update(bytes, 0, bytes.length);
        byte[] key = digest.digest();
        return new SecretKeySpec(key, "AES");
    }

    @SuppressLint("SimpleDateFormat")
    public String date() {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    }

    @SuppressLint("SimpleDateFormat")
    public String year() {
        return new SimpleDateFormat("yyyy").format(new Date());
    }

    @SuppressLint("SimpleDateFormat")
    public String currentTime() {
        return new SimpleDateFormat("HH:mm").format(new Date());
    }

    @SuppressLint("SimpleDateFormat")
    public String monthName() {
        return new SimpleDateFormat("MMMM", Locale.US).format(new Date());
    }

    public void closeDialog(Activity activity) {
        try {
            if (dialog != null) {
                if (dialog.isShowing() && !activity.isFinishing()) {
                    dialog.dismiss();
                }
            }
        }catch (Exception e){}
    }

    public int formatDate(String pre, String current) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm");
        int totalHours = 0;
        try {
            Date date1 = simpleDateFormat.parse(pre);
            Date date2 = simpleDateFormat.parse(current);
            totalHours = TimeDifference(date1, date2);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return totalHours;
    }

    public int TimeDifference(Date startDate, Date endDate) {
        long different = endDate.getTime() - startDate.getTime();
        long secondsInMilli = 1000;
        long minutesInMilli = secondsInMilli * 60;
        long elapsedMinutes = different / minutesInMilli;
        return (int) elapsedMinutes;
    }

    public String getAddress(Geocoder geocoder, double lat, double lng) {
        List<Address> addresses = null;
        try {
            addresses = geocoder.getFromLocation(lat, lng, 1);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (addresses != null && !addresses.isEmpty()) {
            return addresses.get(0).getAddressLine(0);
        } else {
            return "Not Found";
        }
    }

    public LiveData<String> checkWhetherLocationSettingsAreAvailable(Activity activity) {
        MutableLiveData<String> response = new MutableLiveData<>();
        LocationRequest mLocationRequest = LocationRequest.create().setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY).setInterval(1000).setNumUpdates(2);
        final LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(mLocationRequest);
        builder.setAlwaysShow(true);
        builder.setNeedBle(false);
        SettingsClient client = LocationServices.getSettingsClient(activity);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());
        task.addOnSuccessListener(activity, locationSettingsResponse -> {
            response.setValue("yes");
        });
        task.addOnFailureListener(activity, e -> {
            if (e instanceof ResolvableApiException) {
                try {
                    ResolvableApiException resolvable = (ResolvableApiException) e;
                    resolvable.startResolutionForResult(activity, 501);
                } catch (IntentSender.SendIntentException ex) {
                    ex.printStackTrace();
                }
            }
        });
        return response;
    }

    public boolean isMockLocationOn(Location location, Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return location.isFromMockProvider();
        } else {
            String mockLocation = "0";
            try {
                mockLocation = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ALLOW_MOCK_LOCATION);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return !mockLocation.equals("0");
        }
    }

    public int timeDiff(Date startDate, Date endDate) {
        long different = endDate.getTime() - startDate.getTime();
        long secondsInMilli = 1000;
        long minutesInMilli = secondsInMilli * 60;
        long elapsedMinutes = different / minutesInMilli;
        return (int) elapsedMinutes;
    }
}
