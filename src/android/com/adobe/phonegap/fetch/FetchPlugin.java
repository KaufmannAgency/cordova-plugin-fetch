package com.adobe.phonegap.fetch;

import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;

import com.squareup.okhttp.Callback;
import com.squareup.okhttp.Headers;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.internal.tls.OkHostnameVerifier;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;



public class FetchPlugin extends CordovaPlugin {

    public static final String LOG_TAG = "FetchPlugin";
    private static CallbackContext callbackContext;

    private OkHttpClient mClient = null;
    public static final MediaType MEDIA_TYPE_MARKDOWN = MediaType.parse("application/x-www-form-urlencoded; charset=utf-8");

    public static final int MAX_ALLOWED_COOKIES = 5;

    static {
        Log.i(LOG_TAG, "Loading plugin fetch.");
    }

    public FetchPlugin() {
        super();
        Log.i(LOG_TAG, "Initializing plugin fetch.");
        // try {
        //     Log.i(LOG_TAG, "Initializing HTTP Client.");
        //     mClient = new OkHttpClient();
        //     Log.i(LOG_TAG, "HTTP Client initialized.");
        // } catch (Throwable e) {
        //     Log.w(LOG_TAG, "Exception when initializing HTTP Client:\n" + Log.getStackTraceString(e));
        //     throw t;
        // }
    }

