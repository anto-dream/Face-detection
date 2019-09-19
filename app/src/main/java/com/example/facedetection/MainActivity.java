package com.example.facedetection;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.view.TextureView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.core.VideoCapture;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.facedetection.custom.CircularProgressBar;
import com.google.firebase.FirebaseApp;

public class MainActivity extends AppCompatActivity implements ImageAnalyser.ClassificationUpdator {
    private static final String TAG = MainActivity.class.getSimpleName();
    private int REQUEST_CODE_PERMISSIONS = 101;
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA", "android.permission.RECORD_AUDIO", "android.permission.WRITE_EXTERNAL_STORAGE"};
    private TextureView textureView;
    private boolean mRecording = false;
    private TextView mSmiling, mLeftEye, mRightEye;
    private boolean mSleepy;
    private MediaPlayer mp;
    private CircularProgressBar circleProgressBar;
    private ImageAnalyser mImageAnalyser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseApp.initializeApp(this);
        setContentView(R.layout.activity_main);
        initValues();
        startStabilizing();
        if (allPermissionsGranted()) {
            startCamera(); //start camera if permission has been granted by user
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    private void startStabilizing() {
        circleProgressBar.setProgressWithAnimation(100);
    }

    private void initValues() {
        mp = MediaPlayer.create(this, R.raw.beep_sound);
        textureView = findViewById(R.id.view_finder);
        ImageView canvasRelative = findViewById(R.id.canvas);
        mSmiling = findViewById(R.id.smiling);
        mLeftEye = findViewById(R.id.left_eye);
        mRightEye = findViewById(R.id.right_eye);
        circleProgressBar = findViewById(R.id.custom_progressBar);
        mImageAnalyser = new ImageAnalyser(canvasRelative);
        mImageAnalyser.setUpdator(this);
    }

    @SuppressLint("RestrictedApi")
    private void startCamera() {
        CameraX.unbindAll();
        try {
            CameraX.getCameraWithLensFacing(CameraX.LensFacing.FRONT);
        } catch (CameraInfoUnavailableException e) {
            Log.d(TAG, "error: " + e.getMessage());
        }
        Rational aspectRatio = new Rational(textureView.getWidth(), textureView.getHeight());
        Size screen = new Size(textureView.getWidth(), textureView.getHeight()); //size of the screen
        CameraConfigs cameraConfigs = new CameraConfigs(this);
        Preview previewConfig = cameraConfigs.getPreviewConfig(textureView, aspectRatio, screen);
        ImageCapture imgCap = cameraConfigs.getImageCapture();
        VideoCapture videoCap = cameraConfigs.getVideoCaptureConfig(screen);
        ImageAnalysis analysis = cameraConfigs.getImageAnalysisConfig(mImageAnalyser, aspectRatio, screen);
        //bind to lifecycle:
        CameraX.bindToLifecycle(this, analysis);
    }

   /* @SuppressLint("RestrictedApi")
    private void setCaptureButton(ImageCapture imgCap, VideoCapture videoCap) {
        findViewById(R.id.imgCapture).setOnClickListener(v -> {
            File file = new File(Environment.getExternalStorageDirectory() + "/" + System.currentTimeMillis() + ".mp4");
            if (mRecording) {
                Log.d(TAG, "stopRecording");
                mRecording = false;
                videoCap.stopRecording();
            } else {
                Log.d(TAG, "startRecording ");
                mRecording = true;
                videoCap.startRecording(file, new VideoCapture.OnVideoSavedListener() {
                    @Override
                    public void onVideoSaved(@NonNull File file) {
                        String msg = "video captured at " + file.getAbsolutePath();
                        Toast.makeText(getBaseContext(), msg, Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(@NonNull VideoCapture.VideoCaptureError videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
                        String msg = "video capture failed : " + message;
                        Toast.makeText(getBaseContext(), msg, Toast.LENGTH_LONG).show();
                        if (cause != null) {
                            cause.printStackTrace();
                        }
                    }
                });
            }
            *//*imgCap.takePicture(file, new ImageCapture.OnImageSavedListener() {
                @Override
                public void onImageSaved(@NonNull File file) {
                    String msg = "Pic captured at " + file.getAbsolutePath();
                    Toast.makeText(getBaseContext(), msg, Toast.LENGTH_LONG).show();
                }

                @Override
                public void onError(@NonNull ImageCapture.ImageCaptureError imageCaptureError, @NonNull String message, @Nullable Throwable cause) {
                    String msg = "Pic capture failed : " + message;
                    Toast.makeText(getBaseContext(), msg, Toast.LENGTH_LONG).show();
                    if (cause != null) {
                        cause.printStackTrace();
                    }
                }
            });*//*
        });
    }*/

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void smilingProbability(float value) {
        mSmiling.setText("Smiling : " + value);
    }

    @Override
    public void eyeOpenProbability(float leftValue, float rightValue) {
        mLeftEye.setText("LeftEye Open : " + leftValue);
        mRightEye.setText("RightEye Open : " + rightValue);

        if ((double) leftValue < 0.5) {
            if (!mSleepy) {
                Log.d(TAG, "starting timer");
                timer.cancel();
                timer.start();
            }
            mSleepy = true;
        } else {
            mSleepy = false;
            timer.cancel();
            stopSound();
        }
    }

    CountDownTimer timer = new CountDownTimer(2000, 1000) {
        @Override
        public void onTick(long l) {
            Log.d(TAG, "onTick: " + l / 1000);
        }

        @Override
        public void onFinish() {
            Log.d(TAG, "onFinish: ");
            playSound();
        }
    };

    private void playSound() {
        Log.d(TAG, "playSound: ");
        if (!mp.isPlaying()) {
            mp.start();
        }
    }

    private void stopSound() {
        Log.d(TAG, "stopSound: ");
        if (mp.isPlaying()) {
            mp.seekTo(0);
            mp.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mp.release();
    }
}

