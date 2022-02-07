package com.wevois.fenavigation.viewmodelfactory

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.maps.SupportMapFragment
import com.wevois.fenavigation.viewmodels.MapsViewModel

class MapsViewModelFactory(var activity: Activity, var fragment: SupportMapFragment) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return MapsViewModel(activity, fragment) as T
    }
}
