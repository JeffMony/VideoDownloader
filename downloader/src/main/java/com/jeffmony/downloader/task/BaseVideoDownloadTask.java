package com.jeffmony.downloader.task;

import com.jeffmony.downloader.VideoDownloadConfig;
import com.jeffmony.downloader.listener.IDownloadTaskListener;
import com.jeffmony.downloader.model.VideoDownloadInfo;
import com.jeffmony.downloader.utils.LogUtils;

import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class BaseVideoDownloadTask extends VideoDownloadTask {

    private static final String TAG = "BaseVideoDownloadTask";

    private volatile long mCurLength = 0;
    private long mTotalLength = -1L;

    public BaseVideoDownloadTask(VideoDownloadConfig config,
                                 VideoDownloadInfo downloadInfo,
                                 HashMap<String, String> headers) {
        super(config, downloadInfo, headers);
        this.mTotalLength = downloadInfo.getTotalLength();
    }

    @Override
    public void startDownload(IDownloadTaskListener listener) {
        mDownloadTaskListener = listener;
        startDownload(mCurLength);
    }

    private void startDownload(long curLength) {
        if (mDownloadInfo.getIsCompleted()) {
            LogUtils.i(TAG, "BaseVideoDownloadTask local file.");
            notifyDownloadProgress();
            return;
        }
        startTimerTask();
        mDownloadExecutor = new ThreadPoolExecutor(
                THREAD_COUNT, THREAD_COUNT, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(), Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.DiscardOldestPolicy());
        mDownloadExecutor.execute(new Runnable() {
            @Override
            public void run() {

            }
        });
    }

    @Override
    public void pauseDownload() {

    }

    @Override
    public void resumeDownload() {

    }

    private void notifyDownloadProgress() {

    }
}
