package com.example.emotioncommerce.emotion;

import android.graphics.PointF;
import android.util.Log;
import com.google.mlkit.vision.facemesh.FaceMeshPoint;
import java.util.List;

public class GeometricFeatureExtractor {

    private static final String TAG = "FeatureExtractor";

    // Pre-allocated landmark cache — reused every frame, no per-frame allocation
    private final PointF[] pts = new PointF[468];

    public GeometricFeatureExtractor() {
        for (int i = 0; i < 468; i++) pts[i] = new PointF();
    }

    public EmotionFeatures extract(List<FaceMeshPoint> points) {
        EmotionFeatures features = new EmotionFeatures();

        if (points == null || points.size() < 468) {
            Log.w(TAG, "Not enough landmarks: " + (points == null ? "null" : points.size()));
            features.isValid = false;
            return features;
        }

        // Populate reusable pts array in-place
        for (int i = 0; i < 468; i++) {
            com.google.mlkit.vision.common.PointF3D pos = points.get(i).getPosition();
            pts[i].set(pos.getX(), pos.getY());
        }

        // IOD: distance between eye centers
        float lEyeCX = (pts[362].x + pts[263].x) / 2f;
        float lEyeCY = (pts[362].y + pts[263].y) / 2f;
        float rEyeCX = (pts[33].x  + pts[133].x) / 2f;
        float rEyeCY = (pts[33].y  + pts[133].y) / 2f;
        float iod    = dist(lEyeCX, lEyeCY, rEyeCX, rEyeCY);

        if (iod < 1.0f) {
            Log.w(TAG, "IOD too small: " + iod);
            features.isValid = false;
            return features;
        }

        features.browRaiseRatio    = computeBRR(iod);
        features.eyeAspectRatio    = 0f; // not used in classification
        features.mouthCurvature    = computeMC();
        features.mouthAspectRatio  = computeMAR();
        features.browFurrowDistance = computeBFD(iod);
        features.isValid = true;
        return features;
    }

    // BRR: avg distance from mid-brow to top-of-eye, normalized by IOD
    private float computeBRR(float iod) {
        float lEyeMidX  = (pts[385].x + pts[387].x) / 2f;
        float lEyeMidY  = (pts[385].y + pts[387].y) / 2f;
        float leftDist  = dist(pts[105].x, pts[105].y, lEyeMidX, lEyeMidY);

        float rEyeMidX  = (pts[160].x + pts[158].x) / 2f;
        float rEyeMidY  = (pts[160].y + pts[158].y) / 2f;
        float rightDist = dist(pts[334].x, pts[334].y, rEyeMidX, rEyeMidY);

        return ((leftDist + rightDist) / 2f) / iod;
    }

    // MC: positive = smile (corners above center), negative = frown
    private float computeMC() {
        float mouthCenterY = (pts[13].y + pts[14].y) / 2f;
        float cornerMidY   = (pts[61].y + pts[291].y) / 2f;
        float mouthWidth   = dist(pts[61].x, pts[61].y, pts[291].x, pts[291].y);
        if (mouthWidth < 1f) return 0f;
        return (mouthCenterY - cornerMidY) / mouthWidth;
    }

    // MAR: mouth height / mouth width
    private float computeMAR() {
        float height = dist(pts[13].x, pts[13].y, pts[14].x, pts[14].y);
        float width  = dist(pts[78].x, pts[78].y, pts[308].x, pts[308].y);
        return width > 0 ? height / width : 0f;
    }

    // BFD: distance between inner brow points, normalized by IOD
    private float computeBFD(float iod) {
        return dist(pts[107].x, pts[107].y, pts[336].x, pts[336].y) / iod;
    }

    private float dist(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
