package com.jeffmony.downloader.m3u8;

import com.jeffmony.downloader.utils.LogUtils;

import java.util.ArrayList;
import java.util.List;

public class M3U8 {
    private static final String TAG = "M3U8";

    private String mUrl;
    private String mBaseUrl;
    private String mHostUrl;
    private List<M3U8Ts> mTsList;
    private float mTargetDuration;
    private int mSequence = 0;
    private int mVersion = 3;
    private boolean mHasEndList;
    private int mCurTsIndex = 0;

    public M3U8(String url, String baseUrl, String hostUrl) {
        this.mUrl = url;
        this.mBaseUrl = baseUrl;
        this.mHostUrl = hostUrl;
        this.mSequence = 0;
        this.mTsList = new ArrayList<>();
    }

    public void addTs(M3U8Ts ts) { this.mTsList.add(ts); }

    public void setTargetDuration(float targetDuration) {
        this.mTargetDuration = targetDuration;
    }

    public void setVersion(int version) { this.mVersion = version; }

    public void setSequence(int sequence) { this.mSequence = sequence; }

    public void setHasEndList(boolean hasEndList) {
        this.mHasEndList = hasEndList;
    }

    public List<M3U8Ts> getTsList() { return mTsList; }

    public int getVersion() { return mVersion; }

    public float getTargetDuration() { return mTargetDuration; }

    public int getSequence() { return mSequence; }

    public boolean hasEndList() { return mHasEndList; }

    public long getDuration() {
        long duration = 0L;
        for (M3U8Ts ts : mTsList) {
            duration += ts.getDuration();
        }
        return duration;
    }

    public long getDuration(int tsIndex) {
        if (tsIndex < 0 || mTsList.size() <= 0) {
            return 0;
        } else if (tsIndex >= mTsList.size() - 1) {
            return getDuration();
        } else {
            long duration = 0L;
            for (int index = 0; index <= tsIndex; index++) {
                duration += mTsList.get(index).getDuration();
            }
            return duration;
        }
    }

    public int getTsIndex(long playDuration) {
        long duration = 0L;
        int index = 0;
        for (M3U8Ts ts : mTsList) {
            if (playDuration >= duration &&
                    playDuration < duration + ts.getDuration()) {
                return index;
            }
            duration += ts.getDuration();
            index++;
        }
        return 0;
    }

    // Figure it out about the cached size between fromIndex and endIndex.
    public long getCachedSizeFromIndex(int fromIndex, int endIndex) {
        if (mTsList.size() <= 0 || fromIndex < 0 ||
                fromIndex >= mTsList.size() - 1 || endIndex < 0)
            return 0;
        if (fromIndex > endIndex) {
            return 0;
        }
        if (endIndex > mTsList.size() - 1) {
            endIndex = mTsList.size() - 1;
        }
        long cachedSize = 0L;
        for (int index = fromIndex; index <= endIndex; index++) {
            M3U8Ts ts = mTsList.get(index);
            cachedSize += ts.getTsSize();
        }
        return cachedSize;
    }

    public void setCurTsIndex(int curTsIndex) { this.mCurTsIndex = curTsIndex; }

    public int getCurTsIndex() { return mCurTsIndex; }

    public void printM3U8Info() {
        LogUtils.i(TAG, "M3U8 Url=" + mUrl);
        LogUtils.i(TAG,"M3U8 BaseUrl=" + mBaseUrl);
        LogUtils.i(TAG,"M3U8 HostUrl=" + mHostUrl);
        printTsInfo();
    }

    public void printTsInfo() {
        for (int index = 0; index < mTsList.size(); index++) {
            LogUtils.i(TAG,"" + mTsList.get(index));
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof M3U8) {
            M3U8 m3u8 = (M3U8)obj;
            if (mUrl != null && mUrl.equals(m3u8.mUrl))
                return true;
        }
        return false;
    }
}
