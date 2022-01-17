package com.wevois.fenavigation.viewmodelfactory;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.maps.SupportMapFragment;
import com.wevois.fenavigation.viewmodels.HomeMapsViewModel;

public class HomeMapsViewModelFactory implements ViewModelProvider.Factory {
    Activity activity;
    SupportMapFragment fragment;

    public HomeMapsViewModelFactory(Activity activity, SupportMapFragment fragmentById) {
        this.activity = activity;
        this.fragment = fragmentById;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        return (T) new HomeMapsViewModel(activity,fragment);
    }
}

