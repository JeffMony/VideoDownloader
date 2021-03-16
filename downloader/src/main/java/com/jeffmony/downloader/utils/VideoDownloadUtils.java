package com.jeffmony.downloader.utils;


import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.jeffmony.downloader.VideoDownloadConfig;
import com.jeffmony.downloader.model.Video;

import java.io.Closeable;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VideoDownloadUtils {

    private static final String TAG = "VideoDownloadUtils";
    public static final long DEFAULT_CONTENT_LENGTH = -1;
    public static final int DEFAULT_BUFFER_SIZE = 8 * 1024;
    public static final String VIDEO_SUFFIX = ".video";
    public static final String LOCAL_M3U8 = "local.m3u8";
    public static final String REMOTE_M3U8 = "remote.m3u8";
    public static final String OUPUT_VIDEO = "merged.mp4";
    public static final String SEGMENT_PREFIX = "video_";
    public static final String INIT_SEGMENT_PREFIX = "init_video_";

    private static VideoDownloadConfig mDownloadConfig;

    public static void setDownloadConfig(@NonNull VideoDownloadConfig config) {
        mDownloadConfig = config;
    }

    public static VideoDownloadConfig getDownloadConfig() {
        return mDownloadConfig;
    }

    public static boolean isNameSupported(String fileName) {
        if (fileName.endsWith(Video.SUFFIX.SUFFIX_MP4) ||
                fileName.endsWith(Video.SUFFIX.SUFFIX_MOV) ||
                fileName.endsWith(Video.SUFFIX.SUFFIX_WEBM) ||
                fileName.endsWith(Video.SUFFIX.SUFFIX_3GP) ||
                fileName.endsWith(Video.SUFFIX.SUFFIX_MKV)) {
            return true;
        }
        return false;
    }

    public static String getVideoMime(String fileName) {
        if (fileName.endsWith(Video.SUFFIX.SUFFIX_MP4)) {
            return Video.TypeInfo.MP4;
        } else if (fileName.endsWith(Video.SUFFIX.SUFFIX_MOV)) {
            return Video.TypeInfo.MOV;
        } else if (fileName.endsWith(Video.SUFFIX.SUFFIX_WEBM)) {
            return Video.TypeInfo.WEBM;
        } else if (fileName.endsWith(Video.SUFFIX.SUFFIX_3GP)) {
            return Video.TypeInfo.GP3;
        } else if (fileName.endsWith(Video.SUFFIX.SUFFIX_MKV)) {
            return Video.TypeInfo.MKV;
        }
        return Video.TypeInfo.OTHER;
    }

    public static int getVideoType(String type) {
        if (type.endsWith(Video.SUFFIX.SUFFIX_MP4) || type.contains(Video.Mime.MIME_TYPE_MP4)) {
            return Video.Type.MP4_TYPE;
        } else if (type.endsWith(Video.SUFFIX.SUFFIX_MOV) || type.contains(Video.Mime.MIME_TYPE_QUICKTIME)) {
            return Video.Type.QUICKTIME_TYPE;
        } else if (type.endsWith(Video.SUFFIX.SUFFIX_WEBM) || type.contains(Video.Mime.MIME_TYPE_WEBM)) {
            return Video.Type.WEBM_TYPE;
        } else if (type.endsWith(Video.SUFFIX.SUFFIX_3GP) || type.contains(Video.Mime.MIME_TYPE_3GP)) {
            return Video.Type.GP3_TYPE;
        } else if (type.endsWith(Video.SUFFIX.SUFFIX_MKV) || type.contains(Video.Mime.MIME_TYPE_MKV)) {
            return Video.Type.MKV_TYPE;
        }
        return Video.Type.DEFAULT;
    }

    public static boolean isM3U8Mimetype(String mimeType) {
        return !TextUtils.isEmpty(mimeType) &&
                (mimeType.contains(Video.Mime.MIME_TYPE_M3U8_1) ||
                mimeType.contains(Video.Mime.MIME_TYPE_M3U8_2) ||
                mimeType.contains(Video.Mime.MIME_TYPE_M3U8_3) ||
                mimeType.contains(Video.Mime.MIME_TYPE_M3U8_4));
    }

    public static String computeMD5(String string) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte[] digestBytes = messageDigest.digest(string.getBytes());
            return bytesToHexString(digestBytes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String bytesToHexString(byte[] bytes) {
        StringBuffer sb = new StringBuffer();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static boolean isChinese(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        if (ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
                ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                ub == Character.UnicodeBlock.GENERAL_PUNCTUATION ||
                ub == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
                ub == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS) {
            return true;
        }
        return false;
    }

    public static boolean isMessyCode(String strName) {
        Pattern p = Pattern.compile("\\s*|t*|r*|n*");
        Matcher m = p.matcher(strName);
        String after = m.replaceAll("");
        String temp = after.replaceAll("\\p{P}", "");
        char[] ch = temp.trim().toCharArray();
        float chLength = ch.length;
        float count = 0;
        for (int i = 0; i < ch.length; i++) {
            char c = ch[i];
            if (!Character.isLetterOrDigit(c)) {
                if (!isChinese(c)) {
                    count = count + 1;
                }
            }
        }
        if (chLength <= 0)
            return false;
        float result = count / chLength;
        if (result > 0.4) {
            return true;
        } else {
            return false;
        }
    }

    public static void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                LogUtils.w(TAG, "VideoProxyCacheUtils close " + closeable + " failed, exception = " + e);
            }
        }
    }

    public static String getPercent(float percent) {
        DecimalFormat format = new DecimalFormat("###.00");
        return format.format(percent) + "%";
    }

    public static boolean isFloatEqual(float f1, float f2) {
        if (Math.abs(f1 - f2) < 0.01f) {
            return true;
        }
        return false;
    }

    public static String getSuffixName(String name) {
        if (TextUtils.isEmpty(name)) {
            return "";
        }
        int dotIndex = name.lastIndexOf('.');
        return (dotIndex >= 0 && dotIndex < name.length()) ? name.substring(dotIndex) : "";
    }

}
