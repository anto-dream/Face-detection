package com.example.facedetection;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.Image;
import android.util.Log;
import android.widget.ImageView;

import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.android.gms.tasks.Task;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.common.FirebaseVisionPoint;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceContour;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceLandmark;

import java.io.IOException;
import java.util.List;

public class ImageAnalyser implements ImageAnalysis.Analyzer {
    private FirebaseVisionFaceDetectorOptions visionFaceDetectorOptions;
    private ImageView mCanvas;
    private ClassificationUpdator mUpdator;

    public static final String TAG = ImageAnalyser.class.getSimpleName();

    public ImageAnalyser(ImageView view) {
        visionFaceDetectorOptions =
                new FirebaseVisionFaceDetectorOptions.Builder()
                        .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
                        .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
                        .enableTracking()
                        .build();
        mCanvas = view;
    }

    /**
     * Callback for facial classification
     */
    public void setUpdator(ClassificationUpdator updator) {
        this.mUpdator = updator;
    }

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
        performFaceDetetcion(mediaImage, rotation);
    }

    private void performFaceDetetcion(Image mediaImage, int rotation) {
        FirebaseVisionImage image =
                FirebaseVisionImage.fromMediaImage(mediaImage, rotation);
        FirebaseVisionFaceDetector detector = FirebaseVision.getInstance()
                .getVisionFaceDetector(visionFaceDetectorOptions);
        detector.detectInImage(image);
                        /*.addOnSuccessListener(
                                faces -> {
                                    int size = faces.size();
                                    if (size > 0) {
                                        //processLiveFrame(height, width, faces);
                                        detectFaces(height, width, faces);
                                    } else {
                                        if (mCanvas != null) {
                                            mCanvas.setImageBitmap(null);
                                        }
                                    }
                                })
                        .addOnFailureListener(
                                e -> {
                                    Log.d(TAG, "Failure");
                                    if (mCanvas != null) {
                                        mCanvas.setImageBitmap(null);
                                    }
                                }).addOnCompleteListener(task -> {
                    try {
                        detector.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });*/
        try {
            detector.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * For contour drawing
     * cofigure @visionFaceDetectorOptions
     */
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
            if (mCanvas != null) {
                mCanvas.setImageBitmap(flippedBitmap);
            }
        }
    }

    private void detectFaces(int height, int width, List<FirebaseVisionFace> faces) {
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

            if (mUpdator != null) {
                mUpdator.smilingProbability(face.getSmilingProbability());
                mUpdator.eyeOpenProbability(face.getLeftEyeOpenProbability(), face.getRightEyeOpenProbability());
            }

            if (mCanvas != null) {
                Matrix matrix = new Matrix();
                matrix.preScale(-1F, 1F);
                Bitmap flippedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                mCanvas.setImageBitmap(flippedBitmap);
            }
        }
    }

    private void performFaceClassification(List<FirebaseVisionFace> faces) {
        int index;
        int size = faces.size();
        for (index = 0; index < size; index++) {
            FirebaseVisionFace face = faces.get(index);
            if (mUpdator != null) {
                mUpdator.smilingProbability(face.getSmilingProbability());
                mUpdator.eyeOpenProbability(face.getLeftEyeOpenProbability(), face.getRightEyeOpenProbability());
            }
        }
    }

    interface ClassificationUpdator {
        void smilingProbability(float value);

        void eyeOpenProbability(float leftValue, float rightValue);
    }
}
