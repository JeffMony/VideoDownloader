package com.jeffmony.downloader.task;

import android.os.Handler;
import android.os.HandlerThread;

import com.jeffmony.downloader.listener.IVideoCacheListener;
import com.jeffmony.downloader.model.VideoRange;
import com.jeffmony.downloader.model.VideoTaskItem;
import com.jeffmony.downloader.utils.LogUtils;
import com.jeffmony.downloader.utils.VideoDownloadUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MultiSegVideoDownloadTask extends VideoDownloadTask {

    private static final String TAG = "MultiSegVideoDownloadTask";

    private HandlerThread mMultiMsgThread;
    private Handler mMultiMsgHandler;

    private final long mTotalLength;

    private final int mThreadCount;

    public MultiSegVideoDownloadTask(VideoTaskItem taskItem, Map<String, String> headers) {
        super(taskItem, headers);
        if (mHeaders == null) {
            mHeaders = new HashMap<>();
        }
        mCurrentCachedSize = taskItem.getDownloadSize();
        mTotalLength = taskItem.getTotalSize();
        mThreadCount = VideoDownloadUtils.getDownloadConfig().getConcurrentCount();

        mMultiMsgThread = new HandlerThread("Multi-thread download");
        mMultiMsgThread.start();
        mMultiMsgHandler = new Handler(mMultiMsgThread.getLooper());
    }

    @Override
    public void startDownload() {
        mDownloadTaskListener.onTaskStart(mTaskItem.getUrl());
        startDownload(mCurrentCachedSize);
    }

    private void startDownload(long curLength) {
        if (mTaskItem.isCompleted()) {
            LogUtils.i(TAG, "BaseVideoDownloadTask local file.");
            notifyDownloadFinish();
            return;
        }
        mCurrentCachedSize = curLength;
        mDownloadExecutor = new ThreadPoolExecutor(
                mThreadCount + 1, mThreadCount + 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.DiscardOldestPolicy());

        long segSize = mTotalLength / mThreadCount;
        /// 三个线程，每个线程下载segSize大小

        Map<Integer, Long> cachedMap = new HashMap<>();

        Map<Integer, Boolean> completedMap = new HashMap<>();

        for (int i = 0; i < mThreadCount; i++) {
            long requestStart = segSize * i;
            long requestEnd = segSize * (i + 1) - 1;
            if (i == mThreadCount - 1) {
                requestEnd = mTotalLength;
            }

            cachedMap.put(i, 0L);
            completedMap.put(i, false);

            VideoRange range = new VideoRange(requestStart, requestEnd);
            SingleVideoCacheThread thread = new SingleVideoCacheThread(mFinalUrl, mHeaders, range, mTotalLength, mSaveDir.getAbsolutePath());

            thread.setHandler(mMultiMsgHandler);

            thread.setId(i);

            thread.setCacheListener(new IVideoCacheListener() {
                @Override
                public void onFailed(VideoRange range, int id, Exception e) {
                    notifyDownloadError(e);
                }

                @Override
                public void onProgress(VideoRange range, int id, long cachedSize) {
                    LogUtils.i(TAG, "onProgress ID="+id+", size=" + (range.getStart() + cachedSize));
                    cachedMap.put(id, cachedSize);
                    notifyOnProgress(cachedMap);
                }

                @Override
                public void onRangeCompleted(VideoRange range, int id) {
                    LogUtils.i(TAG, "onRangeCompleted Range=" + range);
                    completedMap.put(id, true);

                    boolean completed = true;
                    for (boolean tag : completedMap.values()) {
                        if (!tag) {
                            completed = false;
                            break;
                        }
                    }

                    if (completed) {
                        LogUtils.i(TAG, "TotalSize=" + mTotalLength);
                        notifyDownloadFinish();
                    }
                }

                @Override
                public void onCompleted(VideoRange range, int id) {

                }
            });

            mDownloadExecutor.execute(thread);
        }
    }

    @Override
    public void pauseDownload() {
        if (mDownloadExecutor != null && !mDownloadExecutor.isShutdown()) {
            mDownloadExecutor.shutdownNow();
            notifyOnTaskPaused();
        }
    }

    @Override
    public void resumeDownload() {
        startDownload(mCurrentCachedSize);
    }

    private void notifyOnProgress(Map<Integer, Long> cachedMap) {
        long currentSize = 0;
        for (long size : cachedMap.values()) {
            currentSize += size;
        }
        mCurrentCachedSize = currentSize;
        if (mCurrentCachedSize >= mTotalLength ) {
            mDownloadTaskListener.onTaskProgress(100, mTotalLength, mTotalLength, mSpeed);
            mPercent = 100.0f;
            notifyDownloadFinish();
        } else {
            float percent = mCurrentCachedSize * 1.0f * 100 / mTotalLength;
            if (!VideoDownloadUtils.isFloatEqual(percent, mPercent)) {
                long nowTime = System.currentTimeMillis();
                if (mCurrentCachedSize > mLastCachedSize && nowTime > mLastInvokeTime) {
                    mSpeed = (mCurrentCachedSize - mLastCachedSize) * 1000 * 1.0f / (nowTime - mLastInvokeTime);
                }
                mDownloadTaskListener.onTaskProgress(percent, mCurrentCachedSize, mTotalLength, mSpeed);
                mPercent = percent;
                mLastInvokeTime = nowTime;
                mLastCachedSize = mCurrentCachedSize;
            }
        }
    }

    private void notifyDownloadError(Exception e) {
        notifyOnTaskFailed(e);
    }

    private void notifyDownloadFinish() {
        synchronized (mDownloadLock) {
            if (!mDownloadFinished) {
                mDownloadTaskListener.onTaskFinished(mTotalLength);
                mDownloadFinished = true;
            }
        }
    }
}
