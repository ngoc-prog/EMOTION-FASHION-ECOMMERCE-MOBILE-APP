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
    private static final String MODEL = "gemini-2.0-flash";
    // v1beta accepts both legacy AIza keys and new AQ. format keys.
    // Key is passed via x-goog-api-key header (required for AQ. format).
    private static final String BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/"
            + MODEL + ":generateContent";

    // Per-emotion cooldown (ms) — reduced from 30s to 2 min to avoid burn
    private static final long EMOTION_COOLDOWN_MS  = 120_000;
    // Minimum gap between any two API calls regardless of emotion
    private static final long GLOBAL_COOLDOWN_MS   = 90_000;
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

    private final Map<EmotionLabel, Long>   lastCallTimePerEmotion = new EnumMap<>(EmotionLabel.class);
    // Cache one successful response per emotion for the entire session
    private final Map<EmotionLabel, String> responseCache          = new EnumMap<>(EmotionLabel.class);
    private volatile long    lastGlobalCallTime = 0;
    // After a 429, block calls for 5 minutes then retry automatically
    private static final long QUOTA_BACKOFF_MS  = 5 * 60_000L;
    private volatile long    quotaBlockedUntil  = 0;

    // ── Cooldown helpers ──────────────────────────────────────────────────────

    public boolean canCallGemini(EmotionLabel label) {
        long now = System.currentTimeMillis();
        if (now < quotaBlockedUntil) return false;
        if (now - lastGlobalCallTime < GLOBAL_COOLDOWN_MS) return false;
        Long last = lastCallTimePerEmotion.get(label);
        return last == null || now - last > EMOTION_COOLDOWN_MS;
    }

    public boolean shouldTrigger(EmotionLabel label) {
        return label != EmotionLabel.UNKNOWN && canCallGemini(label);
    }

    public long getCooldownRemainingMs(EmotionLabel label) {
        long now = System.currentTimeMillis();
        long globalWait  = Math.max(0, GLOBAL_COOLDOWN_MS - (now - lastGlobalCallTime));
        Long last = lastCallTimePerEmotion.get(label);
        long emotionWait = last == null ? 0 : Math.max(0, EMOTION_COOLDOWN_MS - (now - last));
        return Math.max(globalWait, emotionWait);
    }

    // ── Customer recommendation ───────────────────────────────────────────────

    public void generateRecommendation(EmotionLabel emotion, Product product,
                                       RecommendationCallback callback) {
        if (BuildConfig.GEMINI_API_KEY == null || BuildConfig.GEMINI_API_KEY.isEmpty()) return;

        // Return cached response immediately — no API call needed
        if (responseCache.containsKey(emotion)) {
            callback.onSuccess(responseCache.get(emotion));
            return;
        }

        if (!shouldTrigger(emotion)) {
            return;
        }

        long now = System.currentTimeMillis();
        lastCallTimePerEmotion.put(emotion, now);
        lastGlobalCallTime = now;

        callGemini(buildPrompt(emotion, product), new InsightsCallback() {
            @Override public void onSuccess(String t) {
                responseCache.put(emotion, t);
                callback.onSuccess(t);
            }
            @Override public void onError(Exception e) {
                // On any API error, return fallback so the UI never breaks
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
        callGemini(prompt, callback);
    }

    // ── Direct REST call ──────────────────────────────────────────────────────

    private void callGemini(String prompt, InsightsCallback callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                JSONObject part    = new JSONObject().put("text", prompt);
                JSONObject content = new JSONObject()
                        .put("parts", new JSONArray().put(part));
                String body = new JSONObject()
                        .put("contents", new JSONArray().put(content))
                        .toString();

                conn = (HttpURLConnection) new URL(BASE_URL).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("x-goog-api-key", BuildConfig.GEMINI_API_KEY);
                conn.setDoOutput(true);
                conn.setConnectTimeout(30_000);
                conn.setReadTimeout(60_000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes("UTF-8"));
                }

                int code = conn.getResponseCode();
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        code == 200 ? conn.getInputStream() : conn.getErrorStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String ln;
                while ((ln = br.readLine()) != null) sb.append(ln);
                br.close();

                if (code == 429) {
                    // Back off 5 minutes then allow retries automatically
                    quotaBlockedUntil = System.currentTimeMillis() + QUOTA_BACKOFF_MS;
                    Log.w(TAG, "Gemini 429 — backing off for 5 minutes");
                    callback.onError(new Exception("HTTP 429: rate limit — thử lại sau 5 phút"));
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
                return "Sản phẩm này đang rất được ưa chuộng! Hãy sở hữu ngay để hoàn thiện phong cách của bạn.";
            case HESITANT:
                return "Bạn có thể yên tâm — sản phẩm chính hãng 100% với chính sách đổi trả trong 30 ngày.";
            default:
                return "Khám phá thêm bộ sưu tập ÉLAN để tìm phụ kiện hoàn hảo cho phong cách của bạn.";
        }
    }

    // ── Prompt builder ────────────────────────────────────────────────────────

    private String buildPrompt(EmotionLabel emotion, Product product) {
        String name     = product.getName();
        String category = product.getCategory();
        String price    = String.format("%,d", product.getPrice());
        String desc     = product.getDescription();
        if (desc.length() > DESC_MAX_CHARS) desc = desc.substring(0, DESC_MAX_CHARS) + "...";

        switch (emotion) {
            case INTERESTED:
                return String.format(
                    "Người dùng đang xem sản phẩm thời trang \"%s\" (danh mục: %s, giá: %sđ). " +
                    "Mô tả: %s. Người dùng có biểu hiện hứng thú rõ rệt. " +
                    "Viết 1-2 câu ngắn bằng tiếng Việt để khuyến khích mua ngay, " +
                    "nhấn mạnh phong cách hoặc sự độc đáo của sản phẩm. " +
                    "Giọng thân thiện, thời trang. Không dùng emoji.",
                    name, category, price, desc);
            case HESITANT:
                return String.format(
                    "Người dùng đang xem sản phẩm thời trang \"%s\" (danh mục: %s, giá: %sđ). " +
                    "Mô tả: %s. Người dùng đang phân vân chưa quyết định. " +
                    "Viết 1-2 câu ngắn bằng tiếng Việt để tăng sự tự tin khi mua, " +
                    "nhấn mạnh chất lượng, độ bền hoặc chính sách đổi trả. " +
                    "Giọng nhẹ nhàng, thuyết phục. Không dùng emoji.",
                    name, category, price, desc);
            default:
                return String.format(
                    "Người dùng đang xem sản phẩm thời trang \"%s\" (danh mục: %s, giá: %sđ). " +
                    "Mô tả: %s. Người dùng chưa có phản ứng rõ ràng. " +
                    "Viết 1-2 câu ngắn bằng tiếng Việt để gợi mở sự tò mò và phong cách. " +
                    "Giọng tươi, sáng tạo. Không dùng emoji.",
                    name, category, price, desc);
        }
    }
}
