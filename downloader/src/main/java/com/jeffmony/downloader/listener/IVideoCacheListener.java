package com.jeffmony.downloader.listener;

import com.jeffmony.downloader.model.VideoRange;

public interface IVideoCacheListener {
    void onFailed(VideoRange range, int id, Exception e);

    void onProgress(VideoRange range, int id, long cachedSize);

    void onRangeCompleted(VideoRange range, int id);

    void onCompleted(VideoRange range, int id);
}
