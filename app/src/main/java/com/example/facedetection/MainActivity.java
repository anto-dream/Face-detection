package com.example.facedetection;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraX;
import androidx.camera.core.VideoCapture;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.facedetection.custom.CircularProgressBar;
import com.example.facedetection.custom.ShakeDetector;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceLandmark;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.Facing;
import com.otaliastudios.cameraview.Frame;
import com.otaliastudios.cameraview.FrameProcessor;

import java.io.File;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ImageAnalyser.ClassificationUpdator, FrameProcessor {
    private static final String TAG = MainActivity.class.getSimpleName();
    private final int REQUEST_CODE_PERMISSIONS = 101;
    private final long CIRCULAR_PROGRESS_DURATION = 6000;
    private final long VIDEO_RECORDING_DURATION = 10000;
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA", "android.permission.RECORD_AUDIO", "android.permission.WRITE_EXTERNAL_STORAGE"};
    private boolean mRecording = false;
    private TextView mSmiling, mLeftEye, mRightEye;
    private boolean mSleepy;
    private MediaPlayer mp;
    private CircularProgressBar mCircleProgressBar;
    private View mWarningView;
    private Animation mAnimFadeIn, mAnimFadeOut;
    private SensorManager mSensorManager;
    private ShakeDetector mDetector;
    private View mCircularProgressContainer;
    private TextView mCircularProgressText;
    private HandlerThread mHandlerThread;
    private CameraView mCameraView;
    private ImageView mCanvas;

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
        mSmiling = findViewById(R.id.smiling);
        mLeftEye = findViewById(R.id.left_eye);
        mRightEye = findViewById(R.id.right_eye);
        mCircleProgressBar = findViewById(R.id.custom_progressBar);
        mWarningView = findViewById(R.id.warning_view);
        mCircularProgressContainer = findViewById(R.id.stabilizing_parent);
        mCircularProgressText = findViewById(R.id.circular_progress_text);
        mCameraView = findViewById(R.id.face_detection_camera_view);
        mCanvas = findViewById(R.id.face_detection_camera_image_view);
        mCameraView.setFacing(Facing.FRONT);
        mCameraView.setLifecycleOwner(this);
    }

    private void initSensor() {
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mDetector = new ShakeDetector();
        mDetector.setOnShakeListener(count -> {
            Log.d(TAG, "onShake: " + count);
            startVideoRecording();
        });
        mSensorManager.registerListener(mDetector, sensor, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startStabilizingAnimation();
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

    public void smilingProbability(float value) {
        //smiling probability available here
    }

    public void eyeOpenProbability(float leftValue, float rightValue, float eulerY) {
        float eyeOpenProbability = (leftValue + rightValue) / 2;
        Log.d("TEST", "eyeOpenProbability: " + eyeOpenProbability);
        Log.d("TEST", "euler y: " + eulerY);
        if ((double) eyeOpenProbability < 0.7 || (eulerY < -20 || eulerY > 20)) {
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
        }, CIRCULAR_PROGRESS_DURATION);
    }

    private void cancelAnimationOnText(Animation animZoomIn, Animation animZoomOut) {
        new Handler().postDelayed(() -> {
            mCircularProgressContainer.setVisibility(View.GONE);
            findViewById(R.id.progressBar_indeterminate).setVisibility(View.VISIBLE);
            animZoomIn.setAnimationListener(null);
            animZoomOut.setAnimationListener(null);
            startFaceTracking();
        }, 4000);
    }

    private void startFaceTracking() {
        mCameraView.addFrameProcessor(this);
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
        mCameraView.stop();
        mCameraView.setVisibility(View.INVISIBLE);
        Log.d(TAG, "startRecording:");
        showVideoRecordingAnimation();
        CameraX.unbindAll();
        try {
            CameraX.getCameraWithLensFacing(CameraX.LensFacing.BACK);
        } catch (CameraInfoUnavailableException e) {
            e.printStackTrace();
        }
        Rational aspectRatio = new Rational(mCameraView.getWidth(), mCameraView.getHeight());
        Size screen = new Size(mCameraView.getWidth(), mCameraView.getHeight());
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
        CameraX.unbindAll();
        mCircularProgressContainer.setVisibility(View.GONE);
        stopWarning();
        initSensor();
        mCameraView.setVisibility(View.VISIBLE);
        mCameraView.start();
        mCameraView.setFacing(Facing.FRONT);
        startFaceTracking();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mp.release();
        mHandlerThread.quit();
    }

    @Override
    public void process(@NonNull Frame frame) {
        int width = frame.getSize().getWidth();
        int height = frame.getSize().getHeight();
        FirebaseVisionImageMetadata metadata = new FirebaseVisionImageMetadata.Builder()
                .setWidth(width)
                .setHeight(height)
                .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21)
                .setRotation(FirebaseVisionImageMetadata.ROTATION_270)
                .build();
        FirebaseVisionImage firebaseVisionImage = FirebaseVisionImage.fromByteArray(frame.getData(), metadata);
        FirebaseVisionFaceDetectorOptions options = new FirebaseVisionFaceDetectorOptions.Builder()
                .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
                .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)

                .build();
        FirebaseVisionFaceDetector faceDetector = FirebaseVision.getInstance().getVisionFaceDetector(options);
        faceDetector.detectInImage(firebaseVisionImage).addOnSuccessListener(firebaseVisionFaces -> {
            if (firebaseVisionFaces.size() > 0) {
                performFaceDetection(width, height, firebaseVisionFaces);
            } else {
                if (mCanvas != null) {
                    mCanvas.setImageBitmap(null);
                }
            }
        }).addOnFailureListener(e -> {
            if (mCanvas != null) {
                mCanvas.setImageBitmap(null);
            }
        });
    }

    private void performFaceDetection(int width, int height, List<FirebaseVisionFace> faces) {
        mCanvas.setImageBitmap(null);
        Bitmap bitmap;
        bitmap = Bitmap.createBitmap(height, width, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint facePaint = new Paint();
        facePaint.setColor(-3800852);
        facePaint.setStyle(Paint.Style.STROKE);
        facePaint.setStrokeWidth(8F);
        Paint faceTextPaint = new Paint();
        faceTextPaint.setColor(Color.RED);
        faceTextPaint.setTextSize(40F);
        faceTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
        Paint landmarkPaint = new Paint();
        landmarkPaint.setColor(-3800852);
        landmarkPaint.setStyle(Paint.Style.FILL);
        landmarkPaint.setStrokeWidth(8F);
        int index;
        int size = faces.size();
        for (index = 0; index < size; index++) {
            FirebaseVisionFace face = faces.get(index);
            canvas.drawRect(face.getBoundingBox(), facePaint);
            //canvas.drawText("Face$index", (face.getBoundingBox().centerX() - face.getBoundingBox().width() / 2F) + 8F, (face.getBoundingBox().centerY() + face.getBoundingBox().height() / 2F) - 8F, faceTextPaint);

            if (face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EYE) != null) {
                FirebaseVisionFaceLandmark leftEye = face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EYE);
                canvas.drawCircle(leftEye.getPosition().getX(), leftEye.getPosition().getY(), 8F, landmarkPaint);
            }
            if (face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EYE) != null) {
                FirebaseVisionFaceLandmark rightEye = face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EYE);
                canvas.drawCircle(rightEye.getPosition().getX(), rightEye.getPosition().getY(), 8F, landmarkPaint);
            }
            if (face.getLandmark(FirebaseVisionFaceLandmark.NOSE_BASE) != null) {
                FirebaseVisionFaceLandmark nose = face.getLandmark(FirebaseVisionFaceLandmark.NOSE_BASE);
                canvas.drawCircle(nose.getPosition().getX(), nose.getPosition().getY(), 8F, landmarkPaint);
            }
            if (face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EAR) != null) {
                FirebaseVisionFaceLandmark leftEar = face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EAR);
                canvas.drawCircle(leftEar.getPosition().getX(), leftEar.getPosition().getY(), 8F, landmarkPaint);
            }
            if (face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EAR) != null) {
                FirebaseVisionFaceLandmark rightEar = face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EAR);
                canvas.drawCircle(rightEar.getPosition().getX(), rightEar.getPosition().getY(), 8F, landmarkPaint);
            }
              /*  if (face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_LEFT) != null && face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_BOTTOM) != null && face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_RIGHT) != null) {
                    FirebaseVisionFaceLandmark leftMouth = face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_LEFT);
                    FirebaseVisionFaceLandmark bottomMouth = face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_BOTTOM);
                    FirebaseVisionFaceLandmark rightMouth = face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_RIGHT);
                    canvas.drawLine(leftMouth.getPosition().getX(), leftMouth.getPosition().getY(), bottomMouth.getPosition().getX(), bottomMouth.getPosition().getY(), landmarkPaint);
                    canvas.drawLine(bottomMouth.getPosition().getX(), bottomMouth.getPosition().getY(), rightMouth.getPosition().getX(), rightMouth.getPosition().getY(), landmarkPaint);
                }*/

            if (mCanvas != null) {
                Matrix matrix = new Matrix();
                matrix.preScale(-1F, 1F);
                Bitmap flippedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                mCanvas.setImageBitmap(flippedBitmap);
            }
            eyeOpenProbability(face.getLeftEyeOpenProbability(), face.getRightEyeOpenProbability(), face.getHeadEulerAngleY());
            //Log.d(TAG, "getEuler Y: " + face.getHeadEulerAngleY());
            //Log.d(TAG, "getEuler Z: " + face.getHeadEulerAngleZ());
        }
    }
}

