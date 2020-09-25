package com.jeffmony.downloader.transcode;

public class FFmpegUtils {
    static {
        System.loadLibrary("jeffmpeg");
        System.loadLibrary("avcodec");
        System.loadLibrary("avfilter");
        System.loadLibrary("avformat");
        System.loadLibrary("avutil");
        System.loadLibrary("postproc");
        System.loadLibrary("swresample");
        System.loadLibrary("swscale");
    }

    public static native int remux(String inputPath, String outputPath);
}
