package com.jeffmony.downloader.task;

import com.jeffmony.downloader.VideoDownloadConfig;
import com.jeffmony.downloader.VideoDownloadException;
import com.jeffmony.downloader.listener.IDownloadTaskListener;
import com.jeffmony.downloader.m3u8.M3U8;
import com.jeffmony.downloader.m3u8.M3U8Ts;
import com.jeffmony.downloader.m3u8.M3U8Utils;
import com.jeffmony.downloader.model.VideoTaskItem;
import com.jeffmony.downloader.utils.HttpUtils;
import com.jeffmony.downloader.utils.LogUtils;
import com.jeffmony.downloader.utils.VideoDownloadUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
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

import javax.net.ssl.HttpsURLConnection;

import static java.net.HttpURLConnection.HTTP_MOVED_PERM;
import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;
import static java.net.HttpURLConnection.HTTP_SEE_OTHER;

public class M3U8VideoDownloadTask extends VideoDownloadTask {

    private static final String TAG = "M3U8VideoDownloadTask";
    private static final int REDIRECTED_COUNT = 3;
    private final Object mFileLock = new Object();

    private static final String TS_PREFIX = "video_";
    private final M3U8 mM3U8;
    private List<M3U8Ts> mTsList;
    private volatile int mCurTs = 0;
    private int mTotalTs;
    private long mTotalSize;
    private long mDuration;

    public M3U8VideoDownloadTask(VideoDownloadConfig config,
                                 VideoTaskItem taskItem, M3U8 m3u8,
                                 HashMap<String, String> headers) {
        super(config, taskItem, headers);
        this.mM3U8 = m3u8;
        this.mTsList = m3u8.getTsList();
        this.mTotalTs = mTsList.size();
        this.mPercent = taskItem.getPercent();
        this.mDuration = m3u8.getDuration();
        if (mDuration == 0) {
            mDuration = 1;
        }
        taskItem.setTotalTs(mTotalTs);
        taskItem.setCurTs(mCurTs);
    }

    private void initM3U8Ts() {
        long tempCurrentCachedSize = 0;
        int tempCurTs = 0;
        for (M3U8Ts ts : mTsList) {
            File tempTsFile = new File(mSaveDir, ts.getIndexName());
            if (tempTsFile.exists() && tempTsFile.length() > 0) {
                ts.setTsSize(tempTsFile.length());
                tempCurTs++;
            } else {
                break;
            }
        }
        mCurTs = tempCurTs;
        mCurrentCachedSize = tempCurrentCachedSize;
    }


    @Override
    public void startDownload(IDownloadTaskListener listener) {
        mDownloadTaskListener = listener;
        if (listener != null) {
            listener.onTaskStart(mTaskItem.getUrl());
        }
        initM3U8Ts();
        startDownload(mCurTs);
    }

    private void startDownload(int curDownloadTs) {
        if (mTaskItem.isCompleted()) {
            LogUtils.i(TAG, "M3U8VideoDownloadTask local file.");
            notifyDownloadFinish();
            return;
        }
        startTimerTask();
        mCurTs = curDownloadTs;
        LogUtils.i(TAG,"startDownload curDownloadTs = " + curDownloadTs);
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
            downloadFile(ts.getUrl(), tsFile);
        }

