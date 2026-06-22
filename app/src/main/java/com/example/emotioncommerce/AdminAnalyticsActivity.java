package com.example.emotioncommerce;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.emotioncommerce.data.SessionAnalyticsRepository;
import com.example.emotioncommerce.data.SessionAnalyticsRepository.ProductStats;
import com.example.emotioncommerce.data.SessionAnalyticsRepository.TimelineEvent;
import com.example.emotioncommerce.emotion.EmotionLabel;
import com.example.emotioncommerce.gemini.GeminiIntegrationManager;
import com.google.android.material.tabs.TabLayout;
import java.util.List;
import java.util.Map;

public class AdminAnalyticsActivity extends AppCompatActivity {

    private GeminiIntegrationManager geminiManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_analytics);

        geminiManager = GeminiIntegrationManager.getInstance();

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        findViewById(R.id.tab_layout).setVisibility(View.VISIBLE);
        findViewById(R.id.tab_content).setVisibility(View.VISIBLE);

        SessionAnalyticsRepository repo = SessionAnalyticsRepository.getInstance();
        if (repo.hasData()) {
            populateOverview(repo);
            populateEmotionDistribution(repo);
            populateProductRanking(repo);
            populateTimeline(repo);
            fillHeaderPills(repo);
        } else {
            ((TextView) findViewById(R.id.tv_session_label))
                    .setText(getString(R.string.admin_analytics_empty));
            findViewById(R.id.tv_pill_products).setVisibility(View.GONE);
            findViewById(R.id.tv_pill_emotion).setVisibility(View.GONE);
        }
        setupGeminiInsights(repo);
        setupResetButton();

        setupTabs();
    }

    // ── Tab navigation ────────────────────────────────────────────────────────

    private void setupTabs() {
        TabLayout tabs = findViewById(R.id.tab_layout);
        tabs.addTab(tabs.newTab().setText(getString(R.string.tab_overview)));
        tabs.addTab(tabs.newTab().setText(getString(R.string.tab_products)));
        tabs.addTab(tabs.newTab().setText(getString(R.string.tab_timeline)));
        tabs.addTab(tabs.newTab().setText(getString(R.string.tab_ai)));

        final View[] panels = {
            findViewById(R.id.panel_overview),
            findViewById(R.id.panel_products),
            findViewById(R.id.panel_timeline),
            findViewById(R.id.panel_ai)
        };

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                for (int i = 0; i < panels.length; i++) {
                    panels[i].setVisibility(i == tab.getPosition() ? View.VISIBLE : View.GONE);
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    // ── Header pills ─────────────────────────────────────────────────────────

    private void fillHeaderPills(SessionAnalyticsRepository repo) {
        TextView tvProducts = findViewById(R.id.tv_pill_products);
        TextView tvEmotion  = findViewById(R.id.tv_pill_emotion);

        tvProducts.setText(repo.getTotalProductsViewed() + " " + getString(R.string.stat_products_viewed));

        Map<EmotionLabel, Long> dist = repo.getOverallEmotionDistribution();
        EmotionLabel dominant = EmotionLabel.HESITANT;
        long maxVal = 0;
        for (Map.Entry<EmotionLabel, Long> e : dist.entrySet()) {
            if (e.getValue() > maxVal) { maxVal = e.getValue(); dominant = e.getKey(); }
        }
        String domLabel;
        switch (dominant) {
            case INTERESTED:  domLabel = "😊 " + getString(R.string.emotion_interested); break;
            case INDIFFERENT: domLabel = "😐 " + getString(R.string.emotion_indifferent); break;
            default:          domLabel = "🤔 " + getString(R.string.emotion_hesitant);
        }
        tvEmotion.setText(domLabel);
    }

    // ── Overview cards ────────────────────────────────────────────────────────

    private void populateOverview(SessionAnalyticsRepository repo) {
        // Update subtitle with data date range
        java.text.SimpleDateFormat dateFmt =
                new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault());
        long earliest = repo.getSessionStartMs();
        long latest   = System.currentTimeMillis();
        String rangeLabel = dateFmt.format(new java.util.Date(earliest))
                + " – " + dateFmt.format(new java.util.Date(latest));
        ((TextView) findViewById(R.id.tv_session_label)).setText(rangeLabel);

        ((TextView) findViewById(R.id.tv_stat_products))
                .setText(String.valueOf(repo.getTotalProductsViewed()));

        long totalMs = repo.getTotalViewTimeMs();
        long totalSec = totalMs / 1000;
        String timeStr = totalSec >= 60
                ? (totalSec / 60) + "m " + (totalSec % 60) + "s"
                : totalSec + "s";
        ((TextView) findViewById(R.id.tv_stat_time)).setText(timeStr);

        ((TextView) findViewById(R.id.tv_stat_carts))
                .setText(String.valueOf(repo.getAddToCartCount()));
        ((TextView) findViewById(R.id.tv_stat_wishlists))
                .setText(String.valueOf(repo.getWishlistCount()));
    }

    // ── Emotion distribution bars ─────────────────────────────────────────────

    private void populateEmotionDistribution(SessionAnalyticsRepository repo) {
        Map<EmotionLabel, Long> dist = repo.getOverallEmotionDistribution();
        long intMs = getOrZero(dist, EmotionLabel.INTERESTED);
        long hesMs = getOrZero(dist, EmotionLabel.HESITANT);
        long indMs = getOrZero(dist, EmotionLabel.INDIFFERENT);
        long total = intMs + hesMs + indMs;
        if (total == 0) return;

        int intPct = (int)(100f * intMs / total);
        int hesPct = (int)(100f * hesMs / total);
        int indPct = 100 - intPct - hesPct;

        setBar(R.id.pb_dist_interested, R.id.tv_pct_interested, intPct);
        setBar(R.id.pb_dist_hesitant,   R.id.tv_pct_hesitant,   hesPct);
        setBar(R.id.pb_dist_indifferent, R.id.tv_pct_indifferent, indPct);
    }

    private void setBar(int pbId, int tvId, int pct) {
        ((ProgressBar) findViewById(pbId)).setProgress(pct);
        ((TextView) findViewById(tvId)).setText(pct + "%");
    }

    // ── Product ranking RecyclerView ──────────────────────────────────────────

    private void populateProductRanking(SessionAnalyticsRepository repo) {
        List<ProductStats> ranking = repo.getProductRanking();
        RecyclerView recycler = findViewById(R.id.recycler_product_stats);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(new ProductStatsAdapter(ranking));
    }

    // ── Gemini insights ───────────────────────────────────────────────────────

    private void setupGeminiInsights(SessionAnalyticsRepository repo) {
        TextView tvResult     = findViewById(R.id.tv_insights_result);
        ProgressBar pbLoading = findViewById(R.id.pb_insights_loading);

        findViewById(R.id.btn_generate_insights).setOnClickListener(v -> {
            v.setEnabled(false);
            pbLoading.setVisibility(View.VISIBLE);
            tvResult.setVisibility(View.GONE);

            geminiManager.generateInsights(repo.buildAnalyticsSummary(),
                new GeminiIntegrationManager.InsightsCallback() {
                    @Override
                    public void onSuccess(String insights) {
                        runOnUiThread(() -> {
                            pbLoading.setVisibility(View.GONE);
                            tvResult.setText(insights);
                            tvResult.setVisibility(View.VISIBLE);
                            v.setEnabled(true);
                        });
                    }
                    @Override
                    public void onError(Exception e) {
                        android.util.Log.e("AdminAnalytics", "Gemini error", e);
                        runOnUiThread(() -> {
                            pbLoading.setVisibility(View.GONE);
                            String msg = e.getMessage();
                            if (msg == null || msg.isEmpty()) {
                                msg = e.getClass().getSimpleName();
                            }
                            if (msg.length() > 300) msg = msg.substring(0, 300) + "...";
                            tvResult.setText(getString(R.string.gemini_error, msg));
                            tvResult.setVisibility(View.VISIBLE);
                            v.setEnabled(true);
                        });
                    }
                });
        });
    }

    // ── Reset analytics ───────────────────────────────────────────────────────

    private void setupResetButton() {
        findViewById(R.id.btn_reset_analytics).setOnClickListener(v ->
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setMessage(getString(R.string.analytics_reset_confirm))
                .setPositiveButton(getString(R.string.analytics_reset_btn), (d, w) -> {
                    SessionAnalyticsRepository.getInstance().clearAll();
                    android.widget.Toast.makeText(this,
                            getString(R.string.analytics_reset_done),
                            android.widget.Toast.LENGTH_SHORT).show();
                    recreate();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show());
    }

    // ── Emotion timeline ──────────────────────────────────────────────────────

    private static final int TIMELINE_MAX = 50;

    private void populateTimeline(SessionAnalyticsRepository repo) {
        List<TimelineEvent> all = repo.getTimeline();
        if (all.isEmpty()) return;

        int total = all.size();
        List<TimelineEvent> shown = total > TIMELINE_MAX
                ? all.subList(total - TIMELINE_MAX, total)
                : all;

        RecyclerView recycler = findViewById(R.id.recycler_timeline);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(new TimelineAdapter(shown, repo.getSessionStartMs(),
                total > TIMELINE_MAX ? total : 0));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static long getOrZero(Map<EmotionLabel, Long> map, EmotionLabel key) {
        Long v = map.get(key);
        return v == null ? 0L : v;
    }

    // ── Timeline adapter ──────────────────────────────────────────────────────

    private static class TimelineAdapter
            extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_EVENT  = 0;
        private static final int TYPE_FOOTER = 1;

        private final List<TimelineEvent> items;
        private final int totalCount; // 0 = no truncation

        TimelineAdapter(List<TimelineEvent> items, long ignored, int totalCount) {
            this.items      = items;
            this.totalCount = totalCount;
        }

        @Override
        public int getItemViewType(int position) {
            return (totalCount > 0 && position == items.size()) ? TYPE_FOOTER : TYPE_EVENT;
        }

        @Override
        public int getItemCount() {
            return totalCount > 0 ? items.size() + 1 : items.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_FOOTER) {
                TextView tv = new TextView(parent.getContext());
                tv.setPadding(dpToPx(parent, 16), dpToPx(parent, 10),
                        dpToPx(parent, 16), dpToPx(parent, 12));
                tv.setTextSize(11f);
                tv.setTextColor(0xFF9A8870);
                tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                return new RecyclerView.ViewHolder(tv) {};
            }
            View v = inf.inflate(R.layout.item_timeline_event, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (getItemViewType(position) == TYPE_FOOTER) {
                ((TextView) holder.itemView).setText(
                        holder.itemView.getContext().getString(R.string.timeline_showing,
                                items.size(), totalCount));
                return;
            }

            VH h = (VH) holder;
            TimelineEvent event = items.get(position);

            h.tvTime.setText(new java.text.SimpleDateFormat("dd/MM HH:mm",
                    java.util.Locale.getDefault()).format(new java.util.Date(event.getTimestampMs())));
            h.tvProduct.setText(event.getProductName());

            int dotColor;
            String detail;
            android.content.Context ctx = h.itemView.getContext();
            if (event.getKind() == TimelineEvent.Kind.EMOTION) {
                switch (event.getEmotion()) {
                    case INTERESTED:  dotColor = 0xFF4CAF50; detail = ctx.getString(R.string.emotion_interested); break;
                    case INDIFFERENT: dotColor = 0xFF9E8589; detail = ctx.getString(R.string.emotion_indifferent); break;
                    default:          dotColor = 0xFFFF9800; detail = ctx.getString(R.string.emotion_hesitant); break;
                }
                detail += " · " + (event.getDurationMs() / 1000) + "s";
            } else {
                if (event.getActionType() == SessionAnalyticsRepository.ActionRecord.ActionType.ADD_CART) {
                    dotColor = 0xFF4CAF50;
                    detail   = ctx.getString(R.string.event_add_cart);
                } else {
                    dotColor = 0xFFC09A6A;
                    detail   = ctx.getString(R.string.event_add_wishlist);
                }
            }

            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(dotColor);
            h.viewDot.setBackground(dot);
            h.tvDetail.setText(detail);
        }

        private static int dpToPx(ViewGroup vg, int dp) {
            return Math.round(dp * vg.getContext().getResources().getDisplayMetrics().density);
        }

        static class VH extends RecyclerView.ViewHolder {
            final android.widget.TextView tvTime, tvProduct, tvDetail;
            final View viewDot;
            VH(View v) {
                super(v);
                tvTime    = v.findViewById(R.id.tv_timeline_time);
                tvProduct = v.findViewById(R.id.tv_timeline_product);
                tvDetail  = v.findViewById(R.id.tv_timeline_detail);
                viewDot   = v.findViewById(R.id.view_timeline_dot);
            }
        }
    }

    // ── ProductStats adapter ──────────────────────────────────────────────────

    private static class ProductStatsAdapter
            extends RecyclerView.Adapter<ProductStatsAdapter.VH> {

        private final List<ProductStats> items;

        ProductStatsAdapter(List<ProductStats> items) { this.items = items; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_product_stats, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            ProductStats ps = items.get(position);

            h.tvName.setText((position + 1) + ". " + ps.getProductName());

            int interestPct = (int)(ps.getInterestRate() * 100);
            h.pbInterest.setProgress(interestPct);
            h.tvInterestPct.setText(interestPct + "%");

            // Dominant emotion badge
            android.content.Context ctx = h.itemView.getContext();
            EmotionLabel dom = ps.getDominantEmotion();
            String domLabel;
            int domColor;
            switch (dom) {
                case INTERESTED:  domLabel = ctx.getString(R.string.emotion_interested); domColor = 0xFF4CAF50; break;
                case INDIFFERENT: domLabel = ctx.getString(R.string.emotion_indifferent); domColor = 0xFF9E8589; break;
                default:          domLabel = ctx.getString(R.string.emotion_hesitant); domColor = 0xFFFF9800; break;
            }
            h.tvDominant.setText(domLabel);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(20f);
            bg.setColor(domColor);
            h.tvDominant.setBackground(bg);

            // Detail: time + cart
            long secs = ps.getTotalViewTimeMs() / 1000;
            String timeStr = secs >= 60 ? (secs/60) + "m " + (secs%60) + "s" : secs + "s";
            String detail = ctx.getString(R.string.detail_view_time, timeStr);
            if (ps.getCartCount() > 0) detail += ctx.getString(R.string.detail_times_cart, ps.getCartCount());
            if (ps.getWishlistCount() > 0) detail += ctx.getString(R.string.detail_times_wishlist, ps.getWishlistCount());
            h.tvDetail.setText(detail);
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tvName, tvInterestPct, tvDominant, tvDetail;
            final ProgressBar pbInterest;
            VH(View v) {
                super(v);
                tvName        = v.findViewById(R.id.tv_product_stats_name);
                tvInterestPct = v.findViewById(R.id.tv_interest_pct);
                tvDominant    = v.findViewById(R.id.tv_dominant_emotion);
                tvDetail      = v.findViewById(R.id.tv_stats_detail);
                pbInterest    = v.findViewById(R.id.pb_interest_rate);
            }
        }
    }
}
