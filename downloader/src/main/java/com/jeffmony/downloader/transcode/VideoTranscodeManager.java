package com.jeffmony.downloader.transcode;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;

public class VideoTranscodeManager {

    private static final String TAG = "VideoTranscodeManager";

    private static final int MSG_VIDEO_TRANSCODE_START = 1;
    private static final int MSG_VIDEO_TRANSCODE_PROGRESS = 2;
    private static final int MSG_VIDEO_TRANSCODE_ERROR = 3;
    private static final int MSG_VIDEO_TRANSCODE_FINISHED = 4;

    private TranscodeHandler mHandler;

    private static class Holder {
        public static VideoTranscodeManager sInstance = new VideoTranscodeManager();
    }

    public static VideoTranscodeManager getInstance() {
        return Holder.sInstance;
    }

    private VideoTranscodeManager() {
        HandlerThread thread = new HandlerThread("video_transcode_thread");
        thread.start();
        mHandler = new TranscodeHandler(thread.getLooper());
    }

    class TranscodeHandler extends Handler {

        public TranscodeHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
        }
    }

    public void transcode(String inputPath, String outputPath) {
        FFmpegUtils.remux(inputPath, outputPath);
    }
}
