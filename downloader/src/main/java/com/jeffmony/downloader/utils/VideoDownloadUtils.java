package com.jeffmony.downloader.utils;

import android.content.Context;
import android.text.TextUtils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VideoDownloadUtils {

    private static final String TAG = "VideoDownloadUtils";
    public static final int DEFAULT_BUFFER_SIZE = 8 * 1024;
    public static final int UPDATE_INTERVAL = 1000;
    public static final String VIDEO_SUFFIX = ".video";
    public static final String LOCAL_M3U8 = "local.m3u8";
    public static final String REMOTE_M3U8 = "remote.m3u8";
    public static final String MERGE_VIDEO = "merge_video.ts";
    public static final String OUPUT_VIDEO = "output.mp4";

    public static File getVideoCacheDir(Context context) {
        return new File(context.getExternalFilesDir("Video"), "Download");
    }

    public static void clearVideoCacheDir(Context context) throws IOException {
        File videoCacheDir = getVideoCacheDir(context);
        cleanDirectory(videoCacheDir);
    }

    private static void cleanDirectory(File file) throws IOException {
        if (!file.exists()) {
            return;
        }
        File[] contentFiles = file.listFiles();
        if (contentFiles != null) {
            for (File contentFile : contentFiles) {
                delete(contentFile);
            }
        }
    }

    public static void delete(File file) throws IOException {
        if (file.isFile() && file.exists()) {
            deleteOrThrow(file);
        } else {
            cleanDirectory(file);
            deleteOrThrow(file);
        }
    }

    private static void deleteOrThrow(File file) throws IOException {
        if (file.exists()) {
            boolean isDeleted = file.delete();
            if (!isDeleted) {
                throw new IOException(
                        String.format("File %s can't be deleted", file.getAbsolutePath()));
            }
        }
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
                LogUtils.w(TAG, "VideoProxyCacheUtils close " + closeable +
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

    public static String toLowerInvariant(String text) {
        return text == null ? null : text.toLowerCase(Locale.US);
    }

    public static byte[] getBytes(String filePath) {
        if (TextUtils.isEmpty(filePath)) {
            return null;
        }
        InputStream inputStream = null;
        BufferedInputStream bis = null;
        ByteArrayOutputStream baos = null;
        if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
            try {
                URL keyURL = new URL(filePath);
                HttpURLConnection connection = (HttpURLConnection) keyURL.openConnection();
                inputStream = connection.getInputStream();
                bis = new BufferedInputStream(inputStream);
                baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[VideoDownloadUtils.DEFAULT_BUFFER_SIZE];
                int read = -1;
                while ((read = bis.read(buffer)) != -1) {
                    baos.write(buffer, 0, read);
                }
                byte[] data = baos.toByteArray();
                baos.flush();
                return data;
            } catch (Exception e) {
                LogUtils.w(TAG, "Request online key failed, exception = " + e);
                VideoDownloadUtils.close(inputStream);
                VideoDownloadUtils.close(bis);
                VideoDownloadUtils.close(baos);
            } finally {
                VideoDownloadUtils.close(inputStream);
                VideoDownloadUtils.close(bis);
                VideoDownloadUtils.close(baos);
            }
        } else {
            File keyFile = new File(filePath);
            try {
                inputStream = new FileInputStream(keyFile);
                bis = new BufferedInputStream(inputStream);
                baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[VideoDownloadUtils.DEFAULT_BUFFER_SIZE];
                int read = -1;
                while ((read = bis.read(buffer)) != -1) {
                    baos.write(buffer, 0, read);
                }
                byte[] data = baos.toByteArray();
                baos.flush();
                return data;
            } catch (Exception e) {
                LogUtils.w(TAG, "Read local key file failed, exception = " + e);
                VideoDownloadUtils.close(inputStream);
                VideoDownloadUtils.close(bis);
                VideoDownloadUtils.close(baos);
            } finally {
                VideoDownloadUtils.close(inputStream);
                VideoDownloadUtils.close(bis);
                VideoDownloadUtils.close(baos);
            }
        }
        return null;
    }

    public static String fetchKey(String keyPath) {
        if (TextUtils.isEmpty(keyPath))
            return keyPath;

        InputStreamReader inputStreamReader = null;
        BufferedReader bufferedReader = null;
        if (keyPath.startsWith("http://") || keyPath.startsWith("https://")) {
            try {
                URL keyURL = new URL(keyPath);
                HttpURLConnection connection = (HttpURLConnection) keyURL.openConnection();
                bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line;
                String result = "";
                while ((line = bufferedReader.readLine()) != null) {
                    result += line;
                }
                return result;
            } catch (Exception e) {
                LogUtils.w(TAG, "Request online key failed, exception = " + e);
                VideoDownloadUtils.close(inputStreamReader);
                VideoDownloadUtils.close(bufferedReader);
            } finally {
                VideoDownloadUtils.close(inputStreamReader);
                VideoDownloadUtils.close(bufferedReader);
            }
        } else {
            File keyFile = new File(keyPath);
            try {
                inputStreamReader = new InputStreamReader(new FileInputStream(keyFile));
                bufferedReader = new BufferedReader(inputStreamReader);
                String line;
                String result = "";
                while ((line = bufferedReader.readLine()) != null) {
                    result += line;
                }
                return result;
            } catch (Exception e) {
                LogUtils.w(TAG, "Read local key file failed, exception = " + e);
                VideoDownloadUtils.close(inputStreamReader);
                VideoDownloadUtils.close(bufferedReader);
            } finally {
                VideoDownloadUtils.close(inputStreamReader);
                VideoDownloadUtils.close(bufferedReader);
            }
        }
        return null;
    }

    public static byte[] str2Hex(String str) {
//        char[] chars = str.toCharArray();
//        StringBuilder sb = new StringBuilder("");
//        byte[] bs = str.getBytes();
//        int bit;
//        for (int i = 0; i < bs.length; i++) {
//            bit = (bs[i] & 0x0f0) >> 4;
//            sb.append(chars[bit]);
//            bit = bs[i] & 0x0f;
//            sb.append(chars[bit]);
//        }
        return "66623339613437616262666663613863".getBytes();
    }
}
