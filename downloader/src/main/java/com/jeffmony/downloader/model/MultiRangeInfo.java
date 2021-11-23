package com.jeffmony.downloader.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class MultiRangeInfo implements Serializable {

    private static final long serialVersionUID = 1234567890123456789L;

    private List<Integer> mIds;
    private List<Long> mSizes;
    private List<Long> mStarts;
    private List<Long> mEnds;

    public MultiRangeInfo() {
        mIds = new ArrayList<>();
        mSizes = new ArrayList<>();
        mStarts = new ArrayList<>();
        mEnds = new ArrayList<>();
    }

    public void setIds(List<Integer> ids) {
        mIds.clear();
        mIds.addAll(ids);
    }

    public List<Integer> getIds() {
        return mIds;
    }

    public void setSizes(List<Long> sizes) {
        mSizes.clear();
        mSizes.addAll(sizes);
    }

    public List<Long> getSizes() {
        return mSizes;
    }

    public void setStarts(List<Long> starts) {
        mStarts.clear();
        mStarts.addAll(starts);
    }

    public List<Long> getStarts() {
        return mStarts;
    }

    public void setEnds(List<Long> ends) {
        mEnds.clear();
        mEnds.addAll(ends);
    }

    public List<Long> getEnds() {
        return mEnds;
    }

}
