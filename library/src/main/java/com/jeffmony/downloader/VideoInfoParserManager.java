package com.jeffmony.downloader;

import android.text.TextUtils;

import com.jeffmony.downloader.common.DownloadConstants;
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

public class VideoInfoParserManager {
    private static volatile VideoInfoParserManager sInstance;

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

    public synchronized void parseVideoInfo(final VideoTaskItem taskItem, IVideoInfoListener listener, final Map<String, String> headers) {
        WorkerThreadHandler.submitRunnableTask(() -> doParseVideoInfoTask(taskItem, listener, headers));
    }

    private void doParseVideoInfoTask(VideoTaskItem taskItem, IVideoInfoListener listener, Map<String, String> headers) {
        try {
            if (taskItem == null) {
                listener.onBaseVideoInfoFailed(new VideoDownloadException(DownloadExceptionUtils.VIDEO_INFO_EMPTY));
                return;
            }
            if (!HttpUtils.matchHttpSchema(taskItem.getUrl())) {
                listener.onBaseVideoInfoFailed(new VideoDownloadException(DownloadExceptionUtils.URL_SCHEMA_ERROR));
                return;
            }

            if (TextUtils.isEmpty(taskItem.getCoverPath()) && !TextUtils.isEmpty(taskItem.getCoverUrl())) {
                //请求视频的封面图
                HttpURLConnection coverConn = HttpUtils.getConnection(taskItem.getCoverUrl(), headers, VideoDownloadUtils.getDownloadConfig().shouldIgnoreCertErrors());
                int responseCode = coverConn.getResponseCode();
                if (responseCode == HttpUtils.RESPONSE_200 || responseCode == HttpUtils.RESPONSE_206) {
                    InputStream inputStream = coverConn.getInputStream();
                    File coverFile = new File(VideoDownloadUtils.getDownloadConfig().getCacheRoot(), taskItem.getFileHash() + ".jpg");
                    boolean result = saveCoverFile(inputStream, coverFile);
                    if (result) {
                        taskItem.setCoverPath(coverFile.getAbsolutePath());
                    }
                }
            }

            String finalUrl = taskItem.getUrl();
            LogUtils.i(DownloadConstants.TAG, "doParseVideoInfoTask url="+finalUrl);

            HttpURLConnection connection = null;
            // Redirect is enabled, send redirect request to get final location.
            try {
                connection = HttpUtils.getConnection(finalUrl, headers, VideoDownloadUtils.getDownloadConfig().shouldIgnoreCertErrors());
            } catch (Exception e) {
                listener.onBaseVideoInfoFailed(new VideoDownloadException(DownloadExceptionUtils.CREATE_CONNECTION_ERROR));
                HttpUtils.closeConnection(connection);
                return;
            }
            if (connection == null) {
                listener.onBaseVideoInfoFailed(new VideoDownloadException(DownloadExceptionUtils.CREATE_CONNECTION_ERROR));
                return;
            }
            finalUrl = connection.getURL().toString();
            if (TextUtils.isEmpty(finalUrl)) {
                listener.onBaseVideoInfoFailed(new VideoDownloadException(DownloadExceptionUtils.FINAL_URL_EMPTY));
                HttpUtils.closeConnection(connection);
                return;
            }
            taskItem.setFinalUrl(finalUrl);
            String contentType = connection.getContentType();

            if (finalUrl.contains(Video.TypeInfo.M3U8) || VideoDownloadUtils.isM3U8Mimetype(contentType)) {
                //这是M3U8视频类型
                taskItem.setMimeType(Video.TypeInfo.M3U8);
                parseNetworkM3U8Info(taskItem, headers, listener);
            } else {
                //这是非M3U8类型, 需要获取视频的totalLength ===> contentLength
                long contentLength = getContentLength(taskItem, headers, connection, false);
                if (contentLength == VideoDownloadUtils.DEFAULT_CONTENT_LENGTH) {
                    listener.onBaseVideoInfoFailed(new VideoDownloadException(DownloadExceptionUtils.FILE_LENGTH_FETCHED_ERROR_STRING));
                    HttpUtils.closeConnection(connection);
                    return;
                }
                taskItem.setTotalSize(contentLength);
                listener.onBaseVideoInfoSuccess(taskItem);
            }
        } catch (Exception e) {
            listener.onBaseVideoInfoFailed(e);
        }
    }

