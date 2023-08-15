package com.jeffmony.downloader;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import com.jeffmony.downloader.common.DownloadConstants;
import com.jeffmony.downloader.database.VideoDownloadDatabaseHelper;
import com.jeffmony.downloader.listener.DownloadListener;
import com.jeffmony.downloader.listener.IDownloadInfosCallback;
import com.jeffmony.downloader.listener.IDownloadTaskListener;
import com.jeffmony.downloader.listener.IVideoInfoListener;
import com.jeffmony.downloader.listener.IVideoInfoParseListener;
import com.jeffmony.downloader.m3u8.M3U8;
import com.jeffmony.downloader.model.Video;
import com.jeffmony.downloader.model.VideoTaskItem;
import com.jeffmony.downloader.model.VideoTaskState;
import com.jeffmony.downloader.listener.IM3U8MergeResultListener;
import com.jeffmony.downloader.task.M3U8VideoDownloadTask;
import com.jeffmony.downloader.task.MultiSegVideoDownloadTask;
import com.jeffmony.downloader.task.VideoDownloadTask;
import com.jeffmony.downloader.utils.ContextUtils;
import com.jeffmony.downloader.utils.DownloadExceptionUtils;
import com.jeffmony.downloader.utils.LogUtils;
import com.jeffmony.downloader.utils.VideoDownloadUtils;
import com.jeffmony.downloader.utils.VideoStorageUtils;
import com.jeffmony.downloader.utils.WorkerThreadHandler;
import com.jeffmony.m3u8library.VideoProcessManager;
import com.jeffmony.m3u8library.listener.IVideoTransformListener;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class VideoDownloadManager {
    private static volatile VideoDownloadManager sInstance = null;
    private DownloadListener mGlobalDownloadListener = null;
    private VideoDownloadDatabaseHelper mVideoDatabaseHelper = null;
    private VideoDownloadQueue mVideoDownloadQueue;
    private Object mQueueLock = new Object();
    private VideoDownloadConfig mConfig;

    private VideoDownloadHandler mVideoDownloadHandler;
    private List<IDownloadInfosCallback> mDownloadInfoCallbacks = new CopyOnWriteArrayList<>();
    private Map<String, VideoDownloadTask> mVideoDownloadTaskMap = new ConcurrentHashMap<>();
    private Map<String, VideoTaskItem> mVideoItemTaskMap = new ConcurrentHashMap<>();

    public static class Build {
        private String mCacheRoot;
        private int mReadTimeOut = 60 * 1000;              // 60 seconds
        private int mConnTimeOut = 60 * 1000;              // 60 seconds
        private boolean mIgnoreCertErrors = false;
        private int mConcurrentCount = 3;
        private boolean mShouldM3U8Merged = false;

        public Build(Context context) {
            ContextUtils.initApplicationContext(context);
        }

        //设置下载目录
        public Build setCacheRoot(String cacheRoot) {
            mCacheRoot = cacheRoot;
            return this;
        }

        //设置超时时间
        public Build setTimeOut(int readTimeOut, int connTimeOut) {
            mReadTimeOut = readTimeOut;
            mConnTimeOut = connTimeOut;
            return this;
        }

        //设置并发下载的个数
        public Build setConcurrentCount(int count) {
            mConcurrentCount = count;
            return this;
        }

        //是否信任证书
        public Build setIgnoreCertErrors(boolean ignoreCertErrors) {
            mIgnoreCertErrors = ignoreCertErrors;
            return this;
        }

        //M3U8下载成功之后是否自动合并
        public Build setShouldM3U8Merged(boolean shouldM3U8Merged) {
            mShouldM3U8Merged = shouldM3U8Merged;
            return this;
        }

        public VideoDownloadConfig buildConfig() {
            return new VideoDownloadConfig(mCacheRoot, mReadTimeOut, mConnTimeOut, mIgnoreCertErrors, mConcurrentCount, mShouldM3U8Merged);
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

    public void setShouldM3U8Merged(boolean enable) {
        if (mConfig != null) {
            LogUtils.w(DownloadConstants.TAG, "setShouldM3U8Merged = " + enable);
            mConfig.setShouldM3U8Merged(enable);
        }
    }

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
        //如果为null, 会crash
        mConfig = config;
        VideoDownloadUtils.setDownloadConfig(config);
        mVideoDatabaseHelper = new VideoDownloadDatabaseHelper(ContextUtils.getApplicationContext());
        HandlerThread stateThread = new HandlerThread("Video_download_state_thread");
        stateThread.start();
        mVideoDownloadHandler = new VideoDownloadHandler(stateThread.getLooper());
    }

    public VideoDownloadConfig downloadConfig() {
        return mConfig;
    }

    public void fetchDownloadItems(IDownloadInfosCallback callback) {
        mDownloadInfoCallbacks.add(callback);
        mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_FETCH_DOWNLOAD_INFO).sendToTarget();
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
        mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_PENDING, tempTaskItem).sendToTarget();
        startDownload(taskItem, null);
    }

    public void startDownload(VideoTaskItem taskItem, Map<String, String> headers) {
        if (taskItem == null || TextUtils.isEmpty(taskItem.getUrl()))
            return;
        parseVideoDownloadInfo(taskItem, headers);
    }

    private void parseVideoDownloadInfo(VideoTaskItem taskItem, Map<String, String> headers) {
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

    private void parseExistVideoDownloadInfo(final VideoTaskItem taskItem, final Map<String, String> headers) {
        if (taskItem.isHlsType()) {
            VideoInfoParserManager.getInstance().parseLocalM3U8File(taskItem, new IVideoInfoParseListener() {
                @Override
                public void onM3U8FileParseSuccess(VideoTaskItem info, M3U8 m3u8) {
                    startM3U8VideoDownloadTask(taskItem, m3u8, headers);
                }

                @Override
                public void onM3U8FileParseFailed(VideoTaskItem info, Throwable error) {
                    parseNetworkVideoInfo(taskItem, headers);
                }
            });
        } else {
            startBaseVideoDownloadTask(taskItem, headers);
        }
    }

    private void parseNetworkVideoInfo(final VideoTaskItem taskItem, final Map<String, String> headers) {
        VideoInfoParserManager.getInstance().parseVideoInfo(taskItem, new IVideoInfoListener() {
            @Override
            public void onFinalUrl(String finalUrl) {
            }

            @Override
            public void onBaseVideoInfoSuccess(VideoTaskItem taskItem) {
                startBaseVideoDownloadTask(taskItem, headers);
            }

            @Override
            public void onBaseVideoInfoFailed(Throwable error) {
                LogUtils.w(DownloadConstants.TAG, "onInfoFailed error=" + error);
                int errorCode = DownloadExceptionUtils.getErrorCode(error);
                taskItem.setErrorCode(errorCode);
                taskItem.setTaskState(VideoTaskState.ERROR);
                mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_ERROR, taskItem).sendToTarget();
            }

            @Override
            public void onM3U8InfoSuccess(VideoTaskItem info, M3U8 m3u8) {
                taskItem.setMimeType(info.getMimeType());
                startM3U8VideoDownloadTask(taskItem, m3u8, headers);
            }

            @Override
            public void onLiveM3U8Callback(VideoTaskItem info) {
                LogUtils.w(DownloadConstants.TAG, "onLiveM3U8Callback cannot be cached.");
                taskItem.setErrorCode(DownloadExceptionUtils.LIVE_M3U8_ERROR);
                taskItem.setTaskState(VideoTaskState.ERROR);
                mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_ERROR, taskItem).sendToTarget();
            }

            @Override
            public void onM3U8InfoFailed(Throwable error) {
                LogUtils.w(DownloadConstants.TAG, "onM3U8InfoFailed : " + error);
                int errorCode = DownloadExceptionUtils.getErrorCode(error);
                taskItem.setErrorCode(errorCode);
                taskItem.setTaskState(VideoTaskState.ERROR);
                mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_ERROR, taskItem).sendToTarget();
            }
        }, headers);
    }

    private void startM3U8VideoDownloadTask(final VideoTaskItem taskItem, M3U8 m3u8, Map<String, String> headers) {
        taskItem.setTaskState(VideoTaskState.PREPARE);
        mVideoItemTaskMap.put(taskItem.getUrl(), taskItem);
        VideoTaskItem tempTaskItem = (VideoTaskItem) taskItem.clone();
        mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_PREPARE, tempTaskItem).sendToTarget();
        synchronized (mQueueLock) {
            if (mVideoDownloadQueue.getDownloadingCount() >= mConfig.getConcurrentCount()) {
                return;
            }
        }
        VideoDownloadTask downloadTask = mVideoDownloadTaskMap.get(taskItem.getUrl());
        if (downloadTask == null) {
            downloadTask = new M3U8VideoDownloadTask(taskItem, m3u8, headers);
            mVideoDownloadTaskMap.put(taskItem.getUrl(), downloadTask);
        }
        startDownloadTask(downloadTask, taskItem);
    }

    private void startBaseVideoDownloadTask(VideoTaskItem taskItem, Map<String, String> headers) {
        taskItem.setTaskState(VideoTaskState.PREPARE);
        mVideoItemTaskMap.put(taskItem.getUrl(), taskItem);
        VideoTaskItem tempTaskItem = (VideoTaskItem) taskItem.clone();
        mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_PREPARE, tempTaskItem).sendToTarget();
        synchronized (mQueueLock) {
            if (mVideoDownloadQueue.getDownloadingCount() >= mConfig.getConcurrentCount()) {
                return;
            }
        }
        VideoDownloadTask downloadTask = mVideoDownloadTaskMap.get(taskItem.getUrl());
        if (downloadTask == null) {
//            downloadTask = new BaseVideoDownloadTask(taskItem, headers);
            downloadTask = new MultiSegVideoDownloadTask(taskItem, headers);
            mVideoDownloadTaskMap.put(taskItem.getUrl(), downloadTask);
        }
        startDownloadTask(downloadTask, taskItem);
    }

    private void startDownloadTask(VideoDownloadTask downloadTask, VideoTaskItem taskItem) {
        if (downloadTask != null) {
            downloadTask.setDownloadTaskListener(new IDownloadTaskListener() {
                @Override
                public void onTaskStart(String url) {
                    taskItem.setTaskState(VideoTaskState.START);
                    mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_START, taskItem).sendToTarget();
                }

                @Override
                public void onTaskProgress(float percent, long cachedSize, long totalSize, float speed) {
                    if (!taskItem.isPaused() && (!taskItem.isErrorState() || !taskItem.isSuccessState())) {
                        taskItem.setTaskState(VideoTaskState.DOWNLOADING);
                        taskItem.setPercent(percent);
                        taskItem.setSpeed(speed);
                        taskItem.setDownloadSize(cachedSize);
                        taskItem.setTotalSize(totalSize);
                        mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_PROCESSING, taskItem).sendToTarget();
                    }
                }

                @Override
                public void onTaskProgressForM3U8(float percent, long cachedSize, int curTs, int totalTs, float speed) {
                    if (!taskItem.isPaused() && (!taskItem.isErrorState() || !taskItem.isSuccessState())) {
                        taskItem.setTaskState(VideoTaskState.DOWNLOADING);
                        taskItem.setPercent(percent);
                        taskItem.setSpeed(speed);
                        taskItem.setDownloadSize(cachedSize);
                        taskItem.setCurTs(curTs);
                        taskItem.setTotalTs(totalTs);
                        mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_PROCESSING, taskItem).sendToTarget();
                    }
                }

                @Override
                public void onTaskPaused() {
                    if (!taskItem.isErrorState() || !taskItem.isSuccessState()) {
                        taskItem.setTaskState(VideoTaskState.PAUSE);
                        taskItem.setPaused(true);
                        mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_PAUSE, taskItem).sendToTarget();
                        mVideoDownloadHandler.removeMessages(DownloadConstants.MSG_DOWNLOAD_PROCESSING);
                    }
                }

                @Override
                public void onTaskFinished(long totalSize) {
                    if (taskItem.getTaskState() != VideoTaskState.SUCCESS) {
                        taskItem.setTaskState(VideoTaskState.SUCCESS);
                        taskItem.setDownloadSize(totalSize);
                        taskItem.setIsCompleted(true);
                        taskItem.setPercent(100f);
                        if (taskItem.isHlsType()) {
                            taskItem.setFilePath(taskItem.getSaveDir() + File.separator + taskItem.getFileHash() + "_" + VideoDownloadUtils.LOCAL_M3U8);
                            taskItem.setFileName(taskItem.getFileHash() + "_" + VideoDownloadUtils.LOCAL_M3U8);
                        } else {
                            taskItem.setFilePath(taskItem.getSaveDir() + File.separator + taskItem.getFileHash() + VideoDownloadUtils.VIDEO_SUFFIX);
                            taskItem.setFileName(taskItem.getFileHash() + VideoDownloadUtils.VIDEO_SUFFIX);
                        }
                        mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_SUCCESS, taskItem).sendToTarget();
                        mVideoDownloadHandler.removeMessages(DownloadConstants.MSG_DOWNLOAD_PROCESSING);
                    }
                }

                @Override
                public void onTaskFailed(Throwable e) {
                    if (!taskItem.isSuccessState()) {
                        int errorCode = DownloadExceptionUtils.getErrorCode(e);
                        taskItem.setErrorCode(errorCode);
                        taskItem.setTaskState(VideoTaskState.ERROR);
                        mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_ERROR, taskItem).sendToTarget();
                        mVideoDownloadHandler.removeMessages(DownloadConstants.MSG_DOWNLOAD_PROCESSING);
                    }
                }
            });

            downloadTask.startDownload();
        }
    }

    public String getDownloadPath() {
        if (mConfig != null) {
            return mConfig.getCacheRoot();
        }
        return null;
    }

    public void deleteAllVideoFiles() {
        try {
            VideoStorageUtils.clearVideoCacheDir();
            mVideoItemTaskMap.clear();
            mVideoDownloadTaskMap.clear();
            mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DELETE_ALL_FILES).sendToTarget();
        } catch (Exception e) {
            LogUtils.w(DownloadConstants.TAG, "clearVideoCacheDir failed, exception = " + e.getMessage());
        }
    }

    public void pauseAllDownloadTasks() {
        synchronized (mQueueLock) {
            List<VideoTaskItem> taskList = mVideoDownloadQueue.getDownloadList();
            LogUtils.i(DownloadConstants.TAG, "pauseAllDownloadTasks queue size="+taskList.size());
            List<String> pausedUrlList = new ArrayList<>();
            for (VideoTaskItem taskItem : taskList) {
                if (taskItem.isPendingTask()) {
                    mVideoDownloadQueue.remove(taskItem);
                    taskItem.setTaskState(VideoTaskState.PAUSE);
                    mVideoItemTaskMap.put(taskItem.getUrl(), taskItem);
                    mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_PAUSE, taskItem).sendToTarget();
                } else {
                    pausedUrlList.add(taskItem.getUrl());
                }
            }
            pauseDownloadTask(pausedUrlList);
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
            pauseDownloadTask(taskItem);
            String saveName = VideoDownloadUtils.computeMD5(taskItem.getUrl());
            File file = new File(cacheFilePath + File.separator + saveName);
            WorkerThreadHandler.submitRunnableTask(() -> mVideoDatabaseHelper.deleteDownloadItemByUrl(taskItem));
            try {
                if (shouldDeleteSourceFile) {
                    VideoStorageUtils.delete(file);
                }
                if (mVideoDownloadTaskMap.containsKey(taskItem.getUrl())) {
                    mVideoDownloadTaskMap.remove(taskItem.getUrl());
                }
                taskItem.reset();
                mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_DEFAULT, taskItem).sendToTarget();
            } catch (Exception e) {
                LogUtils.w(DownloadConstants.TAG, "Delete file: " + file + " failed, exception=" + e.getMessage());
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
            LogUtils.w(DownloadConstants.TAG, "removeDownloadQueue size=" + mVideoDownloadQueue.size() + "," + mVideoDownloadQueue.getDownloadingCount() + "," + mVideoDownloadQueue.getPendingCount());
            int pendingCount = mVideoDownloadQueue.getPendingCount();
            int downloadingCount = mVideoDownloadQueue.getDownloadingCount();
            while (downloadingCount < mConfig.getConcurrentCount() && pendingCount > 0) {
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
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == DownloadConstants.MSG_FETCH_DOWNLOAD_INFO) {
                dispatchDownloadInfos();
            } else if (msg.what == DownloadConstants.MSG_DELETE_ALL_FILES) {
                //删除数据库中所有记录
                WorkerThreadHandler.submitRunnableTask(() -> mVideoDatabaseHelper.deleteAllDownloadInfos());
            } else {
                dispatchDownloadMessage(msg.what, (VideoTaskItem) msg.obj);
            }
        }

        private void dispatchDownloadInfos() {
            WorkerThreadHandler.submitRunnableTask(() -> {
                List<VideoTaskItem> taskItems = mVideoDatabaseHelper.getDownloadInfos();
                for (VideoTaskItem taskItem : taskItems) {
                    if (mConfig != null && mConfig.shouldM3U8Merged() && taskItem.isHlsType()) {
                        doMergeTs(taskItem, taskItem1 -> {
                            mVideoItemTaskMap.put(taskItem1.getUrl(), taskItem1);
                            markDownloadFinishEvent(taskItem1);
                        });
                    } else {
                        mVideoItemTaskMap.put(taskItem.getUrl(), taskItem);
                    }
                }

                for (IDownloadInfosCallback callback : mDownloadInfoCallbacks) {
                    callback.onDownloadInfos(taskItems);
                }
            });
        }

        private void dispatchDownloadMessage(int msg, VideoTaskItem taskItem) {
            switch (msg) {
                case DownloadConstants.MSG_DOWNLOAD_DEFAULT:
                    handleOnDownloadDefault(taskItem);
                    break;
                case DownloadConstants.MSG_DOWNLOAD_PENDING:
                    handleOnDownloadPending(taskItem);
                    break;
                case DownloadConstants.MSG_DOWNLOAD_PREPARE:
                    handleOnDownloadPrepare(taskItem);
                    break;
                case DownloadConstants.MSG_DOWNLOAD_START:
                    handleOnDownloadStart(taskItem);
                    break;
                case DownloadConstants.MSG_DOWNLOAD_PROCESSING:
                    handleOnDownloadProcessing(taskItem);
                    break;
                case DownloadConstants.MSG_DOWNLOAD_PAUSE:
                    handleOnDownloadPause(taskItem);
                    break;
                case DownloadConstants.MSG_DOWNLOAD_ERROR:
                    handleOnDownloadError(taskItem);
                    break;
                case DownloadConstants.MSG_DOWNLOAD_SUCCESS:
                    handleOnDownloadSuccess(taskItem);
                    break;
            }
        }
    }

    private void handleOnDownloadDefault(VideoTaskItem taskItem) {
        mGlobalDownloadListener.onDownloadDefault(taskItem);
    }

    private void handleOnDownloadPending(VideoTaskItem taskItem) {
        mGlobalDownloadListener.onDownloadPending(taskItem);
    }

    private void handleOnDownloadPrepare(VideoTaskItem taskItem) {
        mGlobalDownloadListener.onDownloadPrepare(taskItem);
        markDownloadInfoAddEvent(taskItem);
    }

    private void handleOnDownloadStart(VideoTaskItem taskItem) {
        mGlobalDownloadListener.onDownloadStart(taskItem);
    }

    private void handleOnDownloadProcessing(VideoTaskItem taskItem) {
        mGlobalDownloadListener.onDownloadProgress(taskItem);
        markDownloadProgressInfoUpdateEvent(taskItem);
    }

    private void handleOnDownloadPause(VideoTaskItem taskItem) {
        mGlobalDownloadListener.onDownloadPause(taskItem);
        removeDownloadQueue(taskItem);
    }

    private void handleOnDownloadError(VideoTaskItem taskItem) {
        mGlobalDownloadListener.onDownloadError(taskItem);
        removeDownloadQueue(taskItem);
    }

    private void handleOnDownloadSuccess(VideoTaskItem taskItem) {
        removeDownloadQueue(taskItem);

        LogUtils.i(DownloadConstants.TAG, "handleOnDownloadSuccess shouldM3U8Merged="+mConfig.shouldM3U8Merged() + ", isHlsType="+taskItem.isHlsType());
        if (mConfig.shouldM3U8Merged() && taskItem.isHlsType()) {
            doMergeTs(taskItem, taskItem1 -> {
                mGlobalDownloadListener.onDownloadSuccess(taskItem1);
                markDownloadFinishEvent(taskItem1);
            });
        } else {
            mGlobalDownloadListener.onDownloadSuccess(taskItem);
            markDownloadFinishEvent(taskItem);
        }
    }

    private void doMergeTs(VideoTaskItem taskItem, IM3U8MergeResultListener listener) {
        if (taskItem == null || TextUtils.isEmpty(taskItem.getFilePath())) {
            listener.onCallback(taskItem);
            return;
        }
        LogUtils.i(DownloadConstants.TAG, "VideoMerge doMergeTs taskItem=" + taskItem);
        String inputPath = taskItem.getFilePath();
        if (TextUtils.isEmpty(taskItem.getFileHash())) {
            taskItem.setFileHash(VideoDownloadUtils.computeMD5(taskItem.getUrl()));
        }
        String outputPath = inputPath.substring(0, inputPath.lastIndexOf("/")) + File.separator + taskItem.getFileHash() + "_" + VideoDownloadUtils.OUTPUT_VIDEO;
        File outputFile = new File(outputPath);
        if (outputFile.exists()) {
            outputFile.delete();
        }

        VideoProcessManager.getInstance().transformM3U8ToMp4(inputPath, outputPath, new IVideoTransformListener() {
            @Override
            public void onTransformProgress(float progress) {

            }

            @Override
            public void onTransformFailed(int err) {
                retryMerge(taskItem, listener);
                // LogUtils.i(DownloadConstants.TAG, "VideoMerge onTransformFailed err=" + err);
                // File outputFile = new File(outputPath);
                // if (outputFile.exists()) {
                //     outputFile.delete();
                // }
                // listener.onCallback(taskItem);
            }

            @Override
            public void onTransformFinished() {
                LogUtils.i(DownloadConstants.TAG, "VideoMerge onTransformFinished outputPath=" + outputPath);
                taskItem.setFileName(VideoDownloadUtils.OUTPUT_VIDEO);
                taskItem.setFilePath(outputPath);
                taskItem.setMimeType(Video.Mime.MIME_TYPE_MP4);
                taskItem.setVideoType(Video.Type.MP4_TYPE);
                listener.onCallback(taskItem);

                /// delete source file
                File outputFile = new File(outputPath);
                File[] files = outputFile.getParentFile().listFiles();
                for (File subFile : files) {
                    String subFilePath = subFile.getAbsolutePath();
                    if (!subFilePath.endsWith(VideoDownloadUtils.OUTPUT_VIDEO)) {
                        subFile.delete();
                    }
                }
            }
        });
    }

    private void retryMerge(VideoTaskItem taskItem, IM3U8MergeResultListener listener) {
        LogUtils.i(DownloadConstants.TAG, "VideoMerge retryMerge taskItem=" + taskItem);
        String inputPath = taskItem.getFilePath();
        if (TextUtils.isEmpty(taskItem.getFileHash())) {
            taskItem.setFileHash(VideoDownloadUtils.computeMD5(taskItem.getUrl()));
        }
        String outputPath = inputPath.substring(0, inputPath.lastIndexOf("/")) + File.separator + taskItem.getFileHash() + "_" + VideoDownloadUtils.OUTPUT_VIDEO;
        File outputFile = new File(outputPath);
        if (outputFile.exists()) {
            outputFile.delete();
        }
        inputPath = inputPath.substring(0, inputPath.lastIndexOf("/")) + File.separator + taskItem.getFileHash() + "_" + VideoDownloadUtils.LOCAL_M3U8_WITH_KEY;
        VideoProcessManager.getInstance().transformM3U8ToMp4(inputPath, outputPath, new IVideoTransformListener() {
            @Override
            public void onTransformProgress(float progress) {

            }

            @Override
            public void onTransformFailed(int err) {
                LogUtils.i(DownloadConstants.TAG, "VideoMerge onTransformFailed err=" + err);
                File outputFile = new File(outputPath);
                if (outputFile.exists()) {
                    outputFile.delete();
                }
                listener.onCallback(taskItem);
            }

            @Override
            public void onTransformFinished() {
                LogUtils.i(DownloadConstants.TAG, "VideoMerge onTransformFinished outputPath=" + outputPath);
                taskItem.setFileName(VideoDownloadUtils.OUTPUT_VIDEO);
                taskItem.setFilePath(outputPath);
                taskItem.setMimeType(Video.Mime.MIME_TYPE_MP4);
                taskItem.setVideoType(Video.Type.MP4_TYPE);
                listener.onCallback(taskItem);

                /// delete source file
                File outputFile = new File(outputPath);
                File[] files = outputFile.getParentFile().listFiles();
                for (File subFile : files) {
                    String subFilePath = subFile.getAbsolutePath();
                    if (!subFilePath.endsWith(VideoDownloadUtils.OUTPUT_VIDEO)) {
                        subFile.delete();
                    }
                }
            }
        });
    }

    private void markDownloadInfoAddEvent(VideoTaskItem taskItem) {
        WorkerThreadHandler.submitRunnableTask(() -> mVideoDatabaseHelper.markDownloadInfoAddEvent(taskItem));
    }

    private void markDownloadProgressInfoUpdateEvent(VideoTaskItem taskItem) {
        long currentTime = System.currentTimeMillis();
        if (taskItem.getLastUpdateTime() + 1000 < currentTime) {
            WorkerThreadHandler.submitRunnableTask(() -> mVideoDatabaseHelper.markDownloadProgressInfoUpdateEvent(taskItem));
            taskItem.setLastUpdateTime(currentTime);
        }
    }

    private void markDownloadFinishEvent(VideoTaskItem taskItem) {
        WorkerThreadHandler.submitRunnableTask(() -> mVideoDatabaseHelper.markDownloadProgressInfoUpdateEvent(taskItem));
    }
}
