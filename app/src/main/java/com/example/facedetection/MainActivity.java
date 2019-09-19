package com.example.facedetection;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.Image;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.camera.core.VideoCapture;
import androidx.camera.core.VideoCaptureConfig;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.example.facedetection.custom.CircularProgressBar;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.common.FirebaseVisionPoint;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceContour;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceLandmark;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private int REQUEST_CODE_PERMISSIONS = 101;
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA", "android.permission.RECORD_AUDIO", "android.permission.WRITE_EXTERNAL_STORAGE"};
    private TextureView textureView;
    private FirebaseVisionFaceDetectorOptions highAccuracyOpts;
    private boolean mRecording = false;
    private ImageView canvasRelative;
    private TextView mSmiling, mLeftEye, mRightEye;
    private double mEyeOpenProbability;
    private boolean mSleepy;
    private boolean mInitial = true;
    private MediaPlayer mp;
    private CircularProgressBar circleProgressBar;

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
        canvasRelative = findViewById(R.id.canvas);
        mSmiling = findViewById(R.id.smiling);
        mLeftEye = findViewById(R.id.left_eye);
        mRightEye = findViewById(R.id.right_eye);
        circleProgressBar = findViewById(R.id.custom_progressBar);
        findViewById(R.id.tap).setOnClickListener(view -> playsound());
    }

    @SuppressLint("RestrictedApi")
    private void startCamera() {
        CameraX.unbindAll();
        try {
            CameraX.getCameraWithLensFacing(CameraX.LensFacing.FRONT);
        } catch (CameraInfoUnavailableException e) {
            e.printStackTrace();
        }
        Rational aspectRatio = new Rational(textureView.getWidth(), textureView.getHeight());
        Size screen = new Size(textureView.getWidth(), textureView.getHeight()); //size of the screen
        Preview preview = getPreviewConfig(aspectRatio, screen);
        final ImageCapture imgCap = getImageCapture();
        VideoCapture videoCap = new VideoCapture(getVideoCaptureConfig(screen));
        ImageAnalysis analysis = getImageAnalysis(aspectRatio, screen);
        //bind to lifecycle:
        CameraX.bindToLifecycle((LifecycleOwner) this, analysis);
        configureFaceDetection();
    }

    private VideoCaptureConfig getVideoCaptureConfig(Size screen) {
        @SuppressLint("RestrictedApi")
        VideoCaptureConfig config = new VideoCaptureConfig.Builder().
                setLensFacing(CameraX.LensFacing.BACK).setTargetResolution(screen).
                setTargetAspectRatio(new Rational(1, 1)).build();
        return config;
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

    private ImageAnalysis getImageAnalysis(Rational aspectRatio, Size screen) {
        ImageAnalysisConfig analysisConfig = new ImageAnalysisConfig.Builder()
                .setTargetAspectRatio(aspectRatio)
                .setTargetResolution(screen)
                .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
                .setLensFacing(CameraX.LensFacing.FRONT)
                .build();

        ImageAnalysis analysis = new ImageAnalysis(analysisConfig);
        analysis.setAnalyzer(new CustomImageAnalyzer());
        return analysis;
    }

    private ImageCapture getImageCapture() {
        ImageCaptureConfig imageCaptureConfig = new ImageCaptureConfig.Builder().setCaptureMode(ImageCapture.CaptureMode.MAX_QUALITY)
                .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation()).setLensFacing(CameraX.LensFacing.FRONT).
                        build();
        return new ImageCapture(imageCaptureConfig);
    }

    private Preview getPreviewConfig(Rational aspectRatio, Size screen) {
        PreviewConfig pConfig = new PreviewConfig.Builder().setTargetAspectRatio(aspectRatio).
                setTargetResolution(screen).setLensFacing(CameraX.LensFacing.FRONT).
                build();
        Preview preview = new Preview(pConfig);

        //to update the surface texture we  have to destroy it first then re-add it
        preview.setOnPreviewOutputUpdateListener(
                output -> {
                    Log.d(TAG, "getPreviewConfig: output");
                    ViewGroup parent = (ViewGroup) textureView.getParent();
                    parent.removeView(textureView);
                    parent.addView(textureView, 0);
                    textureView.setSurfaceTexture(output.getSurfaceTexture());
                    updateTransform();
                });
        return preview;
    }

    private void configureFaceDetection() {
        highAccuracyOpts =
                new FirebaseVisionFaceDetectorOptions.Builder()
                        .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
                        .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
                        .enableTracking()
                        .build();
    }

    private void updateTransform() {
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


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                //ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
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

    private class CustomImageAnalyzer implements ImageAnalysis.Analyzer {

        private int degreesToFirebaseRotation(int degrees) {
            switch (degrees) {
                case 0:
                    return FirebaseVisionImageMetadata.ROTATION_0;
                case 90:
                    return FirebaseVisionImageMetadata.ROTATION_90;
                case 180:
                    return FirebaseVisionImageMetadata.ROTATION_180;
                case 270:
                    return FirebaseVisionImageMetadata.ROTATION_270;
                default:
                    throw new IllegalArgumentException(
                            "Rotation must be 0, 90, 180, or 270.");
            }
        }

        @Override
        public void analyze(ImageProxy imageProxy, int degrees) {
            if (imageProxy == null || imageProxy.getImage() == null) {
                return;
            }
            Image mediaImage = imageProxy.getImage();
            int height = mediaImage.getHeight();
            int width = mediaImage.getWidth();
            int rotation = degreesToFirebaseRotation(degrees);
            FirebaseVisionImage image =
                    FirebaseVisionImage.fromMediaImage(mediaImage, rotation);
            // Pass image to an ML Kit Vision API
            // ...
            FirebaseVisionFaceDetector detector = FirebaseVision.getInstance()
                    .getVisionFaceDetector(highAccuracyOpts);

            Task<List<FirebaseVisionFace>> result =
                    detector.detectInImage(image)
                            .addOnSuccessListener(
                                    faces -> {
                                        // Task completed successfully
                                        // ...
                                        int size = faces.size();
                                        if (size > 0) {
                                            //processLiveFrame(height, width, faces);
                                            detectFaces(height, width, faces);
                                        } else {
                                            canvasRelative.setImageBitmap(null);
                                        }
                                    })
                            .addOnFailureListener(
                                    e -> {
                                        Log.d(TAG, "Failure");
                                    });
        }

        void processLiveFrame(int height, int width, List<FirebaseVisionFace> faces) {
            Log.d(TAG, "processLiveFrame: ");
            Bitmap bitmap;
            bitmap = Bitmap.createBitmap(height, width, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint dotPaint = new Paint();
            dotPaint.setColor(Color.RED);
            dotPaint.setStyle(Paint.Style.FILL);
            dotPaint.setStrokeWidth(4F);
            Paint linePaint = new Paint();
            linePaint.setColor(Color.GREEN);
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(3F);

            for (FirebaseVisionFace face : faces) {

                List<FirebaseVisionPoint> faceContours = face.getContour(FirebaseVisionFaceContour.FACE).getPoints();
                int size = faceContours.size();
                for (int i = 0; i < size; i++) {
                    FirebaseVisionPoint visionPoint = faceContours.get(i);
                    if (i == size - 1) {
                        canvas.drawLine(visionPoint.getX(), visionPoint.getY(), faceContours.get(0).getX(), faceContours.get(0).getY(), linePaint);
                    } else {
                        Log.d(TAG, "drawLine ");
                        canvas.drawLine(visionPoint.getX(), visionPoint.getY(), faceContours.get(i + 1).getX(), faceContours.get(i + 1).getY(), linePaint);
                    }
                    canvas.drawCircle(visionPoint.getX(), visionPoint.getY(), 4F, dotPaint);
                }
                    /*for ((i, contour) in faceContours.withIndex()) {
                        if (i != faceContours.lastIndex)
                            canvas.drawLine(contour.x, contour.y, faceContours[i + 1].x, faceContours[i + 1].y, linePaint)
                        else
                            canvas.drawLine(contour.x, contour.y, faceContours[0].x, faceContours[0].y, linePaint)
                        canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
                    }

                    val leftEyebrowTopContours = face.getContour(FirebaseVisionFaceContour.LEFT_EYEBROW_TOP).points
                    for ((i, contour) in leftEyebrowTopContours.withIndex()) {
                        if (i != leftEyebrowTopContours.lastIndex)
                            canvas.drawLine(contour.x, contour.y, leftEyebrowTopContours[i + 1].x, leftEyebrowTopContours[i + 1].y, linePaint)
                        canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
                    }

                    val leftEyebrowBottomContours = face.getContour(FirebaseVisionFaceContour.LEFT_EYEBROW_BOTTOM).points
                    for ((i, contour) in leftEyebrowBottomContours.withIndex()) {
                        if (i != leftEyebrowBottomContours.lastIndex)
                            canvas.drawLine(contour.x, contour.y, leftEyebrowBottomContours[i + 1].x, leftEyebrowBottomContours[i + 1].y, linePaint)
                        canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
                    }

                    val rightEyebrowTopContours = face.getContour(FirebaseVisionFaceContour.RIGHT_EYEBROW_TOP).points
                    for ((i, contour) in rightEyebrowTopContours.withIndex()) {
                        if (i != rightEyebrowTopContours.lastIndex)
                            canvas.drawLine(contour.x, contour.y, rightEyebrowTopContours[i + 1].x, rightEyebrowTopContours[i + 1].y, linePaint)
                        canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
                    }

                    val rightEyebrowBottomContours = face.getContour(FirebaseVisionFaceContour.RIGHT_EYEBROW_BOTTOM).points
                    for ((i, contour) in rightEyebrowBottomContours.withIndex()) {
                        if (i != rightEyebrowBottomContours.lastIndex)
                            canvas.drawLine(contour.x, contour.y, rightEyebrowBottomContours[i + 1].x, rightEyebrowBottomContours[i + 1].y, linePaint)
                        canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
                    }

                    val leftEyeContours = face.getContour(FirebaseVisionFaceContour.LEFT_EYE).points
                    for ((i, contour) in leftEyeContours.withIndex()) {
                        if (i != leftEyeContours.lastIndex)
                            canvas.drawLine(contour.x, contour.y, leftEyeContours[i + 1].x, leftEyeContours[i + 1].y, linePaint)
                        else
                            canvas.drawLine(contour.x, contour.y, leftEyeContours[0].x, leftEyeContours[0].y, linePaint)
                        canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
                    }

                    val rightEyeContours = face.getContour(FirebaseVisionFaceContour.RIGHT_EYE).points
                    for ((i, contour) in rightEyeContours.withIndex()) {
                        if (i != rightEyeContours.lastIndex)
                            canvas.drawLine(contour.x, contour.y, rightEyeContours[i + 1].x, rightEyeContours[i + 1].y, linePaint)
                        else
                            canvas.drawLine(contour.x, contour.y, rightEyeContours[0].x, rightEyeContours[0].y, linePaint)
                        canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
                    }

                    val upperLipTopContours = face.getContour(FirebaseVisionFaceContour.UPPER_LIP_TOP).points
                    for ((i, contour) in upperLipTopContours.withIndex()) {
                        if (i != upperLipTopContours.lastIndex)
                            canvas.drawLine(contour.x, contour.y, upperLipTopContours[i + 1].x, upperLipTopContours[i + 1].y, linePaint)
                        canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
                    }

                    val upperLipBottomContours = face.getContour(FirebaseVisionFaceContour.UPPER_LIP_BOTTOM).points
                    for ((i, contour) in upperLipBottomContours.withIndex()) {
                        if (i != upperLipBottomContours.lastIndex)
                            canvas.drawLine(contour.x, contour.y, upperLipBottomContours[i + 1].x, upperLipBottomContours[i + 1].y, linePaint)
                        canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
                    }

                    val lowerLipTopContours = face.getContour(FirebaseVisionFaceContour.LOWER_LIP_TOP).points
                    for ((i, contour) in lowerLipTopContours.withIndex()) {
                        if (i != lowerLipTopContours.lastIndex)
                            canvas.drawLine(contour.x, contour.y, lowerLipTopContours[i + 1].x, lowerLipTopContours[i + 1].y, linePaint)
                        canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
                    }

                    val lowerLipBottomContours = face.getContour(FirebaseVisionFaceContour.LOWER_LIP_BOTTOM).points
                    for ((i, contour) in lowerLipBottomContours.withIndex()) {
                        if (i != lowerLipBottomContours.lastIndex)
                            canvas.drawLine(contour.x, contour.y, lowerLipBottomContours[i + 1].x, lowerLipBottomContours[i + 1].y, linePaint)
                        canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
                    }

                    val noseBridgeContours = face.getContour(FirebaseVisionFaceContour.NOSE_BRIDGE).points
                    for ((i, contour) in noseBridgeContours.withIndex()) {
                        if (i != noseBridgeContours.lastIndex)
                            canvas.drawLine(contour.x, contour.y, noseBridgeContours[i + 1].x, noseBridgeContours[i + 1].y, linePaint)
                        canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
                    }

                    val noseBottomContours = face.getContour(FirebaseVisionFaceContour.NOSE_BOTTOM).points
                    for ((i, contour) in noseBottomContours.withIndex()) {
                        if (i != noseBottomContours.lastIndex)
                            canvas.drawLine(contour.x, contour.y, noseBottomContours[i + 1].x, noseBottomContours[i + 1].y, linePaint)
                        canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
                    }*/


                Matrix matrix = new Matrix();
                matrix.preScale(-1F, 1F);
                Bitmap flippedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                Log.d(TAG, "postcanvas: ");
                canvasRelative.setImageBitmap(flippedBitmap);
            }
        }

        private void detectFaces(int height, int width, List<FirebaseVisionFace> faces) {
            Bitmap bitmap;
            bitmap = Bitmap.createBitmap(height, width, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint facePaint = new Paint();
            facePaint.setColor(MainActivity.this.getResources().getColor(R.color.colorGreen));
            facePaint.setStyle(Paint.Style.STROKE);
            facePaint.setStrokeWidth(8F);
            Paint faceTextPaint = new Paint();
            faceTextPaint.setColor(Color.RED);
            faceTextPaint.setTextSize(40F);
            faceTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
            Paint landmarkPaint = new Paint();
            landmarkPaint.setColor(MainActivity.this.getResources().getColor(R.color.colorGreen));
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

                mSmiling.setText("Smiling : " + face.getSmilingProbability());
                mLeftEye.setText("LeftEye Open : " + face.getLeftEyeOpenProbability());
                mRightEye.setText("RightEye Open : " + face.getRightEyeOpenProbability());
                mEyeOpenProbability = face.getLeftEyeOpenProbability();
                if (mEyeOpenProbability < 0.5) {
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

                Matrix matrix = new Matrix();
                matrix.preScale(-1F, 1F);
                Bitmap flippedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                canvasRelative.setImageBitmap(flippedBitmap);
            }
        }
    }

    CountDownTimer timer = new CountDownTimer(4000, 1000) {
        @Override
        public void onTick(long l) {
            Log.d(TAG, "onTick: " + l / 1000);
            long secondsRemaining = l / 1000;
            if (secondsRemaining <= 2) {
                playsound();
            }
        }

        @Override
        public void onFinish() {
            Log.d(TAG, "onFinish: ");
        }
    };

    private void playsound() {
        Log.d(TAG, "playsound: ");
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

