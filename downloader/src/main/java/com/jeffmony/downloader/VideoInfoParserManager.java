package com.jeffmony.downloader;

import android.net.Uri;
import android.text.TextUtils;

import com.jeffmony.downloader.listener.IVideoInfoListener;
import com.jeffmony.downloader.listener.IVideoInfoParseListener;
import com.jeffmony.downloader.m3u8.M3U8;
import com.jeffmony.downloader.m3u8.M3U8Utils;
import com.jeffmony.downloader.model.Video;
import com.jeffmony.downloader.model.VideoTaskItem;
import com.jeffmony.downloader.utils.DownloadExceptionUtils;
import com.jeffmony.downloader.utils.HttpUtils;
import com.jeffmony.downloader.utils.LogUtils;
import com.jeffmony.downloader.utils.VideoDownloadUtils;
import com.jeffmony.downloader.utils.WorkerThreadHandler;

import java.io.File;
import java.net.Proxy;
import java.util.HashMap;

public class VideoInfoParserManager {

    private static final String TAG = "VideoInfoParserManager";

    private static VideoInfoParserManager sInstance;
    private VideoDownloadConfig mConfig;

    public static VideoInfoParserManager getInstance() {
        if (sInstance == null) {
            synchronized (VideoInfoParserManager.class) {
                if (sInstance == null) {
                    sInstance = new VideoInfoParserManager();
                }
            }
        }
        return sInstance;
    }

    public void initConfig(VideoDownloadConfig config) {
        mConfig = config;
    }

    public synchronized void parseVideoInfo(final VideoTaskItem taskItem, IVideoInfoListener listener,
                                            final HashMap<String, String> headers,
                                            final boolean shouldRedirect) {
        WorkerThreadHandler.submitRunnableTask(new Runnable() {
            @Override
            public void run() {
                doParseVideoInfoTask(taskItem, listener, headers, shouldRedirect);
            }
        });
    }

    private void doParseVideoInfoTask(VideoTaskItem taskItem, IVideoInfoListener listener, HashMap<String, String> headers, boolean shouldRedirect) {
        try {
            if (taskItem == null) {
                listener.onBaseVideoInfoFailed(new Throwable("Video info is null."));
                return;
            }
            if (!HttpUtils.matchHttpSchema(taskItem.getUrl())) {
                listener.onBaseVideoInfoFailed(
                        new Throwable("Can parse the request resource's schema."));
                return;
            }

            String finalUrl = taskItem.getUrl();

            // Redirect is enabled, send redirect request to get final location.
            if (mConfig.shouldRedirect() && shouldRedirect) {
                finalUrl =
                        HttpUtils.getFinalUrl(mConfig, taskItem.getUrl(), null, headers);
                if (TextUtils.isEmpty(finalUrl)) {
                    listener.onBaseVideoInfoFailed(new Throwable("FinalUrl is null."));
                    return;
                }
                listener.onFinalUrl(finalUrl);
            }
            taskItem.setFinalUrl(finalUrl);
            Uri uri = Uri.parse(finalUrl);
            String fileName = uri.getLastPathSegment();
            LogUtils.i(TAG, "parseVideoInfo  fileName = " + fileName);
            // By suffix name.
            if (fileName != null) {
                fileName = fileName.toLowerCase();
                if (fileName.endsWith(".m3u8")) {
                    taskItem.setMimeType(Video.Mime.M3U8);
                    parseM3U8Info(taskItem, listener, null, headers);
                    return;
                } else if (fileName.endsWith(".mp4")) {
                    taskItem.setMimeType(Video.Mime.MP4);
                    taskItem.setVideoType(Video.Type.MP4_TYPE);
                    listener.onBaseVideoInfoSuccess(taskItem);
                    return;
                } else if (fileName.endsWith(".mov")) {
                    taskItem.setMimeType(Video.Mime.MOV);
                    taskItem.setVideoType(Video.Type.QUICKTIME_TYPE);
                    listener.onBaseVideoInfoSuccess(taskItem);
                    return;
                } else if (fileName.endsWith(".webm")) {
                    taskItem.setMimeType(Video.Mime.WEBM);
                    taskItem.setVideoType(Video.Type.WEBM_TYPE);
                    listener.onBaseVideoInfoSuccess(taskItem);
                    return;
                } else if (fileName.endsWith(".3gp")) {
                    taskItem.setMimeType(Video.Mime.GP3);
                    taskItem.setVideoType(Video.Type.GP3_TYPE);
                    listener.onBaseVideoInfoSuccess(taskItem);
                    return;
                } else if (fileName.endsWith(".mkv")) {
                    taskItem.setMimeType(Video.Mime.MKV);
                    taskItem.setVideoType(Video.Type.MKV_TYPE);
                    listener.onBaseVideoInfoSuccess(taskItem);
                    return;
                }
            }
            // Add more video mimeType.
            String mimeType = HttpUtils.getMimeType(mConfig, finalUrl, null, headers);
            LogUtils.i(TAG,"parseVideoInfo mimeType=" + mimeType);
            if (mimeType != null) {
                mimeType = mimeType.toLowerCase();
                taskItem.setMimeType(mimeType);
                if (mimeType.contains(Video.Mime.MIME_TYPE_MP4)) {
                    taskItem.setVideoType(Video.Type.MP4_TYPE);
                    listener.onBaseVideoInfoSuccess(taskItem);
                } else if (isM3U8Mimetype(mimeType)) {
                    parseM3U8Info(taskItem, listener, null, headers);
                } else if (mimeType.contains(Video.Mime.MIME_TYPE_WEBM)) {
                    taskItem.setVideoType(Video.Type.WEBM_TYPE);
                    listener.onBaseVideoInfoSuccess(taskItem);
                } else if (mimeType.contains(Video.Mime.MIME_TYPE_QUICKTIME)) {
                    taskItem.setVideoType(Video.Type.QUICKTIME_TYPE);
                    listener.onBaseVideoInfoSuccess(taskItem);
                } else if (mimeType.contains(Video.Mime.MIME_TYPE_3GP)) {
                    taskItem.setVideoType(Video.Type.GP3_TYPE);
                    listener.onBaseVideoInfoSuccess(taskItem);
                } else if (mimeType.contains(Video.Mime.MIME_TYPE_MKV)) {
                    taskItem.setVideoType(Video.Type.MKV_TYPE);
                    listener.onBaseVideoInfoSuccess(taskItem);
                } else {
                    listener.onBaseVideoInfoFailed(new VideoDownloadException(DownloadExceptionUtils.MIMETYPE_NOT_FOUND_STRING));
                }
            } else {
                listener.onBaseVideoInfoFailed(new VideoDownloadException(DownloadExceptionUtils.MIMETYPE_NULL_ERROR_STRING));
            }

        } catch (Exception e) {
            listener.onBaseVideoInfoFailed(e);
        }
    }

