package com.jeffmony.downloader;

import com.jeffmony.downloader.model.VideoTaskItem;
import com.jeffmony.downloader.model.VideoTaskState;
import com.jeffmony.downloader.utils.LogUtils;

import java.util.ArrayList;
import java.util.List;

public class VideoDownloadQueue {

    private static final String TAG = "VideoDownloadQueue";

    private List<VideoTaskItem> mQueue;

    public VideoDownloadQueue() {
        mQueue = new ArrayList<>();
    }

    public List<VideoTaskItem> getDownloadList() {
        return mQueue;
    }

    //put it into queue
    public void offer(VideoTaskItem item) {
        mQueue.add(item);
    }

    //Remove Queue head item,
    //Return Next Queue head.
    public VideoTaskItem poll() {
        try {
            if (mQueue.size() >= 2) {
                mQueue.remove(0);
                return mQueue.get(0);
            } else if (mQueue.size() == 1) {
                mQueue.remove(0);
            }
        } catch (Exception e) {
            LogUtils.w(TAG, "DownloadQueue remove failed.");
        }
        return null;
    }

    public VideoTaskItem peek() {
        try {
            if (mQueue.size() >= 1) {
                return mQueue.get(0);
            }
        } catch (Exception e) {
            LogUtils.w(TAG,"DownloadQueue get failed.");
        }
        return null;
    }

    public boolean remove(VideoTaskItem item) {
        if (contains(item)) {
            return mQueue.remove(item);
        }
        return false;
    }

    public boolean contains(VideoTaskItem item) {
        return mQueue.contains(item);
    }

    public VideoTaskItem getTaskItem(String url) {
        try {
            for (int index = 0; index < mQueue.size(); index++) {
                if (mQueue.get(index).getUrl().equals(url)) {
                    return mQueue.get(index);
                }
            }
        } catch (Exception e) {
            LogUtils.w(TAG,"DownloadQueue getTaskItem failed.");
        }
        return null;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public int size() {
        return mQueue.size();
    }

    public boolean isHead(VideoTaskItem item) {
        return item.equals(peek());
    }

    public int getDownloadingCount() {
        int count = 0;
        try {
            for (int index = 0; index < mQueue.size(); index++) {
                if (isTaskRunnig(mQueue.get(index))) {
                    count++;
                }
            }
        } catch (Exception e) {
            LogUtils.w(TAG,"DownloadQueue getDownloadingCount failed.");
        }
        return count;
    }

    public boolean isTaskPending(VideoTaskItem item) {
        int taskState = item.getTaskState();
        return taskState == VideoTaskState.PENDING;
    }

    public boolean isTaskRunnig(VideoTaskItem item) {
        int taskState = item.getTaskState();
        return taskState == VideoTaskState.PREPARE
                || taskState == VideoTaskState.START
                || taskState == VideoTaskState.DOWNLOADING
                || taskState == VideoTaskState.PROXYREADY;
    }

    public VideoTaskItem peekPendingTask() {
        try {
            for (int index = 0; index < mQueue.size(); index++) {
                VideoTaskItem item = mQueue.get(index);
                if (isTaskPending(item)) {
                    return item;
                }
            }
        } catch (Exception e) {
            LogUtils.w(TAG,"DownloadQueue getDownloadingCount failed.");
        }
        return null;
    }

}
