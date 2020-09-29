package com.jeffmony.downloader.process;

public interface IM3U8MergeListener {

    void onMergedFinished();

    void onMergeFailed(Exception e);
}
