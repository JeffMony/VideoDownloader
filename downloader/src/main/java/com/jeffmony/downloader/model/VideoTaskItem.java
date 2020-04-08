package com.jeffmony.downloader.model;

public class VideoTaskItem {

    private String mUrl;            //下载视频的url
    private int mTaskState;         //当前任务的状态
    private String mMimeType;       // 视频url的mime type

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
}
