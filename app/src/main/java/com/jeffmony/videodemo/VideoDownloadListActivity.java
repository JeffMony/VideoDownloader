package com.jeffmony.videodemo;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.jeffmony.downloader.VideoDownloadManager;
import com.jeffmony.downloader.listener.DownloadListener;
import com.jeffmony.downloader.model.VideoTaskItem;
import com.jeffmony.downloader.utils.LogUtils;

public class VideoDownloadListActivity extends AppCompatActivity {

    private static final String TAG = "DownloadFeatureActivity";

    private ListView mDownloadListView;
    private VideoListAdapter mAdapter;
    private VideoTaskItem[] items = new VideoTaskItem[8];

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_list);
        mDownloadListView = (ListView) findViewById(R.id.download_listview);
        VideoDownloadManager.getInstance().setGlobalDownloadListener(mListener);
        initDatas();
    }

    private void initDatas() {
        VideoTaskItem item1 = new VideoTaskItem("http://dov.mkanav1.com/20200203/UxM4G8Wt/index.m3u8");
        VideoTaskItem item2 = new VideoTaskItem(
                "http://api.xundog.top/sp/320.mkv");
        VideoTaskItem item3 = new VideoTaskItem("https://tv.youkutv.cc/2020/01/15/SZpLQDUmJZKF9O0D/playlist.m3u8");
        VideoTaskItem item4 = new VideoTaskItem("https://tv.youkutv.cc/2020/01/15/3d97sO5xQUYB5bvY/playlist.m3u8");
        VideoTaskItem item5 = new VideoTaskItem("https://hls.aoxtv.com/v3.szjal.cn/20200122/TIj9Ekt9/index.m3u8");
        VideoTaskItem item6 = new VideoTaskItem("https://hls.aoxtv.com/v3.szjal.cn/20200114/dtOHlPFE/index.m3u8");
        VideoTaskItem item7 = new VideoTaskItem("https://hls.aoxtv.com/v3.szjal.cn/20200115/qNIba0qo/index.m3u8");
        VideoTaskItem item8 = new VideoTaskItem("https://hls.aoxtv.com/v3.szjal.cn/20200114/2KwuUDMK/index.m3u8");

        items[0] = item1;
        items[1] = item2;
        items[2] = item3;
        items[3] = item4;
        items[4] = item5;
        items[5] = item6;
        items[6] = item7;
        items[7] = item8;

        mAdapter = new VideoListAdapter(this, R.layout.download_item, items);
        mDownloadListView.setAdapter(mAdapter);

        mDownloadListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                VideoTaskItem item = items[position];
                if (item.isInitialTask()) {
                    VideoDownloadManager.getInstance().startDownload(item);
                } else if (item.isRunningTask()) {
                    VideoDownloadManager.getInstance().pauseDownloadTask(item.getUrl());
                } else if (item.isInterruptTask()) {
                    VideoDownloadManager.getInstance().resumeDownload(item.getUrl());
                }
            }
        });
    }

    private DownloadListener mListener = new DownloadListener() {

        @Override
        public void onDownloadDefault(VideoTaskItem item) {
            LogUtils.w(TAG,"onDownloadDefault: " + item);
            notifyChanged(item);
        }

        @Override
        public void onDownloadPending(VideoTaskItem item) {
            LogUtils.w(TAG,"onDownloadPending: " + item);
            notifyChanged(item);
        }

        @Override
        public void onDownloadPrepare(VideoTaskItem item) {
            LogUtils.w(TAG,"onDownloadPrepare: " + item);
            notifyChanged(item);
        }

        @Override
        public void onDownloadStart(VideoTaskItem item) {
            LogUtils.w(TAG,"onDownloadStart: " + item);
            notifyChanged(item);
        }

        @Override
        public void onDownloadProgress(VideoTaskItem item) {
            LogUtils.w(TAG,"onDownloadProgress: " + item.getPercentString());
            notifyChanged(item);
        }

        @Override
        public void onDownloadSpeed(VideoTaskItem item) {
            notifyChanged(item);
        }

        @Override
        public void onDownloadPause(VideoTaskItem item) {
            LogUtils.w(TAG,"onDownloadPause: " + item.getUrl());
            notifyChanged(item);
        }

        @Override
        public void onDownloadError(VideoTaskItem item) {
            LogUtils.w(TAG,"onDownloadError: " + item.getUrl());
            notifyChanged(item);
        }

        @Override
        public void onDownloadSuccess(VideoTaskItem item) {
            LogUtils.w(TAG,"onDownloadSuccess: " + item.getUrl());
            notifyChanged(item);
        }
    };

    private void notifyChanged(final VideoTaskItem item) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mAdapter.notifyChanged(items, item);
            }
        });
    }
}
