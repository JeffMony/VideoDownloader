package com.jeffmony.downloader.task;

import com.jeffmony.downloader.listener.IDownloadTaskListener;
import com.jeffmony.downloader.model.VideoTaskItem;
import com.jeffmony.downloader.utils.VideoDownloadUtils;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

public abstract class VideoDownloadTask {

    protected static final int THREAD_COUNT = 6;
    protected static final int BUFFER_SIZE = VideoDownloadUtils.DEFAULT_BUFFER_SIZE;
    protected final VideoTaskItem mTaskItem;
    protected final String mFinalUrl;
    protected Map<String, String> mHeaders;
    protected File mSaveDir;
    protected String mSaveName;
    protected ThreadPoolExecutor mDownloadExecutor;
    protected IDownloadTaskListener mDownloadTaskListener;
    protected volatile boolean mDownloadFinished = false;
    protected final Object mDownloadLock = new Object();
    protected long mLastCachedSize = 0L;
    protected long mCurrentCachedSize = 0L;
    protected long mLastInvokeTime = 0L;
    protected float mSpeed = 0.0f;
    protected float mPercent = 0.0f;

    protected VideoDownloadTask(VideoTaskItem taskItem, Map<String, String> headers) {
        mTaskItem = taskItem;
        mHeaders = headers;
        mFinalUrl = taskItem.getFinalUrl();
        mSaveName = VideoDownloadUtils.computeMD5(taskItem.getUrl());
        mSaveDir = new File(VideoDownloadUtils.getDownloadConfig().getCacheRoot(), mSaveName);
        if (!mSaveDir.exists()) {
            mSaveDir.mkdir();
        }
        mTaskItem.setSaveDir(mSaveDir.getAbsolutePath());
    }

    public void setDownloadTaskListener(IDownloadTaskListener listener) {
        mDownloadTaskListener = listener;
    }

    public abstract void startDownload();

    public abstract void resumeDownload();

    public abstract void pauseDownload();

    protected void notifyOnTaskPaused() {
        if (mDownloadTaskListener != null) {
            mDownloadTaskListener.onTaskPaused();
        }
    }

    protected void notifyOnTaskFailed(Exception e) {
        if (mDownloadExecutor != null && mDownloadExecutor.isShutdown()) {
            return;
        }
        mDownloadExecutor.shutdownNow();
        mDownloadTaskListener.onTaskFailed(e);
    }

    protected void setThreadPoolArgument(int corePoolSize, int maxPoolSize) {
        if (mDownloadExecutor != null && !mDownloadExecutor.isShutdown()) {
            mDownloadExecutor.setCorePoolSize(corePoolSize);
            mDownloadExecutor.setMaximumPoolSize(maxPoolSize);
        }
    }
}
