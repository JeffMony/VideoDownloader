package com.jeffmony.videodemo;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jeffmony.downloader.merge.M3U8MergeInfo;
import com.jeffmony.downloader.merge.M3U8MergeManager;
import com.jeffmony.downloader.merge.IM3U8MergeListener;
import com.jeffmony.downloader.model.VideoTaskItem;
import com.jeffmony.downloader.utils.LogUtils;
import com.jeffmony.downloader.utils.Utility;

import java.util.List;

public class MergeVideoListAdapter extends ArrayAdapter<VideoTaskItem> {

  private static final String TAG = "MergeVideoListAdapter";

  private Context mContext;

  public MergeVideoListAdapter(Context context, int resource, List<VideoTaskItem> itemList) {
    super(context, resource, itemList);
    mContext = context;
  }

  @NonNull
  @Override
  public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
    View view = LayoutInflater.from(getContext()).inflate(R.layout.m3u8_item, null);
    VideoTaskItem taskItem = getItem(position);
    TextView urlTxt = view.findViewById(R.id.m3u8_url_txt);
    urlTxt.setText(taskItem.getUrl());
    TextView infoTxt = view.findViewById(R.id.m3u8_info_txt);
    infoTxt.setText("大小:" + Utility.getSize(taskItem.getTotalSize()) + "  分片:" + taskItem.getTotalTs());
    TextView progressTxt = view.findViewById(R.id.m3u8_merge_progress);
    TextView mergeBtn = view.findViewById(R.id.m3u8_merge_btn);

    mergeBtn.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        progressTxt.setVisibility(View.VISIBLE);
        M3U8MergeManager.getInstance().mergeM3U8(taskItem, new M3U8MergeListener(progressTxt));
      }
    });

    return view;
  }

  class M3U8MergeListener implements IM3U8MergeListener {

    private TextView mProgressTxt;

    public M3U8MergeListener(TextView progressTxt) {
      mProgressTxt = progressTxt;
    }


    @Override
    public void onMergeProgress(M3U8MergeInfo info) {
      mProgressTxt.setText(info.getCurTs() + "");
    }

    @Override
    public void onMergeFailed(M3U8MergeInfo info) {
      LogUtils.w(TAG, "onMergeFailed error = " + info.getErrorCode());
      mProgressTxt.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onMergeFinished(M3U8MergeInfo info) {
      mProgressTxt.setText(info.getCurTs() + "");
    }
  }
}
