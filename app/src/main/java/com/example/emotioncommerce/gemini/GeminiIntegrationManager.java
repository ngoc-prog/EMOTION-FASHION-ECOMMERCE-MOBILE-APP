package com.example.emotioncommerce.gemini;

import android.util.Log;
import com.example.emotioncommerce.BuildConfig;
import com.example.emotioncommerce.emotion.EmotionLabel;
import com.example.emotioncommerce.model.Product;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.EnumMap;
import java.util.Map;

/**
 * Calls the Gemini REST API directly via HttpURLConnection.
 *
 * Quota protection:
 *  - Per-emotion cooldown: 2 minutes between calls for the same emotion.
 *  - Global cooldown: 90 seconds between any two Gemini calls.
 *  - Response cache: once a response is received for an emotion, it is reused
 *    for the rest of the session without another API call.
 *  - Quota flag: HTTP 429 sets a session-level flag that routes all subsequent
 *    calls to local fallback text, avoiding further quota burn.
 */
public class GeminiIntegrationManager {

    private static final String TAG = "GeminiManager";
    // flash-lite: higher free-tier RPM, sufficient for short recommendation prompts
    private static final String MODEL_LITE    = "gemini-2.0-flash-lite";
    // flash: better reasoning for admin analytics insights
    private static final String MODEL_FULL    = "gemini-2.0-flash";
    private static final String BASE_URL_LITE =
            "https://generativelanguage.googleapis.com/v1beta/models/"
            + MODEL_LITE + ":generateContent";
    private static final String BASE_URL_FULL =
            "https://generativelanguage.googleapis.com/v1beta/models/"
            + MODEL_FULL + ":generateContent";

    private static final long EMOTION_COOLDOWN_MS  = 120_000; // 2 min per product+emotion
    private static final long GLOBAL_COOLDOWN_MS   = 60_000;  // 1 min between any calls
    private static final int  DESC_MAX_CHARS        = 180;

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static GeminiIntegrationManager instance;

    public static GeminiIntegrationManager getInstance() {
        if (instance == null) instance = new GeminiIntegrationManager();
        return instance;
    }

    private GeminiIntegrationManager() {
        if (BuildConfig.GEMINI_API_KEY == null || BuildConfig.GEMINI_API_KEY.isEmpty()) {
            Log.e(TAG, "GEMINI_API_KEY is empty – add it to local.properties and rebuild");
        }
    }

    // ── Interfaces ────────────────────────────────────────────────────────────

    public interface RecommendationCallback {
        void onSuccess(String recommendation);
        void onError(Exception e);
    }

    public interface InsightsCallback {
        void onSuccess(String insights);
        void onError(Exception e);
    }

    // ── State ─────────────────────────────────────────────────────────────────

    // Cache keyed by "productId:emotion" — avoids re-calling for same product
    private final java.util.concurrent.ConcurrentHashMap<String, String> responseCache
            = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, Long> lastCallTime
            = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile long    lastGlobalCallTime = 0;
    // After a 429, block for 15 minutes (free tier resets hourly)
    private static final long QUOTA_BACKOFF_MS  = 15 * 60_000L;
    private volatile long    quotaBlockedUntil  = 0;

    // ── Cooldown helpers ──────────────────────────────────────────────────────

    private String cacheKey(int productId, EmotionLabel label) {
        return productId + ":" + label.name();
    }

    public boolean canCallGemini(int productId, EmotionLabel label) {
        long now = System.currentTimeMillis();
        if (now < quotaBlockedUntil) return false;
        if (now - lastGlobalCallTime < GLOBAL_COOLDOWN_MS) return false;
        Long last = lastCallTime.get(cacheKey(productId, label));
        return last == null || now - last > EMOTION_COOLDOWN_MS;
    }

    public boolean shouldTrigger(int productId, EmotionLabel label) {
        if (label == EmotionLabel.UNKNOWN) return false;
        if (responseCache.containsKey(cacheKey(productId, label))) return false;
        return canCallGemini(productId, label);
    }

    public long getCooldownRemainingMs(int productId, EmotionLabel label) {
        long now = System.currentTimeMillis();
        long globalWait  = Math.max(0, GLOBAL_COOLDOWN_MS - (now - lastGlobalCallTime));
        Long last = lastCallTime.get(cacheKey(productId, label));
        long emotionWait = last == null ? 0 : Math.max(0, EMOTION_COOLDOWN_MS - (now - last));
        return Math.max(globalWait, emotionWait);
    }

    public boolean isQuotaBlocked() {
        return System.currentTimeMillis() < quotaBlockedUntil;
    }

    public long getQuotaBlockedRemainingMs() {
        return Math.max(0, quotaBlockedUntil - System.currentTimeMillis());
    }

    // ── Customer recommendation ───────────────────────────────────────────────

    public void generateRecommendation(EmotionLabel emotion, Product product,
                                       RecommendationCallback callback) {
        if (BuildConfig.GEMINI_API_KEY == null || BuildConfig.GEMINI_API_KEY.isEmpty()) return;

        String key = cacheKey(product.getId(), emotion);

        // Cached → return immediately without any network call
        String cached = responseCache.get(key);
        if (cached != null) {
            callback.onSuccess(cached);
            return;
        }

        if (!shouldTrigger(product.getId(), emotion)) {
            return;
        }

        long now = System.currentTimeMillis();
        lastCallTime.put(key, now);
        lastGlobalCallTime = now;

        callGemini(BASE_URL_LITE, buildPrompt(emotion, product), new InsightsCallback() {
            @Override public void onSuccess(String t) {
                responseCache.put(key, t);
                callback.onSuccess(t);
            }
            @Override public void onError(Exception e) {
                callback.onSuccess(getFallback(emotion));
            }
        });
    }

