package com.singlemalt.googleplay.auth.googleplayauth.tasks;

import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.games.Games;
import com.singlemalt.googleplay.auth.googleplayauth.AuthInstance;
import com.singlemalt.googleplay.auth.googleplayauth.AuthServiceActivity;

import java.io.IOException;

/**
 * Created by singlemalt on 4/12/2016.
 */
public class AuthTask extends AsyncTask<String, Void, String> {
    private AuthServiceActivity authServiceActivity;

    public AuthTask(AuthServiceActivity authServiceActivity) {
        this.authServiceActivity = authServiceActivity;
    }

    @Override
    protected String doInBackground(String... params) {
        if(AuthInstance.getInstance().getGoogleApiClient() == null) {
            Log.d(AuthInstance.TAG, "AuthRunner starting...");
            AuthInstance.getInstance().setGoogleApiClient(
                    new GoogleApiClient.Builder(authServiceActivity.getApplicationContext())
                    .addApi(Games.API).addScope(Games.SCOPE_GAMES)
                    .addConnectionCallbacks(authServiceActivity)
                    .addOnConnectionFailedListener(authServiceActivity)
                    .build());
        }

        if(AuthInstance.getInstance().getGoogleApiClient().isConnected()) {
            Log.d(AuthInstance.TAG, "googleApiClient isConnected, reconnecting");
            AuthInstance.getInstance().getGoogleApiClient().disconnect();

            try {
                GoogleAuthUtil.clearToken(authServiceActivity.getApplicationContext(),
                        AuthInstance.getInstance().getOauthToken());
            } catch (GoogleAuthException | IOException e) {
                Log.e(AuthInstance.TAG, "Could not clear token: ", e);
            }
            Log.d(AuthInstance.TAG, "AuthRunner re-connecting...");
            AuthInstance.getInstance().getGoogleApiClient().connect();
        } else if(AuthInstance.getInstance().getGoogleApiClient().isConnecting()) {
            Log.d(AuthInstance.TAG, "googleApiClient isConnecting, waiting...");
        } else {
            Log.d(AuthInstance.TAG, "AuthRunner connecting...");
            AuthInstance.getInstance().getGoogleApiClient().connect();
        }

        return null;
    }
}
