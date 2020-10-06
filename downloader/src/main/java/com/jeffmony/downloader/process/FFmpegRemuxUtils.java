package com.jeffmony.downloader.process;

public class FFmpegRemuxUtils {

    static {
        System.loadLibrary("avcodec");
        System.loadLibrary("avformat");
        System.loadLibrary("avutil");
        System.loadLibrary("jeffmony");
    }

    public static native int remux(String inputPath, String outputPath);

    public static native void printVideoInfo(String srcPath);
}