    @Override
    public boolean execute(final String action, final JSONArray data, final CallbackContext callbackContext) {
        Log.i(LOG_TAG, "Invoking execute.");

        if(mClient == null) {
            try {
                Log.i(LOG_TAG, "Initializing HTTP Client.");
                mClient = new OkHttpClient();
                Log.i(LOG_TAG, "HTTP Client initialized.");
            } catch (Throwable e) {
                Log.w(LOG_TAG, "Exception when initializing HTTP Client:\n" + Log.getStackTraceString(e));
                callbackContext.error(e.getMessage());
            }
        }

        mClient.setFollowRedirects(false);
        mClient.setFollowSslRedirects(false);

        mClient.setHostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                try {
                    Certificate[] certificates = session.getPeerCertificates();
                    if(certificates == null) {
                        Log.e(LOG_TAG, "Session has no certificates");
                    }
                    else {
                        Log.d(LOG_TAG, "Certification count: " + certificates.length);
                        for(Certificate certificate : certificates) {
                            X509Certificate x509certificate = (X509Certificate) certificate;
                            Log.d(LOG_TAG, "Found certificate: " + x509certificate.toString());
                            if(OkHostnameVerifier.INSTANCE.verify(hostname, x509certificate)) {
                                Log.d(LOG_TAG, "Verified OK");
//                                return true;
                            } else {
                                Log.d(LOG_TAG, "Verified NOK");
//                                continue;
                            }
                        }
                    }
                } catch (Throwable t) {
                    Log.i(LOG_TAG, "Error verifying certificates: " + t.getMessage());
                }
                Log.i(LOG_TAG, "No certificate with matching hostname found.");
                return true; // false;
            }
        });
        
        if (action.equals("fetch")) {

            try {
                String method = data.getString(0);
                Log.v(LOG_TAG, "execute: method = " + method.toString());

                String urlString = data.getString(1);
                Log.v(LOG_TAG, "execute: urlString = " + urlString.toString());

                if(method.equals("DELETE") && urlString.endsWith("cookies")) {
                    Log.v(LOG_TAG, "execute: Removing all cookies on demand.");
                    CookieManager.getInstance().removeAllCookies(new ValueCallback<Boolean>() {
                        @Override
                        public void onReceiveValue(Boolean aBoolean) {
                            Log.v(LOG_TAG, "execute: removed all cookies, result = " + aBoolean);
                            callbackContext.sendPluginResult(aBoolean 
                                ? new PluginResult(PluginResult.Status.OK, "Deleted all cookies")
                                : new PluginResult(PluginResult.Status.ERROR, "Could not delete cookies"));
                        }
                    }); 
                    return true;                   
                }

                String postBody = data.getString(2);
                Log.v(LOG_TAG, "execute: postBody = " + postBody.toString());

                JSONObject headers = data.getJSONObject(3);
                if (headers.has("map") && headers.getJSONObject("map") != null) {
                    headers = headers.getJSONObject("map");
                }

                headers.remove("cookie");
                headers.remove("Cookie");
                JSONArray cookieArray = new JSONArray();
                String cookies = CookieManager.getInstance().getCookie(urlString);

                boolean tooManyCookies = cookies != null && !cookies.isEmpty() && cookies.split(";").length > MAX_ALLOWED_COOKIES;
                Log.v(LOG_TAG, "execute: checking if too many cookies = " + tooManyCookies + ", cookies: " + cookies);
                if(tooManyCookies) {
                    CookieManager.getInstance().removeAllCookies(new ValueCallback<Boolean>() {
                        @Override
                        public void onReceiveValue(Boolean aBoolean) {
                            Log.v(LOG_TAG, "execute: removed all cookies, result = " + aBoolean);
                            System.exit(0);
                        }
                    });
                    // throw new Exception("Too many logins, cleared all cookies.");
                }

                LOG.i(LOG_TAG, "Setting cookies to headers: " + cookies);
                if(cookies != null && !cookies.isEmpty()) {
                    cookieArray.put(cookies.toString());
                    headers.put("Cookie", cookieArray);
                }

                Log.v(LOG_TAG, "execute: headers = " + headers.toString());

                Request.Builder requestBuilder = new Request.Builder();

                // method + postBody
                if (postBody != null && !postBody.equals("null")) {
                    // requestBuilder.post(RequestBody.create(MEDIA_TYPE_MARKDOWN, postBody.toString()));
                    String contentType;
                     if (headers.has("content-type")) {
                         JSONArray contentTypeHeaders = headers.getJSONArray("content-type");
                         contentType = contentTypeHeaders.getString(0);
                     } else {
                         contentType = "application/json";
                     }
                     if(method.equals("PUT")) {
                        requestBuilder.put(RequestBody.create(MediaType.parse(contentType), postBody.toString()));
                     } else {
                        requestBuilder.post(RequestBody.create(MediaType.parse(contentType), postBody.toString()));
                     }
                } else {
                    requestBuilder.method(method, null);
                }

                // url
                requestBuilder.url(urlString);

                // headers
                if (headers != null && headers.names() != null && headers.names().length() > 0) {
                    for (int i = 0; i < headers.names().length(); i++) {

                        String headerName = headers.names().getString(i);
                        JSONArray headerValues = headers.getJSONArray(headers.names().getString(i));

                        if (headerValues.length() > 0) {
                            String headerValue = headerValues.getString(0);
                            Log.v(LOG_TAG, "key = " + headerName + " value = " + headerValue);
                            requestBuilder.addHeader(headerName, headerValue);
                        }
                    }
                }
                requestBuilder.addHeader("X-Pake-Client-Os", "Android");

                Request request = requestBuilder.build();

                mClient.newCall(request).enqueue(new Callback() {

                    @Override
                    public void onFailure(Request request, IOException throwable) {
                        Log.w(LOG_TAG, "Failure at onFailure() of HTTP call:\n" + Log.getStackTraceString(throwable));
                        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, throwable.getMessage()));
                    }

                    @Override
                    public void onResponse(Response response) throws IOException {

                        Log.i(LOG_TAG, "Receiving HTTP response at onResponse()");
                        JSONObject result = new JSONObject();
                        try {
                            Headers responseHeaders = response.headers();

                            JSONObject allHeaders = new JSONObject();

                            if (responseHeaders != null ) {
                                for (int i = 0; i < responseHeaders.size(); i++) {
                                    allHeaders.put(responseHeaders.name(i), responseHeaders.value(i));
                                }
                            }

                            result.put("headers", allHeaders);
                            result.put("statusText", response.body().string());
                            result.put("status", response.code());
                            result.put("url", response.request().urlString());

                        } catch (Exception e) {
                            Log.w(LOG_TAG, "Exception at onResponse() of HTTP call:\n" + Log.getStackTraceString(e));
                            callbackContext.error(e.getMessage());
                        }

                        Log.v(LOG_TAG, "HTTP code: " + response.code());
                        Log.v(LOG_TAG, "returning: " + result.toString());

                        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, result));
                    }
                });

            } catch (Throwable e) {
                Log.w(LOG_TAG, "Exception when invoking HTTP call:\n" + Log.getStackTraceString(e));
                callbackContext.error(e.getMessage());
            }

        } else {
            Log.e(LOG_TAG, "Invalid action : " + action);
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
            return false;
        }

        return true;
    }
}
