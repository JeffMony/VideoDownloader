package com.jeffmony.downloader.task;

import android.text.TextUtils;

import com.jeffmony.downloader.VideoDownloadException;
import com.jeffmony.downloader.common.DownloadConstants;
import com.jeffmony.downloader.m3u8.M3U8;
import com.jeffmony.downloader.m3u8.M3U8Constants;
import com.jeffmony.downloader.m3u8.M3U8Seg;
import com.jeffmony.downloader.model.VideoTaskItem;
import com.jeffmony.downloader.utils.DownloadExceptionUtils;
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
import java.net.ProtocolException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class M3U8VideoDownloadTask extends VideoDownloadTask {
    private static final int CONTINUOUS_SUCCESS_TS_THRESHOLD = 6;
    private final Object mFileLock = new Object();

    private volatile int mM3U8DownloadPoolCount;
    private volatile int mContinuousSuccessTsCount;   //连续请求ts成功的个数

    private final M3U8 mM3U8;
    private List<M3U8Seg> mTsList;
    private volatile int mCurTs = 0;
    private int mTotalTs;
    private long mTotalSize;

    public M3U8VideoDownloadTask(VideoTaskItem taskItem, M3U8 m3u8, Map<String, String> headers) {
        super(taskItem, headers);
        mM3U8 = m3u8;
        mTsList = m3u8.getTsList();
        mTotalTs = mTsList.size();
        mPercent = taskItem.getPercent();
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
        for (M3U8Seg ts : mTsList) {
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
        if (mCurTs == mTotalTs) {
            mTaskItem.setIsCompleted(true);
        }
    }


    @Override
    public void startDownload() {
        mDownloadTaskListener.onTaskStart(mTaskItem.getUrl());
        initM3U8Ts();
        startDownload(mCurTs);
    }

    private void startDownload(int curDownloadTs) {
        if (mTaskItem.isCompleted()) {
            LogUtils.i(DownloadConstants.TAG, "M3U8VideoDownloadTask local file.");
            notifyDownloadFinish();
            return;
        }
        mCurTs = curDownloadTs;
        LogUtils.i(DownloadConstants.TAG, "startDownload curDownloadTs = " + curDownloadTs);
        mDownloadExecutor = new ThreadPoolExecutor(THREAD_COUNT, THREAD_COUNT, 0L,
                TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.DiscardOldestPolicy());
        for (int index = curDownloadTs; index < mTotalTs; index++) {
            final M3U8Seg ts = mTsList.get(index);
            mDownloadExecutor.execute(() -> {
                try {
                    downloadTsTask(ts);
                } catch (Exception e) {
                    LogUtils.w(DownloadConstants.TAG, "M3U8TsDownloadThread download failed, exception=" + e);
                    notifyDownloadError(e);
                }
            });
        }

        notifyDownloadFinish(mCurrentCachedSize);
    }

    private void downloadTsTask(M3U8Seg ts) throws Exception {
        if (ts.hasInitSegment()) {
            String tsInitSegmentName = ts.getInitSegmentName();
            File tsInitSegmentFile = new File(mSaveDir, tsInitSegmentName);
            if (!tsInitSegmentFile.exists()) {
                downloadFile(ts, tsInitSegmentFile, ts.getInitSegmentUri());
            }
        }
        File tsFile = new File(mSaveDir, ts.getIndexName());
        if (!tsFile.exists()) {
            // ts is network resource, download ts file then rename it to local file.
            downloadFile(ts, tsFile, ts.getUrl());
        }

        if (tsFile.exists() && (tsFile.length() == ts.getContentLength())) {
            // rename network ts name to local file name.
            ts.setName(ts.getIndexName());
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
            synchronized (mDownloadLock) {
                if (!mDownloadFinished) {
                    mDownloadTaskListener.onTaskProgressForM3U8(100.0f, mCurrentCachedSize, mCurTs, mTotalTs, mSpeed);
                    mPercent = 100.0f;
                    mTotalSize = mCurrentCachedSize;
                    mDownloadTaskListener.onTaskFinished(mTotalSize);
                    mDownloadFinished = true;
                }
            }
            return;
        }
        if (mCurTs >= mTotalTs) {
            mCurTs = mTotalTs;
        }
        synchronized (mDownloadLock) {
            float percent = mCurTs * 1.0f * 100 / mTotalTs;
            if (!VideoDownloadUtils.isFloatEqual(percent, mPercent)) {
                long nowTime = System.currentTimeMillis();
                if (mCurrentCachedSize > mLastCachedSize && nowTime > mLastInvokeTime) {
                    mSpeed = (mCurrentCachedSize - mLastCachedSize) * 1000 * 1.0f / (nowTime - mLastInvokeTime);
                }
                if (!mDownloadFinished) {
                    mDownloadTaskListener.onTaskProgressForM3U8(percent, mCurrentCachedSize, mCurTs, mTotalTs, mSpeed);
                }
                mPercent = percent;
                mLastCachedSize = mCurrentCachedSize;
                mLastInvokeTime = nowTime;
            }
        }
        boolean isCompleted = true;
        for (M3U8Seg ts : mTsList) {
            File tsFile = new File(mSaveDir, ts.getIndexName());
            if (!tsFile.exists()) {
                isCompleted = false;
                break;
            }
        }
        if (isCompleted) {
            try {
                createLocalM3U8File();
                createLocalM3U8FileWithKey();
            } catch (Exception e) {
                notifyDownloadError(e);
            }
            synchronized (mDownloadLock) {
                if (!mDownloadFinished) {
                    mTotalSize = mCurrentCachedSize;
                    mDownloadTaskListener.onTaskProgressForM3U8(100.0f, mCurrentCachedSize, mCurTs, mTotalTs, mSpeed);
                    mDownloadTaskListener.onTaskFinished(mTotalSize);
                    mDownloadFinished = true;
                }
            }
        }
    }

    private void updateM3U8TsInfo() {
        long tempCurrentCachedSize = 0;
        int tempCurTs = 0;
        for (M3U8Seg ts : mTsList) {
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
            synchronized (mDownloadLock) {
                if (!mDownloadFinished) {
                    mDownloadTaskListener.onTaskFinished(size);
                    mDownloadFinished = true;
                }
            }
        }
    }

    private void notifyDownloadError(Exception e) {
        notifyOnTaskFailed(e);
    }

    public void downloadFile(M3U8Seg ts, File file, String videoUrl) throws Exception {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        try {
            connection = HttpUtils.getConnection(videoUrl, mHeaders, VideoDownloadUtils.getDownloadConfig().shouldIgnoreCertErrors());
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpUtils.RESPONSE_200 || responseCode == HttpUtils.RESPONSE_206) {
                ts.setRetryCount(0);
                mContinuousSuccessTsCount++;
                if (mContinuousSuccessTsCount > CONTINUOUS_SUCCESS_TS_THRESHOLD && mM3U8DownloadPoolCount < THREAD_COUNT) {
                    mM3U8DownloadPoolCount += 1;
                    mContinuousSuccessTsCount -= 1;
                    setThreadPoolArgument(mM3U8DownloadPoolCount, mM3U8DownloadPoolCount);
                }
                inputStream = connection.getInputStream();
                long contentLength = connection.getContentLength();
                saveFile(inputStream, file, contentLength, ts, videoUrl);
            } else {
                mContinuousSuccessTsCount = 0;
                if (responseCode == HttpUtils.RESPONSE_503) {
                    if (mM3U8DownloadPoolCount > 1) {
                        mM3U8DownloadPoolCount -= 1;
                        setThreadPoolArgument(mM3U8DownloadPoolCount, mM3U8DownloadPoolCount);
                        downloadFile(ts, file, videoUrl);
                    } else {
                        ts.setRetryCount(ts.getRetryCount() + 1);
                        if (ts.getRetryCount() < HttpUtils.MAX_RETRY_COUNT) {
                            downloadFile(ts, file, videoUrl);
                        } else {
                            throw new VideoDownloadException(DownloadExceptionUtils.RETRY_COUNT_EXCEED_WITH_THREAD_CONTROL_STRING);
                        }
                    }
                } else {
                    throw new VideoDownloadException(DownloadExceptionUtils.VIDEO_REQUEST_FAILED);
                }
            }
        } catch (Exception e) {
            mContinuousSuccessTsCount = 0;
            if (e instanceof IOException && e.getMessage().contains(DownloadExceptionUtils.PROTOCOL_UNEXPECTED_END_OF_STREAM)) {
                if (mM3U8DownloadPoolCount > 1) {
                    mM3U8DownloadPoolCount -= 1;
                    setThreadPoolArgument(mM3U8DownloadPoolCount, mM3U8DownloadPoolCount);
                    downloadFile(ts, file, videoUrl);
                } else {
                    ts.setRetryCount(ts.getRetryCount() + 1);
                    if (ts.getRetryCount() < HttpUtils.MAX_RETRY_COUNT) {
                        downloadFile(ts, file, videoUrl);
                    } else {
                        throw e;
                    }
                }
            } else {
                LogUtils.w(DownloadConstants.TAG, "downloadFile failed, exception="+e.getMessage());
                throw e;
            }
        } finally {
            HttpUtils.closeConnection(connection);
            VideoDownloadUtils.close(inputStream);
        }
    }

    private void saveFile(InputStream inputStream, File file, long contentLength, M3U8Seg ts, String videoUrl) throws Exception {
        FileOutputStream fos = null;
        long totalLength = 0;
        try {
            fos = new FileOutputStream(file);
            int len;
            byte[] buf = new byte[BUFFER_SIZE];
            while ((len = inputStream.read(buf)) != -1) {
                totalLength += (long)len;
                fos.write(buf, 0, len);
            }
            if (contentLength > 0 && contentLength == totalLength) {
                ts.setContentLength(contentLength);
            } else {
                ts.setContentLength(totalLength);
            }
        } catch (IOException e) {
            if (file.exists() && ((contentLength > 0 && contentLength == file.length()) || (contentLength == -1 && totalLength == file.length()))) {
                //这时候也能说明ts已经下载好了
            } else {
                if ((e instanceof ProtocolException &&
                        !TextUtils.isEmpty(e.getMessage()) &&
                        e.getMessage().contains(DownloadExceptionUtils.PROTOCOL_UNEXPECTED_END_OF_STREAM)) &&
                        (contentLength > totalLength && totalLength == file.length())) {
                    if (file.length() == 0) {
                        ts.setRetryCount(ts.getRetryCount() + 1);
                        if (ts.getRetryCount() < HttpUtils.MAX_RETRY_COUNT) {
                            downloadFile(ts, file, videoUrl);
                        } else {
                            LogUtils.w(DownloadConstants.TAG, file.getAbsolutePath() + ", length=" + file.length() + ", saveFile failed, exception=" + e);
                            if (file.exists()) {
                                file.delete();
                            }
                            throw e;
                        }
                    } else {
                        ts.setContentLength(totalLength);
                    }
                } else {
                    LogUtils.w(DownloadConstants.TAG, file.getAbsolutePath() + ", length=" + file.length() + ", saveFile failed, exception=" + e);
                    if (file.exists()) {
                        file.delete();
                    }
                    throw e;
                }
            }
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
            bfw.write(M3U8Constants.TAG_MEDIA_SEQUENCE + ":" + mM3U8.getInitSequence() + "\n");

            bfw.write(M3U8Constants.TAG_TARGET_DURATION + ":" + mM3U8.getTargetDuration() + "\n");

            for (M3U8Seg m3u8Ts : mTsList) {
                if (m3u8Ts.hasInitSegment()) {
                    String initSegmentInfo;
                    String initSegmentFilePath = mSaveDir.getAbsolutePath() + File.separator + m3u8Ts.getInitSegmentName();
                    if (m3u8Ts.getSegmentByteRange() != null) {
                        initSegmentInfo = "URI=\"" + initSegmentFilePath + "\"" + ",BYTERANGE=\"" + m3u8Ts.getSegmentByteRange() + "\"";
                    } else {
                        initSegmentInfo = "URI=\"" + initSegmentFilePath  + "\"";
                    }
                    bfw.write(M3U8Constants.TAG_INIT_SEGMENT + ":" + initSegmentInfo + "\n");
                }
                if (m3u8Ts.hasKey()) {
                    if (m3u8Ts.getMethod() != null) {
                        String key = "METHOD=" + m3u8Ts.getMethod();
                        if (m3u8Ts.getKeyUri() != null) {
                            File keyFile = new File(mSaveDir, m3u8Ts.getLocalKeyUri());
                            key += ",URI=\"" + keyFile.getAbsolutePath() + "\"";
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
                String byteRange = m3u8Ts.getByteRange();
                if (!TextUtils.isEmpty(byteRange)) {
                    bfw.write(M3U8Constants.TAG_BYTERANGE + ":" + byteRange + "\n");
                }
                bfw.write(mSaveDir.getAbsolutePath() + File.separator + m3u8Ts.getIndexName());
                bfw.newLine();
            }
            bfw.write(M3U8Constants.TAG_ENDLIST);
            bfw.flush();
            bfw.close();

            File localM3U8File = new File(mSaveDir, mSaveName + "_" + VideoDownloadUtils.LOCAL_M3U8);
            if (localM3U8File.exists()) {
                localM3U8File.delete();
            }
            tempM3U8File.renameTo(localM3U8File);
        }
    }

    private void createLocalM3U8FileWithKey() throws IOException {
        synchronized (mFileLock) {
            File tempM3U8File = new File(mSaveDir, "temp.m3u8");
            if (tempM3U8File.exists()) {
                tempM3U8File.delete();
            }

            BufferedWriter bfw = new BufferedWriter(new FileWriter(tempM3U8File, false));
            bfw.write(M3U8Constants.PLAYLIST_HEADER + "\n");
            bfw.write(M3U8Constants.TAG_VERSION + ":" + mM3U8.getVersion() + "\n");
            bfw.write(M3U8Constants.TAG_MEDIA_SEQUENCE + ":" + mM3U8.getInitSequence() + "\n");

            bfw.write(M3U8Constants.TAG_TARGET_DURATION + ":" + mM3U8.getTargetDuration() + "\n");

            for (M3U8Seg m3u8Ts : mTsList) {
                if (m3u8Ts.hasInitSegment()) {
                    String initSegmentInfo;
                    String initSegmentFilePath = mSaveDir.getAbsolutePath() + File.separator + m3u8Ts.getInitSegmentName();
                    if (m3u8Ts.getSegmentByteRange() != null) {
                        initSegmentInfo = "URI=\"" + initSegmentFilePath + "\"" + ",BYTERANGE=\"" + m3u8Ts.getSegmentByteRange() + "\"";
                    } else {
                        initSegmentInfo = "URI=\"" + initSegmentFilePath  + "\"";
                    }
                    bfw.write(M3U8Constants.TAG_INIT_SEGMENT + ":" + initSegmentInfo + "\n");
                }
                if (m3u8Ts.hasKey()) {
                    if (m3u8Ts.getMethod() != null) {
                        String key = "METHOD=" + m3u8Ts.getMethod();
                        if (m3u8Ts.getKeyUri() != null) {
                            key += ",URI=\"" + m3u8Ts.getKeyUri() + "\"";
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
                String byteRange = m3u8Ts.getByteRange();
                if (!TextUtils.isEmpty(byteRange)) {
                    bfw.write(M3U8Constants.TAG_BYTERANGE + ":" + byteRange + "\n");
                }
                bfw.write(mSaveDir.getAbsolutePath() + File.separator + m3u8Ts.getIndexName());
                bfw.newLine();
            }
            bfw.write(M3U8Constants.TAG_ENDLIST);
            bfw.flush();
            bfw.close();

            File localM3U8File = new File(mSaveDir, mSaveName + "_" + VideoDownloadUtils.LOCAL_M3U8_WITH_KEY);
            if (localM3U8File.exists()) {
                localM3U8File.delete();
            }
            tempM3U8File.renameTo(localM3U8File);
        }
    }
}
