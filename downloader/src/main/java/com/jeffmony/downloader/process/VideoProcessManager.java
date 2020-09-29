package com.jeffmony.downloader.process;

public class VideoProcessManager {

    public static VideoProcessManager sInstance = null;

    public static VideoProcessManager getInstance() {
        if (sInstance == null) {
            synchronized (VideoProcessManager.class) {
                if (sInstance == null) {
                    sInstance = new VideoProcessManager();
                }
            }
        }
        return sInstance;
    }

    public void remux(String inputPath, String outputPath, IM3U8MergeListener listener) {

    }

    public void printVideoInfo(String srcPath) {

    }

}
