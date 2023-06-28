package com.jeffmony.downloader.m3u8;

import java.util.ArrayList;
import java.util.List;

public class M3U8 {

    private String mUrl;
    private List<M3U8Seg> mTsList;
    private float mTargetDuration;
    private int mInitSequence;
    private int mVersion = 3;
    private boolean mHasEndList;

    public M3U8() {
        this("");
    }

    public M3U8(String url) {
        mUrl = url;
        mInitSequence = 0;
        mTsList = new ArrayList<>();
    }

    public void addTs(M3U8Seg ts) {
        mTsList.add(ts);
    }

    public void setTargetDuration(float targetDuration) {
        mTargetDuration = targetDuration;
    }

    public void setVersion(int version) {
        mVersion = version;
    }

    public void setSequence(int sequence) {
        mInitSequence = sequence;
    }

    public void setHasEndList(boolean hasEndList) {
        mHasEndList = hasEndList;
    }

    public List<M3U8Seg> getTsList() {
        return mTsList;
    }

    public int getVersion() {
        return mVersion;
    }

    public float getTargetDuration() {
        return mTargetDuration;
    }

    public int getInitSequence() {
        return mInitSequence;
    }

    public boolean hasEndList() {
        return mHasEndList;
    }

    public long getDuration() {
        long duration = 0L;
        for (M3U8Seg ts : mTsList) {
            duration += ts.getDuration();
        }
        return duration;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof M3U8) {
            M3U8 m3u8 = (M3U8) obj;
            if (mUrl != null && mUrl.equals(m3u8.mUrl))
                return true;
        }
        return false;
    }
}
