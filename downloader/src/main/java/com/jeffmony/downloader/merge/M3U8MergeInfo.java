package com.jeffmony.downloader.merge;

public class M3U8MergeInfo {

  private String mUrl;
  private String mFilePath;
  private int mTotalTs;
  private int mCurTs;
  private int mErrorCode;

  public M3U8MergeInfo(String url, String filePath) {
    mUrl = url;
    mFilePath = filePath;
  }

  public String getUrl() {
    return mUrl;
  }

  public String getFilePath() {
    return mFilePath;
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
