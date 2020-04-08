package com.jeffmony.downloader.task;

import com.jeffmony.downloader.VideoDownloadConfig;
import com.jeffmony.downloader.VideoDownloadException;
import com.jeffmony.downloader.listener.IDownloadTaskListener;
import com.jeffmony.downloader.m3u8.M3U8;
import com.jeffmony.downloader.m3u8.M3U8Ts;
import com.jeffmony.downloader.model.VideoDownloadInfo;
import com.jeffmony.downloader.utils.HttpUtils;
import com.jeffmony.downloader.utils.LogUtils;
import com.jeffmony.downloader.utils.VideoDownloadUtils;
import com.jeffmony.downloader.utils.WorkerThreadHandler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.HttpsURLConnection;

import static java.net.HttpURLConnection.HTTP_MOVED_PERM;
import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;
import static java.net.HttpURLConnection.HTTP_SEE_OTHER;

public class M3U8VideoDownloadTask extends VideoDownloadTask {

    private static final String TAG = "M3U8VideoDownloadTask";
    private static final int REDIRECTED_COUNT = 3;

    private static final String TS_PREFIX = "video_";
    private final M3U8 mM3U8;
    private List<M3U8Ts> mTsList;
    private AtomicInteger mCurTs = new AtomicInteger(0);
    private int mTotalTs;
    private long mTotalSize;
    private long mDuration;

    public M3U8VideoDownloadTask(VideoDownloadConfig config,
                                 VideoDownloadInfo downloadInfo, M3U8 m3u8,
                                 HashMap<String, String> headers) {
        super(config, downloadInfo, headers);
        this.mM3U8 = m3u8;
        this.mTsList = m3u8.getTsList();
        this.mTotalTs = mTsList.size();
        this.mPercent = downloadInfo.getPercent();
        this.mDuration = m3u8.getDuration();
        if (mDuration == 0) {
            mDuration = 1;
        }
        downloadInfo.setTotalTs(mTotalTs);
        downloadInfo.setCachedTs(mCurTs.get());
    }

    @Override
    public void startDownload(IDownloadTaskListener listener) {
        mDownloadTaskListener = listener;
        if (listener != null) {
            listener.onTaskStart(mDownloadInfo.getVideoUrl());
        }
        startDownload(mCurTs.get());
    }

