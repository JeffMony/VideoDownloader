package com.jeffmony.videodemo.merge;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.jeffmony.downloader.utils.LogUtils;
import com.jeffmony.m3u8library.VideoProcessManager;
import com.jeffmony.m3u8library.listener.IVideoTransformListener;
import com.jeffmony.videodemo.R;

import java.io.File;
import java.text.DecimalFormat;

public class VideoMergeActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "VideoMergeActivity";

    private EditText mSrcTxt;
    private EditText mDestTxt;
    private Button mConvertBtn;
    private TextView mTransformProgressTxt;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_merge);

        initViews();
    }

    private void initViews() {
        mSrcTxt = findViewById(R.id.src_path_txt);
        mDestTxt = findViewById(R.id.dest_path_txt);
        mConvertBtn = findViewById(R.id.convert_btn);
        mTransformProgressTxt = findViewById(R.id.video_transform_progress_txt);

        mConvertBtn.setOnClickListener(this);
    }

    private void doConvertVideo(String inputPath, String outputPath) {
        if (TextUtils.isEmpty(inputPath) || TextUtils.isEmpty(outputPath)) {
            LogUtils.i(TAG, "InputPath or OutputPath is null");
            return;
        }
        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            return;
        }
        File outputFile = new File(outputPath);
        if (!outputFile.exists()) {
            try {
                outputFile.createNewFile();
            } catch (Exception e) {
                LogUtils.w(TAG, "Create file failed, exception = " + e);
                return;
            }
        }
        LogUtils.i(TAG, "inputPath="+inputPath+", outputPath="+outputPath);
        VideoProcessManager.getInstance().transformM3U8ToMp4(inputPath, outputPath, new IVideoTransformListener() {

            @Override
            public void onTransformProgress(float progress) {
                LogUtils.i(TAG, "onTransformProgress progress="+progress);
                DecimalFormat format = new DecimalFormat(".00");
                mTransformProgressTxt.setText(String.format(getResources().getString(R.string.convert_progress), format.format(progress)));
            }

            @Override
            public void onTransformFinished() {
                LogUtils.i(TAG, "onTransformFinished");
            }

            @Override
            public void onTransformFailed(Exception e) {
                LogUtils.i(TAG, "onTransformFailed, e="+e.getMessage());
            }
        });
    }

    @Override
    public void onClick(View v) {
        if (v == mConvertBtn) {
            doConvertVideo(mSrcTxt.getText().toString(), mDestTxt.getText().toString());
        }
    }
}
