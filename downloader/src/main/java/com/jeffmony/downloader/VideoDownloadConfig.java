package com.jeffmony.downloader;

import android.content.Context;

import java.io.File;

public class VideoDownloadConfig {

    private Context mContext;
    private File mCacheRoot;
    private int mReadTimeOut;
    private int mConnTimeOut;
    private boolean mRedirect;
    private boolean mIgnoreAllCertErrors;
    private int mConcurrentCount;
    private boolean mShouldM3U8Merged;

    public VideoDownloadConfig(Context context, File cacheRoot, int readTimeOut,
                               int connTimeOut, boolean redirect, boolean ignoreAllCertErrors,
                               int concurrentCount, boolean shouldM3U8Merged) {
        mContext = context;
        mCacheRoot = cacheRoot;
        mReadTimeOut = readTimeOut;
        mConnTimeOut = connTimeOut;
        mRedirect = redirect;
        mIgnoreAllCertErrors = ignoreAllCertErrors;
        mConcurrentCount = concurrentCount;
        mShouldM3U8Merged = shouldM3U8Merged;
    }

    public Context getContext() {
        return mContext;
    }

    public File getCacheRoot() {
        return mCacheRoot;
    }

    public int getReadTimeOut() {
        return mReadTimeOut;
    }

    public int getConnTimeOut() {
        return mConnTimeOut;
    }

    public boolean shouldRedirect() {
        return mRedirect;
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
