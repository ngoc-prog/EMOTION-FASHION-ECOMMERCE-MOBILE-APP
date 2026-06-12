package com.example.emotioncommerce.emotion;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EmotionClassifier {

    private static final int WINDOW_DURATION_MS      = 1000;
    private static final int FAST_TRIGGER_FRAMES     = 5;   // ~150ms at 30fps: immediate switch
    private static final int CALIBRATION_DURATION_MS = 3000;
    private static final int MIN_CALIBRATION_SAMPLES = 15;

    private static class TimestampedLabel {
        final EmotionLabel label;
        final long timestamp;
        TimestampedLabel(EmotionLabel label, long timestamp) {
            this.label = label;
            this.timestamp = timestamp;
        }
    }

    private static class NeutralBaseline {
        float brr, mc, bfd;
    }

    // Shared across all ProductDetailActivity instances for the app session.
    // Saves the user from re-calibrating every time they open a new product.
    private static float[] sSharedBaseline = null; // {brr, mc, bfd}

    private final ArrayDeque<TimestampedLabel> window = new ArrayDeque<>();
    private final List<EmotionFeatures> calibrationSamples = new ArrayList<>();
    private long calibrationStartMs = -1;
    private NeutralBaseline baseline = null;

    // Fast-trigger state: track consecutive identical raw labels without copying the window
    private EmotionLabel lastRawLabel = null;
    private int consecutiveCount = 0;

    public EmotionClassifier() {
        if (sSharedBaseline != null) {
            baseline = new NeutralBaseline();
            baseline.brr = sSharedBaseline[0];
            baseline.mc  = sSharedBaseline[1];
            baseline.bfd = sSharedBaseline[2];
        }
    }

    public boolean isCalibrated() {
        return baseline != null;
    }

    public boolean feedCalibration(EmotionFeatures f) {
        if (!f.isValid) return false;
        long now = System.currentTimeMillis();
        if (calibrationStartMs < 0) calibrationStartMs = now;
        calibrationSamples.add(f);
        boolean timeElapsed = now - calibrationStartMs >= CALIBRATION_DURATION_MS;
        boolean enoughSamples = calibrationSamples.size() >= MIN_CALIBRATION_SAMPLES;
        if (timeElapsed && enoughSamples) {
            computeBaseline();
            return true;
        }
        return false;
    }

    public int getCalibrationSecondsRemaining() {
        if (calibrationStartMs < 0) return 3;
        long elapsed = System.currentTimeMillis() - calibrationStartMs;
        int remaining = (int) Math.ceil((CALIBRATION_DURATION_MS - elapsed) / 1000.0);
        return Math.max(0, remaining);
    }

    private void computeBaseline() {
        List<Float> brrs = new ArrayList<>(), mcs = new ArrayList<>(),
                    bfds = new ArrayList<>();
        for (EmotionFeatures f : calibrationSamples) {
            brrs.add(f.browRaiseRatio);
            mcs.add(f.mouthCurvature);
            bfds.add(f.browFurrowDistance);
        }
        baseline = new NeutralBaseline();
        baseline.brr = median(brrs);
        baseline.mc  = median(mcs);
        baseline.bfd = median(bfds);
        sSharedBaseline = new float[]{baseline.brr, baseline.mc, baseline.bfd};
    }

    private float median(List<Float> values) {
        Collections.sort(values);
        int mid = values.size() / 2;
        return values.size() % 2 == 0
                ? (values.get(mid - 1) + values.get(mid)) / 2f
                : values.get(mid);
    }

    /**
     * Design rationale:
     * "Phân vân" has no reliable physical signal across different faces — don't detect it.
     * Instead make it the DEFAULT state: anyone browsing without a clear expression IS considering.
     *
     * INTERESTED  : clear smile — MC up + mouth opens (MAR)               → hứng thú
     * INDIFFERENT : face-wide tension — BRR drops ≥1% (whole-face scrunch) → thờ ơ / không thích
     * HESITANT    : catch-all — no strong signal = still considering       → phân vân (default)
     *
     * BRR is the key signal for INDIFFERENT: full-face expressions (nhăn mặt) engage
     * forehead/brow muscles → BRR drops; mouth-only expressions (mím môi) do not.
     */
    public EmotionLabel classify(EmotionFeatures f) {
        if (!f.isValid || baseline == null) return EmotionLabel.UNKNOWN;

        // INTERESTED: genuine smile (MC well above baseline + mouth opens)
        // MAR > 0.05 blocks lip-purse/scrunch where mouth stays closed
        if (f.mouthCurvature > baseline.mc + 0.08f
                && f.mouthCurvature > 0.04f
                && f.mouthAspectRatio > 0.05f) {
            return EmotionLabel.INTERESTED;
        }

        // INDIFFERENT: face-wide negative expression
        // BRR drops ≥0.5%: loosened from 1% — ML Kit smooths landmarks heavily
        // BFD drops ≥1.5%: loosened from 3% for same reason
        // MC < baseline - 0.05 AND absolute MC < -0.02: mouth corners pulled down (frown)
        boolean browDrop   = f.browRaiseRatio     < baseline.brr * 0.995f;
        boolean furrowDrop = f.browFurrowDistance < baseline.bfd * 0.985f;
        boolean frown      = f.mouthCurvature     < baseline.mc  - 0.05f
                          && f.mouthCurvature     < -0.02f;
        if (browDrop || furrowDrop || frown) {
            return EmotionLabel.INDIFFERENT;
        }

        // HESITANT: default — neutral or ambiguous face = đang cân nhắc (phân vân)
        // Semantically correct: anyone viewing a product without clear +/- signal is considering
        return EmotionLabel.HESITANT;
    }

    public EmotionLabel getSmoothedLabel(EmotionLabel rawLabel) {
        long now = System.currentTimeMillis();
        window.addLast(new TimestampedLabel(rawLabel, now));
        while (!window.isEmpty() && now - window.peekFirst().timestamp > WINDOW_DURATION_MS) {
            window.removeFirst();
        }

        // O(1) consecutive counter — no window copy needed
        if (rawLabel == lastRawLabel) {
            consecutiveCount++;
        } else {
            consecutiveCount = 1;
            lastRawLabel = rawLabel;
        }

        // Fast trigger: FAST_TRIGGER_FRAMES consecutive identical non-HESITANT frames
        if (rawLabel != EmotionLabel.HESITANT && consecutiveCount >= FAST_TRIGGER_FRAMES) {
            return rawLabel;
        }

        return majority(window);
    }

    private EmotionLabel majority(ArrayDeque<TimestampedLabel> window) {
        if (window.isEmpty()) return EmotionLabel.UNKNOWN;
        Map<EmotionLabel, Integer> counts = new HashMap<>();
        for (TimestampedLabel e : window) counts.merge(e.label, 1, Integer::sum);
        return Collections.max(counts.entrySet(), Map.Entry.comparingByValue()).getKey();
    }

    /** Returns {brr, mc, bfd} baseline array, or null if not yet calibrated. */
    public float[] getBaselineArray() {
        if (baseline == null) return null;
        return new float[]{baseline.brr, baseline.mc, baseline.bfd};
    }

    public String getBaselineDebugString() {
        if (baseline == null) return "Baseline: not calibrated";
        return String.format("Baseline BRR:%.3f MC:%.3f BFD:%.3f",
                baseline.brr, baseline.mc, baseline.bfd);
    }
}
