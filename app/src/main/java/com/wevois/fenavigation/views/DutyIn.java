package com.wevois.fenavigation.views;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProviders;

import android.content.Intent;
import android.os.Bundle;

import com.wevois.fenavigation.R;
import com.wevois.fenavigation.databinding.ActivityDutyInBinding;
import com.wevois.fenavigation.viewmodels.DutyInViewModel;

public class DutyIn extends AppCompatActivity {
    ActivityDutyInBinding binding;
    DutyInViewModel viewModel;
    public static final int REFRESH_LOCATION = 750;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_duty_in);
        viewModel = ViewModelProviders.of(this).get(DutyInViewModel.class);
        binding.setDutyinviewmodel(viewModel);
        binding.setLifecycleOwner(this);
        viewModel.init(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.resume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        viewModel.destroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REFRESH_LOCATION) {
            if (resultCode == RESULT_OK) {
                viewModel.getLocation();
            } else {
                viewModel.getLocationPermission();
            }
        }
    }
}