    private long getContentLength(VideoTaskItem taskItem, Map<String, String> headers, HttpURLConnection connection, boolean shouldRetry) {
        if (shouldRetry) {
            try {
                connection = HttpUtils.getConnection(taskItem.getFinalUrl(), headers, VideoDownloadUtils.getDownloadConfig().shouldIgnoreCertErrors());
                if (connection == null) {
                    return VideoDownloadUtils.DEFAULT_CONTENT_LENGTH;
                }
            } catch (Exception e) {
                HttpUtils.closeConnection(connection);
                return VideoDownloadUtils.DEFAULT_CONTENT_LENGTH;
            }
        }
        String length = connection.getHeaderField("content-length");
        if (TextUtils.isEmpty(length)) {
            //有些站点,直接访问不会返回content-length,这时候需要重试获取content-length
            //例如:https://topicstatic.vivo.com.cn/f5ZUD0HxhQMn3J32/wukong/video/db8be4e5-9f64-4881-9765-6c600b89d28a.mp4
            //这个站点不直接返回content-length,需要设置Range才能返回content-length
            if (headers == null) {
                headers = new HashMap<>();
            } else {
                if (headers.containsKey("Range")) {
                    HttpUtils.closeConnection(connection);
                    return VideoDownloadUtils.DEFAULT_CONTENT_LENGTH;
                }
            }
            headers.put("Range", "bytes=0-");
            HttpUtils.closeConnection(connection);
            return getContentLength(taskItem, headers, connection, true);
        } else {
            long totalLength = Long.parseLong(length);
            if (totalLength <= 0) {
                HttpUtils.closeConnection(connection);
                return VideoDownloadUtils.DEFAULT_CONTENT_LENGTH;
            }
            return totalLength;
        }
    }

    private void parseNetworkM3U8Info(VideoTaskItem taskItem, Map<String, String> headers, IVideoInfoListener listener) {
        try {
            M3U8 m3u8 = M3U8Utils.parseNetworkM3U8Info(taskItem.getUrl(), headers, 0);
            // HLS LIVE video cannot be proxy cached.
            if (m3u8.hasEndList()) {
                String saveName = VideoDownloadUtils.computeMD5(taskItem.getUrl());
                File dir = new File(VideoDownloadUtils.getDownloadConfig().getCacheRoot(), saveName);
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

    public void parseLocalM3U8File(VideoTaskItem taskItem, IVideoInfoParseListener callback) {
        File remoteM3U8File = new File(taskItem.getSaveDir(), VideoDownloadUtils.REMOTE_M3U8);
        if (!remoteM3U8File.exists()) {
            callback.onM3U8FileParseFailed(taskItem, new VideoDownloadException(DownloadExceptionUtils.REMOTE_M3U8_EMPTY));
            return;
        }
        try {
            M3U8 m3u8 = M3U8Utils.parseLocalM3U8File(remoteM3U8File);
            callback.onM3U8FileParseSuccess(taskItem, m3u8);
        } catch (Exception e) {
            e.printStackTrace();
            callback.onM3U8FileParseFailed(taskItem, e);
        }
    }

    private boolean saveCoverFile(InputStream inputStream, File coverFile) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(coverFile);
            int len;
            byte[] buf = new byte[VideoDownloadUtils.DEFAULT_BUFFER_SIZE];
            while((len = inputStream.read(buf))!= -1) {
                fos.write(buf, 0, len);
            }
        } catch (IOException e) {
            return false;
        } finally {
            VideoDownloadUtils.close(inputStream);
            VideoDownloadUtils.close(fos);
        }
        return true;
    }

}
