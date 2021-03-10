package com.jeffmony.downloader.task;

import com.jeffmony.downloader.model.VideoTaskItem;
import com.jeffmony.downloader.utils.HttpUtils;
import com.jeffmony.downloader.utils.LogUtils;
import com.jeffmony.downloader.utils.VideoDownloadUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class BaseVideoDownloadTask extends VideoDownloadTask {

    private static final String TAG = "BaseVideoDownloadTask";

    private long mTotalLength;

    public BaseVideoDownloadTask(VideoTaskItem taskItem, Map<String, String> headers) {
        super(taskItem, headers);
        mCurrentCachedSize = taskItem.getDownloadSize();
        mTotalLength = taskItem.getTotalSize();
    }

    @Override
    public void startDownload() {
        startDownload(mCurrentCachedSize);
    }

    private void startDownload(long curLength) {
        if (mTaskItem.isCompleted()) {
            LogUtils.i(TAG, "BaseVideoDownloadTask local file.");
            notifyDownloadFinish();
            return;
        }
        mCurrentCachedSize = curLength;
        startTimerTask();
        mDownloadExecutor = new ThreadPoolExecutor(
                THREAD_COUNT, THREAD_COUNT, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(), Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.DiscardOldestPolicy());
        mDownloadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                File videoFile;
                try {
                    videoFile = new File(mSaveDir, mSaveName + VideoDownloadUtils.VIDEO_SUFFIX);
                    if (!videoFile.exists()) {
                        videoFile.createNewFile();
                    }
                } catch (Exception e) {
                    LogUtils.w(TAG, "BaseDownloadTask createNewFile failed, exception=" + e.getMessage());
                    return;
                }

                InputStream inputStream = null;
                RandomAccessFile randomAccessFile = null;
                try {
                    inputStream = getResponseBody(mFinalUrl, mCurrentCachedSize, mTotalLength);
                    byte[] buf = new byte[BUFFER_SIZE];
                    // Read http stream body.

                    randomAccessFile = new RandomAccessFile(videoFile.getAbsolutePath(), "rw");
                    randomAccessFile.seek(mCurrentCachedSize);
                    int readLength = 0;
                    while ((readLength = inputStream.read(buf)) != -1) {
                        if (mCurrentCachedSize + readLength > mTotalLength) {
                            randomAccessFile.write(buf, 0, (int) (mTotalLength - mCurrentCachedSize));
                            mCurrentCachedSize = mTotalLength;
                        } else {
                            randomAccessFile.write(buf, 0, readLength);
                            mCurrentCachedSize += readLength;
                        }
                        LogUtils.i(TAG, "mCurrentCachedSize=" + mCurrentCachedSize);
                        notifyDownloadProgress();
                    }
                } catch (Exception e) {
                    LogUtils.w(TAG, "FAILED, exception=" + e.getMessage());
                    e.printStackTrace();
                    notifyDownloadError(e);
                } finally {
                    VideoDownloadUtils.close(inputStream);
                    VideoDownloadUtils.close(randomAccessFile);
                }
            }
        });
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

    private void notifyDownloadProgress() {
        if (mCurrentCachedSize >= mTotalLength) {
            mTaskItem.setDownloadSize(mCurrentCachedSize);
            mTaskItem.setIsCompleted(true);
            mDownloadTaskListener.onTaskProgress(100, mTotalLength, mTotalLength, null);
            mPercent = 100.0f;
            notifyDownloadFinish();
        } else {
            mTaskItem.setDownloadSize(mCurrentCachedSize);
            float percent = mCurrentCachedSize * 1.0f * 100 / mTotalLength;
            if (!VideoDownloadUtils.isFloatEqual(percent, mPercent)) {
                mDownloadTaskListener.onTaskProgress(percent, mCurrentCachedSize, mTotalLength, null);
                mPercent = percent;
            }
        }
    }

    private void notifyDownloadError(Exception e) {
        notifyOnTaskFailed(e);
    }

    private void notifyDownloadFinish() {
        mDownloadTaskListener.onTaskFinished(mTotalLength);
        cancelTimer();
    }

    private InputStream getResponseBody(String url, long start, long end) throws IOException {
        if (end == mTotalLength) {
            mHeaders.put("Range", "bytes=" + start + "-");
        } else {
            mHeaders.put("Range", "bytes=" + start + "-" + end);
        }
        HttpURLConnection connection = HttpUtils.getConnection(url, mHeaders, VideoDownloadUtils.getDownloadConfig().shouldIgnoreCertErrors());
        return connection.getInputStream();
    }

}
