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
import com.singlemalt.googleplay.auth.googleplayauth.AuthInstance;
import com.singlemalt.googleplay.auth.googleplayauth.AuthServiceActivity;

import java.io.IOException;

/**
 * Created by singlemalt on 3/29/16.
 */
public class GetOAuthTokenTask extends AsyncTask<String, Void, String> {
    private Activity activity;
    private String scope;
    private String email;

    public GetOAuthTokenTask(Activity activity, String name, String scope) {
        this.scope = scope;
        this.email = name;
        this.activity = activity;
    }

    final DialogInterface.OnCancelListener listener = new DialogInterface.OnCancelListener() {
        @Override
        public void onCancel(DialogInterface dialogInterface) {
            Log.w(AuthInstance.TAG, "OnCancelListener cancelled");
            AuthInstance.getInstance().onCancel(activity);
        }
    };

    /**
     * Executes the asynchronous job. This runs when you call execute()
     * on the AsyncTask instance.
     */
    @Override
    protected String doInBackground(String... params) {
        try {
            Log.d(AuthInstance.TAG, "doInBackground getting token");
            String token = fetchToken();
            if (token != null) {
                Log.d(AuthInstance.TAG, "doInBackground setting token");
                AuthInstance.getInstance().setOauthToken(token);
            }
        } catch (IOException e) {
            Log.e(AuthInstance.TAG, "doInBackground exception", e);
            AuthInstance.getInstance().setOauthToken(null);
        }
        return null;
    }

    /**
     * Gets an authentication token from Google and handles any
     * GoogleAuthException that may occur.
     */
    public String fetchToken() throws IOException {
        try {
            Log.d(AuthInstance.TAG, "fetchToken getting token");

            Account account = new Account(email, GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE);
            return GoogleAuthUtil.getToken(activity.getApplicationContext(), account, scope);

        } catch (final UserRecoverableAuthException e) {
            Log.d(AuthInstance.TAG, "fetchToken exception", e);

            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (e instanceof GooglePlayServicesAvailabilityException) {
                        int statusCode = ((GooglePlayServicesAvailabilityException) e)
                                .getConnectionStatusCode();

                        Dialog dialog = GoogleApiAvailability.getInstance()
                                .getErrorDialog(activity, statusCode, AuthServiceActivity.REQUEST_RESOLVE_ERROR, listener);

                        dialog.show();
                    } else {
                        Intent intent = e.getIntent();
                        activity.startActivityForResult(intent, AuthServiceActivity.REQUEST_RESOLVE_ERROR);
                    }
                }
            });
        } catch (GoogleAuthException fatalException) {
            Log.e(AuthInstance.TAG, "fatalException thrown", fatalException);
            throw new RuntimeException();
        }
        return null;
    }
}