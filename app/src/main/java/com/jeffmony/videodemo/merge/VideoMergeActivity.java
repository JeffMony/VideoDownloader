package com.jeffmony.videodemo.merge;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.jeffmony.downloader.process.IM3U8MergeListener;
import com.jeffmony.downloader.process.VideoProcessManager;
import com.jeffmony.downloader.utils.LogUtils;
import com.jeffmony.videodemo.R;

import java.io.File;

public class VideoMergeActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "VideoMergeActivity";

    private EditText mInputVideoPathTxt;
    private EditText mOutputVideoPathTxt;
    private Button mVideoMergeBtn;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_merge);

        initViews();
    }

    private void initViews() {
        mInputVideoPathTxt = findViewById(R.id.m3u8_video_path_txt);
        mOutputVideoPathTxt = findViewById(R.id.mp4_video_path_txt);
        mVideoMergeBtn = findViewById(R.id.m3u8_2_mp4_btn);

        mVideoMergeBtn.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v == mVideoMergeBtn) {
            doMergeM3U8();
        }
    }

    private void doMergeM3U8() {
        String inputFilePath = mInputVideoPathTxt.getText().toString();
        String outputFilePath = mOutputVideoPathTxt.getText().toString();

        if (TextUtils.isEmpty(inputFilePath) || TextUtils.isEmpty(outputFilePath)) {
            Toast.makeText(this, "请输入文件路径", Toast.LENGTH_SHORT).show();
            return;
        }
        File inputFile = new File(inputFilePath);
        if (!inputFile.exists()) {
            Toast.makeText(this, "输入文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        VideoProcessManager.getInstance().mergeTs(inputFilePath, outputFilePath, new IM3U8MergeListener() {
            @Override
            public void onMergedFinished() {
                LogUtils.i(TAG, "onMergedFinished");
                Toast.makeText(VideoMergeActivity.this, "合并成功", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onMergeFailed(Exception e) {
                LogUtils.i(TAG, "onMergeFailed, e=" + e.getMessage());
                Toast.makeText(VideoMergeActivity.this, "合并失败", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
