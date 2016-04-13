package com.singlemalt.googleplay.auth.googleplayauth;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.Player;
import com.singlemalt.googleplay.auth.googleplayauth.runners.ServerAuthRunner;
import com.singlemalt.googleplay.auth.googleplayauth.tasks.AuthTask;
import com.singlemalt.googleplay.auth.googleplayauth.tasks.GetOAuthTokenTask;
import com.google.android.gms.common.ConnectionResult;

import java.util.concurrent.Executors;

/**
 * Created by singlemalt on 3/28/16.
 */
public class AuthServiceActivity extends Activity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener
{
    public static final int REQUEST_RESOLVE_ERROR = 1001;

    // callback classes
    private class AlertGooglePlayStatus implements Runnable {
        private AuthServiceActivity authServiceActivity;
        private int googlePlayServicesCheck;

        final DialogInterface.OnCancelListener listener = new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                AuthInstance.getInstance().onCancel(authServiceActivity);
            }
        };

        public AlertGooglePlayStatus(AuthServiceActivity authServiceActivity, int googlePlayServicesCheck) {
            this.authServiceActivity = authServiceActivity;
            this.googlePlayServicesCheck = googlePlayServicesCheck;
        }

        @Override
        public void run() {
            Dialog dialog = GoogleApiAvailability.getInstance()
                    .getErrorDialog(authServiceActivity, googlePlayServicesCheck, REQUEST_RESOLVE_ERROR, listener);

            dialog.show();
        }
    }

    // resolution tracker
    private boolean resolvingError = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(AuthInstance.TAG, "Starting auth");

        super.onCreate(savedInstanceState);
        new AuthTask(this).execute();
    }

    // implement callbacks
    @Override
    public void onConnected(Bundle bundle) {
        Log.d(AuthInstance.TAG, "onConnected");

        AuthInstance.getInstance().setAccountName(
                Games.getCurrentAccountName(AuthInstance.getInstance().getGoogleApiClient()));
        Player player = Games.Players.getCurrentPlayer(AuthInstance.getInstance().getGoogleApiClient());

        AuthInstance.getInstance().setPlayerId(player.getPlayerId());
        AuthInstance.getInstance().setPlayerName(player.getDisplayName());

        String scope = String.format("audience:server:client_id:%s", AuthInstance.getInstance().getClientId());
        Log.d(AuthInstance.TAG, "Scope: " + scope);

        new GetOAuthTokenTask(this, AuthInstance.getInstance().getAccountName(), scope).execute();

        AuthInstance.getInstance().setLoginStatus(AuthInstance.Status.Success);
        AuthInstance.getInstance().checkStatus();
        this.finish();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(AuthInstance.TAG, "onConnectionSuspended");

        new AuthTask(this).execute();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(AuthInstance.TAG, "onConnectionFailed");

        if(resolvingError) {
            Log.d(AuthInstance.TAG, "onConnectionFailed currently resolving");
            return;
        }

        AuthInstance.getInstance().setFailureError(connectionResult.toString());
        Log.e(AuthInstance.TAG, "connectionResult " + AuthInstance.getInstance().getFailureError());

        if(connectionResult.hasResolution()) {
            try {
                Log.d(AuthInstance.TAG, "onConnectionFailed resolving");
                resolvingError = true;
                connectionResult.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                Log.e(AuthInstance.TAG, "onConnectionFailed: ", e);

                AuthInstance.getInstance().getGoogleApiClient().connect();
            }
        } else {
            Log.d(AuthInstance.TAG, "onConnectionFailed no resolution");
            resolvingError = true;
            this.runOnUiThread(new AlertGooglePlayStatus(this, connectionResult.getErrorCode()));
            this.finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_RESOLVE_ERROR) {
            resolvingError = false;
            if(resultCode == RESULT_OK) {
                if (!AuthInstance.getInstance().getGoogleApiClient().isConnecting() &&
                        !AuthInstance.getInstance().getGoogleApiClient().isConnected()) {
                    AuthInstance.getInstance().getGoogleApiClient().connect();
                }
            } else {
                // player cancelled login
                AuthInstance.getInstance().setLoginStatus(AuthInstance.Status.Cancel);
                AuthInstance.getInstance().setOauthStatus(AuthInstance.Status.Cancel);

                Executors.newSingleThreadExecutor().execute(new ServerAuthRunner());
                AuthInstance.getInstance().checkStatus();
                this.finish();
            }
        }
    }
}