    private void startDownload(int curDownloadTs) {
        if (mDownloadInfo.getIsCompleted()) {
            LogUtils.i(TAG, "M3U8VideoDownloadTask local file.");
            notifyDownloadFinish();
            return;
        }
        startTimerTask();
        mCurTs.getAndSet(curDownloadTs);
        LogUtils.i(TAG,"seekToDownload curDownloadTs = " + curDownloadTs);
        mDownloadExecutor = new ThreadPoolExecutor(
                THREAD_COUNT, THREAD_COUNT, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(), Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.DiscardOldestPolicy());
        for (int index = curDownloadTs; index < mTotalTs; index++) {
            if (mDownloadExecutor.isShutdown()) {
                break;
            }
            final M3U8Ts ts = mTsList.get(index);
            final String tsName = TS_PREFIX + index + ".ts";
            final File tsFile = new File(mSaveDir, tsName);
            mDownloadExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        LogUtils.w(TAG, "index="+ts.getIndexName() + ", totalTs="+mTotalTs);
                        downloadTsTask(ts, tsFile, tsName);
                    } catch (Exception e) {
                        LogUtils.w(TAG,"M3U8TsDownloadThread download failed, exception=" +
                                e);
                        notifyDownloadError(e);
                    }
                }
            });
        }

        notifyDownloadFinish(mCurrentCachedSize);
    }

    private void downloadTsTask(M3U8Ts ts, File tsFile, String tsName)
            throws Exception {
        if (!tsFile.exists()) {
            // ts is network resource, download ts file then rename it to local file.
            boolean status = downloadFile(ts.getUrl(), tsFile);
            if (status) {
                // rename network ts name to local file name.
                ts.setName(tsName);
                ts.setTsSize(tsFile.length());
                mCurTs.incrementAndGet();
                LogUtils.w(TAG, "# CurTs = " + mCurTs);
                notifyDownloadProgress();
            }
        } else {
            // rename network ts name to local file name.
            ts.setName(tsName);
            ts.setTsSize(tsFile.length());
            mCurTs.incrementAndGet();
            LogUtils.w(TAG, "## CurTs = " + mCurTs);
            notifyDownloadProgress();
        }
    }

    @Override
    public void resumeDownload() {
        startDownload(mCurTs.get());
    }

    @Override
    public void pauseDownload() {
        if (mDownloadExecutor != null && !mDownloadExecutor.isShutdown()) {
            mDownloadExecutor.shutdownNow();
            notifyOnTaskPaused();
        }
        updateDownloadInfo();
    }

    private void notifyDownloadProgress() {
        if (mDownloadTaskListener != null) {
            mCurrentCachedSize = 0;
            for (M3U8Ts ts : mTsList) {
                mCurrentCachedSize += ts.getTsSize();
            }
            if (mCurrentCachedSize == 0) {
                mCurrentCachedSize = VideoDownloadUtils.countTotalSize(mSaveDir);
            }
            if (mDownloadInfo.getIsCompleted()) {
                mCurTs.getAndSet(mTotalTs);
                mDownloadTaskListener.onTaskProgress(100.0f,
                        mCurrentCachedSize, mCurrentCachedSize, mM3U8);
                mPercent = 100.0f;
                mTotalSize = mCurrentCachedSize;
                mDownloadTaskListener.onTaskFinished(mTotalSize);
                return;
            }
            if (mCurTs.get() >= mTotalTs - 1) {
                mCurTs.getAndSet(mTotalTs);
            }
            mDownloadInfo.setCachedTs(mCurTs.get());
            mM3U8.setCurTsIndex(mCurTs.get());
            float percent = mCurTs.get() * 1.0f * 100 / mTotalTs;
            if (!isFloatEqual(percent, mPercent)) {
                mDownloadTaskListener.onTaskProgress(percent,
                        mCurrentCachedSize, mCurrentCachedSize, mM3U8);
                mPercent = percent;
                mDownloadInfo.setPercent(percent);
                mDownloadInfo.setCachedLength(mCurrentCachedSize);
            }
            boolean isCompleted = true;
            for (M3U8Ts ts : mTsList) {
                File tsFile = new File(mSaveDir, ts.getIndexName());
                if (!tsFile.exists()) {
                    isCompleted = false;
                    break;
                }
            }
            mDownloadInfo.setIsCompleted(isCompleted);
            if (isCompleted) {
                mDownloadInfo.setTotalLength(mCurrentCachedSize);
                mTotalSize = mCurrentCachedSize;
                mDownloadTaskListener.onTaskProgress(100.0f,
                        mCurrentCachedSize, mCurrentCachedSize, mM3U8);
                mDownloadTaskListener.onTaskFinished(mTotalSize);
                writeDownloadInfo();
            }
        }
    }

    private void notifyDownloadFinish() {
        notifyDownloadProgress();
        notifyDownloadFinish(mTotalSize);
    }

    private void notifyDownloadFinish(long size) {
        if (mDownloadTaskListener != null) {
            updateDownloadInfo();
            if (mDownloadInfo.getIsCompleted()) {
                mDownloadTaskListener.onTaskFinished(size);
            }
        }
    }

    private void notifyDownloadError(Exception e) {
        if (e instanceof InterruptedIOException) {
            if (e instanceof SocketTimeoutException) {
                LogUtils.w(TAG,"M3U8VideoDownloadTask notifyFailed: " + e);
                resumeDownload();
                return;
            }
            pauseDownload();
            writeDownloadInfo();
            return;
        } else if (e instanceof MalformedURLException) {
            String parsedString = "no protocol: ";
            if (e.toString().contains(parsedString)) {
                String fileName = e.toString().substring(
                        e.toString().indexOf(parsedString) + parsedString.length());
                LogUtils.w(TAG,fileName + " not existed.");
            }
            return;
        }
        if (mDownloadTaskListener != null) {
            mDownloadTaskListener.onTaskFailed(e);
        }
    }

    private void updateDownloadInfo() {
        boolean isCompleted = true;
        for (M3U8Ts ts : mTsList) {
            File tsFile = new File(mSaveDir, ts.getIndexName());
            if (!tsFile.exists()) {
                isCompleted = false;
                break;
            }
        }
        mDownloadInfo.setIsCompleted(isCompleted);
        if (isCompleted) {
            writeDownloadInfo();
        }
    }

    private void writeDownloadInfo() {
        WorkerThreadHandler.submitRunnableTask(new Runnable() {
            @Override
            public void run() {
                LogUtils.i(TAG, "writeProxyCacheInfo : " + mDownloadInfo);
                VideoDownloadUtils.writeDownloadInfo(mDownloadInfo, mSaveDir);
            }
        });
    }

    public boolean downloadFile(String url, File file) throws Exception {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        try {
            connection = openConnection(url);
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpUtils.RESPONSE_OK) {
                inputStream = connection.getInputStream();
                return saveFile(inputStream, file);
            }
            return false;
        } catch (Exception e) {
            throw e;
        } finally {
            if (connection != null)
                connection.disconnect();
            VideoDownloadUtils.close(inputStream);
        }
    }

    private HttpURLConnection openConnection(String videoUrl)
            throws IOException, VideoDownloadException {
        HttpURLConnection connection;
        boolean redirected;
        int redirectedCount = 0;
        do {
            URL url = new URL(videoUrl);
            connection = (HttpURLConnection)url.openConnection();
            if (mConfig.shouldIgnoreCertErrors() && connection instanceof
                    HttpsURLConnection) {
                HttpUtils.trustAllCert((HttpsURLConnection)(connection));
            }
            connection.setConnectTimeout(mConfig.getConnTimeOut());
            connection.setReadTimeout(mConfig.getReadTimeOut());
            if (mHeaders != null) {
                for (Map.Entry<String, String> item : mHeaders.entrySet()) {
                    connection.setRequestProperty(item.getKey(), item.getValue());
                }
            }
            int code = connection.getResponseCode();
            redirected = code == HTTP_MOVED_PERM || code == HTTP_MOVED_TEMP ||
                    code == HTTP_SEE_OTHER;
            if (redirected) {
                redirectedCount++;
                connection.disconnect();
            }
            if (redirectedCount > REDIRECTED_COUNT) {
                throw new VideoDownloadException("Too many redirects: " + redirectedCount);
            }
        } while (redirected);
        return connection;
    }

    private boolean saveFile(InputStream inputStream, File file) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            int len = 0;
            byte[] buf = new byte[BUFFER_SIZE];
            while ((len = inputStream.read(buf)) != -1) {
                fos.write(buf, 0, len);
            }
            return true;
        } catch (IOException e) {
            LogUtils.w(TAG,file.getAbsolutePath() +
                    " saveFile failed, exception=" + e);
            if (file.exists()) {
                file.delete();
            }
            return false;
        } finally {
            VideoDownloadUtils.close(inputStream);
            VideoDownloadUtils.close(fos);
        }
    }
}
