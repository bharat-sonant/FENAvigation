package com.wevois.fenavigation.viewmodels;

import static android.app.Activity.RESULT_OK;
import static android.content.Context.MODE_PRIVATE;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.wevois.fenavigation.CommonMethods;
import com.wevois.fenavigation.views.DutyIn;
import com.wevois.fenavigation.views.HomeMapsActivity;
import com.wevois.fenavigation.views.LoginScreen;

import java.util.HashMap;
import java.util.Objects;

public class LoginViewModel extends ViewModel {
    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseAuth mAuth;
    Activity activity;
    SharedPreferences preferences;
    boolean isPass = true;
    CommonMethods common = CommonMethods.getInstance();

    public void init(LoginScreen loginScreen) {
        activity = loginScreen;
        preferences = activity.getSharedPreferences("FirebasePath", MODE_PRIVATE);
        mAuth = FirebaseAuth.getInstance();
        try {
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken("381118272786-govj6slvjf5uathafc3lm8fq9r79qtiq.apps.googleusercontent.com")
                    .requestEmail()
                    .build();

            mGoogleSignInClient = GoogleSignIn.getClient(activity, gso);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void googleSignInBtn() {
        if (isPass) {
            isPass = false;
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            activity.startActivityForResult(signInIntent, 100);
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential).addOnCompleteListener(activity, task -> {
            isPass = true;
            if (task.isSuccessful()) {
                FirebaseUser user = mAuth.getCurrentUser();
                common.getDatabaseForApplication(activity).child("WastebinMonitor/Users/" + user.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.getValue() == null) {
                            HashMap<String, Object> hashMap = new HashMap<>();
                            hashMap.put("email", user.getEmail());
                            hashMap.put("name", user.getDisplayName());
                            hashMap.put("date", common.date());
                            common.getDatabaseForApplication(activity).child("WastebinMonitor/Users/" + user.getUid() + "/").setValue(hashMap);
                            common.getDatabaseForApplication(activity).child("WastebinMonitor/FieldExecutive/" + user.getUid() + "/").setValue(hashMap);
                        }else {
                            common.getDatabaseForApplication(activity).child("WastebinMonitor/FieldExecutive/" + user.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot1) {
                                    if (snapshot1.getValue()==null) {
                                        HashMap<String, Object> hashMap = new HashMap<>();
                                        hashMap.put("email", user.getEmail());
                                        hashMap.put("name", user.getDisplayName());
                                        hashMap.put("date", common.date());
                                        common.getDatabaseForApplication(activity).child("WastebinMonitor/FieldExecutive/" + user.getUid() + "/").setValue(hashMap);
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {

                                }
                            });
                        }
                        updateUI(user);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {

                    }
                });
            } else {
                updateUI(null);
            }
        });
    }

    public void updateUI(FirebaseUser user) {
        if (user != null) {
            preferences.edit().putString("uid", user.getUid()).apply();
            preferences.edit().putString("name", Objects.requireNonNull(user).getDisplayName()).apply();
            preferences.edit().putString("email", user.getEmail()).apply();
            common.getDatabaseForApplication(activity).child("FEAttendance").child(user.getUid()).child(common.year()).child(common.monthName()).child(common.date()).child("inDetails").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    preferences.edit().putString("loggedIn", "1").apply();
                    if (snapshot.hasChild("time")) {
                        preferences.edit().putString("dutyIn", common.date()).apply();
                        activity.startActivity(new Intent(activity, HomeMapsActivity.class));
                    } else {
                        activity.startActivity(new Intent(activity, DutyIn.class));
                    }
                    common.closeDialog(activity);
                    activity.finish();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                }
            });
        }
    }

    public void resultActivity(int requestCode, int resultCode, Intent data) {
        if (requestCode == 100 && resultCode == RESULT_OK) {
            common.setProgressBar("Login", activity, activity);
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            isPass = true;
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                e.printStackTrace();
            }
        } else {
            isPass = true;
        }
    }
}
