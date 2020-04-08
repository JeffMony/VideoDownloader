package com.jeffmony.downloader.model;

import java.io.Serializable;
import java.util.LinkedHashMap;

public class VideoDownloadInfo implements Serializable {

    private String mVideoUrl;
    private String mFinalUrl; // Final url by redirecting.
    private boolean mIsCompleted;
    private int mVideoType;
    private long mCachedLength;
    private long mTotalLength;
    private int mCachedTs;
    private int mTotalTs;
    private String mSaveDir;
    private LinkedHashMap<Long, Long>
            mSegmentList; // save the video segements' info.
    private float mPercent;
    private long mDownloadTime;
    private String mMimeType;
    private String mFileHash;

    public VideoDownloadInfo(String videoUrl) {
        super();
        mVideoUrl = videoUrl;
    }

    public String getVideoUrl() { return mVideoUrl; }

    public void setFinalUrl(String finalUrl) { mFinalUrl = finalUrl; }

    public String getFinalUrl() { return mFinalUrl; }

    public void setIsCompleted(boolean isCompleted) {
        mIsCompleted = isCompleted;
    }

    public boolean getIsCompleted() { return mIsCompleted; }

    public void setVideoType(int videoType) { mVideoType = videoType; }

    public int getVideoType() { return mVideoType; }

    public void setCachedLength(long cachedLength) {
        mCachedLength = cachedLength;
    }

    public long getCachedLength() { return mCachedLength; }

    public void setTotalLength(long totalLength) {
        mTotalLength = totalLength;
    }

    public long getTotalLength() { return mTotalLength; }

    public void setCachedTs(int cachedTs) { mCachedTs = cachedTs; }

    public int getCachedTs() { return mCachedTs; }

    public void setTotalTs(int totalTs) { mTotalTs = totalTs; }

    public int getTotalTs() { return mTotalTs; }

    public void setSaveDir(String saveDir) { mSaveDir = saveDir; }

    public String getSaveDir() { return mSaveDir; }

    public void setSegmentList(LinkedHashMap<Long, Long> list) {
        mSegmentList = list;
    }

    public LinkedHashMap<Long, Long> getSegmentList() { return mSegmentList; }

    public void setPercent(float percent) { mPercent = percent; }

    public float getPercent() { return mPercent; }

    public void setDownloadTime(long time) {
        mDownloadTime = time;
    }

    public long getDownloadTime() {
        return mDownloadTime;
    }

    public void setMimeType(String mimeType) { mMimeType = mimeType; }

    public String getMimeType() { return mMimeType; }

    public void setFileHash(String name) { mFileHash = name; }

    public String getFileHash() { return mFileHash; }
}
