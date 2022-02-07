package com.wevois.fenavigation.views

import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.maps.SupportMapFragment
import com.wevois.fenavigation.CommonMethods
import com.wevois.fenavigation.R
import com.wevois.fenavigation.databinding.ActivityMapsBinding
import com.wevois.fenavigation.viewmodelfactory.MapsViewModelFactory
import com.wevois.fenavigation.viewmodels.MapsViewModel

class Maps : AppCompatActivity() {
    var viewModel: MapsViewModel? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding: ActivityMapsBinding = DataBindingUtil.setContentView(this, R.layout.activity_maps)
        viewModel = ViewModelProvider(this, MapsViewModelFactory(this, (supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?)!!)).get(MapsViewModel::class.java)
        binding.mapsviewmodel = viewModel
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel!!.destroy()
    }

    override fun onResume() {
        super.onResume()
        Log.d("TAG", "onReceive: check screen data C ")
        viewModel!!.resume()
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    override fun onBackPressed() {
        val preferences = getSharedPreferences("FirebasePath", MODE_PRIVATE)
        if (!preferences.getString("dutyOff", "").equals(CommonMethods.getInstance().date(), ignoreCase = true) && preferences.getString("dutyIn", "").equals(CommonMethods.getInstance().date(), ignoreCase = true)) {
            if (!preferences.getString("isPictureInPictureAllow", "").equals("no", ignoreCase = true)) {
                viewModel!!.back()
            } else {
                super.onBackPressed()
            }
        } else {
            super.onBackPressed()
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    override fun onStop() {
        super.onStop()
        viewModel!!.stop()
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    override fun onPause() {
        super.onPause()
        viewModel!!.pause()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration?) {
        viewModel!!.pictureInPictureMode(isInPictureInPictureMode)
    }
}