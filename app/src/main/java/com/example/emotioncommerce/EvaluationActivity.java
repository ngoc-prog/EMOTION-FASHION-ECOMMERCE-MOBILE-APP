package com.example.emotioncommerce;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.emotioncommerce.emotion.EmotionClassifier;
import com.example.emotioncommerce.emotion.EmotionFeatures;
import com.example.emotioncommerce.emotion.EmotionLabel;
import com.example.emotioncommerce.emotion.GeometricFeatureExtractor;
import com.example.emotioncommerce.evaluation.EvaluationLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.facemesh.FaceMesh;
import com.google.mlkit.vision.facemesh.FaceMeshDetection;
import com.google.mlkit.vision.facemesh.FaceMeshDetector;
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EvaluationActivity extends AppCompatActivity {

    private static final String TAG = "EvaluationActivity";
    private static final int CAMERA_PERMISSION_CODE = 102;
    private static final int UI_UPDATE_INTERVAL_MS = 150;

    // ML Pipeline
    private FaceMeshDetector faceMeshDetector;
    private GeometricFeatureExtractor featureExtractor;
    private EmotionClassifier emotionClassifier;
    private ExecutorService analysisExecutor;

    // Latest frame state (camera thread → UI thread)
    private volatile EmotionFeatures latestFeatures = null;
    private volatile EmotionLabel latestPredicted = EmotionLabel.UNKNOWN;
    private volatile long latestLatencyMs = 0;

    // Calibration state
    private boolean isCalibrating = true;

    // UI throttle
    private long lastUiUpdateMs = 0;

    // Running latency stats (all frames, not just labeled ones)
    private long totalLatencyMs = 0;
    private long totalLatencyFrames = 0;

    // Views
    private PreviewView cameraPreview;
    private View calibrationOverlay;
    private TextView tvCalibCountdown;
    private TextView tvPredicted;
    private TextView tvFeatures;
    private TextView tvLatency;
    private Button btnGtInterested, btnGtHesitant, btnGtIndifferent;
    private TextView tvLastRecord;
    private TextView tvAccuracy;
    private TextView[][] cmCells;    // [3][3]
    private TextView[] precViews, recViews, f1Views;  // [3] each

    private final EvaluationLogger logger = new EvaluationLogger();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_evaluation);

        bindViews();
        setupGroundTruthButtons();
        setupActionButtons();
        setupEmotionPipeline();

        if (emotionClassifier.isCalibrated()) {
            isCalibrating = false;
            calibrationOverlay.setVisibility(View.GONE);
        }

        // Always show camera preview during calibration so user can verify they're in frame
        if (isCalibrating) {
            cameraPreview.setVisibility(View.VISIBLE);
        }

        checkCameraPermission();
    }

    // ── View binding ──────────────────────────────────────────────────────────

    private void bindViews() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        cameraPreview      = findViewById(R.id.camera_preview);
        calibrationOverlay = findViewById(R.id.calibration_overlay);
        tvCalibCountdown   = findViewById(R.id.tv_calib_countdown);
        tvPredicted        = findViewById(R.id.tv_predicted_emotion);
        tvFeatures         = findViewById(R.id.tv_features_display);
        tvLatency          = findViewById(R.id.tv_latency_display);
        btnGtInterested    = findViewById(R.id.btn_gt_interested);
        btnGtHesitant      = findViewById(R.id.btn_gt_hesitant);
        btnGtIndifferent   = findViewById(R.id.btn_gt_indifferent);
        tvLastRecord       = findViewById(R.id.tv_last_record);
        tvAccuracy         = findViewById(R.id.tv_accuracy);

        // Confusion matrix [gt][pred]
        cmCells = new TextView[3][3];
        int[][] cmIds = {
            {R.id.tv_cm_00, R.id.tv_cm_01, R.id.tv_cm_02},
            {R.id.tv_cm_10, R.id.tv_cm_11, R.id.tv_cm_12},
            {R.id.tv_cm_20, R.id.tv_cm_21, R.id.tv_cm_22}
        };
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                cmCells[i][j] = findViewById(cmIds[i][j]);

        precViews = new TextView[]{
            findViewById(R.id.tv_prec_int),
            findViewById(R.id.tv_prec_hes),
            findViewById(R.id.tv_prec_ind)
        };
        recViews = new TextView[]{
            findViewById(R.id.tv_rec_int),
            findViewById(R.id.tv_rec_hes),
            findViewById(R.id.tv_rec_ind)
        };
        f1Views = new TextView[]{
            findViewById(R.id.tv_f1_int),
            findViewById(R.id.tv_f1_hes),
            findViewById(R.id.tv_f1_ind)
        };
    }

    // ── Ground truth buttons ──────────────────────────────────────────────────

    private void setupGroundTruthButtons() {
        setGroundTruthEnabled(false);
        btnGtInterested.setOnClickListener(v -> recordGroundTruth(EmotionLabel.INTERESTED));
        btnGtHesitant.setOnClickListener(v   -> recordGroundTruth(EmotionLabel.HESITANT));
        btnGtIndifferent.setOnClickListener(v -> recordGroundTruth(EmotionLabel.INDIFFERENT));
    }

    private void recordGroundTruth(EmotionLabel groundTruth) {
        EmotionFeatures f     = latestFeatures;
        EmotionLabel predicted = latestPredicted;
        long latency          = latestLatencyMs;

        if (f == null || !f.isValid()) {
            Toast.makeText(this, getString(R.string.no_face_data), Toast.LENGTH_SHORT).show();
            return;
        }

        float[] baseline = emotionClassifier.getBaselineArray();
        float bBrr = baseline != null ? baseline[0] : 0f;
        float bMc  = baseline != null ? baseline[1] : 0f;
        float bBfd = baseline != null ? baseline[2] : 0f;

        logger.addRecord(new EvaluationLogger.EvalRecord(
                System.currentTimeMillis(), f, bBrr, bMc, bBfd,
                predicted, groundTruth, latency));

        boolean correct = predicted == groundTruth;
        String resultStr = correct
                ? getString(R.string.sample_correct)
                : getString(R.string.sample_wrong, emotionLabel(predicted));

        tvLastRecord.setText(getString(R.string.sample_recorded,
                logger.getTotalRecords(),
                "GT=" + emotionLabel(groundTruth) + "  " + resultStr));

        refreshEvalUI();
    }

    // ── Eval UI refresh (confusion matrix + metrics) ──────────────────────────

    private void refreshEvalUI() {
        int n = logger.getTotalRecords();
        if (n == 0) return;

        int[][] cm = logger.getConfusionMatrix();
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                cmCells[i][j].setText(String.valueOf(cm[i][j]));

        float acc = logger.computeAccuracy();
        tvAccuracy.setText(getString(R.string.accuracy_fmt, acc * 100, n));

        float[][] metrics = logger.computeMetrics();
        for (int i = 0; i < 3; i++) {
            precViews[i].setText(String.format("%.0f%%", metrics[i][0] * 100));
            recViews[i].setText(String.format("%.0f%%", metrics[i][1] * 100));
            f1Views[i].setText(String.format("%.0f%%", metrics[i][2] * 100));
        }
    }

    // ── Action buttons (export / clear / camera toggle) ───────────────────────

    private void setupActionButtons() {
        findViewById(R.id.btn_export_csv).setOnClickListener(v -> {
            if (logger.getTotalRecords() == 0) {
                Toast.makeText(this, getString(R.string.no_samples_export), Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                startActivity(logger.buildShareIntent(this));
            } catch (IOException e) {
                Toast.makeText(this, getString(R.string.export_error, e.getMessage()),
                        Toast.LENGTH_LONG).show();
            }
        });

        findViewById(R.id.btn_clear_data).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle(getString(R.string.clear_data))
                .setMessage(getString(R.string.clear_confirm_msg, logger.getTotalRecords()))
                .setPositiveButton(getString(R.string.delete), (d, w) -> {
                    logger.clear();
                    tvLastRecord.setText(getString(R.string.cleared_msg));
                    for (int i = 0; i < 3; i++)
                        for (int j = 0; j < 3; j++)
                            cmCells[i][j].setText("0");
                    tvAccuracy.setText(getString(R.string.accuracy_empty));
                    String dash = "—";
                    for (int i = 0; i < 3; i++) {
                        precViews[i].setText(dash);
                        recViews[i].setText(dash);
                        f1Views[i].setText(dash);
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show());

        Button btnToggle = findViewById(R.id.btn_toggle_camera);
        btnToggle.setOnClickListener(v -> {
            boolean nowVisible = cameraPreview.getVisibility() == View.VISIBLE;
            cameraPreview.setVisibility(nowVisible ? View.INVISIBLE : View.VISIBLE);
            btnToggle.setText(nowVisible ? getString(R.string.show_camera) : getString(R.string.hide_camera));
        });

        findViewById(R.id.btn_skip_calibration).setOnClickListener(v -> skipCalibration());
    }

    // ── ML Pipeline setup ─────────────────────────────────────────────────────

    private void setupEmotionPipeline() {
        FaceMeshDetectorOptions opts = new FaceMeshDetectorOptions.Builder()
                .setUseCase(FaceMeshDetectorOptions.FACE_MESH)
                .build();
        faceMeshDetector = FaceMeshDetection.getClient(opts);
        featureExtractor = new GeometricFeatureExtractor();
        emotionClassifier = new EmotionClassifier();
    }

    // ── Camera ────────────────────────────────────────────────────────────────

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                bindCameraUseCases(future.get());
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera init failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @androidx.camera.core.ExperimentalGetImage
    private void bindCameraUseCases(ProcessCameraProvider provider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analysisExecutor = Executors.newSingleThreadExecutor();
        analysis.setAnalyzer(analysisExecutor, this::analyzeFrame);

        provider.unbindAll();
        provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis);
    }

    @androidx.camera.core.ExperimentalGetImage
    private void analyzeFrame(ImageProxy imageProxy) {
        if (imageProxy.getImage() == null) { imageProxy.close(); return; }

        long t0 = System.currentTimeMillis();
        InputImage img = InputImage.fromMediaImage(
                imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

        faceMeshDetector.process(img)
            .addOnSuccessListener(meshes -> {
                long latency = System.currentTimeMillis() - t0;

                if (meshes.isEmpty()) {
                    latestPredicted = EmotionLabel.UNKNOWN;
                    latestFeatures  = null;
                    latestLatencyMs = latency;
                } else {
                    processFirstFace(meshes.get(0), latency);
                }

                // Throttle UI updates
                long now = System.currentTimeMillis();
                if (now - lastUiUpdateMs >= UI_UPDATE_INTERVAL_MS) {
                    lastUiUpdateMs = now;
                    totalLatencyMs += latency;
                    totalLatencyFrames++;
                    final EmotionLabel pred  = latestPredicted;
                    final EmotionFeatures f  = latestFeatures;
                    runOnUiThread(() -> updatePredictionUI(pred, f, latency));
                }
            })
            .addOnFailureListener(e -> Log.e(TAG, "FaceMesh error", e))
            .addOnCompleteListener(t -> imageProxy.close());
    }

    private void processFirstFace(FaceMesh faceMesh, long latency) {
        EmotionFeatures f = featureExtractor.extract(faceMesh.getAllPoints());
        latestFeatures    = f;
        latestLatencyMs   = latency;

        if (isCalibrating) {
            boolean done = emotionClassifier.feedCalibration(f);
            int secs = emotionClassifier.getCalibrationSecondsRemaining();
            runOnUiThread(() -> tvCalibCountdown.setText(String.valueOf(Math.max(1, secs))));
            if (done) {
                isCalibrating = false;
                runOnUiThread(() -> {
                    calibrationOverlay.setVisibility(View.GONE);
                    cameraPreview.setVisibility(View.INVISIBLE);
                });
            }
            return;
        }

        EmotionLabel raw    = emotionClassifier.classify(f);
        latestPredicted     = emotionClassifier.getSmoothedLabel(raw);
    }

    // ── Prediction UI update ──────────────────────────────────────────────────

    private void updatePredictionUI(EmotionLabel predicted, EmotionFeatures f, long latency) {
        String label;
        int color;
        switch (predicted) {
            case INTERESTED:
                label = getString(R.string.emotion_interested_code);  color = 0xFF388E3C; break;
            case HESITANT:
                label = getString(R.string.emotion_hesitant_code);    color = 0xFFE65100; break;
            case INDIFFERENT:
                label = getString(R.string.emotion_indifferent_code); color = 0xFF6D4C41; break;
            default:
                label = getString(R.string.no_face_detected);         color = 0xFF9A8870; break;
        }
        tvPredicted.setText(label);
        tvPredicted.setTextColor(color);

        if (f != null && f.isValid()) {
            tvFeatures.setText(String.format(
                "BRR: %.3f  |  MC: %.3f  |  MAR: %.3f  |  BFD: %.3f",
                f.getBrowRaiseRatio(), f.getMouthCurvature(),
                f.getMouthAspectRatio(), f.getBrowFurrowDistance()));
        } else {
            tvFeatures.setText("BRR: —  |  MC: —  |  MAR: —  |  BFD: —");
        }

        long avgLatency = totalLatencyFrames > 0 ? totalLatencyMs / totalLatencyFrames : 0;
        tvLatency.setText(String.format("Frame: %dms  |  Avg: %dms  |  ~%.0ffps",
                latency, avgLatency, avgLatency > 0 ? 1000.0 / avgLatency : 0));

        boolean canLabel = !isCalibrating && f != null && f.isValid();
        setGroundTruthEnabled(canLabel);
    }

    private void setGroundTruthEnabled(boolean enabled) {
        if (btnGtInterested == null) return;
        btnGtInterested.setEnabled(enabled);
        btnGtHesitant.setEnabled(enabled);
        btnGtIndifferent.setEnabled(enabled);
    }

    private void skipCalibration() {
        isCalibrating = false;
        calibrationOverlay.setVisibility(View.GONE);
        // Hide camera after calibration skip (user can re-enable with toggle button)
        cameraPreview.setVisibility(View.INVISIBLE);
        tvPredicted.setText(getString(R.string.calib_skipped));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String emotionLabel(EmotionLabel label) {
        switch (label) {
            case INTERESTED:  return getString(R.string.emotion_interested);
            case HESITANT:    return getString(R.string.emotion_hesitant);
            case INDIFFERENT: return getString(R.string.emotion_indifferent);
            default:          return getString(R.string.unknown);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (faceMeshDetector != null) faceMeshDetector.close();
        if (analysisExecutor != null) analysisExecutor.shutdown();
    }
}
