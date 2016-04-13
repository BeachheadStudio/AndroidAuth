package com.singlemalt.googleplay.auth.googleplayauth.runners;

import android.util.Log;

import com.google.gson.Gson;
import com.singlemalt.googleplay.auth.googleplayauth.AuthInstance;
import com.singlemalt.googleplay.auth.googleplayauth.AuthServiceActivity;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

/**
 * Created by singlemalt on 4/12/2016.
 */
public class ServerAuthRunner implements Runnable {
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
        Log.d(AuthInstance.TAG, "ServerAuthRunner starting...");
        HttpURLConnection conn = null;

        try {
            RequestPojo data = new RequestPojo(AuthInstance.getInstance().getPlayerId(),
                    AuthInstance.getInstance().getServerPlayerId(), "GOOGLE",
                    AuthInstance.getInstance().getPlayerName(),
                    AuthInstance.getInstance().getOauthToken());
            String postString = new Gson().toJson(data);
            byte[] postData = postString.getBytes(Charset.defaultCharset());
            int postDataLength = postData.length;

            URL url = new URL(AuthInstance.getInstance().getServerUrl());
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
                AuthInstance.getInstance().setServerPlayerId(player.realPlayerID);
                AuthInstance.getInstance().setPlayerName(player.playerName);
                AuthInstance.getInstance().setAnonymous(player.isAnonymous);

                Map<String, List<String>> headerFields = conn.getHeaderFields();
                List<String> cookiesHeader = headerFields.get("Set-Cookie");

                if(cookiesHeader.size() > 0) {
                    Log.d(AuthInstance.TAG, "Getting session cookie");
                    for(String cookie : cookiesHeader) {
                        List<HttpCookie> httpCookies = HttpCookie.parse(cookie);

                        AuthInstance.getInstance().setSessionToken(httpCookies.get(0).getValue());
                        Log.d(AuthInstance.TAG, "Cookie found: "+ AuthInstance.getInstance().getSessionToken());
                    }
                }

                AuthInstance.getInstance().setServerAuthStatus(AuthInstance.Status.Success);
            } else {
                Log.e(AuthInstance.TAG, "Server sent back error code: " + conn.getResponseCode());
                AuthInstance.getInstance().setFailureError("Server auth failed");
                AuthInstance.getInstance().setServerAuthStatus(AuthInstance.Status.Failure);
            }
        } catch (Exception e) {
            Log.e(AuthInstance.TAG, "Couldn't make a HTTP request", e);
            AuthInstance.getInstance().setFailureError(e.getLocalizedMessage());
            AuthInstance.getInstance().setServerAuthStatus(AuthInstance.Status.Failure);
        } finally {
            if(conn != null) {
                conn.disconnect();
            }
        }

        AuthInstance.getInstance().checkStatus();
    }
}
