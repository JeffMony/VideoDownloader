package com.jeffmony.downloader.model;

import com.jeffmony.downloader.m3u8.M3U8;
import com.jeffmony.downloader.utils.Utility;

public class VideoTaskItem {

    private String mUrl;            //下载视频的url
    private int mTaskState;         //当前任务的状态
    private String mMimeType;       // 视频url的mime type
    private int mErrorCode;         //当前任务下载错误码
    private int mVideoType;         //当前文件类型
    private M3U8 mM3U8;             //M3U8结构,如果非M3U8,则为null
    private float mSpeed;           //当前下载速度, getSpeedString 函数可以将速度格式化
    private float mPercent;         //当前下载百分比, 0 ~ 100,是浮点数
    private long mDownloadSize;     //已下载大小, getDownloadSizeString 函数可以将大小格式化
    private long mTotalSize;        //文件总大小, M3U8文件无法准确获知

    public VideoTaskItem(String url) {
        mUrl = url;
    }

    public String getUrl() {
        return mUrl;
    }

    public void setTaskState(int state) { mTaskState = state; }

    public int getTaskState() { return mTaskState; }

    public void setMimeType(String mimeType) { mMimeType = mimeType; }

    public String getMimeType() {
        return mMimeType;
    }

    public void setErrorCode(int errorCode) { mErrorCode = errorCode; }

    public int getErrorCode() {
        return mErrorCode;
    }

    public void setVideoType(int type) { mVideoType = type; }

    public int getVideoType() {
        return mVideoType;
    }

    public void setM3U8(M3U8 m3u8) { mM3U8 = m3u8; }

    public M3U8 getM3U8() {
        return mM3U8;
    }

    public void setSpeed(float speed) {
        mSpeed = speed;
    }

    public float getSpeed() {
        return mSpeed;
    }

    public String getSpeedString() {
        return Utility.getSize((long)mSpeed) + "/s";
    }

    public void setPercent(float percent) {
        mPercent = percent;
    }

    public float getPercent() {
        return mPercent;
    }

    public String getPercentString() {
        return Utility.getPercent(mPercent);
    }

    public void setDownloadSize(long size) {
        mDownloadSize = size;
    }

    public long getDownloadSize() {
        return mDownloadSize;
    }

    public String getDownloadSizeString() {
        return Utility.getSize(mDownloadSize);
    }

    public void setTotalSize(long size) {
        mTotalSize = size;
    }

    public long getTotalSize() { return mTotalSize; }

    public boolean isRunningTask() {
        return mTaskState == VideoTaskState.DOWNLOADING || mTaskState == VideoTaskState.PROXYREADY;
    }

    public boolean isInterruptTask() {
        return mTaskState == VideoTaskState.PAUSE || mTaskState == VideoTaskState.ERROR;
    }

    public boolean isInitialTask() {
        return mTaskState == VideoTaskState.DEFAULT;
    }

    public String toString() {
        return "VideoTaskItem[Url="+mUrl+",Type="+mVideoType+",Percent="+mPercent+",DownloadSize="+mDownloadSize+"]";
    }
}
