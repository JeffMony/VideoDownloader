package com.jeffmony.downloader.utils;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;

public class VideoStorageUtils {

    public static File getVideoCacheDir(Context context) {
        return new File(context.getExternalFilesDir("Video"), "Download");
    }

    public static void clearVideoCacheDir() throws IOException {
        if (ContextUtils.getApplicationContext() != null) {
            File videoCacheDir = getVideoCacheDir(ContextUtils.getApplicationContext());
            cleanDirectory(videoCacheDir);
        }
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

    public static String getSizeStr(long size) {
        StringBuffer sb = new StringBuffer();
        DecimalFormat format = new DecimalFormat("###.00");
        if (size >= 1024 * 1024 * 1024) {
            double i = (size / (1024.0 * 1024.0 * 1024.0));
            sb.append(format.format(i)).append("GB");
        } else if (size >= 1024 * 1024) {
            double i = (size / (1024.0 * 1024.0));
            sb.append(format.format(i)).append("MB");
        } else if (size >= 1024) {
            double i = (size / (1024.0));
            sb.append(format.format(i)).append("KB");
        } else if (size < 1024) {
            if (size <= 0) {
                sb.append("0B");
            } else {
                sb.append((int) size).append("B");
            }
        }
        return sb.toString();
    }
}
