package com.jeffmony.downloader.task;

import android.os.Handler;

import com.jeffmony.downloader.common.DownloadConstants;
import com.jeffmony.downloader.listener.IVideoCacheListener;
import com.jeffmony.downloader.model.VideoRange;
import com.jeffmony.downloader.utils.HttpUtils;
import com.jeffmony.downloader.utils.LogUtils;
import com.jeffmony.downloader.utils.VideoDownloadUtils;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

public class SingleVideoCacheThread implements Runnable {

    private final VideoRange mRange;
    private final String mUrl;
    private final Map<String, String> mHeaders;
    private final long mTotalSize;
    private final File mSaveDir;
    private final String mMd5;
    private IVideoCacheListener mListener;
    private boolean mIsRunning = true;
    private Handler mMsgHandler;
    private int mId;

    public SingleVideoCacheThread(String url, Map<String, String> headers, VideoRange range, long totalSize, String saveDir) {
        mUrl = url;
        if (headers == null) {
            headers = new HashMap<>();
        }
        mHeaders = headers;
        mRange = range;
        mTotalSize = totalSize;
        mMd5 = VideoDownloadUtils.computeMD5(url);
        mSaveDir = new File(saveDir);
        if (!mSaveDir.exists()) {
            mSaveDir.mkdir();
        }
    }

    public void setId(int id) {
        mId = id;
    }

    public void setHandler(Handler handler) {
        mMsgHandler = handler;
    }

    public void setCacheListener(IVideoCacheListener listener) {
        mListener = listener;
    }

    public boolean isRunning() {
        return mIsRunning;
    }

    public void pause() {
        LogUtils.i(DownloadConstants.TAG, "Pause task");
        mIsRunning = false;
    }

    @Override
    public void run() {
        if (!mIsRunning) {
            return;
        }
        downloadVideo();
    }

    private void downloadVideo() {
        File videoFile;
        try {
            videoFile = new File(mSaveDir, mMd5 + VideoDownloadUtils.VIDEO_SUFFIX);
            if (!videoFile.exists()) {
                videoFile.createNewFile();
            }
        } catch (Exception e) {
            notifyOnFailed(e);
            return;
        }

        long requestStart = mRange.getStart();
        long requestEnd = mRange.getEnd();
        if (requestStart - 10 > 0) {
            requestStart = requestStart - 10;
        }
        if (requestEnd + 10 < mTotalSize) {
            requestEnd = requestEnd + 10;
        }
        long rangeGap = requestEnd - requestStart;
        mHeaders.put("Range", "bytes=" + requestStart + "-" + requestEnd);
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        RandomAccessFile randomAccessFile = null;

        try {
            randomAccessFile = new RandomAccessFile(videoFile.getAbsoluteFile(), "rw");
            randomAccessFile.seek(requestStart);
            long cachedSize = 0;
            LogUtils.i(DownloadConstants.TAG, "Request range = " + mRange);
            connection = HttpUtils.getConnection(mUrl, mHeaders, VideoDownloadUtils.getDownloadConfig().shouldIgnoreCertErrors());
            inputStream = connection.getInputStream();
            LogUtils.i(DownloadConstants.TAG, "Receive response");

            byte[] buffer = new byte[VideoDownloadUtils.DEFAULT_BUFFER_SIZE];
            int readLength;
            while (mIsRunning && (readLength = inputStream.read(buffer)) != -1) {
                if (cachedSize + readLength > rangeGap) {
                    long read = rangeGap - cachedSize;
                    randomAccessFile.write(buffer, 0, (int)read);
                    cachedSize = rangeGap;
                } else {
                    randomAccessFile.write(buffer, 0, readLength);
                    cachedSize += readLength;
                }

                notifyOnProgress(cachedSize);

                if (cachedSize >= rangeGap) {
                    LogUtils.i(DownloadConstants.TAG, "Exceed cachedSize=" + cachedSize +", Range[start=" + requestStart +", end="+requestEnd+"]");
                    notifyOnRangeCompleted();
                    break;
                }
            }

            mIsRunning = false;
        } catch (Exception e) {
            notifyOnFailed(e);
        } finally {
            mIsRunning = false;
            VideoDownloadUtils.close(inputStream);
            VideoDownloadUtils.close(randomAccessFile);
            HttpUtils.closeConnection(connection);
        }
    }

    private void notifyOnFailed(Exception e) {
        mMsgHandler.post(() -> mListener.onFailed(mRange, mId, e));
    }

    private void notifyOnProgress(long cachedSize) {
        mMsgHandler.post(() -> mListener.onProgress(mRange, mId, cachedSize));
    }

    private void notifyOnRangeCompleted() {
        mMsgHandler.post(() -> mListener.onRangeCompleted(mRange, mId));
    }
}
