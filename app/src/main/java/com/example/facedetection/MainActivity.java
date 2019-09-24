package com.example.facedetection;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.view.Menu;
import android.view.MenuItem;
import android.view.TextureView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.core.VideoCapture;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.facedetection.custom.CircularProgressBar;
import com.example.facedetection.custom.ShakeDetector;
import com.google.firebase.FirebaseApp;

import java.io.File;

public class MainActivity extends AppCompatActivity implements ImageAnalyser.ClassificationUpdator {
    private static final String TAG = MainActivity.class.getSimpleName();
    private final int REQUEST_CODE_PERMISSIONS = 101;
    private final long CIRCULAR_PROGRESS_DURATION = 10000;
    private final long VIDEO_RECORDING_DURATION = 10000;
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA", "android.permission.RECORD_AUDIO", "android.permission.WRITE_EXTERNAL_STORAGE"};
    private TextureView textureView;
    private boolean mRecording = false;
    private TextView mSmiling, mLeftEye, mRightEye;
    private boolean mSleepy;
    private MediaPlayer mp;
    private CircularProgressBar mCircleProgressBar;
    private ImageAnalyser mImageAnalyser;
    private View mWarningView;
    private Animation mAnimFadeIn, mAnimFadeOut;
    private SensorManager mSensorManager;
    private ShakeDetector mDetector;
    private View mCircularProgressContainer;
    private TextView mCircularProgressText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseApp.initializeApp(this);
        setContentView(R.layout.activity_main);
        initValues();
        if (allPermissionsGranted()) {
            startStabilizingAnimation();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        initSensor();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(mDetector);
    }

    private void initValues() {
        mp = MediaPlayer.create(this, R.raw.beep_sound);
        textureView = findViewById(R.id.view_finder);
        ImageView canvasRelative = findViewById(R.id.canvas);
        mSmiling = findViewById(R.id.smiling);
        mLeftEye = findViewById(R.id.left_eye);
        mRightEye = findViewById(R.id.right_eye);
        mCircleProgressBar = findViewById(R.id.custom_progressBar);
        mWarningView = findViewById(R.id.warning_view);
        mCircularProgressContainer = findViewById(R.id.stabilizing_parent);
        mCircularProgressText = findViewById(R.id.circular_progress_text);
        mImageAnalyser = new ImageAnalyser(null);
        mImageAnalyser.setUpdator(this);
    }

    private void initSensor() {
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        Log.d(TAG, sensor.getName() + " mindelay: " + sensor.getMinDelay());
        Log.d(TAG, "initSensor: registering");
        mDetector = new ShakeDetector();
        mDetector.setOnShakeListener(count -> {
            Log.d(TAG, "onShake: " + count);
            startVideoRecording();
        });
        mSensorManager.registerListener(mDetector, sensor, SensorManager.SENSOR_DELAY_UI);
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
        ImageAnalysis analysis = cameraConfigs.getImageAnalysisConfig(mImageAnalyser, aspectRatio, screen);
        //bind to lifecycle:
        CameraX.bindToLifecycle(this, analysis, previewConfig);
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
        //smiling probability available here
    }

    @Override
    public void eyeOpenProbability(float leftValue, float rightValue) {
        float eyeOpenProbability = (leftValue + rightValue) / 2;
        Log.d("TEST", "eyeOpenProbability: " + leftValue);
        if ((double) leftValue < 0.7) {
            if (!mSleepy) {
                Log.d("TEST", "starting timer");
                //timer.cancel();
                timer.start();
            }
            mSleepy = true;
        } else {
            Log.d("TEST", "stopping timer");
            mSleepy = false;
            timer.cancel();
            stopWarning();
        }
    }

    CountDownTimer timer = new CountDownTimer(2000, 1000) {
        @Override
        public void onTick(long l) {
            //Log.d(TAG, "onTick: " + l / 1000);
        }

        @Override
        public void onFinish() {
            startWarning();
        }
    };

    private void startWarning() {
        //Log.d(TAG, "startWarning: ");
        if (!mp.isPlaying()) {
            mp.start();
            enableWarning();
        }
    }

