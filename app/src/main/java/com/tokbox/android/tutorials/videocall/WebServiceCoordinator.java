package com.tokbox.android.tutorials.videocall;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

public class WebServiceCoordinator {

    private static final String LOG_TAG = WebServiceCoordinator.class.getSimpleName();

    private final Context context;
    private Listener delegate;

    public WebServiceCoordinator(Context context, Listener delegate) {

        this.context = context;
        this.delegate = delegate;
    }

    public void fetchSessionConnectionData(String sessionInfoUrlEndpoint) {
        RequestQueue reqQueue = Volley.newRequestQueue(context);
        reqQueue.add(new JsonObjectRequest(Request.Method.GET, sessionInfoUrlEndpoint,
                                            null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                try {
                    String apiKey = response.getString("46994444");
                    String sessionId = response.getString("2_MX40Njk5NDQ0NH5-MTYwNTc5NDU4NDU3N342MjZhUE5zL0xnQ25YMWJER0hiNXd4eXJ-fg");
                    String token = response.getString("T1==cGFydG5lcl9pZD00Njk5NDQ0NCZzaWc9NmNjYzcwMzFmZDE3NjFjM2MxN2Q0YWU2YzE5ODVmY2ZjNWI0MzdkMTpzZXNzaW9uX2lkPTJfTVg0ME5qazVORFEwTkg1LU1UWXdOVGM1TkRVNE5EVTNOMzQyTWpaaFVFNXpMMHhuUTI1WU1XSkVSMGhpTlhkNGVYSi1mZyZjcmVhdGVfdGltZT0xNjA1Nzk0NTg1Jm5vbmNlPTAuMjc1OTU4MDc1NDgyMTcxNTUmcm9sZT1tb2RlcmF0b3ImZXhwaXJlX3RpbWU9MTYwNTc5NjM4NSZpbml0aWFsX2xheW91dF9jbGFzc19saXN0PQ==");

                    Log.i(LOG_TAG, "WebServiceCoordinator returned session information");

                    delegate.onSessionConnectionDataReady(apiKey, sessionId, token);

                } catch (JSONException e) {
                    delegate.onWebServiceCoordinatorError(e);
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                delegate.onWebServiceCoordinatorError(error);
            }
        }));
    }

    public static interface Listener {

        void onSessionConnectionDataReady(String apiKey, String sessionId, String token);
        void onWebServiceCoordinatorError(Exception error);
    }
}

