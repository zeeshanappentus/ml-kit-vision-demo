package com.google.mlkit.vision.demo.video;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.mlkit.vision.demo.GraphicOverlay;
import com.google.mlkit.vision.demo.R;
import com.google.mlkit.vision.demo.java.VisionProcessorBase;
import com.google.mlkit.vision.demo.java.posedetector.PoseDetectorProcessor;
import com.google.mlkit.vision.demo.preference.PreferenceUtils;

public abstract class VideoBaseActivity extends AppCompatActivity {
    private static final String TAG = VideoBaseActivity.class.getSimpleName();

    private SimpleExoPlayer player;
    private PlayerView playerView;
    private GraphicOverlay graphicOverlay;

    private VisionProcessorBase imageProcessor;

    private int frameWidth, frameHeight;

    private boolean processing;
    private boolean pending;
    private Bitmap lastFrame;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_base_video);

        player = createPlayer();

        playerView = findViewById(R.id.player_view);
        playerView.setPlayer(player);
        FrameLayout contentFrame = playerView.findViewById(R.id.exo_content_frame);
        View videoFrameView = createVideoFrameView();
        if(videoFrameView != null) contentFrame.addView(videoFrameView);

        graphicOverlay = new GraphicOverlay(this, null);
        contentFrame.addView(graphicOverlay);

        populateProcessorSelector();

        setupPlayer(Uri.parse(getIntent().getStringExtra("VIDEO_VIEW")));

    }


    protected abstract @NonNull SimpleExoPlayer createPlayer();
    protected abstract @Nullable View createVideoFrameView();

    protected void processFrame(Bitmap frame){
        lastFrame = frame;
        if(imageProcessor != null){
            pending = processing;
            if(!processing){
                processing = true;
                if(frameWidth != frame.getWidth() || frameHeight != frame.getHeight()){
                    frameWidth = frame.getWidth();
                    frameHeight = frame.getHeight();
                    graphicOverlay.setImageSourceInfo(frameWidth, frameHeight, false);
                }
                imageProcessor.setOnProcessingCompleteListener(new VisionProcessorBase.OnProcessingCompleteListener() {
                    @Override
                    public void onProcessingComplete() {
                        processing = false;
                        onProcessComplete(frame);
                        if(pending) processFrame(lastFrame);
                    }
                });
                imageProcessor.processBitmap(frame, graphicOverlay);
            }
        }
    }

    protected void onProcessComplete(Bitmap frame){ }

    @Override
    protected void onResume() {
        super.onResume();
        createImageProcessor();
    }

    @Override
    protected void onPause() {
        super.onPause();
        player.pause();
        stopImageProcessor();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        player.stop();
        player.release();
    }

    private void setupPlayer(Uri uri){
        MediaItem mediaItem = MediaItem.fromUri(uri);
        player.stop();
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
    }

    private void populateProcessorSelector() {
        createImageProcessor();
        if(lastFrame != null) processFrame(lastFrame);
    }

    private void createImageProcessor() {
        stopImageProcessor();

        try {
            imageProcessor = new PoseDetectorProcessor(
                    this,
                    PreferenceUtils.getPoseDetectorOptionsForLivePreview(this),
                    PreferenceUtils.shouldShowPoseDetectionInFrameLikelihoodLivePreview(this),
                    PreferenceUtils.shouldPoseDetectionVisualizeZ(this),
                    PreferenceUtils.shouldPoseDetectionRescaleZForVisualization(this),
                    PreferenceUtils.shouldPoseDetectionRunClassification(this),
                    true
            );
        } catch (Exception e) {
            Log.e(TAG, "Can not create image processor: ", e);
            //Log.e(TAG, "Can not create image processor: " + selectedProcessor, e);
            Toast.makeText(
                    getApplicationContext(),
                    "Can not create image processor: " + e.getMessage(),
                    Toast.LENGTH_LONG)
                    .show();
        }
    }

    private void stopImageProcessor(){
        if(imageProcessor != null){
            imageProcessor.stop();
            imageProcessor = null;
            processing = false;
            pending = false;
        }
    }
}