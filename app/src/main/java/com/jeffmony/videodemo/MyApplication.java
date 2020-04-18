package com.jeffmony.videodemo;

import android.app.Application;

import com.jeffmony.downloader.DownloadConstants;
import com.jeffmony.downloader.VideoDownloadConfig;
import com.jeffmony.downloader.VideoDownloadManager;
import com.jeffmony.downloader.utils.VideoDownloadUtils;

import java.io.File;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        File file = VideoDownloadUtils.getVideoCacheDir(this);
        if (!file.exists()) {
            file.mkdir();
        }
        VideoDownloadConfig config = new VideoDownloadManager.Build(this)
                .setCacheRoot(file)
                .setUrlRedirect(true)
                .setTimeOut(DownloadConstants.READ_TIMEOUT, DownloadConstants.CONN_TIMEOUT)
                .setConcurrentCount(DownloadConstants.CONCURRENT)
                .setIgnoreCertErrors(true)
                .buildConfig();
        VideoDownloadManager.getInstance().initConfig(config);
    }
}
