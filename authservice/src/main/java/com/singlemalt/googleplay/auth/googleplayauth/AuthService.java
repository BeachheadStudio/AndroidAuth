package com.singlemalt.googleplay.auth.googleplayauth;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.IntentSender;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.Player;
import com.singlemalt.googleplay.auth.googleplayauth.tasks.GetOAuthTokenTask;
import com.unity3d.player.UnityPlayer;
import com.google.android.gms.common.ConnectionResult;

import java.util.concurrent.Executors;

/**
 * Created by kmiller on 3/28/16.
 */
public class AuthService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = AuthService.class.getSimpleName();

    public enum Status {
        Working,
        Success,
        Failure,
        Cancel
    }

    // singleton methods
    private static AuthService instance;

    public static AuthService getInstance() {
        if(instance == null) {
            instance = new AuthService();
        }

        return instance;
    }

    // callback classes
    private class AlertGooglePlayStatus implements Runnable {
        private AuthService authService;
        private Activity activity;
        private int googlePlayServicesCheck;

        final DialogInterface.OnCancelListener listener = new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                authService.onCancel(activity);
            }
        };

        public AlertGooglePlayStatus(AuthService authService, Activity activity, int googlePlayServicesCheck) {
            this.authService = authService;
            this.activity = activity;
            this.googlePlayServicesCheck = googlePlayServicesCheck;
        }

        @Override
        public void run() {
            Dialog dialog = GoogleApiAvailability.getInstance()
                    .getErrorDialog(activity, googlePlayServicesCheck, REQUEST_RESOLVE_ERROR, listener);

            dialog.show();
        }
    }

    private class AuthRunner implements Runnable {
        private AuthService authService;
        private boolean connectNow;

        public AuthRunner(AuthService authService, boolean connectNow) {
            this.authService = authService;
            this.connectNow = connectNow;
        }

        @Override
        public void run() {
            if(googleApiClient == null) {
                Log.d(TAG, "AuthRunner starting...");
                googleApiClient = new GoogleApiClient.Builder(UnityPlayer.currentActivity.getApplicationContext())
                        .addApi(Games.API).addScope(Games.SCOPE_GAMES)
                        .addConnectionCallbacks(authService)
                        .addOnConnectionFailedListener(authService)
                        .build();
            }

            if(connectNow) {
                Log.d(TAG, "AuthRunner connecting...");
                googleApiClient.connect();
            }
        }
    }

    // connection tracker
    public static final int REQUEST_CODE_PICK_ACCOUNT = 1000;
    public static final int REQUEST_RESOLVE_ERROR = 1001;
    private boolean resolvingError = false;

    // Google API client
    private GoogleApiClient googleApiClient;

    // enums are a pain in the JNI, so using a string
    private Status loginStatus;
    private Status oauthStatus;

    // player fields
    private Player player;
    private String playerId = null;
    private String accountName;
    private String playerName;
    private String oauthToken;
    private String failureError;
    private boolean anonymous = true;

    private AuthService() {
        loginStatus = Status.Working;
        oauthStatus = Status.Working;
    }

    // implement callbacks
    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "onConnected");

        accountName = Games.getCurrentAccountName(googleApiClient);
        player = Games.Players.getCurrentPlayer(googleApiClient);

        setPlayerId(player.getPlayerId());
        anonymous = false;
        playerName = player.getDisplayName();

        new GetOAuthTokenTask(this, UnityPlayer.currentActivity, accountName, GetOAuthTokenTask.SCOPE)
                .execute();

        loginStatus = Status.Success;
        checkStatus();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended");

        Executors.newSingleThreadExecutor().execute(new AuthRunner(this, true));
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed");

        if(resolvingError) {
            return;
        }

        failureError = connectionResult.toString();

        if(connectionResult.hasResolution()) {
            try {
                Log.d(TAG, "onConnectionFailed resolving");
                resolvingError = true;
                connectionResult.startResolutionForResult(UnityPlayer.currentActivity, REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                Log.e(TAG, "onConnectionFailed: ", e);
                googleApiClient.connect();
            }
        } else {
            Log.d(TAG, "onConnectionFailed no resolution");
            resolvingError = true;
            UnityPlayer.currentActivity.runOnUiThread(
                    new AlertGooglePlayStatus(this, UnityPlayer.currentActivity, connectionResult.getErrorCode()));
        }
    }

    public void init() {
        Log.d(TAG, "Starting");

        Executors.newSingleThreadExecutor().execute(new AuthRunner(this, true));
    }

    public void onPause() {
        Log.d(TAG, "onPause");

        if(googleApiClient != null) {
//            googleApiClient.disconnect();
        }
    }

    public void onResume() {
        Log.d(TAG, "onResume");

        if(googleApiClient == null) {
//            Executors.newSingleThreadExecutor().execute(new AuthRunner(this, true));
        }
    }

    public void onCancel(Activity activity) {
        Log.d(TAG, "onCancel");
        anonymous = true;
        loginStatus = Status.Cancel;
        oauthStatus = Status.Cancel;
        checkStatus();
        activity.finish();
    }

    public String getPlayerName() {
        if(anonymous) {
            return "";
        } else {
            return playerName;
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

    public void setOauthToken(String token) {
        oauthToken = token;
        if(oauthToken != null && !oauthToken.isEmpty() && !oauthToken.equals("null")) {
            oauthStatus = Status.Success;
        } else {
            oauthStatus = Status.Failure;
        }

        checkStatus();
    }

    public String getFailureError() {
        return failureError;
    }

    public boolean isAnonymous() {
        return anonymous;
    }

    private void checkStatus() {
        Log.d(TAG, String.format("checkStatus: loginStatus %s", loginStatus));

        if (loginStatus.equals(Status.Working) || oauthStatus.equals(Status.Working)) {
            Log.d(TAG, "not ready to update unity");
        } else if (loginStatus.equals(Status.Success) && oauthStatus.equals(Status.Success)) {
            anonymous = false;
            Log.d(TAG, "login success");

            Log.d(TAG, String.format("accountName: %s playerName: %s playerId: %s oauthToken %s",
                    accountName, playerName, playerId, oauthToken));

            UnityPlayer.UnitySendMessage("Main Camera", "LoginResult", Status.Success.toString());
        } else if (loginStatus.equals(Status.Cancel)) {
            anonymous = true;
            player = null;

            Log.d(TAG, "login cancelled");
            UnityPlayer.UnitySendMessage("Main Camera", "LoginResult", Status.Cancel.toString());
        } else if (loginStatus.equals(Status.Failure) || oauthStatus.equals(Status.Failure)) {
            anonymous = true;
            player = null;

            Log.e(TAG, "login failed");
            UnityPlayer.UnitySendMessage("Main Camera", "LoginResult", Status.Failure.toString());
        }
    }
}
