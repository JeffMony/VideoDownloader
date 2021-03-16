package com.jeffmony.downloader.m3u8;

import android.net.Uri;
import android.text.TextUtils;

import com.jeffmony.downloader.utils.VideoDownloadUtils;

public class M3U8Seg implements Comparable<M3U8Seg> {
    private float mDuration;
    private int mIndex;
    private String mUrl;
    private String mName;
    private long mTsSize;
    private boolean mHasDiscontinuity;
    private boolean mHasKey;
    private String mMethod;
    private String mKeyUri;
    private String mKeyIV;
    private boolean mIsMessyKey;
    private long mContentLength;
    private int mRetryCount;
    private boolean mHasInitSegment;
    private String mInitSegmentUri;
    private String mSegmentByteRange;

    public M3U8Seg() { }

    public void initTsAttributes(String url, float duration, int index,
                                 boolean hasDiscontinuity, boolean hasKey) {
        mUrl = url;
        mName = url;
        mDuration = duration;
        mIndex = index;
        mHasDiscontinuity = hasDiscontinuity;
        mHasKey = hasKey;
        mTsSize = 0L;
    }

    public void setKeyConfig(String method, String keyUri, String keyIV) {
        mMethod = method;
        mKeyUri = keyUri;
        mKeyIV = keyIV;
    }

    public void setInitSegmentInfo(String initSegmentUri, String segmentByteRange) {
        mHasInitSegment = true;
        mInitSegmentUri = initSegmentUri;
        mSegmentByteRange = segmentByteRange;
    }

    public boolean hasKey() {
        return mHasKey;
    }

    public String getMethod() {
        return mMethod;
    }

    public String getKeyUri() {
        return mKeyUri;
    }

    public String getLocalKeyUri() {
        return "local.key";
    }

    public String getKeyIV() {
        return mKeyIV;
    }

    public float getDuration() {
        return mDuration;
    }

    public String getUrl() {
        return mUrl;
    }

    public String getName() {
        return mName;
    }

    /**
     * if ts is local file, name is video_{index}.ts
     * if ts is network resource , name is starting with http or https.
     *
     * @param name
     */
    public void setName(String name) {
        this.mName = name;
    }

    public String getIndexName() {
        String suffixName = "";
        if (!TextUtils.isEmpty(mUrl)) {
            Uri uri = Uri.parse(mUrl);
            String fileName = uri.getLastPathSegment();
            if (!TextUtils.isEmpty(fileName)) {
                fileName = fileName.toLowerCase();
                suffixName = VideoDownloadUtils.getSuffixName(fileName);
            }
        }
        return VideoDownloadUtils.SEGMENT_PREFIX + mIndex + suffixName;
    }

    public void setTsSize(long tsSize) {
        mTsSize = tsSize;
    }

    public long getTsSize() {
        return mTsSize;
    }

    public boolean hasDiscontinuity() {
        return mHasDiscontinuity;
    }

    public void setIsMessyKey(boolean isMessyKey) {
        mIsMessyKey = isMessyKey;
    }

    public boolean isMessyKey() {
        return mIsMessyKey;
    }

    public void setContentLength(long contentLength) {
        mContentLength = contentLength;
    }

    public long getContentLength() {
        return mContentLength;
    }

    public void setRetryCount(int retryCount) {
        mRetryCount = retryCount;
    }

    public int getRetryCount() {
        return mRetryCount;
    }

    public boolean hasInitSegment() { return mHasInitSegment; }

    public String getInitSegmentUri() { return mInitSegmentUri; }

    public String getSegmentByteRange() { return mSegmentByteRange; }

    public String getInitSegmentName() {
        String suffixName = "";
        if (!TextUtils.isEmpty(mInitSegmentUri)) {
            Uri uri = Uri.parse(mInitSegmentUri);
            String fileName = uri.getLastPathSegment();
            if (!TextUtils.isEmpty(fileName)) {
                fileName = fileName.toLowerCase();
                suffixName = VideoDownloadUtils.getSuffixName(fileName);
            }
        }
        return VideoDownloadUtils.INIT_SEGMENT_PREFIX + mIndex + suffixName;
    }

    public String toString() {
        return "duration=" + mDuration + ", index=" + mIndex + ", name=" + mName;
    }

    @Override
    public int compareTo(M3U8Seg object) {
        return mName.compareTo(object.mName);
    }
}

