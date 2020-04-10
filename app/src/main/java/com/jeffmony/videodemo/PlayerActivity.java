package com.jeffmony.videodemo;

import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.jeffmony.playersdk.CommonPlayer;
import com.jeffmony.playersdk.IPlayer;
import com.jeffmony.playersdk.PlayerType;
import com.jeffmony.playersdk.WeakHandler;
import com.jeffmony.playersdk.utils.ScreenUtils;
import com.jeffmony.playersdk.utils.Utility;

public class PlayerActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = PlayerActivity.class.getSimpleName();
    private static final int MSG_UPDATE_PROGRESS = 0x1;
    private static final int INTERVAL = 1000;
    private static final int MAX_PROGRESS = 1000;

    private String mUrl;
    private int mScreenWidth;
    private long mTotalDuration;

    private TextureView mVideoView;
    private SeekBar mProgressView;
    private TextView mTimeView;
    private ImageButton mVideoStateBtn;
    private Surface mSurface;
    private CommonPlayer mPlayer;

    private WeakHandler mHandler = new WeakHandler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            if (msg.what == MSG_UPDATE_PROGRESS) {
                updateVideoProgress();
            }
            return true;
        }
    });

    private void updateVideoProgress() {
        long currentPosition = mPlayer.getCurrentPosition();
        long totalDuration = mTotalDuration;

        String timeStr = Utility.getVideoTimeString(currentPosition) + "/" + Utility.getVideoTimeString(totalDuration);
        mTimeView.setText(timeStr);
        mTimeView.setVisibility(View.VISIBLE);
        int progress = (int)(currentPosition * 1.0f / totalDuration * MAX_PROGRESS);
        mProgressView.setProgress(progress);
        mHandler.sendEmptyMessageDelayed(MSG_UPDATE_PROGRESS, INTERVAL);

    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        mUrl = getIntent().getStringExtra("videoUrl");
        mScreenWidth = ScreenUtils.getScreenWidth(this);
        initViews();
    }

    private void initViews() {
        mVideoView = (TextureView) findViewById(R.id.video_view);
        mProgressView = (SeekBar) findViewById(R.id.video_progress_view);
        mVideoStateBtn = (ImageButton) findViewById(R.id.video_state_btn);
        mTimeView = (TextView) findViewById(R.id.time_view);
        mVideoView.setSurfaceTextureListener(mSurfaceTextureListener);
        mProgressView.setOnSeekBarChangeListener(mSeekBarChangeListener);
        mVideoStateBtn.setOnClickListener(this);
    }

    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            mSurface = new Surface(surface);
            initPlayer();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return surface == null;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private void initPlayer() {
        mPlayer = new CommonPlayer(this, PlayerType.IJK_PLAYER);

        try {
            mPlayer.setDataSource(this, Uri.parse(mUrl));
        } catch (Exception e) {
            Log.w(TAG, "setDataSource failed, exception = " + e.getMessage());
            return;
        }
        mPlayer.setSurface(mSurface);
        mPlayer.setOnPreparedListener(mPrepareListener);
        mPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
        mPlayer.prepareAsync();
    }

    private IPlayer.OnPreparedListener mPrepareListener = new IPlayer.OnPreparedListener() {
        @Override
        public void onPrepared(IPlayer mp) {
            mVideoStateBtn.setClickable(true);
            mTotalDuration = mPlayer.getDuration();
            startPlayer();
        }
    };

    private IPlayer.OnVideoSizeChangedListener mSizeChangedListener = new IPlayer.OnVideoSizeChangedListener() {
        @Override
        public void onVideoSizeChanged(IPlayer mp, int width, int height, int rotationDegree, float pixelRatio, float darRatio) {
            int videoWidth = mScreenWidth;
            int videoHeight = (int)((height * videoWidth * 1.0f)/ width);
            updateVideoSize(videoWidth, videoHeight);
        }
    };

    private SeekBar.OnSeekBarChangeListener mSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            if (mPlayer != null) {
                mHandler.removeMessages(MSG_UPDATE_PROGRESS);
            }
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            if (mPlayer != null) {
                int progress = mProgressView.getProgress();
                long seekPosition = (long) (progress * 1.0f / MAX_PROGRESS * mTotalDuration);
                mPlayer.seekTo(seekPosition);
                mHandler.sendEmptyMessage(MSG_UPDATE_PROGRESS);
            }
        }
    };

    private void updateVideoSize(int width, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        mVideoView.setLayoutParams(params);
    }

    private void updatePlayerState() {
        if (mPlayer.isPlaying()) {
            mPlayer.pause();
            mHandler.removeMessages(MSG_UPDATE_PROGRESS);
            mVideoStateBtn.setImageResource(R.mipmap.video_play);
        } else {
            mPlayer.start();
            mHandler.sendEmptyMessage(MSG_UPDATE_PROGRESS);
            mVideoStateBtn.setImageResource(R.mipmap.video_pause);
        }
    }

    private void startPlayer() {
        if (!mPlayer.isPlaying()) {
            mPlayer.start();
            mHandler.sendEmptyMessage(MSG_UPDATE_PROGRESS);
            mVideoStateBtn.setImageResource(R.mipmap.video_pause);
        }
    }

    private void pausePlayer() {
        if (mPlayer.isPlaying()) {
            mPlayer.pause();
            mHandler.removeMessages(MSG_UPDATE_PROGRESS);
            mVideoStateBtn.setImageResource(R.mipmap.video_play);
        }
    }

    @Override
    public void onClick(View v) {
        if (v == mVideoStateBtn) {
            updatePlayerState();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        pausePlayer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mPlayer != null) {
            mHandler.removeMessages(MSG_UPDATE_PROGRESS);
            mPlayer.release();
            mPlayer = null;
        }
    }
}