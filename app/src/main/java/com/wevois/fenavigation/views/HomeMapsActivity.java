package com.wevois.fenavigation.views;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProviders;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.util.Rational;
import android.view.Display;
import android.view.WindowManager;
import com.google.android.gms.maps.SupportMapFragment;
import com.wevois.fenavigation.CommonMethods;
import com.wevois.fenavigation.R;
import com.wevois.fenavigation.databinding.ActivityHomeMapsBinding;
import com.wevois.fenavigation.viewmodels.HomeMapsViewModel;

public class HomeMapsActivity extends AppCompatActivity {
    private ActivityHomeMapsBinding binding;
    HomeMapsViewModel viewModel;
    SupportMapFragment fragment;
    SharedPreferences preferences;
    CommonMethods common = CommonMethods.getInstance();

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences("FirebasePath", MODE_PRIVATE);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_home_maps);
        viewModel = ViewModelProviders.of(this).get(HomeMapsViewModel.class);
        binding.setHomemapsviewmodel(viewModel);
        binding.setLifecycleOwner(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        fragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        viewModel.init(this, fragment);
        checkPermission();
    }

    public void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 1);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1) {
            if (!Settings.canDrawOverlays(this)) {
                checkPermission();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        viewModel.destroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.resume();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onBackPressed() {
        pictureInPictureMethod();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onStop() {
        super.onStop();
        if (!preferences.getString("dutyOff", "").equalsIgnoreCase(common.date()) && preferences.getString("dutyIn", "").equalsIgnoreCase(common.date())) {
            Intent rIntent = new Intent(this, HomeMapsActivity.class);
            rIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent intent = PendingIntent.getActivity(this, 0, rIntent, PendingIntent.FLAG_IMMUTABLE);
            AlarmManager manager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            manager.set(AlarmManager.RTC, System.currentTimeMillis(), intent);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onPause() {
        super.onPause();
        viewModel.pause();
        pictureInPictureMethod();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void pictureInPictureMethod() {
        if (!preferences.getString("dutyOff", "").equalsIgnoreCase(common.date()) && preferences.getString("dutyIn", "").equalsIgnoreCase(common.date())) {
            Display d = getWindowManager().getDefaultDisplay();
            Point p = new Point();
            d.getSize(p);
            int width = p.x;
            int height = p.y;
            Rational ratio = new Rational(width, height);
            PictureInPictureParams.Builder pip_Builder = new PictureInPictureParams.Builder();
            pip_Builder.setAspectRatio(ratio).build();
            enterPictureInPictureMode();
        }
    }
}