package com.jeffmony.downloader.utils;

import android.content.Context;

import com.jeffmony.downloader.model.VideoDownloadInfo;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VideoDownloadUtils {

    private static final String TAG = "VideoDownloadUtils";
    public static final int DEFAULT_BUFFER_SIZE = 8 * 1024;
    public static final int UPDATE_INTERVAL = 1000;
    public static final String INFO_FILE = "video.info";
    public static final String VIDEO_SUFFIX = ".video";
    public static final String LOCAL_M3U8 = "local.m3u8";
    private static Object sFileLock = new Object();

    public static File getVideoCacheDir(Context context) {
        return new File(context.getExternalCacheDir(), ".video-cache");
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

    public static VideoDownloadInfo readDownloadInfo(File dir) {
        File file = new File(dir, INFO_FILE);
        if (!file.exists()) {
            LogUtils.i(TAG,"readProxyCacheInfo failed, file not exist.");
            return null;
        }
        ObjectInputStream fis = null;
        try {
            synchronized (sFileLock) {
                fis = new ObjectInputStream(new FileInputStream(file));
                VideoDownloadInfo info = (VideoDownloadInfo)fis.readObject();
                return info;
            }
        } catch (Exception e) {
            LogUtils.w(TAG,"readDownloadInfo failed, exception=" + e.getMessage());
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (Exception e) {
                LogUtils.w(TAG,"readDownloadInfo failed, close fis failed.");
            }
        }
        return null;
    }

    public static void writeDownloadInfo(VideoDownloadInfo info, File dir) {
        File file = new File(dir, INFO_FILE);
        ObjectOutputStream fos = null;
        try {
            synchronized (sFileLock) {
                fos = new ObjectOutputStream(new FileOutputStream(file));
                fos.writeObject(info);
            }
        } catch (Exception e) {
            LogUtils.w(TAG,"writeDownloadInfo failed, exception=" +
                    e.getMessage());
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (Exception e) {
                LogUtils.w(TAG,"writeDownloadInfo failed, close fos failed.");
            }
        }
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
                LogUtils.w(TAG,"VideoProxyCacheUtils close " + closeable +
                        " failed, exception = " + e);
            }
        }
    }

    public static long countTotalSize(File file) {
        if (file.isDirectory()) {
            long totalSize = 0;
            for (File f : file.listFiles()) {
                totalSize += countTotalSize(f);
            }
            return totalSize;
        } else {
            return file.length();
        }
    }
}
