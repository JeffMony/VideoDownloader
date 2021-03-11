package com.jeffmony.downloader.task;

import com.jeffmony.downloader.m3u8.M3U8;
import com.jeffmony.downloader.m3u8.M3U8Constants;
import com.jeffmony.downloader.m3u8.M3U8Ts;
import com.jeffmony.downloader.model.VideoTaskItem;
import com.jeffmony.downloader.utils.HttpUtils;
import com.jeffmony.downloader.utils.LogUtils;
import com.jeffmony.downloader.utils.VideoDownloadUtils;
import com.jeffmony.downloader.utils.VideoStorageUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class M3U8VideoDownloadTask extends VideoDownloadTask {

    private static final String TAG = "M3U8VideoDownloadTask";
    private final Object mFileLock = new Object();

    private static final String TS_PREFIX = "video_";
    private final M3U8 mM3U8;
    private List<M3U8Ts> mTsList;
    private volatile int mCurTs = 0;
    private int mTotalTs;
    private long mTotalSize;
    private long mDuration;

    public M3U8VideoDownloadTask(VideoTaskItem taskItem, M3U8 m3u8, Map<String, String> headers) {
        super(taskItem, headers);
        this.mM3U8 = m3u8;
        this.mTsList = m3u8.getTsList();
        this.mTotalTs = mTsList.size();
        this.mPercent = taskItem.getPercent();
        this.mDuration = m3u8.getDuration();
        if (mDuration == 0) {
            mDuration = 1;
        }
        if (mHeaders == null) {
            mHeaders = new HashMap<>();
        }
        mHeaders.put("Connection", "close");
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
    public void startDownload() {
        mDownloadTaskListener.onTaskStart(mTaskItem.getUrl());
        initM3U8Ts();
        startDownload(mCurTs);
    }

    private void startDownload(int curDownloadTs) {
        if (mTaskItem.isCompleted()) {
            LogUtils.i(TAG, "M3U8VideoDownloadTask local file.");
            notifyDownloadFinish();
            return;
        }
        mCurTs = curDownloadTs;
        LogUtils.i(TAG, "startDownload curDownloadTs = " + curDownloadTs);
        mDownloadExecutor = new ThreadPoolExecutor(
                THREAD_COUNT, THREAD_COUNT, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.DiscardOldestPolicy());
        for (int index = curDownloadTs; index < mTotalTs; index++) {
            if (mDownloadExecutor.isShutdown()) {
                break;
            }
            final M3U8Ts ts = mTsList.get(index);
            final String tsName = TS_PREFIX + index + ".ts";
            final File tsFile = new File(mSaveDir, tsName);
            mDownloadExecutor.execute(() -> {
                try {
                    downloadTsTask(ts, tsFile, tsName);
                } catch (Exception e) {
                    LogUtils.w(TAG, "M3U8TsDownloadThread download failed, exception=" + e);
                    notifyDownloadError(e);
                }
            });
        }

        notifyDownloadFinish(mCurrentCachedSize);
    }

    private void downloadTsTask(M3U8Ts ts, File tsFile, String tsName) throws Exception {
        if (!tsFile.exists()) {
            // ts is network resource, download ts file then rename it to local file.
            downloadFile(ts.getUrl(), tsFile);
        }

        if (tsFile.exists()) {
            // rename network ts name to local file name.
            ts.setName(tsName);
            ts.setTsSize(tsFile.length());
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
        updateM3U8TsInfo();
        if (mCurrentCachedSize == 0) {
            mCurrentCachedSize = VideoStorageUtils.countTotalSize(mSaveDir);
        }
        if (mTaskItem.isCompleted()) {
            mCurTs = mTotalTs;
            mDownloadTaskListener.onTaskProgressForM3U8(100.0f, mCurrentCachedSize, mCurTs, mTotalTs, mSpeed);
            mPercent = 100.0f;
            mTotalSize = mCurrentCachedSize;
            mDownloadTaskListener.onTaskFinished(mTotalSize);
            return;
        }
        if (mCurTs >= mTotalTs) {
            mCurTs = mTotalTs;
        }
        float percent = mCurTs * 1.0f * 100 / mTotalTs;
        if (!VideoDownloadUtils.isFloatEqual(percent, mPercent)) {
            long nowTime = System.currentTimeMillis();
            if (mCurrentCachedSize > mLastCachedSize && nowTime > mLastInvokeTime) {
                mSpeed = (mCurrentCachedSize - mLastCachedSize) * 1000 * 1.0f / (nowTime - mLastInvokeTime);
            }
            mDownloadTaskListener.onTaskProgressForM3U8(percent, mCurrentCachedSize, mCurTs, mTotalTs, mSpeed);
            mPercent = percent;
            mLastCachedSize = mCurrentCachedSize;
            mLastInvokeTime = nowTime;
        }
        boolean isCompleted = true;
        for (M3U8Ts ts : mTsList) {
            File tsFile = new File(mSaveDir, ts.getIndexName());
            if (!tsFile.exists()) {
                isCompleted = false;
                break;
            }
        }
        if (isCompleted) {
            try {
                createLocalM3U8File();
            } catch (Exception e) {
                notifyDownloadError(e);
            }
            mTotalSize = mCurrentCachedSize;
            mDownloadTaskListener.onTaskProgressForM3U8(100.0f, mCurrentCachedSize, mCurTs, mTotalTs, mSpeed);
            mDownloadTaskListener.onTaskFinished(mTotalSize);
        }
    }

    private void updateM3U8TsInfo() {
        long tempCurrentCachedSize = 0;
        int tempCurTs = 0;
        for (M3U8Ts ts : mTsList) {
            File tempTsFile = new File(mSaveDir, ts.getIndexName());
            if (tempTsFile.exists() && tempTsFile.length() > 0) {
                ts.setTsSize(tempTsFile.length());
                tempCurTs++;
            }
        }
        mCurTs = tempCurTs;
        mCurrentCachedSize = tempCurrentCachedSize;
    }



    private void notifyDownloadFinish() {
        notifyDownloadProgress();
        notifyDownloadFinish(mTotalSize);
    }

    private void notifyDownloadFinish(long size) {
        if (mTaskItem.isCompleted()) {
            mDownloadTaskListener.onTaskFinished(size);
        }
    }

    private void notifyDownloadError(Exception e) {
        notifyOnTaskFailed(e);
    }

    public void downloadFile(String url, File file) throws Exception {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        try {
            connection = HttpUtils.getConnection(url, mHeaders, VideoDownloadUtils.getDownloadConfig().shouldIgnoreCertErrors());
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpUtils.RESPONSE_200 || responseCode == HttpUtils.RESPONSE_206) {
                inputStream = connection.getInputStream();
                saveFile(inputStream, file);
            }
        } catch (Exception e) {
            throw e;
        } finally {
            HttpUtils.closeConnection(connection);
            VideoDownloadUtils.close(inputStream);
        }
    }

    private void saveFile(InputStream inputStream, File file) throws IOException {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            int len;
            byte[] buf = new byte[BUFFER_SIZE];
            while ((len = inputStream.read(buf)) != -1) {
                fos.write(buf, 0, len);
            }
        } catch (IOException e) {
            LogUtils.w(TAG, file.getAbsolutePath() + ", length="+file.length() +   ", saveFile failed, exception=" + e);
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

            BufferedWriter bfw = new BufferedWriter(new FileWriter(tempM3U8File, false));
            bfw.write(M3U8Constants.PLAYLIST_HEADER + "\n");
            bfw.write(M3U8Constants.TAG_VERSION + ":" + mM3U8.getVersion() + "\n");
            bfw.write(M3U8Constants.TAG_MEDIA_SEQUENCE + ":" + mM3U8.getSequence() + "\n");

            bfw.write(M3U8Constants.TAG_TARGET_DURATION + ":" + mM3U8.getTargetDuration() + "\n");

            for (M3U8Ts m3u8Ts : mTsList) {
                if (m3u8Ts.hasKey()) {
                    if (m3u8Ts.getMethod() != null) {
                        String key = "METHOD=" + m3u8Ts.getMethod();
                        if (m3u8Ts.getKeyUri() != null) {
                            File keyFile = new File(mSaveDir, m3u8Ts.getLocalKeyUri());
                            if (!m3u8Ts.isMessyKey() && keyFile.exists()) {
                                key += ",URI=\"" + keyFile.getAbsolutePath() + "\"";
                            } else {
                                key += ",URI=\"" + m3u8Ts.getKeyUri() + "\"";
                            }
                        }
                        if (m3u8Ts.getKeyIV() != null) {
                            key += ",IV=" + m3u8Ts.getKeyIV();
                        }
                        bfw.write(M3U8Constants.TAG_KEY + ":" + key + "\n");
                    }
                }
                if (m3u8Ts.hasDiscontinuity()) {
                    bfw.write(M3U8Constants.TAG_DISCONTINUITY + "\n");
                }
                bfw.write(M3U8Constants.TAG_MEDIA_DURATION + ":" + m3u8Ts.getDuration() + ",\n");
                bfw.write(mSaveDir.getAbsolutePath() + File.separator + m3u8Ts.getIndexName());
                bfw.newLine();
            }
            bfw.write(M3U8Constants.TAG_ENDLIST);
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
