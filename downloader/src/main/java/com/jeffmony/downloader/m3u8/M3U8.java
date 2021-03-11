package com.jeffmony.downloader.m3u8;

import java.util.ArrayList;
import java.util.List;

public class M3U8 {
    private static final String TAG = "M3U8";

    private String mUrl;
    private String mBaseUrl;
    private String mHostUrl;
    private List<M3U8Ts> mTsList;
    private float mTargetDuration;
    private int mSequence;
    private int mVersion = 3;
    private boolean mHasEndList;

    public M3U8() {
        this("", "", "");
    }

    public M3U8(String url, String baseUrl, String hostUrl) {
        this.mUrl = url;
        this.mBaseUrl = baseUrl;
        this.mHostUrl = hostUrl;
        this.mSequence = 0;
        this.mTsList = new ArrayList<>();
    }

    public void addTs(M3U8Ts ts) {
        this.mTsList.add(ts);
    }

    public void setTargetDuration(float targetDuration) {
        this.mTargetDuration = targetDuration;
    }

    public void setVersion(int version) {
        this.mVersion = version;
    }

    public void setSequence(int sequence) {
        this.mSequence = sequence;
    }

    public void setHasEndList(boolean hasEndList) {
        this.mHasEndList = hasEndList;
    }

    public List<M3U8Ts> getTsList() {
        return mTsList;
    }

    public int getVersion() {
        return mVersion;
    }

    public float getTargetDuration() {
        return mTargetDuration;
    }

    public int getSequence() {
        return mSequence;
    }

    public boolean hasEndList() {
        return mHasEndList;
    }

    public long getDuration() {
        long duration = 0L;
        for (M3U8Ts ts : mTsList) {
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
