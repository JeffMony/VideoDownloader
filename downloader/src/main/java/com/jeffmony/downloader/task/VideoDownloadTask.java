package com.jeffmony.downloader.task;

import com.jeffmony.downloader.VideoDownloadConfig;
import com.jeffmony.downloader.listener.IDownloadTaskListener;
import com.jeffmony.downloader.model.VideoTaskItem;
import com.jeffmony.downloader.utils.VideoDownloadUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ThreadPoolExecutor;

public abstract class VideoDownloadTask {

    protected static final int THREAD_COUNT = 3;
    protected static final int BUFFER_SIZE =  VideoDownloadUtils.DEFAULT_BUFFER_SIZE;
    protected final VideoDownloadConfig mConfig;
    protected final VideoTaskItem mTaskItem;
    protected final String mFinalUrl;
    protected final HashMap<String, String> mHeaders;
    protected File mSaveDir;
    protected String mSaveName;
    protected ThreadPoolExecutor mDownloadExecutor;
    protected IDownloadTaskListener mDownloadTaskListener;
    protected Timer mTimer;
    protected long mOldCachedSize = 0L;
    protected long mCurrentCachedSize = 0L;
    protected float mPercent = 0.0f;
    protected float mSpeed = 0.0f;

    protected VideoDownloadTask(VideoDownloadConfig config,
                                VideoTaskItem taskItem,
                                HashMap<String, String> headers) {
        mConfig = config;
        mTaskItem = taskItem;
        mHeaders = headers;
        mFinalUrl = taskItem.getFinalUrl();
        mSaveName = VideoDownloadUtils.computeMD5(taskItem.getUrl());
        mSaveDir = new File(mConfig.getCacheRoot(), mSaveName);
        if (!mSaveDir.exists()) {
            mSaveDir.mkdir();
        }
        mTaskItem.setSaveDir(mSaveDir.getAbsolutePath());
    }

    public abstract void startDownload(IDownloadTaskListener listener);

    public abstract void resumeDownload();

    public abstract void pauseDownload();

    protected void startTimerTask() {
        if (mTimer == null) {
            mTimer = new Timer();
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    if (mOldCachedSize <= mCurrentCachedSize) {
                        float speed = (mCurrentCachedSize - mOldCachedSize) * 1.0f;
                        if (speed == 0f) {
                            mDownloadTaskListener.onTaskSpeedChanged(mSpeed / 2);
                        } else {
                            mDownloadTaskListener.onTaskSpeedChanged(speed);
                            mOldCachedSize = mCurrentCachedSize;
                            mSpeed = speed;
                        }
                    }
                }
            };
            mTimer.schedule(task, 0, VideoDownloadUtils.UPDATE_INTERVAL);
        }
    }

    protected void cancelTimer() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
    }

    protected void notifyOnTaskPaused() {
        if (mDownloadTaskListener != null) {
            mDownloadTaskListener.onTaskPaused();
            cancelTimer();
        }
    }

    protected void notifyOnTaskFailed(Exception e) {
        if (mDownloadExecutor != null && mDownloadExecutor.isShutdown()) {
            return;
        }
        if (mDownloadTaskListener != null) {
            mDownloadExecutor.shutdownNow();
            mDownloadTaskListener.onTaskFailed(e);
            cancelTimer();
        }
    }


    protected boolean isFloatEqual(float f1, float f2) {
        if (Math.abs(f1-f2) < 0.0001f) {
            return true;
        }
        return false;
    }
}
