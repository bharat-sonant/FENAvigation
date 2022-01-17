package com.wevois.fenavigation.views;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProviders;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.wevois.fenavigation.R;
import com.wevois.fenavigation.databinding.ActivityLoginScreenBinding;
import com.wevois.fenavigation.viewmodels.LoginViewModel;

public class LoginScreen extends AppCompatActivity {
    ActivityLoginScreenBinding binding;
    LoginViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_login_screen);
        viewModel = ViewModelProviders.of(this).get(LoginViewModel.class);
        binding.setLoginviewmodel(viewModel);
        binding.setLifecycleOwner(this);
        viewModel.init(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        viewModel.updateUI(FirebaseAuth.getInstance().getCurrentUser());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d("TAG", "googleSignInBtn: check A ");
        viewModel.resultActivity(requestCode, resultCode, data);
    }
}