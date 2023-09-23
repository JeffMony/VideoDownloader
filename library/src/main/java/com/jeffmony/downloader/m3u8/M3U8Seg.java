package com.jeffmony.downloader.m3u8;

import android.net.Uri;
import android.text.TextUtils;

import com.jeffmony.downloader.utils.VideoDownloadUtils;

public class M3U8Seg implements Comparable<M3U8Seg> {
    private float mDuration;                     // 分片时长
    private int mIndex;                          // 分片索引值,第一个为0
    private int mSequence;                       // 分片的sequence, 根据initSequence自增得到的
    private String mUrl;                         // 分片url
    private String mName;                        // 分片名,可以自己定义
    private long mTsSize;                        // 分片大小
    private boolean mHasDiscontinuity;           // 分片前是否有#EXT-X-DISCONTINUITY标识
    private boolean mHasKey;                     // 分片是否有#EXT-X-KEY
    private String mMethod;                      // 加密的方式
    private String mKeyUri;                      // 加密的url
    private String mKeyIV;                       // 加密的IV
    private long mContentLength;                 // 分片的Content-Length
    private int mRetryCount;                     // 分片的请求重试次数
    private boolean mHasInitSegment;             // 分片前是否有#EXT-X-MAP
    private String mInitSegmentUri;              // MAP的url
    private String mSegmentByteRange;            // MAP的range
    private String mByteRange;                   // byteRange属性

    public M3U8Seg() { }

    public void initTsAttributes(
            String url,
            float duration,
            int index,
            int sequence,
            boolean hasDiscontinuity,
            String byteRange) {
        mUrl = url;
        mName = url;
        mDuration = duration;
        mIndex = index;
        mSequence = sequence;
        mHasDiscontinuity = hasDiscontinuity;
        mTsSize = 0L;
        mByteRange = byteRange;
    }

    public void setKeyConfig(String method, String keyUri, String keyIV) {
        mHasKey = true;
        mMethod = method;
        mKeyUri = keyUri;
        mKeyIV = keyIV;
    }

    public void setInitSegmentInfo(String initSegmentUri, String segmentByteRange) {
        mHasInitSegment = true;
        mInitSegmentUri = initSegmentUri;
        mSegmentByteRange = segmentByteRange;
    }

    public int getSequence() { return mSequence; }

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
        return  "local_"+ mIndex + ".key";
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

    public String getByteRange() { return mByteRange; }

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