    // ── Admin analytics insights ──────────────────────────────────────────────

    public void generateInsights(String analyticsSummary, InsightsCallback callback) {
        if (BuildConfig.GEMINI_API_KEY == null || BuildConfig.GEMINI_API_KEY.isEmpty()) {
            callback.onError(new Exception(
                "API key chưa được cấu hình. Thêm GEMINI_API_KEY vào local.properties và rebuild."));
            return;
        }
        if (System.currentTimeMillis() < quotaBlockedUntil) {
            callback.onError(new Exception(
                "Rate limit tạm thời — vui lòng thử lại sau vài phút."));
            return;
        }
        String prompt =
            "Bạn là chuyên gia tư vấn thương mại điện tử cho cửa hàng thời trang & phụ kiện ÉLAN. " +
            "Dưới đây là dữ liệu hành vi khách hàng được thu thập qua hệ thống nhận diện cảm xúc:\n\n" +
            analyticsSummary + "\n" +
            "Dựa trên dữ liệu trên, hãy đưa ra đúng 4 đề xuất cụ thể, thực tế để cải thiện " +
            "tỷ lệ chuyển đổi và trải nghiệm mua sắm thời trang. " +
            "Mỗi đề xuất viết trên 1 dòng, bắt đầu bằng số thứ tự (1. 2. 3. 4.). " +
            "Ngắn gọn, rõ ràng, không dùng emoji.";
        callGemini(BASE_URL_FULL, prompt, callback);
    }

    // ── Direct REST call ──────────────────────────────────────────────────────

    private void callGemini(String url, String prompt, InsightsCallback callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                JSONObject part    = new JSONObject().put("text", prompt);
                JSONObject content = new JSONObject()
                        .put("parts", new JSONArray().put(part));
                String body = new JSONObject()
                        .put("contents", new JSONArray().put(content))
                        .toString();

                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("x-goog-api-key", BuildConfig.GEMINI_API_KEY);
                conn.setDoOutput(true);
                conn.setConnectTimeout(30_000);
                conn.setReadTimeout(60_000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes("UTF-8"));
                }

                long tGemini = System.currentTimeMillis();
                int code = conn.getResponseCode();
                Log.d("ELAN_PERF", "GeminiRTT:" + (System.currentTimeMillis() - tGemini) + "ms code=" + code);
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        code == 200 ? conn.getInputStream() : conn.getErrorStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String ln;
                while ((ln = br.readLine()) != null) sb.append(ln);
                br.close();

                if (code == 429) {
                    // Back off 5 minutes then allow retries automatically
                    quotaBlockedUntil = System.currentTimeMillis() + QUOTA_BACKOFF_MS;
                    Log.w(TAG, "Gemini 429 — backing off for 15 minutes");
                    callback.onError(new Exception("HTTP 429: rate limit — thử lại sau 15 phút"));
                    return;
                }
                if (code != 200) {
                    Log.e(TAG, "Gemini HTTP " + code + ": " + sb);
                    callback.onError(new Exception("HTTP " + code + ": " + sb));
                    return;
                }

                String text = new JSONObject(sb.toString())
                        .getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts").getJSONObject(0)
                        .getString("text");
                callback.onSuccess(text.trim());

            } catch (Exception e) {
                Log.e(TAG, "Gemini call failed", e);
                callback.onError(e);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    // ── Fallback texts (shown when quota exceeded or API unavailable) ─────────

    private String getFallback(EmotionLabel emotion) {
        switch (emotion) {
            case INTERESTED:
                return "Sản phẩm này đang được yêu thích — thêm vào giỏ hàng trước khi hết.";
            case HESITANT:
                return "Mua sắm tự tin với chính sách đổi trả 30 ngày và hàng chính hãng 100%.";
            default:
                return "Khám phá bộ sưu tập ÉLAN để tìm phụ kiện hoàn hảo cho phong cách của bạn.";
        }
    }

    // ── Prompt builder ────────────────────────────────────────────────────────

    private String buildPrompt(EmotionLabel emotion, Product product) {
        String name     = product.getName();
        String category = product.getCategory();
        String price    = String.format(java.util.Locale.getDefault(), "%,d", product.getPrice());
        String desc     = product.getDescription();
        if (desc.length() > DESC_MAX_CHARS) desc = desc.substring(0, DESC_MAX_CHARS) + "...";

        switch (emotion) {
            case INTERESTED:
                return String.format(
                    "A user is browsing a fashion product \"%s\" (category: %s, price: %s). " +
                    "Description: %s. The user shows clear signs of interest. " +
                    "Write 1-2 short sentences to encourage an immediate purchase, " +
                    "highlighting the style or uniqueness of the product. " +
                    "Respond in the same language as the product name and description. " +
                    "Friendly, fashion-forward tone. No emoji.",
                    name, category, price, desc);
            case HESITANT:
                return String.format(
                    "A user is browsing a fashion product \"%s\" (category: %s, price: %s). " +
                    "Description: %s. The user seems hesitant and undecided. " +
                    "Write 1-2 short sentences to build purchase confidence, " +
                    "emphasising quality, durability, or return policy. " +
                    "Respond in the same language as the product name and description. " +
                    "Gentle, reassuring tone. No emoji.",
                    name, category, price, desc);
            default:
                return String.format(
                    "A user is browsing a fashion product \"%s\" (category: %s, price: %s). " +
                    "Description: %s. The user has no strong reaction yet. " +
                    "Write 1-2 short sentences to spark curiosity about the product's style. " +
                    "Respond in the same language as the product name and description. " +
                    "Fresh, creative tone. No emoji.",
                    name, category, price, desc);
        }
    }
}
