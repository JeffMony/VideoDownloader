package com.jeffmony.downloader.merge;

public class M3U8MergeInfo {

    private String mUrl;
    private String mM3U8Path;
    private String mMergedPath;
    private String mOutputPath;
    private int mTotalTs;
    private int mCurTs;
    private int mErrorCode;

    public M3U8MergeInfo(String url, String m3u8Path, String mergedPath) {
        mUrl = url;
        mM3U8Path = m3u8Path;
        mMergedPath = mergedPath;
    }

    public String getUrl() {
        return mUrl;
    }

    public String getM3U8Path() {
        return mM3U8Path;
    }

    public String getMergedPath() {
        return mMergedPath;
    }

    public void setOutputPath(String outputPath) {
        mOutputPath = outputPath;
    }

    public String getOutputPath() {
        return mOutputPath;
    }

    public void setTotalTs(int totalTs) {
        mTotalTs = totalTs;
    }

    public void setCurTs(int curTs) {
        mCurTs = curTs;
    }

    public int getCurTs() {
        return mCurTs;
    }

    public void setErrorCode(int code) {
        mErrorCode = code;
    }

    public int getErrorCode() {
        return mErrorCode;
    }

}
