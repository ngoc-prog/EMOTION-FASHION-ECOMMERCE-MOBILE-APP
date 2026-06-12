package com.example.emotioncommerce.evaluation;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;
import com.example.emotioncommerce.emotion.EmotionFeatures;
import com.example.emotioncommerce.emotion.EmotionLabel;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EvaluationLogger {

    public static class EvalRecord {
        public final long timestampMs;
        public final float brr, mc, mar, bfd;
        public final float baselineBrr, baselineMc, baselineBfd;
        public final EmotionLabel predicted;
        public final EmotionLabel groundTruth;
        public final long latencyMs;

        public EvalRecord(long timestampMs, EmotionFeatures f,
                          float bBrr, float bMc, float bBfd,
                          EmotionLabel predicted, EmotionLabel groundTruth,
                          long latencyMs) {
            this.timestampMs  = timestampMs;
            this.brr          = f.browRaiseRatio;
            this.mc           = f.mouthCurvature;
            this.mar          = f.mouthAspectRatio;
            this.bfd          = f.browFurrowDistance;
            this.baselineBrr  = bBrr;
            this.baselineMc   = bMc;
            this.baselineBfd  = bBfd;
            this.predicted    = predicted;
            this.groundTruth  = groundTruth;
            this.latencyMs    = latencyMs;
        }
    }

    private final List<EvalRecord> records = new ArrayList<>();

    public void addRecord(EvalRecord r) { records.add(r); }
    public void clear()                  { records.clear(); }
    public int  getTotalRecords()        { return records.size(); }

    /** Confusion matrix [gt_row][pred_col]: 0=INTERESTED, 1=HESITANT, 2=INDIFFERENT */
    public int[][] getConfusionMatrix() {
        int[][] cm = new int[3][3];
        for (EvalRecord r : records) {
            int gt   = labelToIdx(r.groundTruth);
            int pred = labelToIdx(r.predicted);
            if (gt >= 0 && pred >= 0) cm[gt][pred]++;
        }
        return cm;
    }

    public float computeAccuracy() {
        int[][] cm = getConfusionMatrix();
        int correct = 0, total = 0;
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) {
                if (i == j) correct += cm[i][j];
                total += cm[i][j];
            }
        return total > 0 ? (float) correct / total : 0f;
    }

    /** Returns float[class][metric]: [][0]=Precision, [][1]=Recall, [][2]=F1 */
    public float[][] computeMetrics() {
        int[][] cm = getConfusionMatrix();
        float[][] m = new float[3][3];
        for (int i = 0; i < 3; i++) {
            int tp = cm[i][i];
            int predSum = 0, gtSum = 0;
            for (int j = 0; j < 3; j++) {
                predSum += cm[j][i];
                gtSum   += cm[i][j];
            }
            float precision = predSum > 0 ? (float) tp / predSum : 0f;
            float recall    = gtSum   > 0 ? (float) tp / gtSum   : 0f;
            float f1 = (precision + recall) > 0
                    ? 2 * precision * recall / (precision + recall) : 0f;
            m[i][0] = precision;
            m[i][1] = recall;
            m[i][2] = f1;
        }
        return m;
    }

    public Intent buildShareIntent(Context context) throws IOException {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File outDir = context.getExternalFilesDir(null);
        if (outDir == null) outDir = context.getFilesDir();
        File outFile = new File(outDir, "elan_eval_" + ts + ".csv");

        try (FileWriter fw = new FileWriter(outFile)) {
            fw.write("timestamp_ms,brr,mc,mar,bfd," +
                     "baseline_brr,baseline_mc,baseline_bfd," +
                     "predicted,ground_truth,latency_ms\n");
            for (EvalRecord r : records) {
                fw.write(String.format(Locale.US,
                    "%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%s,%s,%d\n",
                    r.timestampMs, r.brr, r.mc, r.mar, r.bfd,
                    r.baselineBrr, r.baselineMc, r.baselineBfd,
                    r.predicted.name(), r.groundTruth.name(), r.latencyMs));
            }
        }

        Uri uri = FileProvider.getUriForFile(context,
                context.getPackageName() + ".fileprovider", outFile);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_SUBJECT,
                "ÉLAN Evaluation — " + records.size() + " samples — " + ts);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return Intent.createChooser(intent, "Xuất dữ liệu đánh giá");
    }

    public static int labelToIdx(EmotionLabel label) {
        switch (label) {
            case INTERESTED:  return 0;
            case HESITANT:    return 1;
            case INDIFFERENT: return 2;
            default:          return -1;
        }
    }
}
