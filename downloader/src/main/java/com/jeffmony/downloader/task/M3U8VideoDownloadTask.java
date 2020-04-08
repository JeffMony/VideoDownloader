package com.jeffmony.downloader.task;

import com.jeffmony.downloader.VideoDownloadConfig;
import com.jeffmony.downloader.listener.IDownloadTaskListener;
import com.jeffmony.downloader.m3u8.M3U8;
import com.jeffmony.downloader.m3u8.M3U8Ts;
import com.jeffmony.downloader.model.VideoDownloadInfo;
import com.jeffmony.downloader.utils.LogUtils;

import java.util.HashMap;
import java.util.List;

public class M3U8VideoDownloadTask extends VideoDownloadTask {

    private static final String TAG = "M3U8VideoDownloadTask";

    private static final String TS_PREFIX = "video_";
    private final M3U8 mM3U8;
    private List<M3U8Ts> mTsList;
    private volatile int mCurTs;
    private int mTotalTs;
    private long mTotalSize;
    private long mDuration;
    private final Object mFileLock = new Object();

    public M3U8VideoDownloadTask(VideoDownloadConfig config,
                                 VideoDownloadInfo downloadInfo, M3U8 m3u8,
                                 HashMap<String, String> headers) {
        super(config, downloadInfo, headers);
        this.mM3U8 = m3u8;
        this.mTsList = m3u8.getTsList();
        this.mTotalTs = mTsList.size();
        this.mPercent = downloadInfo.getPercent();
        this.mCurTs = 0;
        this.mDuration = m3u8.getDuration();
        if (mDuration == 0) {
            mDuration = 1;
        }
        downloadInfo.setTotalTs(mTotalTs);
        downloadInfo.setCachedTs(mCurTs);
    }

    @Override
    public void startDownload(IDownloadTaskListener listener) {
        mDownloadTaskListener = listener;
        if (listener != null) {
            listener.onTaskStart(mDownloadInfo.getVideoUrl());
        }
        startDownload(mCurTs);
    }

    private void startDownload(int curDownloadTs) {
        if (mDownloadInfo.getIsCompleted()) {
            LogUtils.i(TAG, "M3U8VideoDownloadTask local file.");
            return;
        }
    }

    @Override
    public void resumeDownload() {

    }

    @Override
    public void pauseDownload() {

    }
}