    private void stopWarning() {
        //Log.d(TAG, "stopWarning: ");
         try {
            if (mp.isPlaying()) {
                mp.seekTo(0);
                mp.pause();
                disableWarning();
            }
        } catch (IllegalStateException ex) {
            Log.e(TAG, "stopWarning: " + ex.getMessage());
        }
    }

    private void startStabilizingAnimation() {
        mCircleProgressBar.setProgressWithAnimation(100, CIRCULAR_PROGRESS_DURATION);
        Animation animZoomIn = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.zoom_in);
        Animation animZoomOut = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.zoom_out);
        animZoomIn.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mCircularProgressText.startAnimation(animZoomOut);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        animZoomOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mCircularProgressText.startAnimation(animZoomIn);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        new Handler().postDelayed(() -> {
            mCircularProgressText.startAnimation(animZoomIn);
            cancelAnimationOnText(animZoomIn, animZoomOut);
            startFaceTracking();
        }, CIRCULAR_PROGRESS_DURATION);
    }

    private void startFaceTracking() {
        startCamera();
    }

    private void cancelAnimationOnText(Animation animZoomIn, Animation animZoomOut) {
        new Handler().postDelayed(() -> {
            mCircularProgressContainer.setVisibility(View.GONE);
            findViewById(R.id.progressBar_indeterminate).setVisibility(View.VISIBLE);
            animZoomIn.setAnimationListener(null);
            animZoomOut.setAnimationListener(null);
        }, 6000);
    }

    /**
     * Enable red blink screen and Alarm
     */
    private void enableWarning() {
        mAnimFadeIn = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fade_in);
        mAnimFadeOut = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fade_out);
        mAnimFadeIn.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mWarningView.startAnimation(mAnimFadeOut);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        mAnimFadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mWarningView.startAnimation(mAnimFadeIn);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        mWarningView.startAnimation(mAnimFadeIn);
    }

    /**
     * Disable warning view and alarm
     */
    private void disableWarning() {
        mWarningView.setVisibility(View.GONE);
        if (mAnimFadeIn != null && mAnimFadeOut != null) {
            mAnimFadeIn.setAnimationListener(null);
            mAnimFadeOut.setAnimationListener(null);
        }
    }


    @SuppressLint("RestrictedApi")
    private void startVideoRecording() {
        Log.d(TAG, "startRecording:");
        showVideoRecordingAnimation();
        CameraX.unbindAll();
        try {
            CameraX.getCameraWithLensFacing(CameraX.LensFacing.BACK);
        } catch (CameraInfoUnavailableException e) {
            e.printStackTrace();
        }
        Rational aspectRatio = new Rational(textureView.getWidth(), textureView.getHeight());
        Size screen = new Size(textureView.getWidth(), textureView.getHeight());
        VideoCapture videoCap = CameraConfigs.getVideoCaptureConfig(screen, aspectRatio);
        CameraX.bindToLifecycle(this, videoCap);
        File file = new File(Environment.getExternalStorageDirectory() + "/" + System.currentTimeMillis() + ".mp4");
        if (!mRecording) {
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
            new Handler().postDelayed(() -> {
                mRecording = false;
                Log.d(TAG, "stop recording ");
                mCircularProgressText.setText(R.string.video_recording_completed);
                videoCap.stopRecording();
            }, VIDEO_RECORDING_DURATION);
        }
    }

    private void showVideoRecordingAnimation() {
        mCircleProgressBar.setProgress(0);
        mCircleProgressBar.setProgressWithAnimation(100, VIDEO_RECORDING_DURATION);
        mCircularProgressText.setText(R.string.video_recording);
        mCircularProgressContainer.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh:
                reset();
                break;

            default:
                super.onOptionsItemSelected(item);
                break;
        }
        return true;
    }

    private void reset() {
        mCircularProgressContainer.setVisibility(View.GONE);
        stopWarning();
        startFaceTracking();
        initSensor();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mp.release();
    }
}

