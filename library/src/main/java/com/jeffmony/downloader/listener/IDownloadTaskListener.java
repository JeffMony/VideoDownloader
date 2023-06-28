package com.jeffmony.downloader.listener;

public interface IDownloadTaskListener {

    void onTaskStart(String url);

    void onTaskProgress(float percent, long cachedSize, long totalSize, float speed);

    void onTaskProgressForM3U8(float percent, long cachedSize, int curTs, int totalTs, float speed);

    void onTaskPaused();

    void onTaskFinished(long totalSize);

    void onTaskFailed(Throwable e);

}
