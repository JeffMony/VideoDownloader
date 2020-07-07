package com.jeffmony.downloader.task;

import android.text.TextUtils;

import com.jeffmony.downloader.VideoDownloadConfig;
import com.jeffmony.downloader.VideoDownloadException;
import com.jeffmony.downloader.listener.IDownloadTaskListener;
import com.jeffmony.downloader.model.VideoTaskItem;
import com.jeffmony.downloader.utils.DownloadExceptionUtils;
import com.jeffmony.downloader.utils.HttpUtils;
import com.jeffmony.downloader.utils.LogUtils;
import com.jeffmony.downloader.utils.VideoDownloadUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

public class BaseVideoDownloadTask extends VideoDownloadTask {

    private static final String TAG = "BaseVideoDownloadTask";

    private long mTotalLength;

    public BaseVideoDownloadTask(VideoDownloadConfig config,
                                 VideoTaskItem taskItem,
                                 HashMap<String, String> headers) {
        super(config, taskItem, headers);
        mCurrentCachedSize = taskItem.getDownloadSize();
        mTotalLength = taskItem.getTotalSize();
    }

    @Override
    public void startDownload(IDownloadTaskListener listener) {
        mDownloadTaskListener = listener;
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
                if (mTotalLength == 0L) {
                    mTotalLength = getContentLength(mFinalUrl);
                    LogUtils.i(TAG, "file length = " + mTotalLength);
                    if (mTotalLength <= 0) {
                        LogUtils.w(TAG, "BaseVideoDownloadTask file length cannot be fetched.");
                        notifyDownloadError(new VideoDownloadException(DownloadExceptionUtils.FILE_LENGTH_FETCHED_ERROR_STRING));
                        return;
                    }
                    mTaskItem.setTotalSize(mTotalLength);
                }
                File videoFile;
                try {
                    videoFile = new File(mSaveDir, mSaveName + VideoDownloadUtils.VIDEO_SUFFIX);
                    if (!videoFile.exists()) {
                        videoFile.createNewFile();
                    }
                } catch (Exception e) {
                    LogUtils.w(TAG,"BaseDownloadTask createNewFile failed, exception=" +
                            e.getMessage());
                    return;
                }

                InputStream inputStream = null;
                RandomAccessFile randomAccessFile = null;
                try {
                    inputStream = getResponseBody(mFinalUrl, mCurrentCachedSize, mTotalLength);
                    byte[] buf = new byte[BUFFER_SIZE];
                    // Read http stream body.

                    randomAccessFile =
                            new RandomAccessFile(videoFile.getAbsolutePath(), "rw");
                    randomAccessFile.seek(mCurrentCachedSize);
                    int readLength = 0;
                    while ((readLength = inputStream.read(buf)) != -1) {
                        if (mCurrentCachedSize + readLength > mTotalLength) {
                            randomAccessFile.write(buf, 0, (int)(mTotalLength - mCurrentCachedSize));
                            mCurrentCachedSize = mTotalLength;
                        } else {
                            randomAccessFile.write(buf, 0, readLength);
                            mCurrentCachedSize += readLength;
                        }
                        LogUtils.i(TAG, "mCurrentCachedSize="+mCurrentCachedSize);
                        notifyDownloadProgress();
                    }
                } catch (Exception e) {
                    LogUtils.w(TAG, "FAILED, exception="+e.getMessage());
                    e.printStackTrace();
                    notifyDownloadError(e);
                } finally {
                    try {
                        if (inputStream != null) {
                            inputStream.close();
                        }
                        if (randomAccessFile != null) {
                            randomAccessFile.close();
                        }
                    } catch (IOException e) {
                        LogUtils.w(TAG,"Close stream failed, exception: " +
                                e.getMessage());
                    }
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
        if (mDownloadTaskListener != null) {
            if (mCurrentCachedSize >= mTotalLength) {
                mTaskItem.setDownloadSize(mCurrentCachedSize);
                mTaskItem.setIsCompleted(true);
                mDownloadTaskListener.onTaskProgress(100,
                        mTotalLength, mTotalLength, null);
                mPercent = 100.0f;
                notifyDownloadFinish();
            } else {
                mTaskItem.setDownloadSize(mCurrentCachedSize);
                float percent = mCurrentCachedSize * 1.0f * 100 / mTotalLength;
                if (!isFloatEqual(percent, mPercent)) {
                    mDownloadTaskListener.onTaskProgress(percent,
                            mCurrentCachedSize, mTotalLength, null);
                    mPercent = percent;
                }
            }
        }
    }

    private void notifyDownloadError(Exception e) {
        notifyOnTaskFailed(e);
    }

    private void notifyDownloadFinish() {
        if (mDownloadTaskListener != null) {
            mDownloadTaskListener.onTaskFinished(mTotalLength);
            cancelTimer();
        }
    }

    private InputStream getResponseBody(String url, long start, long end)
            throws IOException {
        HttpURLConnection connection = openConnection(url);
        if (end == mTotalLength) {
            connection.setRequestProperty("Range", "bytes=" + start + "-");
        } else {
            connection.setRequestProperty("Range", "bytes=" + start + "-" + end);
        }
        return connection.getInputStream();
    }

    private long getContentLength(String videoUrl) {
        long length = 0;
        HttpURLConnection connection = null;
        try {
            connection = openConnection(videoUrl);
            String contentLength = connection.getHeaderField("content-length");
            if (TextUtils.isEmpty(contentLength)) {
                return -1;
            } else {
                length = Long.parseLong(contentLength);
            }
        } catch (Exception e) {
            LogUtils.w(TAG, "BaseDownloadTask failed, exception=" + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
                connection = null;
            }
        }
        return length;
    }

    private HttpURLConnection openConnection(String videoUrl) throws IOException {
        HttpURLConnection connection;
        URL url = new URL(videoUrl);
        connection = (HttpURLConnection)url.openConnection();
        if (mConfig.shouldIgnoreCertErrors() && connection instanceof
                HttpsURLConnection) {
            HttpUtils.trustAllCert((HttpsURLConnection)(connection));
        }
        connection.setConnectTimeout(mConfig.getReadTimeOut());
        connection.setReadTimeout(mConfig.getConnTimeOut());
        if (mHeaders != null) {
            for (Map.Entry<String, String> item : mHeaders.entrySet()) {
                connection.setRequestProperty(item.getKey(), item.getValue());
            }
        }
        return connection;
    }
}
