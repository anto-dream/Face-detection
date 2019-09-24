package com.example.facedetection;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.util.Rational;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.camera.core.VideoCapture;
import androidx.camera.core.VideoCaptureConfig;

public class CameraConfigs {
    public static final String TAG = CameraConfigs.class.getSimpleName();
    private final Context mContext;

    public CameraConfigs(Context activity) {
        mContext = activity;
    }

    public Preview getPreviewConfig(TextureView textureView, Rational aspectRatio, Size screen) {
        PreviewConfig pConfig = new PreviewConfig.Builder().setTargetAspectRatio(aspectRatio).
                setTargetResolution(screen).setLensFacing(CameraX.LensFacing.FRONT).
                build();
        Preview preview = new Preview(pConfig);

        //to update the surface texture we  have to destroy it first then re-add it
        preview.setOnPreviewOutputUpdateListener(
                output -> {
                    ViewGroup parent = (ViewGroup) textureView.getParent();
                    parent.removeView(textureView);
                    parent.addView(textureView, 0);
                    textureView.setSurfaceTexture(output.getSurfaceTexture());
                    updateTransform(textureView);
                });
        return preview;
    }

    private void updateTransform(TextureView textureView) {
        Matrix mx = new Matrix();
        float w = textureView.getMeasuredWidth();
        float h = textureView.getMeasuredHeight();

        float cX = w / 2f;
        float cY = h / 2f;

        int rotationDgr;
        int rotation = (int) textureView.getRotation();

        switch (rotation) {
            case Surface.ROTATION_0:
                rotationDgr = 0;
                break;
            case Surface.ROTATION_90:
                rotationDgr = 90;
                break;
            case Surface.ROTATION_180:
                rotationDgr = 180;
                break;
            case Surface.ROTATION_270:
                rotationDgr = 270;
                break;
            default:
                return;
        }

        mx.postRotate((float) rotationDgr, cX, cY);
        textureView.setTransform(mx);
    }

    public ImageCapture getImageCapture() {
        ImageCaptureConfig imageCaptureConfig = new ImageCaptureConfig.Builder().setCaptureMode(ImageCapture.CaptureMode.MAX_QUALITY)
                .setTargetRotation(((AppCompatActivity) mContext).getWindowManager().getDefaultDisplay().getRotation()).setLensFacing(CameraX.LensFacing.FRONT).
                        build();
        return new ImageCapture(imageCaptureConfig);
    }

    @SuppressLint("RestrictedApi")
    public static VideoCapture getVideoCaptureConfig(Size screen, Rational aspectRatio) {
        VideoCaptureConfig config = new VideoCaptureConfig.Builder().
                setTargetAspectRatio(aspectRatio).
                setLensFacing(CameraX.LensFacing.BACK).setTargetResolution(screen).
                setTargetAspectRatio(new Rational(1, 1)).build();
        return  new VideoCapture(config);
    }

    public ImageAnalysis getImageAnalysisConfig(ImageAnalyser imageAnalyser, Rational aspectRatio, Size screen) {
        ImageAnalysisConfig analysisConfig = new ImageAnalysisConfig.Builder()
                .setTargetAspectRatio(aspectRatio)
                .setTargetResolution(screen)
                .setTargetRotation(((AppCompatActivity) mContext).getWindowManager().getDefaultDisplay().getRotation())
                .setLensFacing(CameraX.LensFacing.FRONT)
                .build();

        ImageAnalysis analysis = new ImageAnalysis(analysisConfig);
        analysis.setAnalyzer(imageAnalyser);
        return analysis;
    }
}
