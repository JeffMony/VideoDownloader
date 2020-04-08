package com.jeffmony.downloader.listener;

import com.jeffmony.downloader.m3u8.M3U8;
import com.jeffmony.downloader.model.VideoDownloadInfo;

public interface IVideoInfoListener {

    void onFinalUrl(String finalUrl);

    void onBaseVideoInfoSuccess(VideoDownloadInfo info);

    void onBaseVideoInfoFailed(Throwable error);

    void onM3U8InfoSuccess(VideoDownloadInfo info, M3U8 m3u8);

    void onLiveM3U8Callback(VideoDownloadInfo info);

    void onM3U8InfoFailed(Throwable error);
}