        if (tsFile.exists()) {
            // rename network ts name to local file name.
            ts.setName(tsName);
            ts.setTsSize(tsFile.length());
            mCurTs++;
            notifyDownloadProgress();
        }
    }

    @Override
    public void resumeDownload() {
        initM3U8Ts();
        startDownload(mCurTs);
    }

    @Override
    public void pauseDownload() {
        if (mDownloadExecutor != null && !mDownloadExecutor.isShutdown()) {
            mDownloadExecutor.shutdownNow();
            notifyOnTaskPaused();
        }
    }

    private void notifyDownloadProgress() {
        if (mDownloadTaskListener != null) {
            initM3U8Ts();
            if (mCurrentCachedSize == 0) {
                mCurrentCachedSize = VideoDownloadUtils.countTotalSize(mSaveDir);
            }
            if (mTaskItem.isCompleted()) {
                mCurTs = mTotalTs;
                mDownloadTaskListener.onTaskProgress(100.0f,
                        mCurrentCachedSize, mCurrentCachedSize, mM3U8);
                mPercent = 100.0f;
                mTotalSize = mCurrentCachedSize;
                mDownloadTaskListener.onTaskFinished(mTotalSize);
                cancelTimer();
                return;
            }
            if (mCurTs >= mTotalTs - 1) {
                mCurTs = mTotalTs;
            }
            mTaskItem.setCurTs(mCurTs);
            mM3U8.setCurTsIndex(mCurTs);
            float percent = mCurTs * 1.0f * 100 / mTotalTs;
            if (!isFloatEqual(percent, mPercent)) {
                mDownloadTaskListener.onTaskProgress(percent,
                        mCurrentCachedSize, mCurrentCachedSize, mM3U8);
                mPercent = percent;
                mTaskItem.setPercent(percent);
                mTaskItem.setDownloadSize(mCurrentCachedSize);
            }
            boolean isCompleted = true;
            for (M3U8Ts ts : mTsList) {
                File tsFile = new File(mSaveDir, ts.getIndexName());
                if (!tsFile.exists()) {
                    isCompleted = false;
                    break;
                }
            }
            mTaskItem.setIsCompleted(isCompleted);
            if (isCompleted) {
                mTaskItem.setTotalSize(mCurrentCachedSize);
                mTotalSize = mCurrentCachedSize;
                mDownloadTaskListener.onTaskProgress(100.0f,
                        mCurrentCachedSize, mCurrentCachedSize, mM3U8);
                mDownloadTaskListener.onTaskFinished(mTotalSize);
                cancelTimer();
                try {
                    createLocalM3U8File();
                } catch (Exception e) {
                    notifyDownloadError(e);
                }
            }
        }
    }

    private void notifyDownloadFinish() {
        notifyDownloadProgress();
        notifyDownloadFinish(mTotalSize);
    }

    private void notifyDownloadFinish(long size) {
        if (mDownloadTaskListener != null) {
            if (mTaskItem.isCompleted()) {
                mDownloadTaskListener.onTaskFinished(size);
                cancelTimer();
            }
        }
    }

    private void notifyDownloadError(Exception e) {
        notifyOnTaskFailed(e);
    }

    public void downloadFile(String url, File file) throws Exception {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        try {
            connection = openConnection(url);
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpUtils.RESPONSE_OK) {
                inputStream = connection.getInputStream();
                saveFile(inputStream, file);
            }
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

    private void saveFile(InputStream inputStream, File file) throws IOException {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            int len = 0;
            byte[] buf = new byte[BUFFER_SIZE];
            while ((len = inputStream.read(buf)) != -1) {
                fos.write(buf, 0, len);
            }
        } catch (IOException e) {
            LogUtils.w(TAG,file.getAbsolutePath() +
                    " saveFile failed, exception=" + e);
            if (file.exists()) {
                file.delete();
            }
            throw e;
        } finally {
            VideoDownloadUtils.close(inputStream);
            VideoDownloadUtils.close(fos);
        }
    }

    private void createLocalM3U8File() throws IOException {
        synchronized (mFileLock) {
            File tempM3U8File = new File(mSaveDir, "temp.m3u8");
            if (tempM3U8File.exists()) {
                tempM3U8File.delete();
            }

            BufferedWriter bfw =
                    new BufferedWriter(new FileWriter(tempM3U8File, false));
            bfw.write(M3U8Utils.PLAYLIST_HEADER + "\n");
            bfw.write(M3U8Utils.TAG_VERSION + ":" + mM3U8.getVersion() + "\n");
            bfw.write(M3U8Utils.TAG_MEDIA_SEQUENCE + ":" + mM3U8.getSequence() +
                    "\n");

            bfw.write(M3U8Utils.TAG_TARGET_DURATION + ":" +
                    mM3U8.getTargetDuration() + "\n");

            for (M3U8Ts m3u8Ts : mTsList) {
                if (m3u8Ts.hasKey()) {
                    if (m3u8Ts.getMethod() != null) {
                        String key = "METHOD=" + m3u8Ts.getMethod();
                        if (m3u8Ts.getKeyUri() != null) {
                            File keyFile = new File(mSaveDir, m3u8Ts.getLocalKeyUri());
                            if (!m3u8Ts.isMessyKey() && keyFile.exists()) {
                                key += ",URI=\"" + m3u8Ts.getLocalKeyUri() + "\"";
                            } else {
                                key += ",URI=\"" + m3u8Ts.getKeyUri() + "\"";
                            }
                        }
                        if (m3u8Ts.getKeyIV() != null) {
                            key += ",IV=" + m3u8Ts.getKeyIV();
                        }
                        bfw.write(M3U8Utils.TAG_KEY + ":" + key + "\n");
                    }
                }
                if (m3u8Ts.hasDiscontinuity()) {
                    bfw.write(M3U8Utils.TAG_DISCONTINUITY + "\n");
                }
                bfw.write(M3U8Utils.TAG_MEDIA_DURATION + ":" + m3u8Ts.getDuration() +
                        ",\n");
                bfw.write(m3u8Ts.getIndexName());
                bfw.newLine();
            }
            bfw.write(M3U8Utils.TAG_ENDLIST);
            bfw.flush();
            bfw.close();

            File localM3U8File = new File(mSaveDir, VideoDownloadUtils.LOCAL_M3U8);
            if (localM3U8File.exists()) {
                localM3U8File.delete();
            }
            tempM3U8File.renameTo(localM3U8File);
        }
    }
}
