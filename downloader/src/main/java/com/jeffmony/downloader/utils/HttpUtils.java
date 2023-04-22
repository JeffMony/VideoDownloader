package com.jeffmony.downloader.utils;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.text.TextUtils;

import com.jeffmony.downloader.common.DownloadConstants;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.ProtocolException;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class HttpUtils {
    public static final int MAX_RETRY_COUNT = 100;
    public static final int MAX_REDIRECT = 20;
    public static final int RESPONSE_200 = 200;
    public static final int RESPONSE_206 = 206;

    public static final int RESPONSE_503 = 503;

    private static int sRedirectCount = 0;

    public static boolean matchHttpSchema(String url) {
        if (TextUtils.isEmpty(url))
            return false;
        Uri uri = Uri.parse(url);
        String schema = uri.getScheme();
        return "http".equals(schema) || "https".equals(schema);
    }

    public static HttpURLConnection getConnection(String videoUrl, Map<String, String> headers, boolean shouldIgnoreCertErrors) throws IOException {
        URL url = new URL(videoUrl);
        sRedirectCount = 0;
        while(sRedirectCount < MAX_REDIRECT) {
            try {
                HttpURLConnection connection = makeConnection(url, headers, shouldIgnoreCertErrors);
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_MULT_CHOICE ||
                        responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                        responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                        responseCode == HttpURLConnection.HTTP_SEE_OTHER &&
                                (responseCode == 307 || responseCode == 308)) {
                    String location = connection.getHeaderField("Location");
                    connection.disconnect();
                    url = new URL(location);
                    sRedirectCount++;
                } else {
                    return connection;
                }
            } catch (IOException e) {
                if ((e instanceof SSLHandshakeException || e instanceof SSLPeerUnverifiedException) && !shouldIgnoreCertErrors) {
                    //这种情况下需要信任证书重试
                    return getConnection(videoUrl, headers, true);
                }
            }
        }
        throw new NoRouteToHostException("Too many redirects: " + sRedirectCount);
    }

    private static URL handleRedirect(URL originalUrl, String location) throws IOException {
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

    private static HttpURLConnection makeConnection(URL url, Map<String, String> headers, boolean shouldIgnoreCertErrors) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        if (shouldIgnoreCertErrors && connection instanceof HttpsURLConnection) {
            trustAllCert((HttpsURLConnection) (connection));
        }
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(VideoDownloadUtils.getDownloadConfig().getConnTimeOut());
        connection.setReadTimeout(VideoDownloadUtils.getDownloadConfig().getReadTimeOut());
        if (headers != null) {
            for (Map.Entry<String, String> item : headers.entrySet()) {
                connection.setRequestProperty(item.getKey(), item.getValue());
            }
        }
        connection.connect();
        return connection;
    }

    public static void closeConnection(HttpURLConnection connection) {
        if (connection != null) {
            connection.disconnect();
        }
    }

    public static void trustAllCert(HttpsURLConnection httpsURLConnection) {
        SSLContext sslContext = null;
        try {
            sslContext = SSLContext.getInstance("TLS");
            if (sslContext != null) {
                sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                    @SuppressLint("TrustAllX509TrustManager")
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain,
                                                   String authType) throws CertificateException {
                    }

                    @SuppressLint("TrustAllX509TrustManager")
                    @Override
                    public void checkServerTrusted(X509Certificate[] chain,
                                                   String authType) throws CertificateException {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }}, null);
            }
        } catch (Exception e) {
            LogUtils.w(DownloadConstants.TAG,"SSLContext init failed");
        }
        // Cannot do ssl checkl.
        if (sslContext == null) {
            return;
        }
        // Trust the cert.
        HostnameVerifier hostnameVerifier = (hostname, session) -> true;
        httpsURLConnection.setHostnameVerifier(hostnameVerifier);
        httpsURLConnection.setSSLSocketFactory(sslContext.getSocketFactory());
    }
}

