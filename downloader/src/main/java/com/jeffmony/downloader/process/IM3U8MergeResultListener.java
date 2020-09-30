package com.jeffmony.downloader.process;

import com.jeffmony.downloader.model.VideoTaskItem;

public interface IM3U8MergeResultListener {

    void onCallback(VideoTaskItem taskItem);
}
