package com.wevois.fenavigation.views;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProviders;

import android.annotation.TargetApi;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowManager;
import com.google.android.gms.maps.SupportMapFragment;
import com.wevois.fenavigation.R;
import com.wevois.fenavigation.databinding.ActivityHomeMapsBinding;
import com.wevois.fenavigation.viewmodels.HomeMapsViewModel;

public class HomeMapsActivity extends AppCompatActivity {
    private ActivityHomeMapsBinding binding;
    HomeMapsViewModel viewModel;
    SupportMapFragment fragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
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

    @Override
    protected void onPause() {
        super.onPause();
        viewModel.pause();
    }
}