package com.jeffmony.videodemo;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.jeffmony.downloader.VideoDownloadManager;
import com.jeffmony.downloader.utils.Utility;
import com.jeffmony.downloader.utils.VideoDownloadUtils;
import com.jeffmony.playersdk.WeakHandler;

import java.io.File;

public class DownloadSettingsActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int MSG_COUNT_SIZE = 0x1;

    private TextView mStoreTextView;
    private TextView mDownloadSizeTextView;
    private Button mClearBtn;

    private WeakHandler mHandler = new WeakHandler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            if (msg.what == MSG_COUNT_SIZE) {
                String filePath = VideoDownloadManager.getInstance().getDownloadPath();
                File file = new File(filePath);
                if (file.exists()) {
                    long size = VideoDownloadUtils.countTotalSize(file);
                    mDownloadSizeTextView.setText("缓存文件size: " + Utility.getSize(size));
                }
            }
            return true;
        }
    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_settings);

        initViews();
    }

    private void initViews() {
        mStoreTextView = (TextView) findViewById(R.id.store_loc_txt);
        mDownloadSizeTextView = (TextView) findViewById(R.id.video_size_txt);
        mClearBtn = (Button) findViewById(R.id.clear_download_cache);

        mStoreTextView.setText(VideoDownloadManager.getInstance().getDownloadPath());

        mClearBtn.setOnClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mHandler.sendEmptyMessage(MSG_COUNT_SIZE);
    }


    @Override
    public void onClick(View v) {
        if (v == mClearBtn) {
            VideoDownloadManager.getInstance().deleteAllVideoFiles(this);
            mHandler.sendEmptyMessage(MSG_COUNT_SIZE);
        }
    }
}
