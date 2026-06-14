package com.example.emotioncommerce;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.graphics.drawable.GradientDrawable;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.widget.NestedScrollView;
import androidx.viewpager2.widget.ViewPager2;
import java.util.ArrayList;
import java.util.List;
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
import com.example.emotioncommerce.BuildConfig;
import com.example.emotioncommerce.data.AuthRepository;
import com.example.emotioncommerce.data.CartRepository;
import com.example.emotioncommerce.data.SessionAnalyticsRepository;
import com.example.emotioncommerce.data.WishlistRepository;
import com.example.emotioncommerce.emotion.EmotionClassifier;
import com.example.emotioncommerce.emotion.EmotionFeatures;
import com.example.emotioncommerce.emotion.EmotionLabel;
import com.example.emotioncommerce.gemini.GeminiIntegrationManager;
import com.example.emotioncommerce.model.Product;
import com.example.emotioncommerce.ui.EmotionIndicatorView;
import com.example.emotioncommerce.ui.RecommendationCardView;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.facemesh.FaceMesh;
import com.google.mlkit.vision.facemesh.FaceMeshDetection;
import com.google.mlkit.vision.facemesh.FaceMeshDetector;
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProductDetailActivity extends AppCompatActivity {

    private static final String TAG = "ProductDetail";
    private static final int CAMERA_PERMISSION_CODE = 100;

    private Product currentProduct;
    private FaceMeshDetector faceMeshDetector;
    private com.example.emotioncommerce.emotion.GeometricFeatureExtractor featureExtractor;
    private EmotionClassifier emotionClassifier;
    private GeminiIntegrationManager geminiManager;

    private EmotionIndicatorView emotionIndicator;
    private RecommendationCardView recommendationCard;
    private PreviewView cameraPreview;
    private TextView tvDebugOverlay;
    private boolean debugVisible = false;

    private View calibrationOverlay;
    private TextView tvCalibrationCountdown;
    private boolean isCalibrating = true;

    // Hesitant promotion
    private static final long PROMO_DELAY_MS = 8000;
    private View layoutHesitantPromo;
    private TextView tvPromoOriginalPrice;
    private TextView tvPromoDiscountedPrice;
    private TextView tvDetailPrice;
    private TextView tvStickyPrice;
    private NestedScrollView scrollView;
    private boolean promoShown = false;
    private boolean userWasInterested = false;
    private final Handler promoHandler = new Handler(Looper.getMainLooper());
    private final Runnable promoRunnable = () -> {
        if (!promoShown && !userWasInterested) showHesitantPromotion();
    };

    private ExecutorService analysisExecutor;

    // Analytics tracking
    private EmotionLabel lastTrackedEmotion = EmotionLabel.UNKNOWN;
    private long lastEmotionStartMs = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_detail);

        currentProduct = (Product) getIntent().getSerializableExtra("product");
        if (currentProduct == null) {
            finish();
            return;
        }

        setupViews();
        setupEmotionPipeline();
        // If a baseline was saved from a previous product view, skip calibration immediately.
        // The overlay stays hidden and detection starts as soon as the camera opens.
        if (emotionClassifier.isCalibrated()) {
            isCalibrating = false;
            calibrationOverlay.setVisibility(View.GONE);
            promoHandler.postDelayed(promoRunnable, PROMO_DELAY_MS);
        }
        checkCameraPermission();
    }

    private void setupViews() {
        // Image gallery (ViewPager2 + dot indicators)
        List<String> images = new ArrayList<>(currentProduct.getImages());
        if (images.isEmpty() && !currentProduct.getImageUrl().isEmpty()) {
            images.add(currentProduct.getImageUrl());
        }
        ViewPager2 vpImages = findViewById(R.id.vp_product_images);
        LinearLayout llDots = findViewById(R.id.ll_dots);
        if (!images.isEmpty()) {
            vpImages.setAdapter(new ImagePagerAdapter(images));
            final List<String> finalImages = images;
            setupDots(llDots, finalImages.size(), 0);
            vpImages.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    setupDots(llDots, finalImages.size(), position);
                }
            });
        }

        // Back button
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // Brand
        TextView tvBrand = findViewById(R.id.tv_detail_brand);
        String brand = currentProduct.getBrand();
        tvBrand.setText(brand.isEmpty() ? "ÉLAN" : brand.toUpperCase());

        // Name, price, description
        ((TextView) findViewById(R.id.tv_detail_name)).setText(currentProduct.getName());
        String priceText = getString(R.string.price_currency, currentProduct.getPrice());
        tvDetailPrice = findViewById(R.id.tv_detail_price);
        tvDetailPrice.setText(priceText);
        ((TextView) findViewById(R.id.tv_detail_description)).setText(
                currentProduct.getDescription());

        // Sticky bar price mirror
        tvStickyPrice = findViewById(R.id.tv_sticky_price);
        tvStickyPrice.setText(priceText);

        // Rating row (A2)
        if (currentProduct.getRating() > 0f) {
            LinearLayout ratingRow = findViewById(R.id.layout_detail_rating);
            TextView tvRating = findViewById(R.id.tv_detail_rating);
            String ratingText = String.format("%.1f", currentProduct.getRating());
            if (currentProduct.getReviewCount() > 0) {
                ratingText += getString(R.string.reviews_suffix, currentProduct.getReviewCount());
            }
            tvRating.setText(ratingText);
            ratingRow.setVisibility(View.VISIBLE);
        }

        // Wishlist button (A1)
        ImageButton btnWishlist = findViewById(R.id.btn_detail_wishlist);
        updateWishlistIcon(btnWishlist);
        btnWishlist.setOnClickListener(v -> {
            WishlistRepository.getInstance().toggle(currentProduct);
            updateWishlistIcon(btnWishlist);
            // Only record when adding (toggle adds then removes; track add only)
            if (WishlistRepository.getInstance().isWishlisted(currentProduct.getId())) {
                SessionAnalyticsRepository.getInstance().recordAction(
                        currentProduct.getId(), currentProduct.getName(),
                        lastTrackedEmotion, SessionAnalyticsRepository.ActionRecord.ActionType.WISHLIST);
            }
        });

        emotionIndicator       = findViewById(R.id.emotion_indicator);
        recommendationCard     = findViewById(R.id.recommendation_card);
        cameraPreview          = findViewById(R.id.camera_preview);
        tvDebugOverlay         = findViewById(R.id.tv_debug_overlay);
        calibrationOverlay     = findViewById(R.id.calibration_overlay);
        tvCalibrationCountdown = findViewById(R.id.tv_calibration_countdown);
        scrollView             = findViewById(R.id.scroll_view);
        layoutHesitantPromo    = findViewById(R.id.layout_hesitant_promo);
        tvPromoOriginalPrice   = findViewById(R.id.tv_promo_original_price);
        tvPromoDiscountedPrice = findViewById(R.id.tv_promo_discounted_price);

        // Size chips
        setupSizeChips(findViewById(R.id.ll_size_chips));

        // Add to cart
        findViewById(R.id.btn_add_to_cart).setOnClickListener(v -> {
            CartRepository.getInstance().addProduct(currentProduct);
            SessionAnalyticsRepository.getInstance().recordAction(
                    currentProduct.getId(), currentProduct.getName(),
                    lastTrackedEmotion, SessionAnalyticsRepository.ActionRecord.ActionType.ADD_CART);
            Toast.makeText(this, getString(R.string.added_to_cart_toast, currentProduct.getName()),
                    Toast.LENGTH_SHORT).show();
        });

        // Buy now → add to cart then go to cart
        findViewById(R.id.btn_buy_now).setOnClickListener(v -> {
            CartRepository.getInstance().addProduct(currentProduct);
            SessionAnalyticsRepository.getInstance().recordAction(
                    currentProduct.getId(), currentProduct.getName(),
                    lastTrackedEmotion, SessionAnalyticsRepository.ActionRecord.ActionType.ADD_CART);
            finish();
            // Navigate to cart tab via MainActivity
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("open_tab", "cart");
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });

        // Debug toggle — only shown for admin users
        Button btnDebug = findViewById(R.id.btn_debug_toggle);
        if (AuthRepository.getInstance().isAdmin()) {
            btnDebug.setVisibility(View.VISIBLE);
        }
        btnDebug.setOnClickListener(v -> {
            debugVisible = !debugVisible;
            tvDebugOverlay.setVisibility(debugVisible ? View.VISIBLE : View.GONE);
            cameraPreview.setVisibility(debugVisible ? View.VISIBLE : View.INVISIBLE);
        });
    }

    private int selectedSizeIndex = 1; // default S

    private void setupSizeChips(LinearLayout container) {
        String[] sizes = {"XS", "S", "M", "L", "XL"};
        int colorSelected = Color.parseColor("#3D2B1F");
        int colorUnselected = Color.parseColor("#F5F0EB");
        int textSelected = Color.WHITE;
        int textUnselected = Color.parseColor("#3D2B1F");

        for (int i = 0; i < sizes.length; i++) {
            TextView chip = new TextView(this);
            int sizePx = dpToPx(36);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
            lp.setMargins(0, 0, dpToPx(8), 0);
            chip.setLayoutParams(lp);
            chip.setText(sizes[i]);
            chip.setTextSize(12f);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setTextColor(i == selectedSizeIndex ? textSelected : textUnselected);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(i == selectedSizeIndex ? colorSelected : colorUnselected);
            chip.setBackground(bg);

            final int idx = i;
            chip.setOnClickListener(v -> {
                selectedSizeIndex = idx;
                // Refresh all chips
                for (int j = 0; j < container.getChildCount(); j++) {
                    TextView c = (TextView) container.getChildAt(j);
                    boolean active = (j == selectedSizeIndex);
                    ((GradientDrawable) c.getBackground()).setColor(active ? colorSelected : colorUnselected);
                    c.setTextColor(active ? textSelected : textUnselected);
                }
            });
            container.addView(chip);
        }
    }

    private void updateWishlistIcon(ImageButton btn) {
        boolean wished = WishlistRepository.getInstance().isWishlisted(currentProduct.getId());
        if (wished) {
            btn.setImageResource(R.drawable.ic_heart_filled);
            btn.setColorFilter(ContextCompat.getColor(this, R.color.lume_primary));
        } else {
            btn.setImageResource(R.drawable.ic_heart);
            btn.setColorFilter(ContextCompat.getColor(this, R.color.lume_text_secondary));
        }
    }

    private void setupDots(LinearLayout container, int count, int active) {
        container.removeAllViews();
        if (count <= 1) return;
        for (int i = 0; i < count; i++) {
            android.view.View dot = new android.view.View(this);
            boolean isActive = (i == active);
            int sizePx = dpToPx(isActive ? 8 : 6);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
            lp.setMargins(dpToPx(3), 0, dpToPx(3), 0);
            dot.setLayoutParams(lp);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(isActive ? 0xFFFFFFFF : 0x88FFFFFF);
            dot.setBackground(shape);
            container.addView(dot);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void setupEmotionPipeline() {
        FaceMeshDetectorOptions options = new FaceMeshDetectorOptions.Builder()
                .setUseCase(FaceMeshDetectorOptions.FACE_MESH)
                .build();
        faceMeshDetector  = FaceMeshDetection.getClient(options);
        featureExtractor  = new com.example.emotioncommerce.emotion.GeometricFeatureExtractor();
        emotionClassifier = new EmotionClassifier();
        geminiManager     = GeminiIntegrationManager.getInstance();
    }

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
    private void bindCameraUseCases(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analysisExecutor = Executors.newSingleThreadExecutor();
        analysis.setAnalyzer(analysisExecutor, this::analyzeFrame);

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this,
                CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis);
    }

    @androidx.camera.core.ExperimentalGetImage
    private void analyzeFrame(ImageProxy imageProxy) {
        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }
        InputImage inputImage = InputImage.fromMediaImage(
                imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

        faceMeshDetector.process(inputImage)
                .addOnSuccessListener(meshes -> {
                    if (!meshes.isEmpty()) {
                        processFirstFace(meshes.get(0));
                    } else {
                        updateEmotionUI(EmotionLabel.UNKNOWN);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Face mesh error", e))
                .addOnCompleteListener(t -> imageProxy.close());
    }

    private void processFirstFace(FaceMesh faceMesh) {
        EmotionFeatures features = featureExtractor.extract(faceMesh.getAllPoints());

        if (isCalibrating) {
            boolean done = emotionClassifier.feedCalibration(features);
            int secs = emotionClassifier.getCalibrationSecondsRemaining();
            runOnUiThread(() -> tvCalibrationCountdown.setText(
                    String.valueOf(Math.max(1, secs))));
            if (done) {
                isCalibrating = false;
                Log.d(TAG, "Calibration done. " + emotionClassifier.getBaselineDebugString());
                runOnUiThread(() -> {
                    calibrationOverlay.setVisibility(View.GONE);
                    promoHandler.postDelayed(promoRunnable, PROMO_DELAY_MS);
                });
            }
            return;
        }

        EmotionLabel rawLabel = emotionClassifier.classify(features);
        EmotionLabel smoothed = emotionClassifier.getSmoothedLabel(rawLabel);

        if (BuildConfig.DEBUG) {
            Log.d(TAG, String.format("BRR=%.3f MC=%.3f MAR=%.3f BFD=%.3f | %s -> %s",
                    features.getBrowRaiseRatio(), features.getMouthCurvature(),
                    features.getMouthAspectRatio(), features.getBrowFurrowDistance(),
                    rawLabel, smoothed));
        }

        updateEmotionUI(smoothed);
        tryTriggerGemini(smoothed);

        if (debugVisible) {
            long cooldownSec = geminiManager.getCooldownRemainingMs(smoothed) / 1000;
            String debug = String.format(
                "BRR: %.3f  MC:  %.3f\nMAR: %.3f  BFD: %.3f\n" +
                "Raw: %s  Smoothed: %s\nGemini: %s\n%s",
                features.getBrowRaiseRatio(), features.getMouthCurvature(),
                features.getMouthAspectRatio(), features.getBrowFurrowDistance(),
                rawLabel, smoothed,
                cooldownSec > 0 ? "cooldown " + cooldownSec + "s" : "ready",
                emotionClassifier.getBaselineDebugString());
            runOnUiThread(() -> tvDebugOverlay.setText(debug));
        }
    }

    private void updateEmotionUI(EmotionLabel label) {
        runOnUiThread(() -> {
            emotionIndicator.updateEmotion(label);
            trackEmotionChange(label);
            if (label == EmotionLabel.INTERESTED && !promoShown) {
                userWasInterested = true;
                promoHandler.removeCallbacks(promoRunnable);
                SessionAnalyticsRepository.getInstance()
                        .boostCategory(currentProduct.getCategory());
            }
        });
    }

    private void showHesitantPromotion() {
        if (promoShown) return;
        promoShown = true;

        long originalPrice = currentProduct.getPrice();
        long discountedPrice = (long) (originalPrice * 0.9);

        tvPromoOriginalPrice.setText(getString(R.string.price_currency, originalPrice));
        tvPromoOriginalPrice.setPaintFlags(
                tvPromoOriginalPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        tvPromoDiscountedPrice.setText(getString(R.string.price_currency, discountedPrice));

        String discountedPriceText = getString(R.string.price_currency, discountedPrice);
        tvDetailPrice.setText(discountedPriceText);
        tvStickyPrice.setText(discountedPriceText);

        layoutHesitantPromo.setVisibility(View.VISIBLE);
        layoutHesitantPromo.setAlpha(0f);
        layoutHesitantPromo.setTranslationY(-16f);
        layoutHesitantPromo.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(450)
                .withEndAction(() -> {
                    if (scrollView != null) {
                        scrollView.post(() -> scrollView.smoothScrollTo(
                                0, layoutHesitantPromo.getTop()));
                    }
                })
                .start();
    }

    private void trackEmotionChange(EmotionLabel newLabel) {
        if (newLabel == lastTrackedEmotion) return;
        long now = System.currentTimeMillis();
        if (lastTrackedEmotion != EmotionLabel.UNKNOWN && lastEmotionStartMs > 0) {
            long duration = now - lastEmotionStartMs;
            SessionAnalyticsRepository.getInstance().recordEmotionSegment(
                    currentProduct.getId(), currentProduct.getName(),
                    lastTrackedEmotion, duration);
        }
        lastTrackedEmotion = newLabel;
        lastEmotionStartMs = now;
    }

    private void flushCurrentEmotionSegment() {
        if (lastTrackedEmotion == EmotionLabel.UNKNOWN || lastEmotionStartMs == 0) return;
        long duration = System.currentTimeMillis() - lastEmotionStartMs;
        SessionAnalyticsRepository.getInstance().recordEmotionSegment(
                currentProduct.getId(), currentProduct.getName(),
                lastTrackedEmotion, duration);
        lastEmotionStartMs = 0;
    }

    private void tryTriggerGemini(EmotionLabel label) {
        geminiManager.generateRecommendation(label, currentProduct,
                new GeminiIntegrationManager.RecommendationCallback() {
                    @Override
                    public void onSuccess(String recommendation) {
                        runOnUiThread(() -> recommendationCard.showRecommendation(recommendation));
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Gemini error", e);
                    }
                });
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
        promoHandler.removeCallbacks(promoRunnable);
        flushCurrentEmotionSegment();
        if (faceMeshDetector != null) faceMeshDetector.close();
        if (analysisExecutor != null) analysisExecutor.shutdown();
    }
}
