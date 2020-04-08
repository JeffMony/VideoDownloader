package com.jeffmony.downloader.task;

import com.jeffmony.downloader.VideoDownloadConfig;
import com.jeffmony.downloader.listener.IDownloadTaskListener;
import com.jeffmony.downloader.model.VideoDownloadInfo;

import java.util.HashMap;

public class BaseVideoDownloadTask extends VideoDownloadTask {

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

    }

    @Override
    public void pauseDownload() {

    }

    @Override
    public void resumeDownload() {

    }
}
