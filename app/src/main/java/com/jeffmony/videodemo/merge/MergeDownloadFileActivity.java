package com.jeffmony.videodemo.merge;

import android.os.Bundle;
import android.widget.ListView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.jeffmony.downloader.VideoDownloadManager;
import com.jeffmony.downloader.listener.IDownloadInfosCallback;
import com.jeffmony.downloader.model.VideoTaskItem;
import com.jeffmony.downloader.utils.LogUtils;
import com.jeffmony.videodemo.R;
import com.jeffmony.videodemo.merge.MergeVideoListAdapter;

import java.util.ArrayList;
import java.util.List;

public class MergeDownloadFileActivity extends AppCompatActivity {

  private static final String TAG = "MergeDownloadFileActivity";

  private ListView mDownloadFileView;
  private List<VideoTaskItem> mM3U8ItemList = new ArrayList<>();
  private MergeVideoListAdapter mAdapter;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_merge_download_file);

    mDownloadFileView = findViewById(R.id.download_m3u8_view);

    mAdapter = new MergeVideoListAdapter(this, R.layout.m3u8_item, mM3U8ItemList);
    mDownloadFileView.setAdapter(mAdapter);
    VideoDownloadManager.getInstance().fetchDownloadItems(mInfosCallback);
  }

  private IDownloadInfosCallback mInfosCallback =
          new IDownloadInfosCallback() {
            @Override
            public void onDownloadInfos(List<VideoTaskItem> items) {
              mM3U8ItemList.clear();
              for (VideoTaskItem item : items) {
                if (item.isHlsType() && item.isCompleted()) {
                  mM3U8ItemList.add(item);
                }
              }
              LogUtils.i(TAG, "onDownloadInfos size=" + items.size() + ", result size="+mM3U8ItemList.size());
            }
          };

  @Override
  protected void onDestroy() {
    super.onDestroy();
    VideoDownloadManager.getInstance().removeDownloadInfosCallback(mInfosCallback);
  }
}
