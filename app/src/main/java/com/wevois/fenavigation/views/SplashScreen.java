package com.wevois.fenavigation.views;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProviders;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;

import com.wevois.fenavigation.R;
import com.wevois.fenavigation.databinding.ActivitySplashScreenBinding;
import com.wevois.fenavigation.viewmodels.SplashViewModel;

public class SplashScreen extends AppCompatActivity {
    ActivitySplashScreenBinding binding;
    SplashViewModel viewModel;
    boolean checkPermission=false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_splash_screen);
        viewModel = ViewModelProviders.of(this).get(SplashViewModel.class);
        binding.setSplashviewmodel(viewModel);
        Animation myAnim = AnimationUtils.loadAnimation(this, R.anim.mysplashanimation);
        binding.layout.setAnimation(myAnim);
        viewModel.init(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 500) {
            if (grantResults.length > 0) {
                for (int i = 0; i < permissions.length; i++) {
                    String per = permissions[i];
                    if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        if (ActivityCompat.shouldShowRequestPermissionRationale(this, per)) {
                            AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
                            alertBuilder.setCancelable(false);
                            alertBuilder.setTitle("जरूरी सूचना");
                            alertBuilder.setMessage("सभी permissions देना अनिवार्य है बिना permissions के आप आगे नहीं बढ़ सकते है |");
                            alertBuilder.setPositiveButton(android.R.string.yes, (dialog, which) -> viewModel.checkPermission());

                            AlertDialog alert = alertBuilder.create();
                            alert.show();
                        } else {
                            AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
                            alertBuilder.setCancelable(false);
                            alertBuilder.setTitle("जरूरी सूचना");
                            alertBuilder.setMessage("सभी permissions देना अनिवार्य है बिना permissions के आप आगे नहीं बढ़ सकते है |");
                            alertBuilder.setPositiveButton(android.R.string.yes, (dialog, which) -> {
                                checkPermission = true;
                                Intent intent = new Intent();
                                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package", this.getPackageName(), null);
                                intent.setData(uri);
                                startActivity(intent);
                            });

                            AlertDialog alert = alertBuilder.create();
                            alert.show();
                        }
                        return;
                    }
                }
                viewModel.checkPermission();
            } else {
                viewModel.checkPermission();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 500) {
            if (resultCode == RESULT_OK) {
                viewModel.checkPermission();
            } else {
                viewModel.checkPermission();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (checkPermission) {
            checkPermission = false;
            viewModel.checkPermission();
        }
    }
}