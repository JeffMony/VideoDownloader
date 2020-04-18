package com.jeffmony.downloader.listener;

import com.jeffmony.downloader.model.VideoTaskItem;

import java.util.List;

public interface IDownloadInfosCallback {

    void onDownloadInfos(List<VideoTaskItem> items);
}
