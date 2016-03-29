package com.singlemalt.googleplay.auth.googleplayauth;

import android.util.Log;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.games.Player;
import com.unity3d.player.UnityPlayer;
import com.google.android.gms.common.ConnectionResult;

/**
 * Created by kmiller on 3/28/16.
 */
public class AuthService {
    private static final String TAG = AuthService.class.getSimpleName();

    public static final String[] STATUS_VALUES = new String[]
            { "Working", "Success", "Failure", "Cancel"};

    // singleton methods
    private static AuthService instance;

    public static AuthService getInstance() {
        if(instance == null) {
            instance = new AuthService();
        }

        return instance;
    }

    // enums are a pain in the JNI, so using a string
    private String loginStatus;

    private Player player;
    private String playerId = null;
    private String oauthToken = "";
    private String failureError;
    private boolean anonymous = true;

    private AuthService() { }

    public void init() {
        Log.d(TAG, "Starting");

        ConnectionResult result = new ConnectionResult(
                GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(UnityPlayer.currentActivity.getApplicationContext())
        );

        Log.d(TAG, "Connection result: "+result.toString());
    }

    public void onPause() {
        Log.d(TAG, "onPause");

    }

    public void onResume() {
        Log.d(TAG, "onResume");
    }

    public String getPlayerName() {
        if(anonymous) {
            return "";
        } else {
            return "";// player.getAlias();
        }
    }

    public void setPlayerId(String playerId) {
        if(this.playerId != null) {
            if(!this.playerId.equals(playerId)) {
                this.playerId = playerId;
                UnityPlayer.UnitySendMessage("Main Camera", "PlayerChange", "true");
            }
        } else {
            this.playerId = playerId;
        }
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getOauthToken() {
        return oauthToken;
    }

    public String getFailureError() {
        return failureError;
    }

    public boolean isAnonymous() {
        return anonymous;
    }

    private void checkStatus() {
        Log.d(TAG, String.format("checkStatus: loginStatus %s", loginStatus));


        if(loginStatus.equals(STATUS_VALUES[0])) {
            Log.d(TAG, "not ready to update unity");
        } else if(loginStatus.equals(STATUS_VALUES[1])) {
            anonymous = false;

            Log.d(TAG, "login success");
            UnityPlayer.UnitySendMessage("Main Camera", "LoginResult", STATUS_VALUES[1]);
        } else if(loginStatus.equals(STATUS_VALUES[3])) {
            anonymous = true;
            player = null;

            Log.d(TAG, "login cancelled");
            UnityPlayer.UnitySendMessage("Main Camera", "LoginResult", STATUS_VALUES[3]);
        } else if(loginStatus.equals(STATUS_VALUES[2])) {
            anonymous = true;
            player = null;

            Log.e(TAG, "login failed");
            UnityPlayer.UnitySendMessage("Main Camera", "LoginResult", STATUS_VALUES[2]);
        }
    }
}
