package com.jeffmony.downloader.m3u8;

import android.text.TextUtils;

import com.jeffmony.downloader.common.DownloadConstants;
import com.jeffmony.downloader.utils.HttpUtils;
import com.jeffmony.downloader.utils.LogUtils;
import com.jeffmony.downloader.utils.VideoDownloadUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class M3U8Utils {

    /**
     * parse network M3U8 file.
     *
     * @param videoUrl
     * @return
     * @throws IOException
     */
    public static M3U8 parseNetworkM3U8Info(String videoUrl, Map<String, String> headers, int retryCount) throws IOException {
        BufferedReader bufferedReader = null;
        try {
            HttpURLConnection connection = HttpUtils.getConnection(videoUrl, headers, VideoDownloadUtils.getDownloadConfig().shouldIgnoreCertErrors());
            int responseCode = connection.getResponseCode();
            LogUtils.i(DownloadConstants.TAG, "parseNetworkM3U8Info responseCode="+responseCode);
            if (responseCode == HttpUtils.RESPONSE_503 && retryCount < HttpUtils.MAX_RETRY_COUNT) {
                return parseNetworkM3U8Info(videoUrl, headers, retryCount+1);
            }
            bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            M3U8 m3u8 = new M3U8(videoUrl);
            float tsDuration = 0;
            String byteRange = "";
            int targetDuration = 0;
            int tsIndex = 0;
            int version = 0;
            int sequence = 0;
            int initSequence = 0;
            boolean hasDiscontinuity = false;
            boolean hasEndList = false;
            boolean hasStreamInfo = false;
            boolean hasKey = false;
            boolean hasInitSegment = false;
            String method = null;
            String encryptionIV = null;
            String encryptionKeyUri = null;
            String initSegmentUri = null;
            String segmentByteRange = null;
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                line = line.trim();
                if (TextUtils.isEmpty(line)) {
                    continue;
                }
                LogUtils.i(DownloadConstants.TAG, "line = " + line);
                if (line.startsWith(M3U8Constants.TAG_PREFIX)) {
                    if (line.startsWith(M3U8Constants.TAG_MEDIA_DURATION)) {
                        String ret = parseStringAttr(line, M3U8Constants.REGEX_MEDIA_DURATION);
                        if (!TextUtils.isEmpty(ret)) {
                            tsDuration = Float.parseFloat(ret);
                        }
                    } else if (line.startsWith(M3U8Constants.TAG_BYTERANGE)) {
                        byteRange = parseStringAttr(line, M3U8Constants.REGEX_BYTERANGE);
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
                            initSequence = sequence;
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
                                        encryptionKeyUri = getM3U8AbsoluteUrl(videoUrl, tempKeyUri);
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
                    } else if (line.startsWith(M3U8Constants.TAG_INIT_SEGMENT)) {
                        String tempInitSegmentUri = parseStringAttr(line, M3U8Constants.REGEX_URI);
                        if (!TextUtils.isEmpty(tempInitSegmentUri)) {
                            hasInitSegment = true;
                            initSegmentUri = getM3U8AbsoluteUrl(videoUrl, tempInitSegmentUri);
                            segmentByteRange = parseOptionalStringAttr(line, M3U8Constants.REGEX_ATTR_BYTERANGE);
                        }
                    }
                    continue;
                }
                // It has '#EXT-X-STREAM-INF' DownloadConstants.TAG;
                if (hasStreamInfo) {
                    return parseNetworkM3U8Info(getM3U8AbsoluteUrl(videoUrl, line), headers, retryCount);
                }
                if (Math.abs(tsDuration) < 0.001f) {
                    continue;
                }
                M3U8Seg ts = new M3U8Seg();
                ts.initTsAttributes(getM3U8AbsoluteUrl(videoUrl, line), tsDuration, tsIndex, sequence++, hasDiscontinuity, byteRange);
                if (hasKey) {
                    ts.setKeyConfig(method, encryptionKeyUri, encryptionIV);
                }
                if (hasInitSegment) {
                    ts.setInitSegmentInfo(initSegmentUri, segmentByteRange);
                }
                m3u8.addTs(ts);
                tsIndex++;
                tsDuration = 0;
                hasStreamInfo = false;
                hasDiscontinuity = false;
                hasKey = false;
                hasInitSegment = false;
                method = null;
                encryptionKeyUri = null;
                encryptionIV = null;
                initSegmentUri = null;
                segmentByteRange = null;
            }
            m3u8.setTargetDuration(targetDuration);
            m3u8.setVersion(version);
            m3u8.setSequence(initSequence);
            m3u8.setHasEndList(hasEndList);
            return m3u8;
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        } finally {
            VideoDownloadUtils.close(bufferedReader);
        }
    }

    public static M3U8 parseLocalM3U8File(File m3u8File) throws IOException {
        InputStreamReader inputStreamReader = null;
        BufferedReader bufferedReader = null;
        try {
            inputStreamReader = new InputStreamReader(new FileInputStream(m3u8File));
            bufferedReader = new BufferedReader(inputStreamReader);
            M3U8 m3u8 = new M3U8();
            float tsDuration = 0;
            String byteRange = "";
            int targetDuration = 0;
            int tsIndex = 0;
            int version = 0;
            int sequence = 0;
            boolean hasDiscontinuity = false;
            boolean hasKey = false;
            boolean hasInitSegment = false;
            String method = null;
            String encryptionIV = null;
            String encryptionKeyUri = null;
            String initSegmentUri = null;
            String segmentByteRange = null;
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                LogUtils.i(DownloadConstants.TAG, "line = " + line);
                if (line.startsWith(M3U8Constants.TAG_PREFIX)) {
                    if (line.startsWith(M3U8Constants.TAG_MEDIA_DURATION)) {
                        String ret = parseStringAttr(line, M3U8Constants.REGEX_MEDIA_DURATION);
                        if (!TextUtils.isEmpty(ret)) {
                            tsDuration = Float.parseFloat(ret);
                        }
                    }  else if (line.startsWith(M3U8Constants.TAG_BYTERANGE)) {
                        byteRange = parseStringAttr(line, M3U8Constants.REGEX_BYTERANGE);
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
                    } else if (line.startsWith(M3U8Constants.TAG_DISCONTINUITY)) {
                        hasDiscontinuity = true;
                    } else if (line.startsWith(M3U8Constants.TAG_KEY)) {
                        hasKey = true;
                        method = parseOptionalStringAttr(line, M3U8Constants.REGEX_METHOD);
                        String keyFormat = parseOptionalStringAttr(line, M3U8Constants.REGEX_KEYFORMAT);
                        if (!M3U8Constants.METHOD_NONE.equals(method)) {
                            encryptionIV = parseOptionalStringAttr(line, M3U8Constants.REGEX_IV);
                            if (M3U8Constants.KEYFORMAT_IDENTITY.equals(keyFormat) || keyFormat == null) {
                                if (M3U8Constants.METHOD_AES_128.equals(method)) {
                                    // The segment is fully encrypted using an identity key.
                                    encryptionKeyUri = parseStringAttr(line, M3U8Constants.REGEX_URI);
                                } else {
                                    // Do nothing. Samples are encrypted using an identity key,
                                    // but this is not supported. Hopefully, a traditional DRM
                                    // alternative is also provided.
                                }
                            } else {
                                // Do nothing.
                            }
                        }
                    } else if (line.startsWith(M3U8Constants.TAG_INIT_SEGMENT)) {
                        initSegmentUri = parseStringAttr(line, M3U8Constants.REGEX_URI);
                        if (!TextUtils.isEmpty(initSegmentUri)) {
                            hasInitSegment = true;
                            segmentByteRange = parseOptionalStringAttr(line, M3U8Constants.REGEX_ATTR_BYTERANGE);
                        }
                    }
                    continue;
                }
                M3U8Seg ts = new M3U8Seg();
                ts.initTsAttributes(line, tsDuration, tsIndex, sequence++, hasDiscontinuity, byteRange);
                if (hasKey) {
                    ts.setKeyConfig(method, encryptionKeyUri, encryptionIV);
                }
                if (hasInitSegment) {
                    ts.setInitSegmentInfo(initSegmentUri, segmentByteRange);
                }
                m3u8.addTs(ts);
                tsIndex++;
                tsDuration = 0;
                hasDiscontinuity = false;
                hasKey = false;
                hasInitSegment = false;
                method = null;
                encryptionKeyUri = null;
                encryptionIV = null;
                initSegmentUri = null;
                segmentByteRange = null;
            }
            m3u8.setTargetDuration(targetDuration);
            m3u8.setVersion(version);
            m3u8.setSequence(sequence);
            return m3u8;
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        } finally {
            VideoDownloadUtils.close(inputStreamReader);
            VideoDownloadUtils.close(bufferedReader);
        }
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
        bfw.write(M3U8Constants.TAG_MEDIA_SEQUENCE + ":" + m3u8.getInitSequence() + "\n");
        bfw.write(M3U8Constants.TAG_TARGET_DURATION + ":" + m3u8.getTargetDuration() + "\n");
        for (M3U8Seg m3u8Ts : m3u8.getTsList()) {
            if (m3u8Ts.hasInitSegment()) {
                String initSegmentInfo;
                if (m3u8Ts.getSegmentByteRange() != null) {
                    initSegmentInfo = "URI=\"" + m3u8Ts.getInitSegmentUri() + "\"" + ",BYTERANGE=\"" + m3u8Ts.getSegmentByteRange() + "\"";
                } else {
                    initSegmentInfo = "URI=\"" + m3u8Ts.getInitSegmentUri()  + "\"";
                }
                bfw.write(M3U8Constants.TAG_INIT_SEGMENT + ":" + initSegmentInfo + "\n");
            }
            if (m3u8Ts.hasKey()) {
                if (m3u8Ts.getMethod() != null) {
                    String key = "METHOD=" + m3u8Ts.getMethod();
                    if (m3u8Ts.getKeyUri() != null) {
                        String keyUri = m3u8Ts.getKeyUri();
                        key += ",URI=\"" + keyUri + "\"";
                        HttpURLConnection connection = HttpUtils.getConnection(keyUri, null, true);
                        DataInputStream dis = new DataInputStream(connection.getInputStream());
                        File keyFile = new File(dir, m3u8Ts.getLocalKeyUri());
                        DataOutputStream dos = new DataOutputStream(new FileOutputStream(keyFile));
                        byte[] buffer = new byte[4096];
                        int count;
                        while ((count = dis.read(buffer)) > 0) {
                            dos.write(buffer, 0, count);
                        }
                        dis.close();
                        dos.close();
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
            String byteRange = m3u8Ts.getByteRange();
            if (!TextUtils.isEmpty(byteRange)) {
                bfw.write(M3U8Constants.TAG_BYTERANGE + ":" + byteRange + "\n");
            }
            bfw.write(m3u8Ts.getUrl());
            bfw.newLine();
        }
        bfw.write(M3U8Constants.TAG_ENDLIST);
        bfw.flush();
        bfw.close();
    }

    public static String getM3U8AbsoluteUrl(String videoUrl, String line) {
        if (TextUtils.isEmpty(videoUrl) || TextUtils.isEmpty(line)) {
            return "";
        }
        if (videoUrl.startsWith("file://") || videoUrl.startsWith("/")) {
            return videoUrl;
        }
        String baseUriPath = getBaseUrl(videoUrl);
        String hostUrl = getHostUrl(videoUrl);
        if (line.startsWith("//")) {
            String tempUrl = getSchema(videoUrl) + ":" + line;
            return tempUrl;
        }
        if (line.startsWith("/")) {
            String pathStr = getPathStr(videoUrl);
            String longestCommonPrefixStr = getLongestCommonPrefixStr(pathStr, line);
            if (hostUrl.endsWith("/")) {
                hostUrl = hostUrl.substring(0, hostUrl.length() - 1);
            }
            String tempUrl = hostUrl + longestCommonPrefixStr + line.substring(longestCommonPrefixStr.length());
            return tempUrl;
        }
        if (line.startsWith("http")) {
            return line;
        }
        return baseUriPath + line;
    }

    private static String getSchema(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        int index = url.indexOf("://");
        if (index != -1) {
            String result = url.substring(0, index);
            return result;
        }
        return "";
    }

    /**
     * 例如https://xvideo.d666111.com/xvideo/taohuadao56152307/index.m3u8
     * 我们希望得到https://xvideo.d666111.com/xvideo/taohuadao56152307/
     *
     * @param url
     * @return
     */
    public static String getBaseUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        int slashIndex = url.lastIndexOf("/");
        if (slashIndex != -1) {
            return url.substring(0, slashIndex + 1);
        }
        return url;
    }

    /**
     * 例如https://xvideo.d666111.com/xvideo/taohuadao56152307/index.m3u8
     * 我们希望得到https://xvideo.d666111.com/
     *
     * @param url
     * @return
     */
    public static String getHostUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        try {
            URL formatURL = new URL(url);
            String host = formatURL.getHost();
            if (host == null) {
                return url;
            }
            int hostIndex = url.indexOf(host);
            if (hostIndex != -1) {
                int port = formatURL.getPort();
                String resultUrl;
                if (port != -1) {
                    resultUrl = url.substring(0, hostIndex + host.length()) + ":" + port + "/";
                } else {
                    resultUrl = url.substring(0, hostIndex + host.length()) + "/";
                }
                return resultUrl;
            }
            return url;
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * 例如https://xvideo.d666111.com/xvideo/taohuadao56152307/index.m3u8
     * 我们希望得到   /xvideo/taohuadao56152307/index.m3u8
     *
     * @param url
     * @return
     */
    public static String getPathStr(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        String hostUrl = getHostUrl(url);
        if (TextUtils.isEmpty(hostUrl)) {
            return url;
        }
        return url.substring(hostUrl.length() - 1);
    }

    /**
     * 获取两个字符串的最长公共前缀
     * /xvideo/taohuadao56152307/500kb/hls/index.m3u8   与     /xvideo/taohuadao56152307/index.m3u8
     * <p>
     * /xvideo/taohuadao56152307/500kb/hls/jNd4fapZ.ts  与     /xvideo/taohuadao56152307/500kb/hls/index.m3u8
     *
     * @param str1
     * @param str2
     * @return
     */
    public static String getLongestCommonPrefixStr(String str1, String str2) {
        if (TextUtils.isEmpty(str1) || TextUtils.isEmpty(str2)) {
            return "";
        }
        if (TextUtils.equals(str1, str2)) {
            return str1;
        }
        char[] arr1 = str1.toCharArray();
        char[] arr2 = str2.toCharArray();
        int j = 0;
        while (j < arr1.length && j < arr2.length) {
            if (arr1[j] != arr2[j]) {
                break;
            }
            j++;
        }
        return str1.substring(0, j);
    }
}

