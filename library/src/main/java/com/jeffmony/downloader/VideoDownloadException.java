package com.jeffmony.downloader;

public class VideoDownloadException extends Exception {
    private String mMsg;

    public VideoDownloadException(String message) {
        super(message);
        mMsg = message;
    }

    public VideoDownloadException(String message, Throwable cause) {
        super(message, cause);
        mMsg = message;
    }

    public VideoDownloadException(Throwable cause) {
        super(cause);
    }

    public String getMsg() {
        return mMsg;
    }
}