package com.jeffmony.downloader;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.jeffmony.downloader.database.VideoDownloadDatabaseHelper;
import com.jeffmony.downloader.listener.DownloadListener;
import com.jeffmony.downloader.listener.IDownloadInfosCallback;
import com.jeffmony.downloader.listener.IDownloadListener;
import com.jeffmony.downloader.listener.IDownloadTaskListener;
import com.jeffmony.downloader.listener.IVideoInfoListener;
import com.jeffmony.downloader.listener.IVideoInfoParseListener;
import com.jeffmony.downloader.m3u8.M3U8;
import com.jeffmony.downloader.model.VideoTaskItem;
import com.jeffmony.downloader.model.VideoTaskState;
import com.jeffmony.downloader.task.BaseVideoDownloadTask;
import com.jeffmony.downloader.task.M3U8VideoDownloadTask;
import com.jeffmony.downloader.task.VideoDownloadTask;
import com.jeffmony.downloader.utils.DownloadExceptionUtils;
import com.jeffmony.downloader.utils.LogUtils;
import com.jeffmony.downloader.utils.VideoDownloadUtils;
import com.jeffmony.downloader.utils.WorkerThreadHandler;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class VideoDownloadManager {
    private static final String TAG = "VideoDownloadManager";
    private static final int MSG_DOWNLOAD_DEFAULT = 0;
    private static final int MSG_DOWNLOAD_PENDING = 1;
    private static final int MSG_DOWNLOAD_PREPARE = 2;
    private static final int MSG_DOWNLOAD_START = 3;
    private static final int MSG_DOWNLOAD_PROCESSING = 4;
    private static final int MSG_DOWNLOAD_SPEED = 5;
    private static final int MSG_DOWNLOAD_PAUSE = 6;
    private static final int MSG_DOWNLOAD_SUCCESS = 7;
    private static final int MSG_DOWNLOAD_ERROR = 8;

    private static final int MSG_FETCH_DOWNLOAD_INFO = 100;
    private static final int MSG_DELETE_ALL_FILES = 101;

    private static VideoDownloadManager sInstance = null;
    private DownloadListener mGlobalDownloadListener = null;
    private VideoDownloadDatabaseHelper mVideoDatabaseHelper = null;
    private VideoDownloadQueue mVideoDownloadQueue;
    private Object mQueueLock = new Object();
    private VideoDownloadConfig mConfig;

    private VideoDownloadHandler mVideoDownloadHandler;
    private List<IDownloadInfosCallback> mDownloadInfoCallbacks = new CopyOnWriteArrayList<>();
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
        mVideoDatabaseHelper = new VideoDownloadDatabaseHelper(mConfig.getContext());
        VideoInfoParserManager.getInstance().initConfig(config);
        HandlerThread stateThread = new HandlerThread("Video_download_state_thread");
        stateThread.start();
        mVideoDownloadHandler = new VideoDownloadHandler(stateThread.getLooper());
    }

    public VideoDownloadConfig downloadConfig() {
        return mConfig;
    }

    public void fetchDownloadItems(IDownloadInfosCallback callback) {
        mDownloadInfoCallbacks.add(callback);
        mVideoDownloadHandler.obtainMessage(MSG_FETCH_DOWNLOAD_INFO)
                .sendToTarget();
    }

    public void removeDownloadInfosCallback(IDownloadInfosCallback callback) {
        mDownloadInfoCallbacks.remove(callback);
    }

    public void setGlobalDownloadListener(DownloadListener downloadListener) {
        mGlobalDownloadListener = downloadListener;
    }

    public void startDownload(VideoTaskItem taskItem) {
        if (taskItem == null || TextUtils.isEmpty(taskItem.getUrl()))
            return;

        synchronized (mQueueLock) {
            if (mVideoDownloadQueue.contains(taskItem)) {
                taskItem = mVideoDownloadQueue.getTaskItem(taskItem.getUrl());
            } else {
                mVideoDownloadQueue.offer(taskItem);
            }
        }
        taskItem.setPaused(false);
        taskItem.setDownloadCreateTime(taskItem.getDownloadCreateTime());
        taskItem.setTaskState(VideoTaskState.PENDING);
        VideoTaskItem tempTaskItem = (VideoTaskItem) taskItem.clone();
        mVideoDownloadHandler
                .obtainMessage(MSG_DOWNLOAD_PENDING, tempTaskItem)
                .sendToTarget();
        startDownload(taskItem, null);
    }

    public void startDownload(VideoTaskItem taskItem, HashMap<String, String> headers) {
        if (taskItem == null || TextUtils.isEmpty(taskItem.getUrl()))
            return;
        parseVideoDownloadInfo(taskItem, headers);
    }

    private void parseVideoDownloadInfo(VideoTaskItem taskItem, HashMap<String, String> headers) {
        String videoUrl = taskItem.getUrl();
        String saveName = VideoDownloadUtils.computeMD5(videoUrl);
        taskItem.setFileHash(saveName);
        boolean taskExisted = taskItem.getDownloadCreateTime() != 0;
        if (taskExisted) {
            parseExistVideoDownloadInfo(taskItem, headers);
        } else {
            parseNetworkVideoInfo(taskItem, headers);
        }
    }

    private void parseExistVideoDownloadInfo(final VideoTaskItem taskItem, final HashMap<String, String> headers) {
        if (taskItem.isNonHlsType()) {
            startBaseVideoDownloadTask(taskItem, headers);
        } else if (taskItem.isHlsType()) {
            VideoInfoParserManager.getInstance()
                    .parseM3U8File(taskItem, new IVideoInfoParseListener() {

                        @Override
                        public void onM3U8FileParseSuccess(VideoTaskItem info, M3U8 m3u8) {
                            startM3U8VideoDownloadTask(taskItem, m3u8, headers);
                        }

                        @Override
                        public void onM3U8FileParseFailed(VideoTaskItem info, Throwable error) {
                            parseNetworkVideoInfo(taskItem, headers);
                        }
                    });
        }
    }

    private void parseNetworkVideoInfo(final VideoTaskItem taskItem, final HashMap<String, String> headers) {
        VideoInfoParserManager.getInstance().parseVideoInfo(taskItem, new IVideoInfoListener() {
            @Override
            public void onFinalUrl(String finalUrl) { }

            @Override
            public void onBaseVideoInfoSuccess(VideoTaskItem taskItem) {
                taskItem.setMimeType(taskItem.getMimeType());
                startBaseVideoDownloadTask(taskItem, headers);
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
            public void onM3U8InfoSuccess(VideoTaskItem info, M3U8 m3u8) {
                taskItem.setMimeType(info.getMimeType());
                startM3U8VideoDownloadTask(taskItem, m3u8, headers);
            }

            @Override
            public void onLiveM3U8Callback(VideoTaskItem info) {
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
        }, headers, mConfig.shouldRedirect());
    }

    private void startM3U8VideoDownloadTask(final VideoTaskItem taskItem, M3U8 m3u8, HashMap<String, String> headers) {
        taskItem.setTaskState(VideoTaskState.PREPARE);
        VideoTaskItem tempTaskItem = (VideoTaskItem)taskItem.clone();
        mVideoDownloadHandler.obtainMessage(MSG_DOWNLOAD_PREPARE, tempTaskItem).sendToTarget();
        synchronized (mQueueLock) {
            if (mVideoDownloadQueue.getDownloadingCount() >= mConfig.getConcurrentCount()) {
                return;
            }
        }
        VideoDownloadTask downloadTask;
        if (!mVideoDownloadTaskMap.containsKey(taskItem.getUrl())) {
            downloadTask = new M3U8VideoDownloadTask(mConfig, taskItem, m3u8, headers);
            mVideoDownloadTaskMap.put(taskItem.getUrl(), downloadTask);
        } else {
            downloadTask = mVideoDownloadTaskMap.get(taskItem.getUrl());
        }

        startDownloadTask(downloadTask, taskItem);
    }

    private void startBaseVideoDownloadTask(VideoTaskItem taskItem, HashMap<String, String> headers) {
        taskItem.setTaskState(VideoTaskState.PREPARE);
        VideoTaskItem tempTaskItem = (VideoTaskItem)taskItem.clone();
        mVideoDownloadHandler.obtainMessage(MSG_DOWNLOAD_PREPARE, tempTaskItem).sendToTarget();
        synchronized (mQueueLock) {
            if (mVideoDownloadQueue.getDownloadingCount() >= mConfig.getConcurrentCount()) {
                return;
            }
        }
        VideoDownloadTask downloadTask;
        if (!mVideoDownloadTaskMap.containsKey(taskItem.getUrl())) {
            downloadTask = new BaseVideoDownloadTask(mConfig, taskItem, headers);
            mVideoDownloadTaskMap.put(taskItem.getUrl(), downloadTask);
        } else {
            downloadTask = mVideoDownloadTaskMap.get(taskItem.getUrl());
        }
        startDownloadTask(downloadTask, taskItem);
    }

    private void startDownloadTask(VideoDownloadTask downloadTask, VideoTaskItem taskItem) {
        if (downloadTask != null) {
            downloadTask.startDownload(new IDownloadTaskListener() {
                @Override
                public void onTaskStart(String url) {
                    taskItem.setTaskState(VideoTaskState.START);
                    mVideoDownloadHandler.obtainMessage(MSG_DOWNLOAD_START, taskItem).sendToTarget();
                }

                @Override
                public void onTaskProgress(float percent, long cachedSize, long totalSize, M3U8 m3u8) {
                    if (taskItem.isPaused()) {
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
                    if (!taskItem.isErrorState() || !taskItem.isSuccessState()) {
                        taskItem.setTaskState(VideoTaskState.PAUSE);
                        taskItem.setPaused(true);
                        mVideoDownloadHandler.obtainMessage(MSG_DOWNLOAD_PAUSE, taskItem).sendToTarget();
                        mVideoDownloadHandler.removeMessages(MSG_DOWNLOAD_PROCESSING);
                    }
                }

                @Override
                public void onTaskFinished(long totalSize) {
                    if (taskItem.getTaskState() != VideoTaskState.SUCCESS) {
                        taskItem.setTaskState(VideoTaskState.SUCCESS);
                        taskItem.setDownloadSize(totalSize);
                        taskItem.setPercent(100f);
                        if (taskItem.isHlsType()) {
                            taskItem.setFilePath(taskItem.getSaveDir() + File.separator + VideoDownloadUtils.LOCAL_M3U8);
                            taskItem.setFileName(VideoDownloadUtils.LOCAL_M3U8);
                        } else if (taskItem.isNonHlsType()) {
                            taskItem.setFilePath(taskItem.getSaveDir() + File.separator + taskItem.getFileHash() + VideoDownloadUtils.VIDEO_SUFFIX);
                            taskItem.setFileName(taskItem.getFileHash() + VideoDownloadUtils.VIDEO_SUFFIX);
                        }
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

    public String getDownloadPath() {
        if (mConfig != null) {
            return mConfig.getCacheRoot().getAbsolutePath();
        }
        return null;
    }

    public void deleteAllVideoFiles(Context context) {
        try {
            VideoDownloadUtils.clearVideoCacheDir(context);
            mVideoDownloadHandler.obtainMessage(MSG_DELETE_ALL_FILES).sendToTarget();
        } catch (Exception e) {
            LogUtils.w(TAG , "clearVideoCacheDir failed, exception = " + e.getMessage());
        }
    }

    public void pauseAllDownloadTasks() {
        List<VideoTaskItem> taskList = mVideoDownloadQueue.getDownloadList();
        for (VideoTaskItem taskItem : taskList) {
            if (taskItem.isPendingTask()) {
                mVideoDownloadQueue.remove(taskItem);
                mVideoDownloadHandler.obtainMessage(MSG_DOWNLOAD_DEFAULT, taskItem).sendToTarget();
            } else if (taskItem.isRunningTask()) {
                pauseDownloadTask(taskItem);
            }
        }
    }

    public void pauseDownloadTask(List<String> urlList) {
        for (String url : urlList) {
            pauseDownloadTask(url);
        }
    }

    public void pauseDownloadTask(String videoUrl) {
        if (mVideoItemTaskMap.containsKey(videoUrl)) {
            VideoTaskItem taskItem = mVideoItemTaskMap.get(videoUrl);
            pauseDownloadTask(taskItem);
        }
    }

    public void pauseDownloadTask(VideoTaskItem taskItem) {
        if (taskItem == null || TextUtils.isEmpty(taskItem.getUrl()))
            return;
        synchronized (mQueueLock) {
            mVideoDownloadQueue.remove(taskItem);
        }
        String url = taskItem.getUrl();
        VideoDownloadTask task = mVideoDownloadTaskMap.get(url);
        if (task != null) {
            task.pauseDownload();
        }
    }

    public void resumeDownload(String videoUrl) {
        if (mVideoItemTaskMap.containsKey(videoUrl)) {
            VideoTaskItem taskItem = mVideoItemTaskMap.get(videoUrl);
            startDownload(taskItem);
        }
    }

    //Delete one task
    public void deleteVideoTask(VideoTaskItem taskItem, boolean shouldDeleteSourceFile) {
        String cacheFilePath = getDownloadPath();
        if (!TextUtils.isEmpty(cacheFilePath)) {
            if (taskItem.isRunningTask()) {
                pauseDownloadTask(taskItem);
            }
            String saveName = VideoDownloadUtils.computeMD5(taskItem.getUrl());
            File file = new File(cacheFilePath + File.separator + saveName);
            try {
                if (shouldDeleteSourceFile) {
                    VideoDownloadUtils.delete(file);
                }
                if (mVideoDownloadTaskMap.containsKey(taskItem.getUrl())) {
                    mVideoDownloadTaskMap.remove(taskItem.getUrl());
                }
                taskItem.reset();
                mVideoDownloadHandler.obtainMessage(MSG_DOWNLOAD_DEFAULT, taskItem).sendToTarget();
            } catch (Exception e) {
                LogUtils.w(TAG, "Delete file: " + file +" failed, exception="+e.getMessage());
            }
        }
    }

    public void deleteVideoTask(String videoUrl, boolean shouldDeleteSourceFile) {
        if (mVideoItemTaskMap.containsKey(videoUrl)) {
            VideoTaskItem taskItem = mVideoItemTaskMap.get(videoUrl);
            deleteVideoTask(taskItem, shouldDeleteSourceFile);
            mVideoItemTaskMap.remove(videoUrl);
        }
    }

    public void deleteVideoTasks(List<String> urlList, boolean shouldDeleteSourceFile) {
        for (String url : urlList) {
            deleteVideoTask(url, shouldDeleteSourceFile);
        }
    }

    public void deleteVideoTasks(VideoTaskItem[] taskItems, boolean shouldDeleteSourceFile) {
        String cacheFilePath = getDownloadPath();
        if (!TextUtils.isEmpty(cacheFilePath)) {
            for (VideoTaskItem item : taskItems) {
                deleteVideoTask(item, shouldDeleteSourceFile);
            }
        }
    }

    private void removeDownloadQueue(VideoTaskItem taskItem) {
        synchronized (mQueueLock) {
            mVideoDownloadQueue.remove(taskItem);
            LogUtils.w(TAG, "removeDownloadQueue size="+mVideoDownloadQueue.size() +","+mVideoDownloadQueue.getDownloadingCount()+","+mVideoDownloadQueue.getPendingCount());
            int pendingCount = mVideoDownloadQueue.getPendingCount();
            int downloadingCount = mVideoDownloadQueue.getDownloadingCount();
            while (downloadingCount < mConfig.getConcurrentCount()
                    && pendingCount > 0) {
                if (mVideoDownloadQueue.size() == 0)
                    break;
                if (downloadingCount == mVideoDownloadQueue.size())
                    break;
                VideoTaskItem item1 = mVideoDownloadQueue.peekPendingTask();
                startDownload(item1, null);
                pendingCount--;
                downloadingCount++;
            }
        }
    }


    class VideoDownloadHandler extends Handler {

        public VideoDownloadHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if (msg.what == MSG_FETCH_DOWNLOAD_INFO) {
                dispatchDownloadInfos();
            } else if (msg.what == MSG_DELETE_ALL_FILES ) {
                //删除数据库中所有记录
                WorkerThreadHandler.submitRunnableTask(new Runnable() {
                    @Override
                    public void run() {
                        mVideoDatabaseHelper.deleteAllDownloadInfos();
                    }
                });
            } else {
                dispatchDownloadMessage(msg.what, (VideoTaskItem) msg.obj, mGlobalDownloadListener);
            }
        }

        private void dispatchDownloadInfos() {
            WorkerThreadHandler.submitRunnableTask(new Runnable() {
                @Override
                public void run() {
                    List<VideoTaskItem> taskItems = mVideoDatabaseHelper.getDownloadInfos();
                    for (VideoTaskItem taskItem : taskItems) {
                        mVideoItemTaskMap.put(taskItem.getUrl(), taskItem);
                    }
                    for (IDownloadInfosCallback callback : mDownloadInfoCallbacks) {
                        callback.onDownloadInfos(taskItems);
                    }
                }
            });
        }

        private void dispatchDownloadMessage(int msg, VideoTaskItem taskItem, IDownloadListener listener) {
            if (listener != null) {
                mVideoItemTaskMap.put(taskItem.getUrl(), taskItem);
                switch (msg) {
                    case MSG_DOWNLOAD_DEFAULT:
                        handleOnDownloadDefault(taskItem, listener);
                        break;
                    case MSG_DOWNLOAD_PENDING:
                        handleOnDownloadPending(taskItem, listener);
                        break;
                    case MSG_DOWNLOAD_PREPARE:
                        handleOnDownloadPrepare(taskItem, listener);
                        break;
                    case MSG_DOWNLOAD_START:
                        handleOnDownloadStart(taskItem, listener);
                        break;
                    case MSG_DOWNLOAD_PROCESSING:
                        handleOnDownloadProcessing(taskItem, listener);
                        break;
                    case MSG_DOWNLOAD_SPEED:
                        handleOnDownloadSpeed(taskItem, listener);
                        break;
                    case MSG_DOWNLOAD_PAUSE:
                        handleOnDownloadPause(taskItem, listener);
                        break;
                    case MSG_DOWNLOAD_ERROR:
                        handleOnDownloadError(taskItem, listener);
                        break;
                    case MSG_DOWNLOAD_SUCCESS:
                        handleOnDownloadSuccess(taskItem, listener);
                        break;
                }
            }
        }
    }

    private void handleOnDownloadDefault(VideoTaskItem taskItem, IDownloadListener listener) {
        listener.onDownloadDefault(taskItem);
    }

    private void handleOnDownloadPending(VideoTaskItem taskItem, IDownloadListener listener){
        listener.onDownloadPending(taskItem);
    }

    private void handleOnDownloadPrepare(VideoTaskItem taskItem, IDownloadListener listener) {
        listener.onDownloadPrepare(taskItem);
        markDownloadInfoAddEvent(taskItem);
    }

    private void handleOnDownloadStart(VideoTaskItem taskItem, IDownloadListener listener) {
        listener.onDownloadStart(taskItem);
    }

    private void handleOnDownloadProcessing(VideoTaskItem taskItem, IDownloadListener listener) {
        listener.onDownloadProgress(taskItem);
        markDownloadProgressInfoUpdateEvent(taskItem);
    }

    private void handleOnDownloadSpeed(VideoTaskItem taskItem, IDownloadListener listener) {
        listener.onDownloadSpeed(taskItem);
    }

    private void handleOnDownloadPause(VideoTaskItem taskItem, IDownloadListener listener) {
        listener.onDownloadPause(taskItem);
        removeDownloadQueue(taskItem);
    }

    private void handleOnDownloadError(VideoTaskItem taskItem, IDownloadListener listener) {
        listener.onDownloadError(taskItem);
        removeDownloadQueue(taskItem);
    }

    private void handleOnDownloadSuccess(VideoTaskItem taskItem, IDownloadListener listener) {
        listener.onDownloadSuccess(taskItem);
        removeDownloadQueue(taskItem);
        markDownloadFinishEvent(taskItem);
    }

    private void markDownloadInfoAddEvent(VideoTaskItem taskItem) {
        WorkerThreadHandler.submitRunnableTask(new Runnable() {
            @Override
            public void run() {
                mVideoDatabaseHelper.markDownloadInfoAddEvent(taskItem);
            }
        });
    }
    private void markDownloadProgressInfoUpdateEvent(VideoTaskItem taskItem) {
        long currentTime = System.currentTimeMillis();
        if (taskItem.getLastUpdateTime() + 1000 < currentTime) {
            WorkerThreadHandler.submitRunnableTask(new Runnable() {
                @Override
                public void run() {
                    mVideoDatabaseHelper.markDownloadProgressInfoUpdateEvent(taskItem);
                }
            });
            taskItem.setLastUpdateTime(currentTime);
        }
    }

    private void markDownloadFinishEvent(VideoTaskItem taskItem) {
        WorkerThreadHandler.submitRunnableTask(new Runnable() {
            @Override
            public void run() {
                mVideoDatabaseHelper.markDownloadProgressInfoUpdateEvent(taskItem);
            }
        });
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

    public void setConcurrentCount(int count) {
        if (mConfig != null) {
            mConfig.setConcurrentCount(count);
        }
    }

    public void setIgnoreAllCertErrors(boolean enable) {
        if (mConfig != null) {
            mConfig.setIgnoreAllCertErrors(enable);
        }
    }
}
