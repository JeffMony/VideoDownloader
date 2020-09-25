package com.jeffmony.downloader.m3u8;

import android.text.TextUtils;

import com.jeffmony.downloader.VideoDownloadConfig;
import com.jeffmony.downloader.utils.HttpUtils;
import com.jeffmony.downloader.utils.LogUtils;
import com.jeffmony.downloader.utils.VideoDownloadUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

public class M3U8Utils {

    private static final String TAG = "M3U8Utils";

    /**
     * parse M3U8 file.
     *
     * @param videoUrl
     * @return
     * @throws IOException
     */
    public static M3U8 parseM3U8Info(VideoDownloadConfig config,
                                     String videoUrl, boolean isLocalFile,
                                     File m3u8File) throws IOException {
        URL url = new URL(videoUrl);
        InputStreamReader inputStreamReader = null;
        BufferedReader bufferedReader;
        if (isLocalFile) {
            inputStreamReader = new InputStreamReader(new FileInputStream(m3u8File));
            bufferedReader = new BufferedReader(inputStreamReader);
        } else {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            if (config.shouldIgnoreCertErrors() && connection instanceof
                    HttpsURLConnection) {
                HttpUtils.trustAllCert((HttpsURLConnection) connection);
                bufferedReader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
            } else {
                bufferedReader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
            }
        }
        String baseUriPath = videoUrl.substring(0, videoUrl.lastIndexOf("/") + 1);
        String hostUrl = videoUrl.substring(0, videoUrl.indexOf(url.getPath()) + 1);
        M3U8 m3u8 = new M3U8(videoUrl, baseUriPath, hostUrl);
        float tsDuration = 0;
        int targetDuration = 0;
        int tsIndex = 0;
        int version = 0;
        int sequence = 0;
        boolean hasDiscontinuity = false;
        boolean hasEndList = false;
        boolean hasStreamInfo = false;
        boolean hasKey = false;
        String method = null;
        String encryptionIV = null;
        String encryptionKeyUri = null;
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            LogUtils.i(TAG, "line = " + line);
            if (line.startsWith(M3U8Constants.TAG_PREFIX)) {
                if (line.startsWith(M3U8Constants.TAG_MEDIA_DURATION)) {
                    String ret = parseStringAttr(line, M3U8Constants.REGEX_MEDIA_DURATION);
                    if (!TextUtils.isEmpty(ret)) {
                        tsDuration = Float.parseFloat(ret);
                    }
                } else if (line.startsWith(M3U8Constants.TAG_TARGET_DURATION)) {
                    String ret = parseStringAttr(line, M3U8Constants.REGEX_TARGET_DURATION);
                    if (!TextUtils.isEmpty(ret)) {
                        targetDuration = Integer.parseInt(ret);
                    }
                } else if (line.startsWith(M3U8Constants.TAG_VERSION)) {
                    String ret = parseStringAttr(line, M3U8Constants.REGEX_VERSION);
                    if (!TextUtils.isEmpty(ret)) {
                        version = Integer.parseInt(ret);
                    }
                } else if (line.startsWith(M3U8Constants.TAG_MEDIA_SEQUENCE)) {
                    String ret = parseStringAttr(line, M3U8Constants.REGEX_MEDIA_SEQUENCE);
                    if (!TextUtils.isEmpty(ret)) {
                        sequence = Integer.parseInt(ret);
                    }
                } else if (line.startsWith(M3U8Constants.TAG_STREAM_INF)) {
                    hasStreamInfo = true;
                } else if (line.startsWith(M3U8Constants.TAG_DISCONTINUITY)) {
                    hasDiscontinuity = true;
                } else if (line.startsWith(M3U8Constants.TAG_ENDLIST)) {
                    hasEndList = true;
                } else if (line.startsWith(M3U8Constants.TAG_KEY)) {
                    hasKey = true;
                    method = parseOptionalStringAttr(line, M3U8Constants.REGEX_METHOD);
                    String keyFormat = parseOptionalStringAttr(line, M3U8Constants.REGEX_KEYFORMAT);
                    if (!M3U8Constants.METHOD_NONE.equals(method)) {
                        encryptionIV = parseOptionalStringAttr(line, M3U8Constants.REGEX_IV);
                        if (M3U8Constants.KEYFORMAT_IDENTITY.equals(keyFormat) || keyFormat == null) {
                            if (M3U8Constants.METHOD_AES_128.equals(method)) {
                                // The segment is fully encrypted using an identity key.
                                String tempKeyUri = parseStringAttr(line, M3U8Constants.REGEX_URI);
                                if (tempKeyUri != null) {
                                    encryptionKeyUri = getFinalUrl(videoUrl, tempKeyUri);
                                }
                            } else {
                                // Do nothing. Samples are encrypted using an identity key,
                                // but this is not supported. Hopefully, a traditional DRM
                                // alternative is also provided.
                            }
                        } else {
                            // Do nothing.
                        }
                    }
                }
                continue;
            }
            // It has '#EXT-X-STREAM-INF' tag;
            if (hasStreamInfo) {
                return parseM3U8Info(config, getFinalUrl(videoUrl, line), isLocalFile, m3u8File);
            }
            M3U8Ts ts = new M3U8Ts();
            if (isLocalFile) {
                ts.initTsAttributes(line, tsDuration, tsIndex, hasDiscontinuity,
                        hasKey);
            } else {
                ts.initTsAttributes(getFinalUrl(videoUrl, line), tsDuration, tsIndex, hasDiscontinuity,
                        hasKey);
            }
            if (hasKey) {
                ts.setKeyConfig(method, encryptionKeyUri, encryptionIV);
            }
            m3u8.addTs(ts);
            tsIndex++;
            tsDuration = 0;
            hasStreamInfo = false;
            hasDiscontinuity = false;
            hasKey = false;
            method = null;
            encryptionKeyUri = null;
            encryptionIV = null;
        }
        if (inputStreamReader != null) {
            inputStreamReader.close();
        }
        if (bufferedReader != null) {
            bufferedReader.close();
        }
        m3u8.setTargetDuration(targetDuration);
        m3u8.setVersion(version);
        m3u8.setSequence(sequence);
        m3u8.setHasEndList(hasEndList);
        return m3u8;
    }

    private static String getFinalUrl(String videoUrl, String line) throws IOException {
        URL url = new URL(videoUrl);
        String baseUriPath = videoUrl.substring(0, videoUrl.lastIndexOf("/") + 1);
        String hostUrl = videoUrl.substring(0, videoUrl.indexOf(url.getPath()) + 1);
        if (line.startsWith("/")) {
            int tempIndex = line.indexOf('/', 1);
            String tempUrl;
            if (tempIndex == -1) {
                tempUrl = baseUriPath + line.substring(1);
            } else {
                tempUrl = line.substring(0, tempIndex);
                tempIndex = videoUrl.indexOf(tempUrl);
                if (tempIndex == -1) {
                    tempUrl = hostUrl + line.substring(1);
                } else {
                    tempUrl = videoUrl.substring(0, tempIndex) + line;
                }
            }
            return tempUrl;
        }
        if (line.startsWith("https://") || line.startsWith("http://")) {
            return line;
        }
        return baseUriPath + line;
    }

    public static String parseStringAttr(String line, Pattern pattern) {
        if (pattern == null)
            return null;
        Matcher matcher = pattern.matcher(line);
        if (matcher.find() && matcher.groupCount() == 1) {
            return matcher.group(1);
        }
        return null;
    }

    public static String parseOptionalStringAttr(String line, Pattern pattern) {
        if (pattern == null)
            return null;
        Matcher matcher = pattern.matcher(line);
        return matcher.find() ? matcher.group(1) : null;
    }

    public static void createRemoteM3U8(File dir, M3U8 m3u8) throws IOException {
        File m3u8File = new File(dir, VideoDownloadUtils.REMOTE_M3U8);
        if (m3u8File.exists()) {
            return;
        }
        BufferedWriter bfw = new BufferedWriter(new FileWriter(m3u8File, false));
        bfw.write(M3U8Constants.PLAYLIST_HEADER + "\n");
        bfw.write(M3U8Constants.TAG_VERSION + ":" + m3u8.getVersion() + "\n");
        bfw.write(M3U8Constants.TAG_MEDIA_SEQUENCE + ":" + m3u8.getSequence() + "\n");
        bfw.write(M3U8Constants.TAG_TARGET_DURATION + ":" + m3u8.getTargetDuration() + "\n");
        for (M3U8Ts m3u8Ts : m3u8.getTsList()) {
            if (m3u8Ts.hasKey()) {
                if (m3u8Ts.getMethod() != null) {
                    String key = "METHOD=" + m3u8Ts.getMethod();
                    if (m3u8Ts.getKeyUri() != null) {
                        String keyUri = m3u8Ts.getKeyUri();
                        key += ",URI=\"" + keyUri + "\"";
                        URL keyURL = new URL(keyUri);
                        BufferedReader bufferedReader =
                                new BufferedReader(new InputStreamReader(keyURL.openStream()));
                        StringBuilder textBuilder = new StringBuilder();
                        String line = null;
                        while ((line = bufferedReader.readLine()) != null) {
                            textBuilder.append(line);
                        }
                        boolean isMessyStr =
                                VideoDownloadUtils.isMessyCode(textBuilder.toString());
                        m3u8Ts.setIsMessyKey(isMessyStr);
                        File keyFile = new File(dir, m3u8Ts.getLocalKeyUri());
                        FileOutputStream outputStream = new FileOutputStream(keyFile);
                        outputStream.write(textBuilder.toString().getBytes());
                        bufferedReader.close();
                        outputStream.close();
                        if (m3u8Ts.getKeyIV() != null) {
                            key += ",IV=" + m3u8Ts.getKeyIV();
                        }
                    }
                    bfw.write(M3U8Constants.TAG_KEY + ":" + key + "\n");
                }
            }
            if (m3u8Ts.hasDiscontinuity()) {
                bfw.write(M3U8Constants.TAG_DISCONTINUITY + "\n");
            }
            bfw.write(M3U8Constants.TAG_MEDIA_DURATION + ":" + m3u8Ts.getDuration() + ",\n");
            bfw.write(m3u8Ts.getUrl());
            bfw.newLine();
        }
        bfw.write(M3U8Constants.TAG_ENDLIST);
        bfw.flush();
        bfw.close();
    }
}

