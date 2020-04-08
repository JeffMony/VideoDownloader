package com.jeffmony.downloader;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.jeffmony.downloader.listener.DownloadListener;
import com.jeffmony.downloader.listener.IDownloadListener;
import com.jeffmony.downloader.listener.IDownloadTaskListener;
import com.jeffmony.downloader.listener.IVideoInfoListener;
import com.jeffmony.downloader.m3u8.M3U8;
import com.jeffmony.downloader.model.Video;
import com.jeffmony.downloader.model.VideoDownloadInfo;
import com.jeffmony.downloader.model.VideoTaskItem;
import com.jeffmony.downloader.model.VideoTaskState;
import com.jeffmony.downloader.task.M3U8VideoDownloadTask;
import com.jeffmony.downloader.task.VideoDownloadTask;
import com.jeffmony.downloader.utils.DownloadExceptionUtils;
import com.jeffmony.downloader.utils.LogUtils;
import com.jeffmony.downloader.utils.VideoDownloadUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VideoDownloadManager {
    private static final String TAG = "VideoDownloadManager";
    public static final int READ_TIMEOUT = 30 * 1000;
    public static final int CONN_TIMEOUT = 30 * 1000;
    private static final int MSG_DOWNLOAD_DEFAULT = 0x0;
    private static final int MSG_DOWNLOAD_PENDING = 0x1;
    private static final int MSG_DOWNLOAD_PREPARE = 0x2;
    private static final int MSG_DOWNLOAD_START = 0x3;
    private static final int MSG_DOWNLOAD_PROCESSING = 0x4;
    private static final int MSG_DOWNLOAD_SPEED = 0x5;
    private static final int MSG_DOWNLOAD_PAUSE = 0x6;
    private static final int MSG_DOWNLOAD_SUCCESS =0x7;
    private static final int MSG_DOWNLOAD_ERROR = 0x8;

    private static VideoDownloadManager sInstance = null;
    private VideoDownloadConfig mConfig;
    private DownloadListener mGlobalDownloadListener = null;
    private VideoDownloadQueue mVideoDownloadQueue;
    private VideoDownloadHandler mVideoDownloadHandler = new VideoDownloadHandler();
    private Map<String, VideoDownloadTask> mVideoDownloadTaskMap = new ConcurrentHashMap<>();
    private Map<String, VideoTaskItem> mVideoItemTaskMap = new ConcurrentHashMap<>();

    public static VideoDownloadManager getInstance() {
        if (sInstance == null) {
            synchronized (VideoDownloadManager.class) {
                if (sInstance == null) {
                    sInstance = new VideoDownloadManager();
                }
            }
        }
        return sInstance;
    }

    private VideoDownloadManager() {
        mVideoDownloadQueue = new VideoDownloadQueue();
    }

    public void initConfig(VideoDownloadConfig config) {
        mConfig = config;
    }

    public void setGlobalDownloadListener(DownloadListener downloadListener) {
        mGlobalDownloadListener = downloadListener;
    }

    public void startDownload(VideoTaskItem taskItem) {
        if (taskItem == null || TextUtils.isEmpty(taskItem.getUrl()))
            return;

        if (mVideoDownloadQueue.contains(taskItem)) {
            taskItem = mVideoDownloadQueue.getTaskItem(taskItem.getUrl());
        } else {
            mVideoDownloadQueue.offer(taskItem);
        }
        taskItem.setTaskState(VideoTaskState.PENDING);
        mVideoDownloadHandler.obtainMessage(MSG_DOWNLOAD_PENDING, taskItem).sendToTarget();
        if (mVideoDownloadQueue.getDownloadingCount() < mConfig.getConcurrentCount()) {
            startDownload(taskItem, null);
        }
    }

    public void startDownload(VideoTaskItem taskItem, HashMap<String, String> headers) {
        if (taskItem == null || TextUtils.isEmpty(taskItem.getUrl()))
            return;
        taskItem.setTaskState(VideoTaskState.PREPARE);
        mVideoDownloadHandler.obtainMessage(MSG_DOWNLOAD_PREPARE, taskItem).sendToTarget();
        parseVideoInfo(taskItem, headers);
    }

    public void pauseDownloadTask(String videoUrl) {
        if (mVideoItemTaskMap.containsKey(videoUrl)) {
            VideoTaskItem taskItem = mVideoItemTaskMap.get(videoUrl);
            pauseDownloadTask(taskItem);
        }
    }

    public void pauseDownloadTask(VideoTaskItem taskItem) {

    }

    public void resumeDownload(String videoUrl) {
        if (mVideoItemTaskMap.containsKey(videoUrl)) {
            VideoTaskItem taskItem = mVideoItemTaskMap.get(videoUrl);
            startDownload(taskItem);
        }
    }


    private void parseVideoInfo(VideoTaskItem taskItem, HashMap<String, String> headers) {
        String videoUrl = taskItem.getUrl();
        String saveName = VideoDownloadUtils.computeMD5(videoUrl);
        VideoDownloadInfo downloadInfo = VideoDownloadUtils.readDownloadInfo(new File(mConfig.getCacheRoot(), saveName));
        if (downloadInfo != null) {
            downloadInfo.setFileHash(saveName);
            taskItem.setMimeType(downloadInfo.getMimeType());
            if (downloadInfo.getVideoType() == Video.Type.MP4_TYPE
                    || downloadInfo.getVideoType() == Video.Type.WEBM_TYPE
                    || downloadInfo.getVideoType() == Video.Type.QUICKTIME_TYPE
                    || downloadInfo.getVideoType() == Video.Type.GP3_TYPE) {

            } else if (downloadInfo.getVideoType() == Video.Type.HLS_TYPE) {

            }
        } else {
            downloadInfo = new VideoDownloadInfo(videoUrl);
            downloadInfo.setFileHash(saveName);
            parseNetworkVideoInfo(taskItem, downloadInfo, headers);
        }

    }

    private void parseNetworkVideoInfo(final VideoTaskItem taskItem, final VideoDownloadInfo downloadInfo, final HashMap<String, String> headers) {
        VideoInfoParserManager.getInstance().parseVideoInfo(downloadInfo, new IVideoInfoListener() {
            @Override
            public void onFinalUrl(String finalUrl) {

            }

            @Override
            public void onBaseVideoInfoSuccess(VideoDownloadInfo info) {
                taskItem.setMimeType(info.getMimeType());
                startBaseVideoDownloadTask(taskItem, downloadInfo, headers);
            }

            @Override
            public void onBaseVideoInfoFailed(Throwable error) {
                LogUtils.w(TAG, "onInfoFailed error=" +error);
                int errorCode = DownloadExceptionUtils.getErrorCode(error);
                taskItem.setErrorCode(errorCode);
                taskItem.setTaskState(VideoTaskState.ERROR);
                mVideoDownloadHandler.obtainMessage(MSG_DOWNLOAD_ERROR, taskItem).sendToTarget();
            }

            @Override
            public void onM3U8InfoSuccess(VideoDownloadInfo info, M3U8 m3u8) {
                taskItem.setMimeType(info.getMimeType());
                startM3U8VideoDownloadTask(taskItem, downloadInfo, headers, m3u8);
            }

            @Override
            public void onLiveM3U8Callback(VideoDownloadInfo info) {
                LogUtils.w(TAG, "onLiveM3U8Callback cannot be cached.");
                taskItem.setErrorCode(DownloadExceptionUtils.LIVE_M3U8_ERROR);
                taskItem.setTaskState(VideoTaskState.ERROR);
                mVideoDownloadHandler.obtainMessage(MSG_DOWNLOAD_ERROR, taskItem).sendToTarget();
            }

            @Override
            public void onM3U8InfoFailed(Throwable error) {
                LogUtils.w(TAG, "onM3U8InfoFailed : " + error);
                int errorCode = DownloadExceptionUtils.getErrorCode(error);
                taskItem.setErrorCode(errorCode);
                taskItem.setTaskState(VideoTaskState.ERROR);
                mVideoDownloadHandler.obtainMessage(MSG_DOWNLOAD_ERROR, taskItem).sendToTarget();
            }
        }, mConfig, headers, mConfig.shouldRedirect());
    }

    private void startM3U8VideoDownloadTask(final VideoTaskItem taskItem, VideoDownloadInfo downloadInfo, HashMap<String, String> headers, M3U8 m3u8) {
        taskItem.setVideoType(downloadInfo.getVideoType());
        VideoDownloadTask downloadTask = null;
        if (!mVideoDownloadTaskMap.containsKey(downloadInfo.getVideoUrl())) {
            downloadTask = new M3U8VideoDownloadTask(mConfig, downloadInfo, m3u8, headers);
            mVideoDownloadTaskMap.put(downloadInfo.getVideoUrl(), downloadTask);
        } else {
            downloadTask = mVideoDownloadTaskMap.get(downloadInfo.getVideoUrl());
        }

        if (downloadTask != null) {
            downloadTask.startDownload(new IDownloadTaskListener() {
                @Override
                public void onTaskStart(String url) {
                    taskItem.setTaskState(VideoTaskState.START);
                    mVideoDownloadHandler.obtainMessage(MSG_DOWNLOAD_START, taskItem).sendToTarget();
                }

                @Override
                public void onTaskProgress(float percent, long cachedSize, long totalSize, M3U8 m3u8) {
                    if (taskItem.getTaskState() == VideoTaskState.PAUSE || taskItem.getTaskState() == VideoTaskState.SUCCESS) {

                    } else {
                        taskItem.setTaskState(VideoTaskState.DOWNLOADING);
                        taskItem.setPercent(percent);
                        taskItem.setDownloadSize(cachedSize);
                        taskItem.setTotalSize(totalSize);
                        taskItem.setM3U8(m3u8);
                        mVideoDownloadHandler.obtainMessage(MSG_DOWNLOAD_PROCESSING, taskItem).sendToTarget();
                    }
                }

                @Override
                public void onTaskSpeedChanged(float speed) {
                    taskItem.setSpeed(speed);
                    mVideoDownloadHandler.obtainMessage(MSG_DOWNLOAD_SPEED, taskItem).sendToTarget();
                }

                @Override
                public void onTaskPaused() {
                    taskItem.setTaskState(VideoTaskState.PAUSE);
                    mVideoDownloadHandler.obtainMessage(MSG_DOWNLOAD_PAUSE, taskItem).sendToTarget();
                }

                @Override
                public void onTaskFinished(long totalSize) {
                    if (taskItem.getTaskState() != VideoTaskState.SUCCESS) {
                        taskItem.setTaskState(VideoTaskState.SUCCESS);
                        taskItem.setPercent(100f);
                        taskItem.setDownloadSize(totalSize);
                        mVideoDownloadHandler.obtainMessage(MSG_DOWNLOAD_SUCCESS, taskItem).sendToTarget();
                    }
                }

                @Override
                public void onTaskFailed(Throwable e) {
                    int errorCode = DownloadExceptionUtils.getErrorCode(e);
                    taskItem.setErrorCode(errorCode);
                    taskItem.setTaskState(VideoTaskState.ERROR);
                    mVideoDownloadHandler.obtainMessage(MSG_DOWNLOAD_ERROR, taskItem).sendToTarget();
                }
            });
        }
    }

    private void startBaseVideoDownloadTask(VideoTaskItem taskItem, VideoDownloadInfo downloadInfo, HashMap<String, String> headers) {

    }

    private void removeDownloadQueue(VideoTaskItem item) {
        mVideoDownloadQueue.remove(item);
        while(mVideoDownloadQueue.getDownloadingCount() < mConfig.getConcurrentCount() ) {
            if (mVideoDownloadQueue.getDownloadingCount() == mVideoDownloadQueue.size())
                break;
            VideoTaskItem item1 = mVideoDownloadQueue.peekPendingTask();
            startDownload(item1, null);
        }
    }


    class VideoDownloadHandler extends Handler {

        public VideoDownloadHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);

            handleMessage(msg.what, (VideoTaskItem) msg.obj, mGlobalDownloadListener);
        }

        private void handleMessage(int msg, VideoTaskItem item, IDownloadListener listener) {
            if (listener != null) {
                mVideoItemTaskMap.put(item.getUrl(), item);
                switch (msg) {
                    case MSG_DOWNLOAD_DEFAULT:
                        listener.onDownloadDefault(item);
                        break;
                    case MSG_DOWNLOAD_PENDING:
                        listener.onDownloadPending(item);
                        break;
                    case MSG_DOWNLOAD_PREPARE:
                        listener.onDownloadPrepare(item);
                        break;
                    case MSG_DOWNLOAD_START:
                        listener.onDownloadStart(item);
                        break;
                    case MSG_DOWNLOAD_PROCESSING:
                        listener.onDownloadProgress(item);
                        break;
                    case MSG_DOWNLOAD_SPEED:
                        listener.onDownloadSpeed(item);
                        break;
                    case MSG_DOWNLOAD_PAUSE:
                        removeDownloadQueue(item);
                        listener.onDownloadPause(item);
                        break;
                    case MSG_DOWNLOAD_ERROR:
                        removeDownloadQueue(item);
                        listener.onDownloadError(item);
                        break;
                    case MSG_DOWNLOAD_SUCCESS:
                        removeDownloadQueue(item);
                        listener.onDownloadSuccess(item);
                        break;
                }
            }
        }
    }

    public static class Build {
        private Context mContext;
        private File mCacheRoot;
        private int mReadTimeOut = 30 * 1000;              // 30 seconds
        private int mConnTimeOut = 30 * 1000;              // 30 seconds
        private boolean mRedirect = true;
        private boolean mIgnoreCertErrors = true;
        private int mConcurrentCount = 3;

        public Build(Context context) { mContext = context; }

        public Build setCacheRoot(File cacheRoot) {
            mCacheRoot = cacheRoot;
            return this;
        }

        public Build setTimeOut(int readTimeOut, int connTimeOut) {
            mReadTimeOut = readTimeOut;
            mConnTimeOut = connTimeOut;
            return this;
        }

        public Build setUrlRedirect(boolean redirect) {
            mRedirect = redirect;
            return this;
        }

        public Build setConcurrentCount(int count) {
            mConcurrentCount = count;
            return this;
        }

        public Build setIgnoreCertErrors(boolean ignoreCertErrors) {
            mIgnoreCertErrors = ignoreCertErrors;
            return this;
        }

        public VideoDownloadConfig buildConfig() {
            return new VideoDownloadConfig(mContext, mCacheRoot, mReadTimeOut,
                    mConnTimeOut, mRedirect, mIgnoreCertErrors, mConcurrentCount);
        }
    }
}
