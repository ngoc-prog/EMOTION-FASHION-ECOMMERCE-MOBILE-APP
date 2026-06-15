package com.example.emotioncommerce.data;

import com.example.emotioncommerce.emotion.EmotionLabel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SessionAnalyticsRepository {

    // ── Data models ──────────────────────────────────────────────────────────

    public static class EmotionRecord {
        private final int productId;
        private final String productName;
        private final EmotionLabel emotion;
        private final long durationMs;
        private final long timestampMs;

        EmotionRecord(int productId, String productName, EmotionLabel emotion,
                      long durationMs, long timestampMs) {
            this.productId   = productId;
            this.productName = productName;
            this.emotion     = emotion;
            this.durationMs  = durationMs;
            this.timestampMs = timestampMs;
        }

        public int        getProductId()   { return productId; }
        public String     getProductName() { return productName; }
        public EmotionLabel getEmotion()   { return emotion; }
        public long       getDurationMs()  { return durationMs; }
        public long       getTimestampMs() { return timestampMs; }
    }

    public static class ActionRecord {
        public enum ActionType { ADD_CART, WISHLIST }
        private final int productId;
        private final String productName;
        private final EmotionLabel emotionAtTime;
        private final ActionType type;
        private final long timestampMs;

        ActionRecord(int productId, String productName,
                     EmotionLabel emotionAtTime, ActionType type, long timestampMs) {
            this.productId     = productId;
            this.productName   = productName;
            this.emotionAtTime = emotionAtTime;
            this.type          = type;
            this.timestampMs   = timestampMs;
        }

        public int          getProductId()     { return productId; }
        public String       getProductName()   { return productName; }
        public EmotionLabel getEmotionAtTime() { return emotionAtTime; }
        public ActionType   getType()          { return type; }
        public long         getTimestampMs()   { return timestampMs; }
    }

    public static class TimelineEvent {
        public enum Kind { EMOTION, ACTION }
        private final Kind kind;
        private final int productId;
        private final String productName;
        private final long timestampMs;
        private final EmotionLabel emotion;
        private final long durationMs;
        private final ActionRecord.ActionType actionType;

        TimelineEvent(EmotionRecord r) {
            kind        = Kind.EMOTION;
            productId   = r.getProductId();
            productName = r.getProductName();
            timestampMs = r.getTimestampMs();
            emotion     = r.getEmotion();
            durationMs  = r.getDurationMs();
            actionType  = null;
        }

        TimelineEvent(ActionRecord a) {
            kind        = Kind.ACTION;
            productId   = a.getProductId();
            productName = a.getProductName();
            timestampMs = a.getTimestampMs();
            emotion     = null;
            durationMs  = 0;
            actionType  = a.getType();
        }

        public Kind                    getKind()        { return kind; }
        public int                     getProductId()   { return productId; }
        public String                  getProductName() { return productName; }
        public long                    getTimestampMs() { return timestampMs; }
        public EmotionLabel            getEmotion()     { return emotion; }
        public long                    getDurationMs()  { return durationMs; }
        public ActionRecord.ActionType getActionType()  { return actionType; }
    }

    public static class ProductStats {
        private final int productId;
        private final String productName;
        private final Map<EmotionLabel, Long> emotionTimeMs;
        private final long totalViewTimeMs;
        private int cartCount;
        private int wishlistCount;

        ProductStats(int productId, String productName, Map<EmotionLabel, Long> emotionTimeMs) {
            this.productId    = productId;
            this.productName  = productName;
            this.emotionTimeMs = emotionTimeMs;
            long total = 0;
            for (long ms : emotionTimeMs.values()) total += ms;
            this.totalViewTimeMs = total;
        }

        public int                       getProductId()     { return productId; }
        public String                    getProductName()   { return productName; }
        public Map<EmotionLabel, Long>   getEmotionTimeMs() { return emotionTimeMs; }
        public long                      getTotalViewTimeMs() { return totalViewTimeMs; }
        public int                       getCartCount()     { return cartCount; }
        public int                       getWishlistCount() { return wishlistCount; }
        void incrementCartCount()     { cartCount++; }
        void incrementWishlistCount() { wishlistCount++; }

        public float getInterestRate() {
            if (totalViewTimeMs == 0) return 0f;
            Long ms = emotionTimeMs.get(EmotionLabel.INTERESTED);
            return ms == null ? 0f : (float) ms / totalViewTimeMs;
        }

        public EmotionLabel getDominantEmotion() {
            EmotionLabel top = EmotionLabel.HESITANT;
            long topMs = 0;
            for (Map.Entry<EmotionLabel, Long> e : emotionTimeMs.entrySet()) {
                if (e.getValue() > topMs) {
                    topMs = e.getValue();
                    top = e.getKey();
                }
            }
            return top;
        }
    }

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static SessionAnalyticsRepository instance;

    public static SessionAnalyticsRepository getInstance() {
        if (instance == null) instance = new SessionAnalyticsRepository();
        return instance;
    }

    private final List<EmotionRecord>  emotionRecords     = new ArrayList<>();
    private final List<ActionRecord>   actionRecords      = new ArrayList<>();
    private final Set<String>          boostedCategories  = new LinkedHashSet<>();
    private long sessionStartMs = 0;

    private SessionAnalyticsRepository() {}

    public long getSessionStartMs() { return sessionStartMs; }

    public void boostCategory(String category) {
        boostedCategories.add(category);
    }

    public Set<String> getBoostedCategories() {
        return Collections.unmodifiableSet(boostedCategories);
    }

    // ── Recording ─────────────────────────────────────────────────────────────

    public void recordEmotionSegment(int productId, String productName,
                                     EmotionLabel emotion, long durationMs) {
        if (emotion == EmotionLabel.UNKNOWN || durationMs < 500) return;
        long ts = System.currentTimeMillis() - durationMs;
        if (sessionStartMs == 0) sessionStartMs = ts;
        emotionRecords.add(new EmotionRecord(productId, productName, emotion, durationMs, ts));
    }

    public void recordAction(int productId, String productName,
                             EmotionLabel currentEmotion, ActionRecord.ActionType type) {
        long ts = System.currentTimeMillis();
        if (sessionStartMs == 0) sessionStartMs = ts;
        actionRecords.add(new ActionRecord(productId, productName, currentEmotion, type, ts));
    }

    // ── Aggregates ────────────────────────────────────────────────────────────

    public boolean hasData() {
        return !emotionRecords.isEmpty();
    }

    public int getTotalProductsViewed() {
        Set<Integer> ids = new HashSet<>();
        for (EmotionRecord r : emotionRecords) ids.add(r.productId);
        return ids.size();
    }

    public long getTotalViewTimeMs() {
        long total = 0;
        for (EmotionRecord r : emotionRecords) total += r.durationMs;
        return total;
    }

    public int getAddToCartCount() {
        int n = 0;
        for (ActionRecord r : actionRecords)
            if (r.type == ActionRecord.ActionType.ADD_CART) n++;
        return n;
    }

    public int getWishlistCount() {
        int n = 0;
        for (ActionRecord r : actionRecords)
            if (r.type == ActionRecord.ActionType.WISHLIST) n++;
        return n;
    }

    /** Overall emotion distribution as ms per label. */
    public Map<EmotionLabel, Long> getOverallEmotionDistribution() {
        Map<EmotionLabel, Long> dist = new EnumMap<>(EmotionLabel.class);
        for (EmotionRecord r : emotionRecords) {
            Long prev = dist.get(r.emotion);
            dist.put(r.emotion, (prev == null ? 0L : prev) + r.durationMs);
        }
        return dist;
    }

    /** Product stats sorted by interest rate descending. */
    public List<ProductStats> getProductRanking() {
        Map<Integer, Map<EmotionLabel, Long>> emotionByProduct = new LinkedHashMap<>();
        Map<Integer, String> nameById = new LinkedHashMap<>();

        for (EmotionRecord r : emotionRecords) {
            nameById.put(r.productId, r.productName);
            Map<EmotionLabel, Long> map = emotionByProduct.get(r.productId);
            if (map == null) {
                map = new EnumMap<>(EmotionLabel.class);
                emotionByProduct.put(r.productId, map);
            }
            Long prev = map.get(r.emotion);
            map.put(r.emotion, (prev == null ? 0L : prev) + r.durationMs);
        }

        List<ProductStats> stats = new ArrayList<>();
        for (Map.Entry<Integer, Map<EmotionLabel, Long>> e : emotionByProduct.entrySet()) {
            ProductStats ps = new ProductStats(e.getKey(), nameById.get(e.getKey()), e.getValue());
            for (ActionRecord a : actionRecords) {
                if (a.productId == e.getKey()) {
                    if (a.type == ActionRecord.ActionType.ADD_CART) ps.incrementCartCount();
                    else ps.incrementWishlistCount();
                }
            }
            stats.add(ps);
        }

        stats.sort((a, b) -> Float.compare(b.getInterestRate(), a.getInterestRate()));
        return stats;
    }

    /** Plain-text summary to feed into the Gemini admin insights prompt. */
    public String buildAnalyticsSummary() {
        int viewed    = getTotalProductsViewed();
        long totalMs  = getTotalViewTimeMs();
        int carts     = getAddToCartCount();
        int wishlists = getWishlistCount();

        Map<EmotionLabel, Long> dist = getOverallEmotionDistribution();
        long intMs = getOrZero(dist, EmotionLabel.INTERESTED);
        long hesMs = getOrZero(dist, EmotionLabel.HESITANT);
        long indMs = getOrZero(dist, EmotionLabel.INDIFFERENT);
        long emotionTotal = intMs + hesMs + indMs;

        String intPct = pct(intMs, emotionTotal);
        String hesPct = pct(hesMs, emotionTotal);
        String indPct = pct(indMs, emotionTotal);

        long totalSec = totalMs / 1000;
        String timeStr = totalSec >= 60
            ? (totalSec / 60) + " phút " + (totalSec % 60) + " giây"
            : totalSec + " giây";

        StringBuilder sb = new StringBuilder();
        sb.append("Dữ liệu hành vi khách hàng từ phiên mua sắm trên app ÉLAN:\n\n");
        sb.append("- Tổng sản phẩm đã xem: ").append(viewed).append("\n");
        sb.append("- Tổng thời gian xem: ").append(timeStr).append("\n");
        sb.append("- Thêm vào giỏ hàng: ").append(carts).append(" lần\n");
        sb.append("- Thêm vào yêu thích: ").append(wishlists).append(" lần\n");
        sb.append("- Phân bố cảm xúc tổng: Hứng thú ").append(intPct)
          .append(", Phân vân ").append(hesPct)
          .append(", Thờ ơ ").append(indPct).append("\n");

        List<ProductStats> ranking = getProductRanking();
        if (!ranking.isEmpty()) {
            sb.append("\nChi tiết theo sản phẩm (top ").append(Math.min(5, ranking.size())).append("):\n");
            for (int i = 0; i < Math.min(5, ranking.size()); i++) {
                ProductStats ps = ranking.get(i);
                sb.append(String.format("- \"%s\": %.0f%% hứng thú, %ds xem, %d thêm giỏ\n",
                        ps.getProductName(),
                        ps.getInterestRate() * 100,
                        ps.getTotalViewTimeMs() / 1000,
                        ps.getCartCount()));
            }
        }

        return sb.toString();
    }

    /** Chronological list of all emotion segments and actions for timeline display. */
    public List<TimelineEvent> getTimeline() {
        List<TimelineEvent> events = new ArrayList<>();
        for (EmotionRecord r : emotionRecords) events.add(new TimelineEvent(r));
        for (ActionRecord a : actionRecords)   events.add(new TimelineEvent(a));
        events.sort((a, b) -> Long.compare(a.timestampMs, b.timestampMs));
        return events;
    }

    private static long getOrZero(Map<EmotionLabel, Long> map, EmotionLabel key) {
        Long v = map.get(key);
        return v == null ? 0L : v;
    }

    private static String pct(long part, long total) {
        if (total == 0) return "0%";
        return (int)(100f * part / total) + "%";
    }
}
