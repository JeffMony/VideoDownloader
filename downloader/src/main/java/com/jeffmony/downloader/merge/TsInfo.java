package com.jeffmony.downloader.merge;

public class TsInfo {

    private String mFilePath;
    private String mKey;
    private String mMethod;

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

    public String getMethod() {
        return mMethod;
    }
}
