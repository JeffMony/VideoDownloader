package com.jeffmony.downloader.utils;

import android.net.Uri;
import android.text.TextUtils;

import com.jeffmony.downloader.VideoDownloadConfig;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class HttpUtils {

    private static final String TAG = "HttpUtils";

    public static int MAX_REDIRECT = 5;
    public static final int RESPONSE_OK = 200;

    public static boolean matchHttpSchema(String url) {
        if (TextUtils.isEmpty(url))
            return false;
        Uri uri = Uri.parse(url);
        String schema = uri.getScheme();
        return "http".equals(schema) || "https".equals(schema);
    }

    public static String getMimeType(VideoDownloadConfig config,
                                     String videoUrl, Proxy proxy,
                                     HashMap<String, String> headers)
            throws IOException {
        String mimeType = null;
        URL url = null;
        try {
            url = new URL(videoUrl);
        } catch (MalformedURLException e) {
            LogUtils.w(TAG, "VideoUrl(" + videoUrl +
                    ") packages error, exception = " + e.getMessage());
            throw new MalformedURLException("URL parse error.");
        }
        HttpURLConnection connection = null;
        if (url != null) {
            try {
                connection = makeConnection(config, url, proxy, headers);
            } catch (IOException e) {
                LogUtils.w(TAG,"Unable to connect videoUrl(" + videoUrl +
                        "), exception = " + e.getMessage());
                closeConnection(connection);
                throw new IOException("getMimeType connect failed.");
            }
            int responseCode = 0;
            if (connection != null) {
                try {
                    responseCode = connection.getResponseCode();
                } catch (IOException e) {
                    LogUtils.w(TAG,"Unable to Get reponseCode videoUrl(" + videoUrl +
                            "), exception = " + e.getMessage());
                    closeConnection(connection);
                    throw new IOException("getMimeType get responseCode failed.");
                }
                if (responseCode == RESPONSE_OK) {
                    String contentType = connection.getContentType();
                    LogUtils.i(TAG,"contentType = " + contentType);
                    return contentType;
                }
            }
        }
        return mimeType;
    }

    public static String getFinalUrl(VideoDownloadConfig config,
                                     String videoUrl, Proxy proxy,
                                     HashMap<String, String> headers)
            throws IOException {
        URL url = null;
        try {
            url = new URL(videoUrl);
        } catch (MalformedURLException e) {
            LogUtils.w(TAG,"VideoUrl(" + videoUrl +
                    ") packages error, exception = " + e.getMessage());
            throw new MalformedURLException("URL parse error.");
        }
        url = handleRedirectRequest(config, url, proxy, headers);
        return url.toString();
    }

    public static URL handleRedirectRequest(VideoDownloadConfig config, URL url,
                                            Proxy proxy,
                                            HashMap<String, String> headers)
            throws IOException {
        int redirectCount = 0;
        while (redirectCount++ < MAX_REDIRECT) {
            HttpURLConnection connection =
                    makeConnection(config, url, proxy, headers);
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_MULT_CHOICE ||
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER &&
                            (responseCode == 307 /* HTTP_TEMP_REDIRECT */
                                    || responseCode == 308 /* HTTP_PERM_REDIRECT */)) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                url = handleRedirect(url, location);
                return handleRedirectRequest(config, url, proxy, headers);
            } else {
                return url;
            }
        }
        throw new NoRouteToHostException("Too many redirects: " + redirectCount);
    }

    private static HttpURLConnection
    makeConnection(VideoDownloadConfig config, URL url, Proxy proxy,
                   HashMap<String, String> headers) throws IOException {
        HttpURLConnection connection = null;
        if (proxy != null) {
            // V-Card
            connection = (HttpURLConnection)url.openConnection(proxy);
        } else {
            connection = (HttpURLConnection)url.openConnection();
        }
        if (config.shouldIgnoreCertErrors() && connection instanceof
                HttpsURLConnection) {
            trustAllCert((HttpsURLConnection)(connection));
        }
        connection.setConnectTimeout(config.getConnTimeOut());
        connection.setReadTimeout(config.getReadTimeOut());
        if (headers != null) {
            for (Map.Entry<String, String> item : headers.entrySet()) {
                connection.setRequestProperty(item.getKey(), item.getValue());
            }
        }
        connection.connect();
        return connection;
    }

    private static URL handleRedirect(URL originalUrl, String location)
            throws IOException {
        if (location == null) {
            throw new ProtocolException("Null location redirect");
        }
        URL url = new URL(originalUrl, location);
        String protocol = url.getProtocol();
        if (!"https".equals(protocol) && !"http".equals(protocol)) {
            throw new ProtocolException("Unsupported protocol redirect: " + protocol);
        }
        return url;
    }

    private static void closeConnection(HttpURLConnection connection) {
        if (connection != null) {
            connection.disconnect();
            connection = null;
        }
    }

    public static void trustAllCert(HttpsURLConnection httpsURLConnection) {
        SSLContext sslContext = null;
        try {
            sslContext = SSLContext.getInstance("TLS");
            if (sslContext != null) {
                TrustManager tm = new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }

                    public void checkClientTrusted(X509Certificate[] chain,
                                                   String authType) {
                        LogUtils.d(TAG,"checkClientTrusted.");
                    }

                    public void checkServerTrusted(X509Certificate[] chain,
                                                   String authType) {
                        LogUtils.d(TAG,"checkServerTrusted.");
                    }
                };
                sslContext.init(null, new TrustManager[] {tm}, null);
            }
        } catch (Exception e) {
            LogUtils.w(TAG,"SSLContext init failed");
        }
        // Cannot do ssl checkl.
        if (sslContext != null) {
            httpsURLConnection.setSSLSocketFactory(sslContext.getSocketFactory());
        }
        // Trust the cert.
        HostnameVerifier hostnameVerifier = new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };
        httpsURLConnection.setHostnameVerifier(hostnameVerifier);
    }
}

