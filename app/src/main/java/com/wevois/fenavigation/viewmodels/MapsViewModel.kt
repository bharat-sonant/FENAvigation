package com.wevois.fenavigation.viewmodels

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.location.Geocoder
import android.location.Location
import android.os.*
import android.util.Log
import android.util.Rational
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator
import android.widget.DatePicker
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.ObservableField
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.firebase.crashlytics.internal.Logger
import com.google.firebase.database.DataSnapshot
import com.google.maps.android.PolyUtil
import com.wevois.fenavigation.CommonMethods
import com.wevois.fenavigation.Model
import com.wevois.fenavigation.MyService
import com.wevois.fenavigation.model.BoundaryLatLngModel
import com.wevois.fenavigation.repository.MapsRepo
import com.wevois.fenavigation.views.Maps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import java.lang.Exception
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class MapsViewModel(var activity: Activity, var mapFragment: SupportMapFragment) : ViewModel() {
    var mMap: GoogleMap? = null
    var common = CommonMethods.getInstance()
    var isAlertBoxOpen = false
    var firstTime = true
    var isCall = true
    var isCallGetZoneWard = true
    var isDutyOff = true
    var isPictureInPictureMode = false
    var countDownTimerLocation: CountDownTimer? = null
    var countDownTimerGetZoneAndWard: CountDownTimer? = null
    var onPauseTimer: CountDownTimer? = null
    var latLng: LatLng? = null
    var previousLat: LatLng? = null
    var moveDistance = FloatArray(1)
    var markerCurrent: Marker? = null
    var markerMove: Marker? = null
    var repository = MapsRepo()
    var boundaryLatLngModels = ArrayList<BoundaryLatLngModel>()
    var ward = ObservableField("fetching..")
    var zone = ObservableField("fetching..")
    var totalDistance = ObservableField("fetching..")
    var totalHalt = ObservableField("fetching..")
    var totalPhoto = ObservableField("fetching..")
    var speedTv = ObservableField("0")
    var timeDate = ObservableField("0")
    var textTv = ObservableField("Gray color me previous day ka data show ho rha hai.")
    var preferences: SharedPreferences
    var directionPositionList = ArrayList<LatLng>()
    var polyline: Polyline? = null
    var polyLine1: Polyline? = null
    var haltMarker = HashMap<String, Marker>()
    private var mCustomMarkerView: View? = null
    private var mMarkerImageView: ImageView? = null
    var markerTV: TextView? = null
    var today = common.date()
    var imageMarker = HashMap<String, Marker?>()
    var isOpen = true
    var isScreenOn = true

    init {
        preferences = activity.getSharedPreferences("FirebasePath", Context.MODE_PRIVATE)
        common.checkWhetherLocationSettingsAreAvailable(activity).observe((activity as LifecycleOwner), { response: String? -> })
        common.setProgressBar("Please Wait", activity, activity)
        repository.fetchWardBoundariesData(activity).observe((activity as LifecycleOwner), { response: String? ->
            repository.wardFromAvailableLatLng(activity).observe((activity as LifecycleOwner), { result: ArrayList<BoundaryLatLngModel> ->
                boundaryLatLngModels = result
                common.closeDialog(activity)
                setWardAndZone()
            })
        })
        textTv.set(preferences.getString("PreviousDayMessage", ""))
        timeDate.set(today)
        initViews()
        setMap()
        onRefreshClick()
        callMethodAfterDutyOff()
        if (!preferences.getString("dutyOff", "").equals(common.date(), ignoreCase = true) && preferences.getString("dutyIn", "").equals(common.date(), ignoreCase = true)) {
            repository.checkDutyOff(activity, preferences.getString("uid", "")).observeForever { response: DataSnapshot? ->
                if (response != null) {
                    if (response.value != null) {
                        preferences.edit().putString("dutyOff", common.date()).apply()
                    } else {
                        callLocationService()
                    }
                } else {
                    callLocationService()
                }
            }
        }
        previousTrackingLine()
    }

    private fun previousTrackingLine() {
        val date1 = Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24)
        val date = SimpleDateFormat("yyyy-MM-dd").format(date1)
        val year = SimpleDateFormat("yyyy").format(date1)
        val month = SimpleDateFormat("MMMM", Locale.US).format(date1)
        preferences.getString("uid", "")?.let {
            repository.getLocationData(year, month, date, activity, it).observeForever { result: DataSnapshot? ->
                if (result != null) {
                    if (result.value != null) {
                        val data1 = ArrayList<LatLng>()
                        for (dataSnapshot in result.children) {
                            if (dataSnapshot.hasChild("lat-lng")) {
                                val data = dataSnapshot.child("lat-lng").value.toString().split("~").toTypedArray()
                                for (i in data.indices) {
                                    val latLngs = data[i].substring(1, data[i].length - 1).split(",").toTypedArray()
                                    if (latLngs[0].trim { it <= ' ' }.toDouble() != 0.0 && latLngs[1].trim { it <= ' ' }.toDouble() != 0.0) {
                                        data1.add(LatLng(latLngs[0].trim { it <= ' ' }.toDouble(), latLngs[1].trim { it <= ' ' }.toDouble()))
                                    }
                                }
                            }
                        }
                        showLineOnMap(data1, false)
                    }
                }
            }
        }
    }

    private fun callMethodAfterDutyOff() {
        val locationHistoryData = preferences.getString("LocationHistory", "")
        if (!locationHistoryData.equals("", ignoreCase = true)) {
            try {
                jsonObjectLocationHistory = JSONObject(locationHistoryData)
                setLocationOnMap()
            } catch (e: JSONException) {
                e.printStackTrace()
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

    private fun setMap() {
        mapFragment.getMapAsync { googleMap: GoogleMap? ->
            if (googleMap != null) {
                mMap = googleMap
            }
            val LOCATION_REQUEST = 500
            mMap = googleMap
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_REQUEST)
                return@getMapAsync
            }
            mMap!!.isMyLocationEnabled = false
            mMap!!.uiSettings.isCompassEnabled = false
            mMap!!.isBuildingsEnabled = true
            mMap!!.setMapStyle(MapStyleOptions.loadRawResourceStyle(activity, com.wevois.fenavigation.R.raw.style_json))
        }
    }

    private fun callLocationService() {
        if (!isMyServiceRunning(MyService::class.java)) {
            val locationHistoryData = preferences.getString("LocationHistory", "")
            if (locationHistoryData.equals("", ignoreCase = true)) {
                preferences.getString("uid", "")?.let {
                    repository.getLocationData(common.year(), common.monthName(), common.date(), activity, it).observeForever { result: DataSnapshot? ->
                        if (result != null) {
                            if (result.value != null) {
                                for (dataSnapshot in result.children) {
                                    if (dataSnapshot.hasChild("lat-lng")) {
                                        try {
                                            jsonObjectLocationHistory!!.put(dataSnapshot.key, dataSnapshot.child("lat-lng").value.toString())
                                        } catch (e: JSONException) {
                                            e.printStackTrace()
                                        }
                                        preferences.edit().putString("LocationHistory", jsonObjectLocationHistory.toString()).apply()
                                    }
                                }
                                if (result.hasChild("TotalCoveredDistance")) {
                                    try {
                                        jsonObjectLocationHistory!!.put("totalDistance", "" + result.child("TotalCoveredDistance").value.toString().toDouble().toInt())
                                    } catch (e: JSONException) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                        Log.d("TAG", "onStartCommand: check D4 ")
                        val intent = Intent(activity, MyService::class.java)
                        ContextCompat.startForegroundService(activity, intent)
                    }
                }
            } else {
                val intent = Intent(activity, MyService::class.java)
                ContextCompat.startForegroundService(activity, intent)
            }
        }
        Log.d("TAG", "onStartCommand: check D5 ")
        val intentFilter = IntentFilter("locationInfo")
        activity.registerReceiver(locReceiver, intentFilter)
    }

    private var locReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "locationInfo") {
                val latB = intent.getDoubleExtra("lat", 0.0)
                val lngB = intent.getDoubleExtra("lng", 0.0)
                val alertBox = intent.getStringExtra("alertBox")
                val speed = intent.getFloatExtra("speed", 0f)
                val locationString = intent.getStringExtra("locationHistoryJson")
                //String haltString = intent.getStringExtra("haltJson");
                try {
                    jsonObjectLocationHistory = JSONObject(locationString)
                    //jsonObjectHalt = new JSONObject(haltString);
                } catch (e: Exception) {
                }
                speedTv.set("" + (speed * 3.6).toInt())
                latLng = LatLng(latB, lngB)
                if (preferences.getString("BoundariesLatLng", "")!!.length > 0) {
                    if (isCallGetZoneWard) {
                        isCallGetZoneWard = false
                        if (countDownTimerGetZoneAndWard != null) {
                            countDownTimerGetZoneAndWard!!.cancel()
                            countDownTimerGetZoneAndWard = null
                        }
                        getZoneAndWard()
                    }
                }
                if (isCall) {
                    isCall = false
                    firstTime = true
                    if (countDownTimerLocation != null) {
                        countDownTimerLocation!!.cancel()
                        countDownTimerLocation = null
                    }
                    currentLocationShow()
                }
                if (alertBox == "yes") {
                    if (!isAlertBoxOpen) {
                        isAlertBoxOpen = true
                        if (isPictureInPictureMode) {
                            if (!preferences.getString("isPictureInPictureAllow", "").equals("no", true)) {
                                reOpenActivity()
                            }
                        }
                        common.checkWhetherLocationSettingsAreAvailable(activity).observeForever { response: String? -> }
                    }
                }
            }
        }
    }

    private fun setScreenOff() {
        try {
            if (isScreenOn) {
                val pm = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!pm.isScreenOn) {
                    isScreenOn = false
                    repository.sendActivityEvents("ScreenOff", common.getDatabaseForApplication(activity), preferences)
                }
            }else{
                val pm = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (pm.isScreenOn) {
                    isScreenOn = true
                    repository.sendActivityEvents("ScreenOn", common.getDatabaseForApplication(activity), preferences)
                }
            }
        }catch (e:Exception){}
    }

    fun pictureInPictureMode(isPictureInPictureModes: Boolean) {
        isPictureInPictureMode = if (isPictureInPictureModes) {
            isPictureInPictureModes
        } else {
            isPictureInPictureModes
        }
    }

    private fun currentLocationShow() {
        CoroutineScope(Dispatchers.Main).launch {
            countDownTimerLocation = object : CountDownTimer(2000, 1000) {
                override fun onTick(millisUntilFinished: Long) {}
                override fun onFinish() {
                    Log.d("TAG", "onReceive: check screen data ")
                    setScreenOff()
                    if (firstTime) {
                        firstTime = false
                        previousLat = latLng
                        setMovingMarker()
                    }
                    Location.distanceBetween(latLng!!.latitude, latLng!!.longitude, previousLat!!.latitude, previousLat!!.longitude, moveDistance)
                    if (moveDistance[0] > 2) {
                        val startPosition = previousLat!!
                        val finalPosition = latLng!!
                        val handler = Handler()
                        val start = SystemClock.uptimeMillis()
                        val interpolator: Interpolator = AccelerateDecelerateInterpolator()
                        val durationInMs = 500f
                        handler.post(object : Runnable {
                            var elapsed: Long = 0
                            var t = 0f
                            var v = 0f
                            override fun run() {
                                elapsed = SystemClock.uptimeMillis() - start
                                t = elapsed / durationInMs
                                v = interpolator.getInterpolation(t)
                                val currentPosition = LatLng(
                                        startPosition.latitude * (1 - t) + finalPosition.latitude * t,
                                        startPosition.longitude * (1 - t) + finalPosition.longitude * t)
                                markerCurrent!!.position = currentPosition
                                markerMove!!.position = currentPosition
                                if (t < 1) {
                                    markerCurrent!!.isVisible = false
                                    markerMove!!.isVisible = true
                                    handler.postDelayed(this, 10)
                                } else {
                                    markerMove!!.isVisible = false
                                    markerCurrent!!.isVisible = true
                                }
                            }
                        })
                        previousLat = latLng
                    }
                    if (!preferences.getString("dutyOff", "").equals(common.date(), ignoreCase = true) && preferences.getString("dutyIn", "").equals(common.date(), ignoreCase = true)) {
                        currentLocationShow()
                    } else {
                        activity.stopService(Intent(activity, MyService::class.java))
                    }
                }
            }.start()
        }
    }

    private fun getZoneAndWard() {
        CoroutineScope(Dispatchers.Main).launch {
            countDownTimerGetZoneAndWard = object : CountDownTimer(60000, 10000) {
                override fun onTick(millisUntilFinished: Long) {}
                override fun onFinish() {
                    if (!preferences.getString("dutyOff", "").equals(common.date(), true) && preferences.getString("dutyIn", "").equals(common.date(), true)) {
                        if (today.equals(common.date(), ignoreCase = true)) {
                            Log.d("TAG", "onReceive: check A " + jsonObjectLocationHistory)
                            setWardAndZone()
                            if (jsonObjectLocationHistory != null) setLocationOnMap()
                            //                                if (jsonObjectHalt != null)
//                                    setHaltOnMap();
                        }
                        if (!preferences.getString("dutyOff", "").equals(common.date(), true) && preferences.getString("dutyIn", "").equals(common.date(), true)) {
                            getZoneAndWard()
                        }
                    }
                }
            }.start()
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
    private fun getMarkerBitmapFromView(view: View?, background: Int, key: String): Bitmap {
        mMarkerImageView!!.setImageResource(com.wevois.fenavigation.R.drawable.ic_capture_image_loc)
        markerTV!!.text = key
        markerTV!!.setBackgroundResource(background)
        view!!.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        view.buildDrawingCache()
        val returnedBitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        canvas.drawColor(Color.WHITE, PorterDuff.Mode.SRC_IN)
        val drawable = view.background
        drawable?.draw(canvas)
        view.draw(canvas)
        return returnedBitmap
    }

    private fun initViews() {
        mCustomMarkerView = (activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(com.wevois.fenavigation.R.layout.view_custom_marker, null)
        mMarkerImageView = mCustomMarkerView!!.findViewById(com.wevois.fenavigation.R.id.profile_image)
        markerTV = mCustomMarkerView!!.findViewById(com.wevois.fenavigation.R.id.haltTimeTV)
    }

    private fun setLocationOnMap() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                directionPositionList.clear()
                if (jsonObjectLocationHistory!!.has("totalDistance")) {
                    showTotalDistance(jsonObjectLocationHistory!!.getString("totalDistance"))
                }
            } catch (e: Exception) {
            }
            var iterator: Iterator<String>? = null
            try {
                iterator = jsonObjectLocationHistory!!.keys()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            while (iterator!!.hasNext()) {
                try {
                    val key = iterator.next()
                    if (!key.equals("totalDistance", ignoreCase = true)) {
                        val stringLatLng = jsonObjectLocationHistory!!.getString(key).split("~").toTypedArray()
                        for (i in stringLatLng.indices) {
                            val latLngs = stringLatLng[i].substring(1, stringLatLng[i].length - 1).split(",").toTypedArray()
                            if (latLngs[0].trim { it <= ' ' }.toDouble() != 0.0 && latLngs[1].trim { it <= ' ' }.toDouble() != 0.0) {
                                directionPositionList.add(LatLng(latLngs[0].trim { it <= ' ' }.toDouble(), latLngs[1].trim { it <= ' ' }.toDouble()))
                            }
                        }
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
            showLineOnMap(directionPositionList, true)
        }
    }

    private fun setMovingMarker() {
        CoroutineScope(Dispatchers.Main).launch {
            val height = 100
            val width = 50
            val bitManOne = activity.resources.getDrawable(com.wevois.fenavigation.R.drawable.map_men) as BitmapDrawable
            val bitManMove = activity.resources.getDrawable(com.wevois.fenavigation.R.drawable.map_men) as BitmapDrawable
            val markerOptions = MarkerOptions()
            val markerOptionsMove = MarkerOptions()
            markerOptions.position(latLng!!)
            markerOptionsMove.position(latLng!!)
            markerOptionsMove.icon(BitmapDescriptorFactory.fromBitmap(Bitmap.createScaledBitmap(bitManMove.bitmap, width, height, false)))
            markerOptions.icon(BitmapDescriptorFactory.fromBitmap(Bitmap.createScaledBitmap(bitManOne.bitmap, width, height, false)))
            if (markerCurrent != null) {
                markerCurrent!!.remove()
            }
            markerCurrent = mMap!!.addMarker(markerOptions)
            markerMove = mMap!!.addMarker(markerOptionsMove)
            markerMove!!.isVisible = false
            val builder = LatLngBounds.Builder()
            //                if (directionPositionList.size() > 0) {
//                    for (int a = 0; a < directionPositionList.size(); a++) {
//                        builder.include(directionPositionList.get(a));
//                    }
//                }
            if (latLng != null) {
                builder.include(latLng!!)
            }
            val cu = CameraUpdateFactory.newLatLngBounds(builder.build(), 100)
            try {
                mMap!!.animateCamera(cu)
            } catch (e: Exception) {
            }
        }
    }

    fun destroy() {
        repository.sendActivityEvents("Closed", common.getDatabaseForApplication(activity), preferences)
        common.closeDialog(activity)
        try {
            activity.unregisterReceiver(locReceiver)
        } catch (e: Exception) {
        }
    }

    fun resume() {
        isAlertBoxOpen = false
        isDutyOff = true
        if (onPauseTimer != null) {
            onPauseTimer!!.cancel()
            onPauseTimer = null
        }
        if (!preferences.getString("dutyOff", "").equals(common.date(), true) && preferences.getString("dutyIn", "").equals(common.date(), true)) {
            repository.sendActivityEvents("Opened", common.getDatabaseForApplication(activity), preferences)
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    fun pause() {
        if (!isAlertBoxOpen) {
            pictureInPictureMethod()
        }
        if (!preferences.getString("dutyOff", "").equals(common.date(), true) && preferences.getString("dutyIn", "").equals(common.date(), true)) {
            repository.sendActivityEvents("Minimized", common.getDatabaseForApplication(activity), preferences)
            if (preferences.getString("isAppOpen", "").equals("yes", true)) {
                if (onPauseTimer != null) {
                    onPauseTimer!!.cancel()
                    onPauseTimer = null
                }
                pauseTimerCall()
            }
        }
    }

    private fun pauseTimerCall() {
        onPauseTimer = object : CountDownTimer(preferences.getInt("appClosedTimerTime", 60000).toLong(), 10000) {
            @SuppressLint("MissingPermission")
            override fun onTick(millisUntilFinished: Long) {
            }

            @SuppressLint("SimpleDateFormat")
            override fun onFinish() {
                reOpenActivity()
            }
        }.start()
    }

    fun reOpenActivity() {
        if (!preferences.getString("dutyOff", "").equals(common.date(), true) && preferences.getString("dutyIn", "").equals(common.date(), true)) {
            if (isScreenOn) {
                val rIntent = Intent(activity, Maps::class.java)
                rIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                val intent = PendingIntent.getActivity(activity, 0, rIntent, PendingIntent.FLAG_IMMUTABLE)
                val manager = activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                manager[AlarmManager.RTC, System.currentTimeMillis()] = intent
            }
        }
    }

    fun stop() {
        if (!preferences.getString("isPictureInPictureAllow", "").equals("no", true)) {
            reOpenActivity()
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    fun back() {
        pictureInPictureMethod()
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun pictureInPictureMethod() {
        if (!preferences.getString("dutyOff", "").equals(common.date(), ignoreCase = true) && preferences.getString("dutyIn", "").equals(common.date(), ignoreCase = true)) {
            Log.d(Logger.TAG, "pictureInPictureMethod: check " + preferences.getString("isPictureInPictureAllow", ""))
            if (!preferences.getString("isPictureInPictureAllow", "").equals("no", ignoreCase = true)) {
                val d = activity.windowManager.defaultDisplay
                val p = Point()
                d.getSize(p)
                val width = p.x
                val height = p.y
                val ratio = Rational(width, height)
                val pip_Builder = PictureInPictureParams.Builder()
                pip_Builder.setAspectRatio(ratio).build()
                activity.enterPictureInPictureMode()
            }
        }
    }

    fun onRefreshClick() {
        common.setProgressBar("Please wait...", activity, activity)
        getPhoto(common.year(), common.monthName(), common.date(), true)
    }

    fun onDutyOffClick() {
        if (!preferences.getString("dutyOff", "").equals(common.date(), ignoreCase = true) && preferences.getString("dutyIn", "").equals(common.date(), ignoreCase = true)) {
            if (isDutyOff) {
                isDutyOff = false
                common.setProgressBar("Check network...", activity, activity)
                val geocoder = Geocoder(activity, Locale.getDefault())
                val address = common.getAddress(geocoder, latLng!!.latitude, latLng!!.longitude)
                common.checkNetWork(activity).observeForever { result: Boolean ->
                    if (result) {
                        common.closeDialog(activity)
                        val builder = AlertDialog.Builder(activity)
                        builder.setMessage("Are you sure?\nYou want to duty off.")
                                .setCancelable(false)
                                .setPositiveButton("Yes") { dialog: DialogInterface, id: Int ->
                                    common.setProgressBar("Uploading...", activity, activity)
                                    preferences.getString("uid", "")?.let {
                                        repository.dutyOff(activity, it, latLng!!, address).observeForever { response: String ->
                                            common.closeDialog(activity)
                                            if (response.equals("success", ignoreCase = true)) {
                                                preferences.edit().putString("dutyOff", common.date()).apply()
                                                activity.stopService(Intent(activity, MyService::class.java))
                                            } else {
                                                common.showAlertBox("please Refresh Location", false, activity, false)
                                            }
                                            isDutyOff = true
                                        }
                                    }
                                    dialog.cancel()
                                }.setNegativeButton("No"
                                ) { dialog: DialogInterface, i: Int ->
                                    dialog.cancel()
                                    isDutyOff = true
                                }
                        val alertDialog = builder.create()
                        alertDialog.show()
                    } else {
                        common.closeDialog(activity)
                        isDutyOff = true
                        val builder = AlertDialog.Builder(activity)
                        builder.setMessage("Internet not Available")
                                .setCancelable(false)
                                .setPositiveButton("Retry") { dialog: DialogInterface, id: Int ->
                                    onDutyOffClick()
                                    dialog.cancel()
                                }.setNegativeButton("Exit") { dialog: DialogInterface?, i: Int -> activity.finish() }
                        val alertDialog = builder.create()
                        alertDialog.show()
                    }
                }
            }
        } else {
            common.showAlertBox("Already duty off.", true, activity, false)
        }
    }

    fun onClickOpen() {
        try {
            val i = Intent()
            i.action = Intent.ACTION_VIEW
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            i.setClassName("com.wevois.wastebinmonitor", "com.wevois.wastebinmonitor.SplashActivity")
            activity.startActivity(i)
        } catch (e: Exception) {
            common.showAlertBox("Wastebin app not found.", false, activity, false)
        }
    }

    @SuppressLint("StaticFieldLeak")
    fun datePicker() {
        if (isOpen) {
            isOpen = false
            val mYear: Int
            val mMonth: Int
            val mDay: Int
            val c = Calendar.getInstance()
            mYear = c[Calendar.YEAR]
            mMonth = c[Calendar.MONTH]
            mDay = c[Calendar.DAY_OF_MONTH]
            val datePickerDialog = DatePickerDialog(activity,
                    { view: DatePicker?, year: Int, monthOfYear: Int, dayOfMonth: Int ->
                        val date_time: String
                        date_time = if (monthOfYear + 1 <= 9) {
                            if (dayOfMonth <= 9) {
                                year.toString() + "-0" + (monthOfYear + 1) + "-" + "0" + dayOfMonth
                            } else {
                                year.toString() + "-0" + (monthOfYear + 1) + "-" + dayOfMonth
                            }
                        } else {
                            if (dayOfMonth <= 9) {
                                year.toString() + "-" + (monthOfYear + 1) + "-" + "0" + dayOfMonth
                            } else {
                                year.toString() + "-" + (monthOfYear + 1) + "-" + dayOfMonth
                            }
                        }
                        val fromUser = SimpleDateFormat("yyyy-MM-dd")
                        val myYear = SimpleDateFormat("yyyy")
                        val myMonth = SimpleDateFormat("MMMM", Locale.US)
                        today = date_time
                        timeDate.set(today)
                        common.setProgressBar("Please wait...", activity, activity)
                        isOpen = true
                        if (date_time.equals(common.date(), ignoreCase = true)) {
                            ward.set("fetching..")
                            zone.set("fetching..")
                            setWardAndZone()
                            setLocationOnMap()
                            //setHaltOnMap();
                        } else {
                            ward.set("...")
                            zone.set("...")
                            try {
                                preferences.getString("uid", "")?.let {
                                    repository.getLocationData(myYear.format(fromUser.parse(date_time)), myMonth.format(fromUser.parse(date_time)), date_time, activity, it).observeForever { result: DataSnapshot? ->
                                        if (result != null) {
                                            if (result.value != null) {
                                                try {
                                                    directionPositionList.clear()
                                                    if (result.hasChild("TotalCoveredDistance")) {
                                                        showTotalDistance(result.child("TotalCoveredDistance").value.toString().toDouble().toInt().toString())
                                                    }
                                                } catch (e: Exception) {
                                                }
                                                for (snapshot in result.children) {
                                                    if (snapshot.hasChild("lat-lng")) {
                                                        val stringLatLng = snapshot.child("lat-lng").value.toString().split("~").toTypedArray()
                                                        for (i in stringLatLng.indices) {
                                                            val latLngs = stringLatLng[i].substring(1, stringLatLng[i].length - 1).split(",").toTypedArray()
                                                            if (latLngs[0].trim { it <= ' ' }.toDouble() != 0.0 && latLngs[1].trim { it <= ' ' }.toDouble() != 0.0) {
                                                                directionPositionList.add(LatLng(latLngs[0].trim { it <= ' ' }.toDouble(), latLngs[1].trim { it <= ' ' }.toDouble()))
                                                            }
                                                        }
                                                    }
                                                }
                                                showLineOnMap(directionPositionList, true)
                                            } else {
                                                clearPreviousLocationDataOnMap()
                                            }
                                        } else {
                                            clearPreviousLocationDataOnMap()
                                        }
                                    }
                                }
                            } catch (e: ParseException) {
                                e.printStackTrace()
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
                            getPhoto(myYear.format(fromUser.parse(date_time)), myMonth.format(fromUser.parse(date_time)), date_time, false)
                        } catch (e: ParseException) {
                            e.printStackTrace()
                        }
                    }, mYear, mMonth, mDay)
            datePickerDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel") { dialog: DialogInterface?, which: Int ->
                datePickerDialog.dismiss()
                isOpen = true
            }
            datePickerDialog.show()
        }
    }

    private fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun showTotalDistance(distance: String) {
        if (distance.toInt() >= 1000) {
            totalDistance.set("" + distance.toDouble() / 1000 + " Km")
        } else {
            totalDistance.set("" + distance.toInt() + " m")
        }
    }

    private fun showLineOnMap(distance: ArrayList<LatLng>, b: Boolean) {
        activity.runOnUiThread {
            if (b) {
                try {
                    polyline!!.remove()
                } catch (e: Exception) {
                }
                polyline = mMap!!.addPolyline(PolylineOptions().addAll(distance).color(Color.GREEN))
            } else {
                polyLine1 = mMap!!.addPolyline(PolylineOptions().addAll(distance).color(Color.GRAY))
            }
            common.closeDialog(activity)
            if (preferences.getString("dutyOff", "").equals(common.date(), ignoreCase = true)) {
                val builder = LatLngBounds.Builder()
                if (distance.size > 0) {
                    for (a in distance.indices) {
                        builder.include(distance[a])
                    }
                }
                if (latLng != null) {
                    builder.include(latLng!!)
                }
                try {
                    val cu = CameraUpdateFactory.newLatLngBounds(builder.build(), 100)
                    mMap!!.animateCamera(cu)
                } catch (e: Exception) {
                }
            }
        }
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
    private fun showImageCaptureLocationOnMap(haltLatLng: Array<String>, duration: String, key: String) {
        activity.runOnUiThread {
            val backColor: Int = com.wevois.fenavigation.R.drawable.outline_bg_map
            val markers = arrayOfNulls<Marker>(1)
            markers[0] = mMap!!.addMarker(MarkerOptions().position(LatLng(haltLatLng[0].toDouble(), haltLatLng[1].toDouble())).title(key)
                    .icon(BitmapDescriptorFactory.fromBitmap(getMarkerBitmapFromView(mCustomMarkerView, backColor, duration))))
            markers[0]!!.tag = key
            imageMarker[key] = markers[0]
        }
    }

    private fun clearPreviousLocationDataOnMap() {
        common.closeDialog(activity)
        totalDistance.set("0 m")
        try {
            directionPositionList.clear()
        } catch (e: Exception) {
        }
        try {
            polyline!!.remove()
        } catch (e: Exception) {
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
    private fun clearPreviousImageOnMap() {
        try {
            activity.runOnUiThread {
                val size = imageMarker.size
                for (i in 0 until size) {
                    try {
                        val marker = imageMarker[imageMarker.keys.toTypedArray()[0]]
                        marker!!.remove()
                        imageMarker.remove(imageMarker.keys.toTypedArray()[0])
                    } catch (e: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
        }
    }

    private fun getPhoto(year: String?, month: String?, date: String?, isProgressOn: Boolean) {
        repository.getTotalPhoto(activity, preferences.getString("uid", ""), year!!, month!!, date!!).observeForever { response: ArrayList<Model> ->
            if (isProgressOn) {
                common.closeDialog(activity)
            }
            clearPreviousImageOnMap()
            if (response.size != 0) {
                var count = 1
                for (model in response) {
                    val lng = model.latLng.trim { it <= ' ' }.split(",").toTypedArray()
                    showImageCaptureLocationOnMap(lng, "" + count, model.time)
                    count++
                }
                totalPhoto.set("" + response.size)
            } else {
                totalPhoto.set("0")
            }
        }
    }

    fun setWardAndZone() {
        if (latLng != null) {
            Log.d(Logger.TAG, "setWardAndZone: check $boundaryLatLngModels")
            if (!boundaryLatLngModels.isEmpty()) {
                Log.d(Logger.TAG, "setWardAndZone: check A $boundaryLatLngModels")
                for (boundaryLatLngModel in boundaryLatLngModels) {
                    if (PolyUtil.containsLocation(LatLng(latLng!!.latitude, latLng!!.longitude), boundaryLatLngModel.latLngArrayList, true)) {
                        val data = boundaryLatLngModel.key.split("_").toTypedArray()
                        ward.set(data[0])
                        zone.set(data[1])
                        break
                    }
                }
            }
        }
    }

    companion object {
        var jsonObjectLocationHistory: JSONObject? = JSONObject()
        var jsonObjectHalt = JSONObject()
    }
}