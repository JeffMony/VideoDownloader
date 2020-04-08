package com.jeffmony.downloader.listener;

import com.jeffmony.downloader.m3u8.M3U8;
import com.jeffmony.downloader.model.VideoDownloadInfo;

public interface IVideoInfoParseListener {

    void onM3U8FileParseSuccess(VideoDownloadInfo info, M3U8 m3u8);

    void onM3U8FileParseFailed(VideoDownloadInfo info, Throwable error);
}
