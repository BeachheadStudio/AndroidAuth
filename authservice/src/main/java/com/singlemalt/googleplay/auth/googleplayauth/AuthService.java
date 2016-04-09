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
import com.google.gson.Gson;
import com.singlemalt.googleplay.auth.googleplayauth.tasks.GetOAuthTokenTask;
import com.unity3d.player.UnityPlayer;
import com.google.android.gms.common.ConnectionResult;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private class ServerAuthRunner implements Runnable {
        public class RequestPojo {
            public String playerId;
            public String serverPlayerId;
            public String network;
            public String playerName;
            public String token;

            public RequestPojo(String playerId, String serverPlayerId, String network, String playerName, String token) {
                this.playerId = playerId;
                this.serverPlayerId = serverPlayerId;
                this.network = network;
                this.playerName = playerName;
                this.token = token;
            }
        }

        private class ServerPlayer {
            public String realPlayerID;
            public String playerName;
            public boolean isAnonymous;
        }

        public ServerAuthRunner() { }

        @Override
        public void run() {
            Log.d(TAG, "ServerAuthRunner starting...");
            HttpURLConnection conn = null;

            try {
                RequestPojo data = new RequestPojo(getPlayerId(), getServerPlayerId(), "GOOGLE", getPlayerName(), getOauthToken());
                String postString = new Gson().toJson(data);
                byte[] postData = postString.getBytes(Charset.defaultCharset());
                int postDataLength = postData.length;

                URL url = new URL(serverUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(true);
                conn.setInstanceFollowRedirects(false);
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(20000);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Content-Length", Integer.toString(postDataLength));
                conn.setUseCaches(false);

                DataOutputStream wr = new DataOutputStream( conn.getOutputStream());
                wr.write( postData );
                wr.close();

                if(conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    ServerPlayer player = new Gson().fromJson(reader, ServerPlayer.class);
                    serverPlayerId = player.realPlayerID;
                    playerName = player.playerName;
                    anonymous = player.isAnonymous;

                    Map<String, List<String>> headerFields = conn.getHeaderFields();
                    List<String> cookiesHeader = headerFields.get("Set-Cookie");

                    if(cookiesHeader.size() > 0) {
                        Log.d(TAG, "Getting session cookie");
                        for(String cookie : cookiesHeader) {
                            List<HttpCookie> httpCookies = HttpCookie.parse(cookie);

                            AuthService.getInstance().SetSessionToken(httpCookies.get(0).getValue());
                            Log.d(TAG, "Cookie found: "+AuthService.getInstance().getSessionToken());
                        }
                    }

                    serverAuthStatus = Status.Success;
                } else {
                    Log.e(TAG, "Server sent back error code: "+conn.getResponseCode());
                    failureError = "Server auth failed";
                    serverAuthStatus = Status.Failure;
                }
            } catch (Exception e) {
                Log.e(TAG, "Couldn't make a HTTP request", e);
                failureError = e.getLocalizedMessage();
                serverAuthStatus = Status.Failure;
            } finally {
                if(conn != null) {
                    conn.disconnect();
                }
            }

            checkStatus();
        }
    }

    // connection tracker
    public static final int REQUEST_CODE_PICK_ACCOUNT = 1000;
    public static final int REQUEST_RESOLVE_ERROR = 1001;
    public static final int REQUEST_ACHIEVEMENTS = 1002;
    private boolean resolvingError = false;

    // Google API client
    private GoogleApiClient googleApiClient;

    // enums are a pain in the JNI, so using a string
    private Status loginStatus;
    private Status oauthStatus;
    private Status serverAuthStatus;

    // player fields
    private Player player;
    private String playerId = null;
    private String playerName;
    private String oauthToken;
    private String failureError;
    private String serverPlayerId;
    private String sessionToken = "";
    private String clientId;
    private String serverUrl;
    private String scope;
    private String accountName;
    private boolean anonymous = true;

    private AuthService() {
        loginStatus = Status.Working;
        oauthStatus = Status.Working;
        serverAuthStatus = Status.Working;
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

        scope = String.format("audience:server:client_id:%s", clientId);
        new GetOAuthTokenTask(this, UnityPlayer.currentActivity, accountName, scope).execute();

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
        Log.e(TAG, "connectionResult " + failureError);

        if(connectionResult.hasResolution()) {
            try {
                Log.d(TAG, "onConnectionFailed resolving");
                resolvingError = true;
                connectionResult.startResolutionForResult(UnityPlayer.currentActivity, REQUEST_RESOLVE_ERROR);

                googleApiClient.connect();
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

    public void init(String clientId, String serverUrl, String playerId) {
        Log.d(TAG, "Starting");

        this.clientId = clientId;
        this.serverUrl = serverUrl;
        this.serverPlayerId = playerId;

        Executors.newSingleThreadExecutor().execute(new AuthRunner(this, true));
    }

    public void onPause() {
        Log.d(TAG, "onPause");

    }

    public void onResume() {
        Log.d(TAG, "onResume");

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
                UnityPlayer.UnitySendMessage("AuthGameObject", "PlayerChange", "true");
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

            Executors.newSingleThreadExecutor().execute(new ServerAuthRunner());
        } else {
            oauthStatus = Status.Failure;
        }

        checkStatus();
    }

    public void SetSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public String getServerPlayerId() {
        return serverPlayerId;
    }

    public String getFailureError() {
        return failureError;
    }

    public boolean IsAnonymous() {
        return anonymous;
    }

    public String getAuthParams() {
        scope = String.format("audience:server:client_id:%s", clientId);
        GetOAuthTokenTask task = new GetOAuthTokenTask(this, UnityPlayer.currentActivity, accountName, scope);
        try {
            oauthToken = task.fetchToken();
        } catch (IOException e) {
            Log.e(TAG, "Failed getting new oauth token");
            return null;
        }

        Map<String, String> authParams = new HashMap<>();
        authParams.put("playerId", playerId);
        authParams.put("serverPlayerId", serverPlayerId);
        authParams.put("network", "GOOGLE");
        authParams.put("playerName", playerName);
        authParams.put("token", oauthToken);

        return new Gson().toJson(authParams);
    }

    public void awardAchievement(String achievementId) {
        Games.Achievements.unlock(googleApiClient, achievementId);

        UnityPlayer.currentActivity.startActivityForResult(
                Games.Achievements.getAchievementsIntent(googleApiClient), REQUEST_ACHIEVEMENTS);
    }

    private void checkStatus() {
        if (loginStatus.equals(Status.Working) || oauthStatus.equals(Status.Working)
                || serverAuthStatus.equals(Status.Working)) {
            Log.d(TAG, "not ready to update unity");
        } else if (loginStatus.equals(Status.Success) && oauthStatus.equals(Status.Success)
                && serverAuthStatus.equals(Status.Success)) {
            anonymous = false;
            Log.d(TAG, "login success");
            UnityPlayer.UnitySendMessage("AuthGameObject", "LoginResult", Status.Success.toString());
        } else if (loginStatus.equals(Status.Cancel)) {
            anonymous = true;
            player = null;

            Log.d(TAG, "login cancelled");
            UnityPlayer.UnitySendMessage("AuthGameObject", "LoginResult", Status.Cancel.toString());
        } else if (loginStatus.equals(Status.Failure) || oauthStatus.equals(Status.Failure)
                || serverAuthStatus.equals(Status.Failure)) {
            anonymous = true;
            player = null;

            Log.e(TAG, "login failed");
            UnityPlayer.UnitySendMessage("AuthGameObject", "LoginResult", Status.Failure.toString());
        }
    }
}
