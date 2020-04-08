package com.jeffmony.downloader;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.jeffmony.downloader.listener.IVideoInfoListener;
import com.jeffmony.downloader.m3u8.M3U8;
import com.jeffmony.downloader.model.Video;
import com.jeffmony.downloader.model.VideoDownloadInfo;
import com.jeffmony.downloader.model.VideoTaskItem;
import com.jeffmony.downloader.model.VideoTaskState;
import com.jeffmony.downloader.utils.VideoDownloadUtils;

import java.io.File;
import java.util.HashMap;

public class VideoDownloadManager {
    private static final String TAG = "VideoDownloadManager";
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
    private VideoDownloadQueue mVideoDownloadQueue;
    private VideoDownloadHandler mVideoDownloadHandler = new VideoDownloadHandler();
    private VideoDownloadConfig mConfig;

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

    private void parseNetworkVideoInfo(VideoTaskItem taskItem, VideoDownloadInfo downloadInfo, HashMap<String, String> headers) {
        VideoInfoParserManager.getInstance().parseVideoInfo(downloadInfo, new IVideoInfoListener() {
            @Override
            public void onFinalUrl(String finalUrl) {

            }

            @Override
            public void onBaseVideoInfoSuccess(VideoDownloadInfo info) {

            }

            @Override
            public void onBaseVideoInfoFailed(Throwable error) {

            }

            @Override
            public void onM3U8InfoSuccess(VideoDownloadInfo info, M3U8 m3u8) {

            }

            @Override
            public void onLiveM3U8Callback(VideoDownloadInfo info) {

            }

            @Override
            public void onM3U8InfoFailed(Throwable error) {

            }
        }, mConfig, headers, mConfig.shouldRedirect());
    }

    class VideoDownloadHandler extends Handler {

        public VideoDownloadHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
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
