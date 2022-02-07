package com.wevois.fenavigation.repository

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FileDownloadTask
import com.google.firebase.storage.StorageMetadata
import com.wevois.fenavigation.CommonMethods
import com.wevois.fenavigation.Model
import com.wevois.fenavigation.model.BoundaryLatLngModel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.lang.Exception
import java.lang.StringBuilder
import java.text.SimpleDateFormat
import java.util.*

class MapsRepo {
    var common = CommonMethods.getInstance()

    fun fetchWardBoundariesData(activity: Activity): LiveData<String> {
        val response = MutableLiveData<String>()
        GlobalScope.launch {
            val preferences = activity.getSharedPreferences("FirebasePath", Context.MODE_PRIVATE)
            common.getStoRef(activity).child("/Defaults/BoundariesLatLng.json").metadata.addOnSuccessListener { storageMetadata: StorageMetadata ->
                val fileCreationTime = storageMetadata.creationTimeMillis
                val fileDownloadTime = preferences.getLong("BoundariesLatLngDownloadTime", 0)
                if (fileDownloadTime != fileCreationTime) {
                    try {
                        val localFile = File.createTempFile("images", "jpg")
                        common.getStoRef(activity).child("/Defaults/BoundariesLatLng.json").getFile(localFile)
                                .addOnCompleteListener { task: Task<FileDownloadTask.TaskSnapshot?>? ->
                                    try {
                                        val br = BufferedReader(InputStreamReader(FileInputStream(localFile)))
                                        val sb = StringBuilder()
                                        var str: String?
                                        while (br.readLine().also { str = it } != null) {
                                            sb.append(str)
                                        }
                                        preferences.edit().putString("BoundariesLatLng", sb.toString()).apply()
                                        preferences.edit().putLong("BoundariesLatLngDownloadTime", fileCreationTime).apply()
                                        response.value = "success"
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    response.setValue("success")
                }
            }
        }
        return response
    }

    @SuppressLint("StaticFieldLeak")
    fun wardFromAvailableLatLng(activity: Activity): LiveData<ArrayList<BoundaryLatLngModel>> {
        val response = MutableLiveData<ArrayList<BoundaryLatLngModel>>()
        GlobalScope.launch {
            val boundaryLatLngModels = ArrayList<BoundaryLatLngModel>()
            val preferences = activity.getSharedPreferences("FirebasePath", Context.MODE_PRIVATE)
            var iterator: Iterator<String?>? = null
            try {
                iterator = JSONObject(preferences.getString("BoundariesLatLng", "")).keys()
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            while (iterator!!.hasNext()) {
                try {
                    val key = iterator.next()
                    val tempLatLngArray = JSONArray(JSONObject(preferences.getString("BoundariesLatLng", ""))[key].toString())
                    val latLngOfBoundaryArrayList = ArrayList<LatLng>()
                    for (i in 0..tempLatLngArray.length() - 1) {
                        val latlngArray = tempLatLngArray[i].toString().split(",").toTypedArray()
                        latLngOfBoundaryArrayList.add(LatLng(latlngArray[1].trim { it <= ' ' }.toDouble(), latlngArray[0].trim { it <= ' ' }.toDouble()))
                        if (i == tempLatLngArray.length() - 1) {
                            boundaryLatLngModels.add(BoundaryLatLngModel(key, latLngOfBoundaryArrayList))
                        }
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
            response.postValue(boundaryLatLngModels)
        }
        return response
    }

    @SuppressLint("StaticFieldLeak")
    fun getTotalPhoto(activity: Activity, uid: String?, year: String, month: String, date: String): LiveData<ArrayList<Model>> {
        val models = ArrayList<Model>()
        val response = MutableLiveData<ArrayList<Model>>()
        GlobalScope.launch {
            for (i in 1..3) {
                activity.runOnUiThread {
                    common.getDatabaseForApplication(activity).child("WastebinMonitor/ImagesData/$year/$month/$date/$i").orderByChild("user").equalTo(uid).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(dataSnapshot: DataSnapshot) {
                            if (dataSnapshot.value != null) {
                                var counts = 0
                                for (snapshot in dataSnapshot.children) {
                                    counts++
                                    models.add(Model(snapshot.child("time").value.toString(),
                                            snapshot.child("latLng").value.toString()))
                                    if (i == 3 && counts.toLong() == dataSnapshot.childrenCount) {
                                        val formatter = SimpleDateFormat("HH:mm")
                                        models.sortWith(Comparator { obj1: Model, obj2: Model ->
                                            var a: Date? = null
                                            var b: Date? = null
                                            try {
                                                a = formatter.parse(obj1.time)
                                                b = formatter.parse(obj2.time)
                                            } catch (e: Exception) {
                                            }
                                            a!!.compareTo(b)
                                        })
                                        response.setValue(models)
                                    }
                                }
                            } else {
                                response.setValue(models)
                            }
                        }

                        override fun onCancelled(databaseError: DatabaseError) {}
                    })
                }
            }
        }
        return response
    }

    @SuppressLint("StaticFieldLeak")
    fun checkDutyOff(activity: Activity?, uid: String?): LiveData<DataSnapshot> {
        val response = MutableLiveData<DataSnapshot>()
        GlobalScope.launch {
            common.getDatabaseForApplication(activity).child("FEAttendance").child(uid!!).child(common.year()).child(common.monthName()).child(common.date()).child("outDetails").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    response.value = dataSnapshot
                }

                override fun onCancelled(databaseError: DatabaseError) {}
            })
        }
        return response
    }

    @SuppressLint("StaticFieldLeak")
    fun dutyOff(activity: Activity?, uid: String, latLng: LatLng, address: String): LiveData<String> {
        val response = MutableLiveData<String>()
        GlobalScope.launch {
            if (address.isNotEmpty()) {
                common.getDatabaseForApplication(activity).child("FEAttendance/" + uid + "/" + common.year() + "/" + common.monthName() + "/" + common.date() + "/outDetails").addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        if (dataSnapshot.value != null) {
                            common.showAlertBox("Already duty off.", true, activity, false)
                        } else {
                            val map = HashMap<String, String>()
                            map["address"] = address
                            map["location"] = latLng.latitude.toString() + "," + latLng.longitude
                            map["time"] = common.currentTime()
                            map["status"] = "0"
                            common.getDatabaseForApplication(activity).child("FEAttendance/" + uid + "/" + common.year() + "/" + common.monthName() + "/" + common.date() + "/outDetails").setValue(map)
                                    .addOnCompleteListener { task2: Task<Void?>? -> response.setValue("success") }
                        }
                    }

                    override fun onCancelled(databaseError: DatabaseError) {}
                })
            } else {
                response.setValue("fail")
            }
        }
        return response
    }

    @SuppressLint("StaticFieldLeak")
    fun getLocationData(year: String, month: String, date: String, activity: Activity?, uid: String): LiveData<DataSnapshot> {
        val response = MutableLiveData<DataSnapshot>()
        GlobalScope.launch {
            common.getDatabaseForApplication(activity).child("LocationHistory/$uid/$year/$month/$date").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    response.setValue(dataSnapshot)
                }

                override fun onCancelled(databaseError: DatabaseError) {}
            })
        }
        return response
    }

    @SuppressLint("StaticFieldLeak")
    fun getHaltData(year: String, month: String, date: String, activity: Activity?, uid: String): LiveData<DataSnapshot> {
        val response = MutableLiveData<DataSnapshot>()
        GlobalScope.launch {
            common.getDatabaseForApplication(activity).child("HaltInfo/$uid/$year/$month/$date").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    response.setValue(dataSnapshot)
                }

                override fun onCancelled(databaseError: DatabaseError) {}
            })
        }
        return response
    }

    fun sendActivityEvents(message: String?,database:DatabaseReference,preferences: SharedPreferences) {
        GlobalScope.launch {
            database.child("FENavigationEventsTracking/Employee/" + common.year() + "/" + common.monthName() + "/" + common.date() + "/" + preferences.getString("uid", "") + "/" + SimpleDateFormat("HH:mm:ss:SSS").format(Date())).setValue(message)
            database.child("FENavigationEventsTracking/Date/" + preferences.getString("uid", "") + "/" + common.year() + "/" + common.monthName() + "/" + common.date() + "/" + SimpleDateFormat("HH:mm:ss:SSS").format(Date())).setValue(message)
        }
    }
}