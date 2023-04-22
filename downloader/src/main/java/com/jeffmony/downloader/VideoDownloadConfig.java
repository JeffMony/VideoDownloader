package com.jeffmony.downloader;

public class VideoDownloadConfig {
    private String mCacheRoot;
    private int mReadTimeOut;
    private int mConnTimeOut;
    private boolean mIgnoreAllCertErrors;
    private int mConcurrentCount;
    private boolean mShouldM3U8Merged;

    public VideoDownloadConfig(String cacheRoot, int readTimeOut,
                               int connTimeOut, boolean ignoreAllCertErrors,
                               int concurrentCount, boolean shouldM3U8Merged) {
        mCacheRoot = cacheRoot;
        mReadTimeOut = readTimeOut;
        mConnTimeOut = connTimeOut;
        mIgnoreAllCertErrors = ignoreAllCertErrors;
        mConcurrentCount = concurrentCount;
        mShouldM3U8Merged = shouldM3U8Merged;
    }

    public String getCacheRoot() {
        return mCacheRoot;
    }

    public int getReadTimeOut() {
        return mReadTimeOut;
    }

    public int getConnTimeOut() {
        return mConnTimeOut;
    }

    public boolean shouldIgnoreCertErrors() {
        return mIgnoreAllCertErrors;
    }

    public void setIgnoreAllCertErrors(boolean ignoreAllCertErrors) {
        mIgnoreAllCertErrors = ignoreAllCertErrors;
    }

    public int getConcurrentCount() {
        return mConcurrentCount;
    }

    public void setConcurrentCount(int count) {
        mConcurrentCount = count;
    }

    public void setShouldM3U8Merged(boolean enable) { mShouldM3U8Merged = enable; }

    public boolean shouldM3U8Merged() { return mShouldM3U8Merged; }
}
