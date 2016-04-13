package com.singlemalt.googleplay.auth.googleplayauth;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.games.Games;
import com.google.gson.Gson;
import com.singlemalt.googleplay.auth.googleplayauth.runners.ServerAuthRunner;
import com.singlemalt.googleplay.auth.googleplayauth.tasks.GetOAuthTokenTask;
import com.unity3d.player.UnityPlayer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Created by singlemalt on 4/12/2016.
 */
public class AuthInstance {
    public static final String TAG = AuthInstance.class.getSimpleName();
    public static final int REQUEST_ACHIEVEMENTS = 1002;

    private static AuthInstance ourInstance = new AuthInstance();

    private String playerId = null;
    private String playerName;
    private String failureError;
    private String serverPlayerId;
    private String sessionToken = "";
    private String scope;
    private String accountName;
    private String oauthToken;
    private String clientId;
    private String serverUrl;
    private boolean anonymous = true;

    private Status loginStatus;
    private Status oauthStatus;
    private Status serverAuthStatus;

    // Google API client
    private GoogleApiClient googleApiClient;

    public enum Status {
        Working,
        Success,
        Failure,
        Cancel
    }

    public static AuthInstance getInstance() {
        return ourInstance;
    }

    private AuthInstance() {

    }

    public void init(String clientId, String serverUrl, String playerId) {
        Log.d(TAG, "init instance");

        this.clientId = clientId;
        this.serverUrl = serverUrl;
        this.serverPlayerId = playerId;
        this.loginStatus = Status.Working;
        this.oauthStatus = Status.Working;
        this.serverAuthStatus = Status.Working;

        UnityPlayer.currentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "start activity");
                Intent intent = new Intent(UnityPlayer.currentActivity.getApplicationContext(),
                        AuthServiceActivity.class);
                UnityPlayer.currentActivity.startActivity(intent);
            }
        });
    }

    public void onPause() {
        Log.d(TAG, "onPause");
    }

    public void onResume() {
        Log.d(TAG, "onResume");
    }

    public void onCancel(final Activity activity) {
        Log.d(TAG, "onCancel");
        loginStatus = Status.Cancel;
        oauthStatus = Status.Cancel;

        Executors.newSingleThreadExecutor().execute(new ServerAuthRunner());
        checkStatus();
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.startActivity(UnityPlayer.currentActivity.getIntent());
            }
        });
        activity.finish();
    }

    public String getAuthParams() {
        scope = String.format("audience:server:client_id:%s", clientId);

        GetOAuthTokenTask task = new GetOAuthTokenTask(UnityPlayer.currentActivity, accountName, scope);
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

    public void startOauth() {
        Log.d(AuthInstance.TAG, "startOauth");

        String scope = String.format("audience:server:client_id:%s",  clientId);
        Log.d(AuthInstance.TAG, "Scope: " + scope);

        new GetOAuthTokenTask(UnityPlayer.currentActivity, accountName, scope)
                .execute();
    }

    public void checkStatus() {
        if (loginStatus.equals(Status.Working) || oauthStatus.equals(Status.Working)
                || serverAuthStatus.equals(Status.Working)) {
            Log.d(TAG, "not ready to update unity");
        } else if (loginStatus.equals(Status.Success) && oauthStatus.equals(Status.Success)
                && serverAuthStatus.equals(Status.Success)) {
            anonymous = false;
            Log.d(TAG, "login success");
            UnityPlayer.UnitySendMessage("AuthGameObject", "LoginResult", Status.Success.toString());
        } else if (serverAuthStatus.equals(Status.Success) && (loginStatus.equals(Status.Cancel) || oauthStatus.equals(Status.Cancel))) {
            anonymous = true;

            Log.d(TAG, "login cancelled");
            UnityPlayer.UnitySendMessage("AuthGameObject", "LoginResult", Status.Cancel.toString());
        } else if (serverAuthStatus.equals(Status.Failure)) {
            anonymous = true;

            Log.e(TAG, "login failed");
            UnityPlayer.UnitySendMessage("AuthGameObject", "LoginResult", Status.Failure.toString());
        }
    }

    public void awardAchievement(String achievementId) {
        Games.Achievements.unlock(googleApiClient, achievementId);

        UnityPlayer.currentActivity.startActivityForResult(
                Games.Achievements.getAchievementsIntent(googleApiClient), REQUEST_ACHIEVEMENTS);
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

        Executors.newSingleThreadExecutor().execute(new ServerAuthRunner());
        checkStatus();
    }

    public String getFailureError() {
        return failureError;
    }

    public void setFailureError(String failureError) {
        this.failureError = failureError;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        if(this.playerId != null) {
            if(!this.playerId.equals(playerId)) {
                Log.d(TAG, "New playerId found: " +playerId);
                UnityPlayer.UnitySendMessage("AuthGameObject", "PlayerChange", "true");
            }
        }
        this.playerId = playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public String getServerPlayerId() {
        return serverPlayerId;
    }

    public void setServerPlayerId(String serverPlayerId) {
        this.serverPlayerId = serverPlayerId;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public boolean isAnonymous() {
        return anonymous;
    }

    public void setAnonymous(boolean anonymous) {
        this.anonymous = anonymous;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getScope() {
        return scope;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setLoginStatus(Status loginStatus) {
        this.loginStatus = loginStatus;
    }

    public void setOauthStatus(Status oauthStatus) {
        this.oauthStatus = oauthStatus;
    }

    public void setServerAuthStatus(Status serverAuthStatus) {
        this.serverAuthStatus = serverAuthStatus;
    }

    public GoogleApiClient getGoogleApiClient() {
        return googleApiClient;
    }

    public void setGoogleApiClient(GoogleApiClient googleApiClient) {
        this.googleApiClient = googleApiClient;
    }
}
