package com.singlemalt.googleplay.auth.googleplayauth.tasks;

import android.accounts.Account;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.GooglePlayServicesAvailabilityException;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.singlemalt.googleplay.auth.googleplayauth.AuthService;

import java.io.IOException;

/**
 * Created by kmiller on 3/29/16.
 */
public class GetOAuthTokenTask extends AsyncTask<String, Void, String> {
    private static final String TAG = GetOAuthTokenTask.class.getSimpleName();
    public static final String SCOPE = "oauth2:https://www.googleapis.com/auth/userinfo.profile";

    private AuthService authService;
    private Activity activity;
    private String scope;
    private String email;

    public GetOAuthTokenTask(AuthService authService, Activity activity, String name, String scope) {
        this.activity = activity;
        this.scope = scope;
        this.email = name;
        this.authService = authService;
    }

    final DialogInterface.OnCancelListener listener = new DialogInterface.OnCancelListener() {
        @Override
        public void onCancel(DialogInterface dialogInterface) {
            Log.w(TAG, "OnCancelListener cancelled");
            authService.onCancel(activity);
        }
    };

    /**
     * Executes the asynchronous job. This runs when you call execute()
     * on the AsyncTask instance.
     */
    @Override
    protected String doInBackground(String... params) {
        try {
            Log.d(TAG, "doInBackground getting token");
            String token = fetchToken();
            if (token != null) {
                Log.d(TAG, "doInBackground setting token");
                authService.setOauthToken(token);
            }
        } catch (IOException e) {
            Log.e(TAG, "doInBackground exception", e);
            authService.setOauthToken(null);
        }
        return null;
    }

    /**
     * Gets an authentication token from Google and handles any
     * GoogleAuthException that may occur.
     */
    protected String fetchToken() throws IOException {
        try {
            Log.d(TAG, "fetchToken getting token");
            return GoogleAuthUtil.getToken(activity.getApplicationContext(),
                    new Account(email, "com.google"), scope);
        } catch (final UserRecoverableAuthException e) {
            Log.d(TAG, "fetchToken exception", e);

            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (e instanceof GooglePlayServicesAvailabilityException) {
                        int statusCode = ((GooglePlayServicesAvailabilityException)e)
                                .getConnectionStatusCode();

                        Dialog dialog = GoogleApiAvailability.getInstance()
                                .getErrorDialog(activity, statusCode, AuthService.REQUEST_RESOLVE_ERROR, listener);

                        dialog.show();
                    } else {
                        Intent intent = e.getIntent();
                        activity.startActivityForResult(intent, AuthService.REQUEST_RESOLVE_ERROR);
                    }
                }
            });
        } catch (GoogleAuthException fatalException) {
            Log.e(TAG, "fatalException thrown", fatalException);
            throw new RuntimeException();
        }
        return null;
    }
}