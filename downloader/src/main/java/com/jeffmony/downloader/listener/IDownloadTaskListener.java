package com.jeffmony.downloader.listener;

import com.jeffmony.downloader.m3u8.M3U8;

public interface IDownloadTaskListener {

    void onTaskStart(String url);

    void onTaskProgress(float percent, long cachedSize, long totalSize, M3U8 m3u8);

    void onTaskSpeedChanged(float speed);

    void onTaskPaused();

    void onTaskFinished(long totalSize);

    void onTaskFailed(Throwable e);

}
