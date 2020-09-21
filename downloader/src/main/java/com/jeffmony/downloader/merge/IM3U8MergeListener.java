package com.jeffmony.downloader.merge;

public interface IM3U8MergeListener {

  void onMergeProgress(M3U8MergeInfo info);

  void onMergeFailed(M3U8MergeInfo info);

  void onMergeFinished(M3U8MergeInfo info);
}
