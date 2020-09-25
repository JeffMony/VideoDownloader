package com.jeffmony.videodemo.transcode;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.jeffmony.downloader.transcode.VideoTranscodeManager;
import com.jeffmony.downloader.utils.VideoDownloadUtils;

public class VideoTranscodeActivity extends AppCompatActivity {

  private static String INPUT_PATH = "/sdcard/Android/data/com.jeffmony.videodemo/files/" + VideoDownloadUtils.MERGE_VIDEO;
  private static String OUTPUT_PATH = "/sdcard/Android/data/com.jeffmony.videodemo/files/" + VideoDownloadUtils.OUPUT_VIDEO;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    VideoTranscodeManager.getInstance().transcode(INPUT_PATH, OUTPUT_PATH);
  }
}