    private void parseM3U8Info(VideoTaskItem taskItem, IVideoInfoListener listener, Proxy proxy,
                               HashMap<String, String> headers) {
        try {
            M3U8 m3u8 =
                    M3U8Utils.parseM3U8Info(mConfig, taskItem.getUrl(), false, null);
            // HLS LIVE video cannot be proxy cached.
            if (m3u8.hasEndList()) {
                String saveName = VideoDownloadUtils.computeMD5(taskItem.getUrl());
                File dir = new File(mConfig.getCacheRoot(), saveName);
                if (!dir.exists()) {
                    dir.mkdir();
                }
                M3U8Utils.createRemoteM3U8(dir, m3u8);

                taskItem.setSaveDir(dir.getAbsolutePath());
                taskItem.setVideoType(Video.Type.HLS_TYPE);
                listener.onM3U8InfoSuccess(taskItem, m3u8);
            } else {
                taskItem.setVideoType(Video.Type.HLS_LIVE_TYPE);
                listener.onLiveM3U8Callback(taskItem);
            }
        } catch (Exception e) {
            e.printStackTrace();
            listener.onM3U8InfoFailed(e);
        }
    }

    public void parseM3U8File(VideoTaskItem taskItem,
                              IVideoInfoParseListener callback) {
        File remoteM3U8File = new File(taskItem.getSaveDir(), "remote.m3u8");
        if (!remoteM3U8File.exists()) {
            callback.onM3U8FileParseFailed(
                    taskItem, new Throwable("Cannot find remote.m3u8 file."));
            return;
        }
        try {
            M3U8 m3u8 = M3U8Utils.parseM3U8Info(mConfig, taskItem.getUrl(), true,
                    remoteM3U8File);
            callback.onM3U8FileParseSuccess(taskItem, m3u8);
        } catch (Exception e) {
            e.printStackTrace();
            callback.onM3U8FileParseFailed(taskItem, e);
        }
    }

    private boolean isM3U8Mimetype(String mimeType) {
        return mimeType.contains(Video.Mime.MIME_TYPE_M3U8_1) ||
                mimeType.contains(Video.Mime.MIME_TYPE_M3U8_2) ||
                mimeType.contains(Video.Mime.MIME_TYPE_M3U8_3) ||
                mimeType.contains(Video.Mime.MIME_TYPE_M3U8_4);
    }

}
