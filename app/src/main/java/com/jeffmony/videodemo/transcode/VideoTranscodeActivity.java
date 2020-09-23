package com.jeffmony.videodemo.transcode;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.jeffmony.downloader.transcode.VideoTranscodeManager;

public class VideoTranscodeActivity extends AppCompatActivity {

  private static String INPUT_PATH = "/sdcard/Android/data/com.jeffmony.videodemo/files/merge_video.ts";
  private static String OUTPUT_PATH = "/sdcard/Android/data/com.jeffmony.videodemo/files/output.mp4";

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    VideoTranscodeManager.getInstance().transcode(INPUT_PATH, OUTPUT_PATH);
  }
}
