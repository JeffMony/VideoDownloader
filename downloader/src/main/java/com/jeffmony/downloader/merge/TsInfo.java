package com.jeffmony.downloader.merge;

public class TsInfo {

    private String mFilePath;
    private String mKey;
    private String mMethod;
    private String mIv;

    public TsInfo(String filePath) {
        mFilePath = filePath;
    }

    public void setKey(String key) {
        mKey = key;
    }

    public void setMethod(String method) {
        mMethod = method;
    }

    public String getFilePath() {
        return mFilePath;
    }

    public String getKey() {
        return mKey;
    }

    public void setIv(String iv) {
        mIv = iv;
    }

    public String getIv() { return mIv; }


    public String getMethod() {
        return mMethod;
    }
}
