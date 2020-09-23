package com.jeffmony.downloader.transcode;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;

import com.jeffmony.downloader.utils.LogUtils;
import com.jeffmony.downloader.utils.WorkerThreadHandler;

import java.io.File;
import java.nio.ByteBuffer;

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
      LogUtils.i(TAG, "Input File is not existing");
      return;
    }
    WorkerThreadHandler.submitRunnableTask(new Runnable() {
      @Override
      public void run() {
        extractInputFile(inputPath, outputPath);
      }
    });
  }

  private void extractInputFile(String inputPath, String outputPath) {
    MediaExtractor extractor = new MediaExtractor();
    try {
      extractor.setDataSource(inputPath);
    } catch (Exception e) {
      LogUtils.i(TAG, "MediaExtractor create failed, exception = " + e);
      return;
    }
    int videoTrackIndex = -1;
    int audioTrackIndex = -1;
    MediaFormat videoFormat = null;
    MediaFormat audioFormat = null;
    int trackCount = extractor.getTrackCount();
    for (int index = 0; index < trackCount; index++) {
      MediaFormat format = extractor.getTrackFormat(index);
      if (format.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
        videoTrackIndex = index;
        videoFormat = format;
      } else if (format.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
        audioTrackIndex = index;
        audioFormat = format;
      }
      LogUtils.i(TAG, "Format = " + format);
    }

    LogUtils.i(TAG, "VideoTrackIndex = " + videoTrackIndex + ", AudioTrackIndex = " + audioTrackIndex);
    MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
    MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();

    MediaMuxer mediaMuxer = null;
    try {
      mediaMuxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    } catch (Exception e) {
      LogUtils.w(TAG, "MediaMuxer create failed, exception = " + e);
    }
    if (mediaMuxer == null) {
      LogUtils.w(TAG, "MediaMuxer is failed");
      return;
    }
    LogUtils.i(TAG, "MediaMuxer operate");
    MediaFormat writeVideoFormat = new MediaFormat();
    byte[] header_sps = {0, 0, 0, 1, 103, 100, 0, 31, -84, -76, 2, -128, 45, -56};
    byte[] header_pps = {0, 0, 0, 1, 104, -18, 60, 97, 15, -1, -16, -121, -1, -8, 67, -1, -4, 33, -1, -2, 16, -1, -1, 8, 127, -1, -64};
    writeVideoFormat.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
    writeVideoFormat.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));
    writeVideoFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_VIDEO_AVC);
    writeVideoFormat.setInteger(MediaFormat.KEY_HEIGHT, videoFormat.getInteger(MediaFormat.KEY_HEIGHT));
    writeVideoFormat.setInteger(MediaFormat.KEY_WIDTH, videoFormat.getInteger(MediaFormat.KEY_WIDTH));
    writeVideoFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
    writeVideoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
    int writeVideoTrackIndex = mediaMuxer.addTrack(writeVideoFormat);

    MediaFormat writeAudioFormat = new MediaFormat();
    writeAudioFormat.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
    writeAudioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE));
    writeAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, audioFormat.getInteger(MediaFormat.KEY_BIT_RATE));
    writeAudioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
    writeAudioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
    int writeAudioTrackIndex = mediaMuxer.addTrack(writeAudioFormat);

    mediaMuxer.start();

    ByteBuffer byteBuffer = ByteBuffer.allocate(500 * 1024);

    extractor.selectTrack(videoTrackIndex);
    long sampleTime = 0;
    {
      LogUtils.i(TAG, "START");
      extractor.readSampleData(byteBuffer, 0);
      if (extractor.getSampleFlags() == MediaExtractor.SAMPLE_FLAG_SYNC) {
        extractor.advance();
      }
      extractor.readSampleData(byteBuffer, 0);
      long secondTime = extractor.getSampleTime();
      extractor.advance();
      long thirdTime = extractor.getSampleTime();
      sampleTime = Math.abs(thirdTime - secondTime);
      LogUtils.i(TAG, "SampleTime = " + sampleTime+", " + secondTime + ", " + thirdTime);
    }
    extractor.unselectTrack(videoTrackIndex);
    extractor.selectTrack(videoTrackIndex);
    while(true) {
      int readVideoSampleSize = extractor.readSampleData(byteBuffer, 0);
      if (readVideoSampleSize < 0) {
        break;
      }
      videoBufferInfo.size = readVideoSampleSize;
      videoBufferInfo.presentationTimeUs += sampleTime;
      videoBufferInfo.offset = 0;
      videoBufferInfo.flags = extractor.getSampleFlags();
      mediaMuxer.writeSampleData(writeVideoTrackIndex, byteBuffer, videoBufferInfo);
      extractor.advance();
    }

    extractor.unselectTrack(videoTrackIndex);
    extractor.selectTrack(audioTrackIndex);
    while (true) {
      int readAudioSampleSize = extractor.readSampleData(byteBuffer, 0);
      if (readAudioSampleSize < 0) {
        break;
      }
      audioBufferInfo.size = readAudioSampleSize;
      audioBufferInfo.presentationTimeUs += sampleTime;
      audioBufferInfo.offset = 0;
      audioBufferInfo.flags = extractor.getSampleFlags();
      mediaMuxer.writeSampleData(writeAudioTrackIndex, byteBuffer, audioBufferInfo);
      extractor.advance();
    }
    mediaMuxer.stop();
    mediaMuxer.release();
    extractor.release();
  }
}
