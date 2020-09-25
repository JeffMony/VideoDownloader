package com.jeffmony.downloader.transcode;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;

import com.jeffmony.downloader.utils.LogUtils;

import java.io.File;

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
        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            LogUtils.w(TAG, "Input File is empty.");
            return;
        }
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(inputPath);
        } catch (Exception e) {
            LogUtils.w(TAG, "MediaExtractor setDataSource failed, exception="+e);
            return;
        }
        int width = 0;
        int height = 0;
        for(int index = 0; index < extractor.getTrackCount(); index++) {
            MediaFormat format = extractor.getTrackFormat(index);
            LogUtils.i(TAG, ": " +format);
            if (format.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                width = format.getInteger(MediaFormat.KEY_WIDTH);
                height = format.getInteger(MediaFormat.KEY_HEIGHT);
                break;

            }
        }
        LogUtils.i(TAG, "Width="+width+", Height="+height);
        if (extractor != null) {
            extractor.release();
            extractor = null;
        }
        FFmpegUtils.remux(inputPath, outputPath, width, height);
    }
}
