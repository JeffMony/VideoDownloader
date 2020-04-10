package com.jeffmony.downloader;

import android.net.Uri;
import android.text.TextUtils;

import com.jeffmony.downloader.listener.IVideoInfoListener;
import com.jeffmony.downloader.listener.IVideoInfoParseListener;
import com.jeffmony.downloader.m3u8.M3U8;
import com.jeffmony.downloader.m3u8.M3U8Utils;
import com.jeffmony.downloader.model.Video;
import com.jeffmony.downloader.model.VideoDownloadInfo;
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
    private IVideoInfoListener mListener;

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

    public synchronized void parseVideoInfo(final VideoDownloadInfo downloadInfo, IVideoInfoListener listener,
                                            VideoDownloadConfig config, final HashMap<String, String> headers,
                                            final boolean shouldRedirect) {
        mConfig = config;
        mListener = listener;

        WorkerThreadHandler.submitRunnableTask(new Runnable() {
            @Override
            public void run() {
                doParseVideoInfoTask(downloadInfo, headers, shouldRedirect);
            }
        });
    }

    private void doParseVideoInfoTask(VideoDownloadInfo downloadInfo, HashMap<String, String> headers, boolean shouldRedirect) {
        try {
            if (downloadInfo == null) {
                mListener.onBaseVideoInfoFailed(new Throwable("Video info is null."));
                return;
            }
            if (!HttpUtils.matchHttpSchema(downloadInfo.getVideoUrl())) {
                mListener.onBaseVideoInfoFailed(
                        new Throwable("Can parse the request resource's schema."));
                return;
            }

            String finalUrl = downloadInfo.getVideoUrl();

            // Redirect is enabled, send redirect request to get final location.
            if (mConfig.shouldRedirect() && shouldRedirect) {
                finalUrl =
                        HttpUtils.getFinalUrl(mConfig, downloadInfo.getVideoUrl(), null, headers);
                if (TextUtils.isEmpty(finalUrl)) {
                    mListener.onBaseVideoInfoFailed(new Throwable("FinalUrl is null."));
                    return;
                }
                mListener.onFinalUrl(finalUrl);
            }
            downloadInfo.setFinalUrl(finalUrl);
            Uri uri = Uri.parse(finalUrl);
            String fileName = uri.getLastPathSegment();
            LogUtils.i(TAG, "parseVideoInfo  fileName = " + fileName);
            // By suffix name.
            if (fileName != null) {
                fileName = fileName.toLowerCase();
                if (fileName.endsWith(".m3u8")) {
                    downloadInfo.setMimeType(Video.Mime.M3U8);
                    parseM3U8Info(downloadInfo, null, headers);
                    return;
                } else if (fileName.endsWith(".mp4")) {
                    downloadInfo.setMimeType(Video.Mime.MP4);
                    downloadInfo.setVideoType(Video.Type.MP4_TYPE);
                    mListener.onBaseVideoInfoSuccess(downloadInfo);
                    return;
                } else if (fileName.endsWith(".mov")) {
                    downloadInfo.setMimeType(Video.Mime.MOV);
                    downloadInfo.setVideoType(Video.Type.QUICKTIME_TYPE);
                    mListener.onBaseVideoInfoSuccess(downloadInfo);
                    return;
                } else if (fileName.endsWith(".webm")) {
                    downloadInfo.setMimeType(Video.Mime.WEBM);
                    downloadInfo.setVideoType(Video.Type.WEBM_TYPE);
                    mListener.onBaseVideoInfoSuccess(downloadInfo);
                    return;
                } else if (fileName.endsWith(".3gp")) {
                    downloadInfo.setMimeType(Video.Mime.GP3);
                    downloadInfo.setVideoType(Video.Type.GP3_TYPE);
                    mListener.onBaseVideoInfoSuccess(downloadInfo);
                    return;
                } else if (fileName.endsWith(".mkv")) {
                    downloadInfo.setMimeType(Video.Mime.MKV);
                    downloadInfo.setVideoType(Video.Type.MKV_TYPE);
                    mListener.onBaseVideoInfoSuccess(downloadInfo);
                }
            }
            // Add more video mimeType.
            String mimeType = HttpUtils.getMimeType(mConfig, finalUrl, null, headers);
            LogUtils.i(TAG,"parseVideoInfo mimeType=" + mimeType);
            if (mimeType != null) {
                mimeType = mimeType.toLowerCase();
                downloadInfo.setMimeType(mimeType);
                if (mimeType.contains(Video.Mime.MIME_TYPE_MP4)) {
                    downloadInfo.setVideoType(Video.Type.MP4_TYPE);
                    mListener.onBaseVideoInfoSuccess(downloadInfo);
                } else if (isM3U8Mimetype(mimeType)) {
                    parseM3U8Info(downloadInfo, null, headers);
                } else if (mimeType.contains(Video.Mime.MIME_TYPE_WEBM)) {
                    downloadInfo.setVideoType(Video.Type.WEBM_TYPE);
                    mListener.onBaseVideoInfoSuccess(downloadInfo);
                } else if (mimeType.contains(Video.Mime.MIME_TYPE_QUICKTIME)) {
                    downloadInfo.setVideoType(Video.Type.QUICKTIME_TYPE);
                    mListener.onBaseVideoInfoSuccess(downloadInfo);
                } else if (mimeType.contains(Video.Mime.MIME_TYPE_3GP)) {
                    downloadInfo.setVideoType(Video.Type.GP3_TYPE);
                    mListener.onBaseVideoInfoSuccess(downloadInfo);
                } else if (mimeType.contains(Video.Mime.MIME_TYPE_MKV)) {
                    downloadInfo.setVideoType(Video.Type.MKV_TYPE);
                    mListener.onBaseVideoInfoSuccess(downloadInfo);
                } else {
                    mListener.onBaseVideoInfoFailed(new VideoDownloadException(DownloadExceptionUtils.MIMETYPE_NOT_FOUND_STRING));
                }
            } else {
                mListener.onBaseVideoInfoFailed(new VideoDownloadException(DownloadExceptionUtils.MIMETYPE_NULL_ERROR_STRING));
            }

        } catch (Exception e) {
            mListener.onBaseVideoInfoFailed(e);
        }
    }

    private void parseM3U8Info(VideoDownloadInfo downloadInfo, Proxy proxy,
                               HashMap<String, String> headers) {
        try {
            M3U8 m3u8 =
                    M3U8Utils.parseM3U8Info(mConfig, downloadInfo.getVideoUrl(), false, null);
            // HLS LIVE video cannot be proxy cached.
            if (m3u8.hasEndList()) {
                String saveName = VideoDownloadUtils.computeMD5(downloadInfo.getVideoUrl());
                File dir = new File(mConfig.getCacheRoot(), saveName);
                if (!dir.exists()) {
                    dir.mkdir();
                }
                M3U8Utils.createRemoteM3U8(dir, m3u8);

                downloadInfo.setSaveDir(dir.getAbsolutePath());
                downloadInfo.setVideoType(Video.Type.HLS_TYPE);
                mListener.onM3U8InfoSuccess(downloadInfo, m3u8);
            } else {
                downloadInfo.setVideoType(Video.Type.HLS_LIVE_TYPE);
                mListener.onLiveM3U8Callback(downloadInfo);
            }
        } catch (Exception e) {
            mListener.onM3U8InfoFailed(e);
        }
    }

    public void parseM3U8File(VideoDownloadInfo info,
                              IVideoInfoParseListener callback) {
        File remoteM3U8File = new File(info.getSaveDir(), "remote.m3u8");
        if (!remoteM3U8File.exists()) {
            callback.onM3U8FileParseFailed(
                    info, new Throwable("Cannot find remote.m3u8 file."));
            return;
        }
        try {
            M3U8 m3u8 = M3U8Utils.parseM3U8Info(mConfig, info.getVideoUrl(), true,
                    remoteM3U8File);
            callback.onM3U8FileParseSuccess(info, m3u8);
        } catch (Exception e) {
            callback.onM3U8FileParseFailed(info, e);
        }
    }

    private boolean isM3U8Mimetype(String mimeType) {
        return mimeType.contains(Video.Mime.MIME_TYPE_M3U8_1) ||
                mimeType.contains(Video.Mime.MIME_TYPE_M3U8_2) ||
                mimeType.contains(Video.Mime.MIME_TYPE_M3U8_3) ||
                mimeType.contains(Video.Mime.MIME_TYPE_M3U8_4);
    }

}
