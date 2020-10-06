package com.jeffmony.videodemo.download;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.jeffmony.downloader.VideoDownloadManager;
import com.jeffmony.downloader.listener.DownloadListener;
import com.jeffmony.downloader.listener.IDownloadInfosCallback;
import com.jeffmony.downloader.model.VideoTaskItem;
import com.jeffmony.downloader.utils.LogUtils;
import com.jeffmony.videodemo.R;

import java.util.List;

public class VideoDownloadListActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "DownloadFeatureActivity";

    private Button mPauseAllBtn;
    private Button mStartAllBtn;
    private ListView mDownloadListView;

    private VideoDownloadListAdapter mAdapter;
    private VideoTaskItem[] items = new VideoTaskItem[7];

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_list);
        mPauseAllBtn = (Button) findViewById(R.id.pause_task_btn);
        mStartAllBtn = (Button) findViewById(R.id.start_task_btn);
        mDownloadListView = (ListView) findViewById(R.id.download_listview);
        mStartAllBtn.setOnClickListener(this);
        mPauseAllBtn.setOnClickListener(this);
        VideoDownloadManager.getInstance().setGlobalDownloadListener(mListener);
        initDatas();
    }

    private void initDatas() {
        VideoTaskItem item1 = new VideoTaskItem("http://video.yjf138.com:8091/20180812/6yl0Q2YZ/index.m3u8");
        VideoTaskItem item2 = new VideoTaskItem("https://videos.kkyun-iqiyi.com/20171117/XZiuPaA3/index.m3u8");
        VideoTaskItem item3 = new VideoTaskItem("https://tv2.youkutv.cc/2020/04/14/MbqulRmS8sjQGJG9/playlist.m3u8");
        VideoTaskItem item4 = new VideoTaskItem("https://tv2.youkutv.cc/2020/04/14/Pejd7TL3wdLZVbxO/playlist.m3u8");
        VideoTaskItem item5 = new VideoTaskItem("https://tv2.youkutv.cc/2020/04/13/AWlDA5ORHHzLX81U/playlist.m3u8");
        VideoTaskItem item6 = new VideoTaskItem("https://hls.aoxtv.com/v3.szjal.cn/20200114/dtOHlPFE/index.m3u8");
        VideoTaskItem item7 = new VideoTaskItem("http://videoconverter.vivo.com.cn/201706/655_1498479540118.mp4.main.m3u8");

        items[0] = item1;
        items[1] = item2;
        items[2] = item3;
        items[3] = item4;
        items[4] = item5;
        items[5] = item6;
        items[6] = item7;

        mAdapter = new VideoDownloadListAdapter(this, R.layout.download_item, items);
        mDownloadListView.setAdapter(mAdapter);

        VideoDownloadManager.getInstance().fetchDownloadItems(mInfosCallback);

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


    private long mLastProgressTimeStamp;
    private long mLastSpeedTimeStamp;

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
            long currentTimeStamp = System.currentTimeMillis();
            if (currentTimeStamp - mLastProgressTimeStamp > 1000) {
                LogUtils.w(TAG, "onDownloadProgress: " + item.getPercentString() + ", curTs=" + item.getCurTs() + ", totalTs=" + item.getTotalTs());
                notifyChanged(item);
                mLastProgressTimeStamp = currentTimeStamp;
            }
        }

        @Override
        public void onDownloadSpeed(VideoTaskItem item) {
            long currentTimeStamp = System.currentTimeMillis();
            if (currentTimeStamp - mLastSpeedTimeStamp > 1000) {
                notifyChanged(item);
                mLastSpeedTimeStamp = currentTimeStamp;
            }
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
            LogUtils.w(TAG,"onDownloadSuccess: " + item);
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

    private IDownloadInfosCallback mInfosCallback =
            new IDownloadInfosCallback() {
                @Override
                public void onDownloadInfos(List<VideoTaskItem> items) {
                    for (VideoTaskItem item : items) {
                        notifyChanged(item);
                    }
                }
            };


    @Override
    public void onClick(View v) {
        if (v == mStartAllBtn) {

        } else if (v == mPauseAllBtn) {
            VideoDownloadManager.getInstance().pauseAllDownloadTasks();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        VideoDownloadManager.getInstance().removeDownloadInfosCallback(mInfosCallback);
    }

}
