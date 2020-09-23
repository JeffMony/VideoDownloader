package com.jeffmony.downloader.merge;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.jeffmony.downloader.m3u8.M3U8Constants;
import com.jeffmony.downloader.m3u8.M3U8Utils;
import com.jeffmony.downloader.model.VideoTaskItem;
import com.jeffmony.downloader.utils.LogUtils;
import com.jeffmony.downloader.utils.VideoDownloadUtils;
import com.jeffmony.downloader.utils.WorkerThreadHandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.spec.AlgorithmParameterSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class M3U8MergeManager {

  private static final String TAG = "M3U8MergeManager";
  private static final int MSG_MERGE_FINISHED = 1;
  private static final int MSG_MERGE_PROCESSING = 2;
  private static final int MSG_MERGE_ERROR = 3;
  private static final int MSG_DELETE_FILE = 4;
  private MergeHandler mHandler;
  private Map<String, IM3U8MergeListener> mMergeListenerMap = new ConcurrentHashMap<>();

  private static class Holder {
    public static M3U8MergeManager sInstance = new M3U8MergeManager();
  }

  public static M3U8MergeManager getInstance() {
    return Holder.sInstance;
  }

  private M3U8MergeManager() {
    HandlerThread mergeThread = new HandlerThread("m3u8_merge_handler");
    mergeThread.start();
    mHandler = new MergeHandler(mergeThread.getLooper());
  }

  class MergeHandler extends Handler {

    public MergeHandler(Looper looper) {
      super(looper);
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
      super.handleMessage(msg);
      M3U8MergeInfo info = (M3U8MergeInfo) msg.obj;
      IM3U8MergeListener listener = (info != null) ? mMergeListenerMap.get(info.getUrl()) : null;
      switch (msg.what) {
        case MSG_MERGE_FINISHED:
          if (listener != null) {
            listener.onMergeFinished(info);
          }
          obtainMessage(MSG_DELETE_FILE, info).sendToTarget();
          break;
        case MSG_MERGE_PROCESSING:
          if (listener != null) {
            listener.onMergeProgress(info);
          }
          break;
        case MSG_MERGE_ERROR:
          if (listener != null) {
            listener.onMergeFailed(info);
          }
          break;
        case MSG_DELETE_FILE:
          deleteM3U8File(info);
          break;
        default:
          break;
      }
    }
  }

  public void mergeM3U8(VideoTaskItem taskItem, IM3U8MergeListener listener) {
    if (taskItem == null || TextUtils.isEmpty(taskItem.getUrl())) {
      return;
    }

    mMergeListenerMap.put(taskItem.getUrl(), listener);

    WorkerThreadHandler.submitRunnableTask(new Runnable() {
      @Override
      public void run() {
        doMergeM3U8(taskItem);
      }
    });
  }

  private void doMergeM3U8(VideoTaskItem taskItem) {
    String filePath = taskItem.getFilePath();
    String parentPath = filePath.substring(0, filePath.lastIndexOf("/"));
    File outputFile = new File(parentPath, VideoDownloadUtils.MERGE_VIDEO);
    M3U8MergeInfo info = new M3U8MergeInfo(taskItem.getUrl(), taskItem.getFilePath());
    if (outputFile.exists() && outputFile.length() != 0) {
      info.setCurTs(taskItem.getTotalTs());
      mHandler.obtainMessage(MSG_MERGE_FINISHED, info).sendToTarget();
      return;
    }
    File m3u8File = new File(filePath);
    if (!m3u8File.exists()) {
      info.setErrorCode(MergeError.ERROR_NO_M3U8_FILE);
      mHandler.obtainMessage(MSG_MERGE_ERROR, info).sendToTarget();
      return;
    }
    LogUtils.i(TAG, "VIDEO PATH = "+outputFile.getAbsolutePath());
    InputStreamReader inputStreamReader = null;
    BufferedReader bufferedReader = null;
    try {
      inputStreamReader = new InputStreamReader(new FileInputStream(m3u8File));
      bufferedReader = new BufferedReader(inputStreamReader);
    } catch (Exception e) {
      info.setErrorCode(MergeError.ERROR_CREATE_INPUT_STREAM);
      mHandler.obtainMessage(MSG_MERGE_ERROR, info).sendToTarget();
      VideoDownloadUtils.close(inputStreamReader);
      VideoDownloadUtils.close(bufferedReader);
      return;
    }
    info.setTotalTs(taskItem.getTotalTs());
    List<TsInfo> tsFileList = new ArrayList<>();
    String line;
    String key = null;
    String method = null;
    try {
      while ((line = bufferedReader.readLine()) != null){
        if (line.startsWith(M3U8Constants.TAG_PREFIX)) {
          if (line.startsWith(M3U8Constants.TAG_KEY)) {
            method = M3U8Utils.parseOptionalStringAttr(line, M3U8Constants.REGEX_METHOD);
            String keyFormat = M3U8Utils.parseOptionalStringAttr(line, M3U8Constants.REGEX_KEYFORMAT);
            if (!M3U8Constants.METHOD_NONE.equals(method)) {
              if (M3U8Constants.KEYFORMAT_IDENTITY.equals(keyFormat) || keyFormat == null) {
                if (M3U8Constants.METHOD_AES_128.equals(method)) {
                  key = fetchKey(M3U8Utils.parseStringAttr(line, M3U8Constants.REGEX_URI));
                }
              }
              LogUtils.i(TAG, "Key = " + key);
            }
          }
          continue;
        }
        if (TextUtils.isEmpty(line)) {
          continue;
        }
        TsInfo tsInfo = new TsInfo(line);
        tsInfo.setKey(key);
        tsInfo.setMethod(method);
        tsFileList.add(tsInfo);
      }
    } catch (Exception e) {
      info.setErrorCode(MergeError.ERROR_READ_STREAM);
      mHandler.obtainMessage(MSG_MERGE_ERROR, info).sendToTarget();
      return;
    } finally {
      VideoDownloadUtils.close(inputStreamReader);
      VideoDownloadUtils.close(bufferedReader);
    }

    for (TsInfo tsInfo : tsFileList) {
      File tsFile = new File(tsInfo.getFilePath());
      if (!tsFile.exists()) {
        info.setErrorCode(MergeError.ERROR_NO_TS_FILE);
        mHandler.obtainMessage(MSG_MERGE_ERROR, info).sendToTarget();
        return;
      }
    }

    FileOutputStream fos = null;

    try {
      fos = new FileOutputStream(outputFile);
    } catch (Exception e) {
      info.setErrorCode(MergeError.ERROR_CREATE_OUTPUT_FILE);
      mHandler.obtainMessage(MSG_MERGE_ERROR, info).sendToTarget();
      VideoDownloadUtils.close(fos);
      return;
    }

    byte[] buffer = new byte[VideoDownloadUtils.DEFAULT_BUFFER_SIZE];
    int progress = 0;
    for (TsInfo tsInfo : tsFileList) {
      File tsFile = new File(tsInfo.getFilePath());
      int len;
      FileInputStream fis = null;
      try {
        fis = new FileInputStream(tsFile);
        while((len = fis.read(buffer)) != -1) {
          fos.write(decryptM3U8Ts(buffer, tsInfo.getKey(), tsInfo.getMethod()), 0, len);
        }
        VideoDownloadUtils.close(fis);
        fos.flush();
        progress++;
        info.setCurTs(progress);
        mHandler.obtainMessage(MSG_MERGE_PROCESSING, info).sendToTarget();
      } catch (Exception e) {
        LogUtils.w(TAG, "Write file failed, exception="+e);
        info.setErrorCode(MergeError.ERROR_WRITE_FILE);
        mHandler.obtainMessage(MSG_MERGE_ERROR, info).sendToTarget();
        VideoDownloadUtils.close(fis);
        VideoDownloadUtils.close(fos);
        return;
      } finally {
        VideoDownloadUtils.close(fis);
      }
    }
    VideoDownloadUtils.close(fos);
    info.setCurTs(taskItem.getTotalTs());
    mHandler.obtainMessage(MSG_MERGE_FINISHED, info).sendToTarget();
  }

  private String fetchKey(String keyPath) {
    if (TextUtils.isEmpty(keyPath))
      return keyPath;

    InputStreamReader inputStreamReader = null;
    BufferedReader bufferedReader = null;
    if (keyPath.startsWith("http://") || keyPath.startsWith("https://")) {
      try {
        URL keyURL = new URL(keyPath);
        HttpURLConnection connection = (HttpURLConnection) keyURL.openConnection();
        bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String line;
        String result = "";
        while((line = bufferedReader.readLine()) != null) {
          result += line;
        }
        return result;
      } catch (Exception e) {
        LogUtils.w(TAG, "Request online key failed, exception = " + e);
        VideoDownloadUtils.close(inputStreamReader);
        VideoDownloadUtils.close(bufferedReader);
      } finally {
        VideoDownloadUtils.close(inputStreamReader);
        VideoDownloadUtils.close(bufferedReader);
      }
    } else {
      File keyFile = new File(keyPath);
      try {
        inputStreamReader = new InputStreamReader(new FileInputStream(keyFile));
        bufferedReader = new BufferedReader(inputStreamReader);
        String line;
        String result = "";
        while((line = bufferedReader.readLine()) != null) {
          result += line;
        }
        return result;
      } catch (Exception e) {
        LogUtils.w(TAG, "Read local key file failed, exception = " + e);
        VideoDownloadUtils.close(inputStreamReader);
        VideoDownloadUtils.close(bufferedReader);
      } finally {
        VideoDownloadUtils.close(inputStreamReader);
        VideoDownloadUtils.close(bufferedReader);
      }
    }
    return null;
  }

  private byte[] decryptM3U8Ts(byte[] src, String key, String method) {
    if (TextUtils.isEmpty(key) || TextUtils.isEmpty(method) || M3U8Constants.METHOD_NONE.equals(method)) {
      return src;
    }
    try {
      if (M3U8Constants.METHOD_AES_128.equals(method) ||
              M3U8Constants.METHOD_SAMPLE_AES.equals(method) ||
              M3U8Constants.METHOD_SAMPLE_AES_CENC.equals(method) ||
              M3U8Constants.METHOD_SAMPLE_AES_CTR.equals(method)) {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes("utf-8"), "AES");
        // 如果m3u8有IV标签，那么IvParameterSpec构造函数就把IV标签后的内容转成字节数组传进去
        AlgorithmParameterSpec paramSpec = new IvParameterSpec(new byte[16]);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, paramSpec);
        return cipher.doFinal(src);
      }
    } catch (Exception e) {
      LogUtils.w(TAG, "decrypt file failed, exception = " + e);
      return src;
    }
    return src;
  }

  private void deleteM3U8File(M3U8MergeInfo info) {
    String filePath = info.getFilePath();
    String parentPath = filePath.substring(0, filePath.lastIndexOf("/"));
    File parentFile = new File(parentPath);
    if (parentFile.exists()) {
      for (File subFile : parentFile.listFiles()) {
        String name = subFile.getName();
        if (!VideoDownloadUtils.MERGE_VIDEO.equals(name) &&
                !VideoDownloadUtils.REMOTE_M3U8.equals(name)) {
          try {
            VideoDownloadUtils.delete(subFile);
          } catch (Exception e) {
            LogUtils.i(TAG, "delete file failed, exception = " + e);
          }
        }
      }
    }
  }

}
