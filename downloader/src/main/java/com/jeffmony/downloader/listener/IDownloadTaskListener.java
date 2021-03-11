package com.jeffmony.downloader.listener;

import com.jeffmony.downloader.m3u8.M3U8;

public interface IDownloadTaskListener {

    void onTaskStart(String url);

    void onTaskProgress(float percent, long cachedSize, long totalSize, float speed);

    void onTaskProgressForM3U8(float percent, long cachedSize, float speed, M3U8 m3u8);

    void onTaskPaused();

    void onTaskFinished(long totalSize);

    void onTaskFailed(Throwable e);

}
