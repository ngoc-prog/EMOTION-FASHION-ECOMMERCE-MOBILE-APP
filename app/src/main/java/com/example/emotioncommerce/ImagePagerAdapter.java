package com.example.emotioncommerce;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.bumptech.glide.Glide;

public class ImagePagerAdapter extends RecyclerView.Adapter<ImagePagerAdapter.ViewHolder> {

    private final List<String> imageUrls;

    public ImagePagerAdapter(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ImageView iv = new ImageView(parent.getContext());
        iv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return new ViewHolder(iv);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Glide.with(holder.imageView.getContext())
                .load(imageUrls.get(position))
                .placeholder(R.drawable.product_placeholder)
                .error(R.drawable.product_placeholder)
                .fitCenter()
                .into(holder.imageView);

        holder.imageView.setOnClickListener(v -> {
            int idx = holder.getBindingAdapterPosition();
            if (idx == RecyclerView.NO_POSITION) return;
            showImageViewer(v.getContext(), imageUrls, idx);
        });
    }

    @Override
    public int getItemCount() { return imageUrls.size(); }

    // ── Full-screen image viewer modal (shared by product images + review photos) ──

    static void showImageViewer(Context ctx, List<String> urls, int startIndex) {
        Dialog dialog = new Dialog(ctx);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        Window win = dialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new ColorDrawable(0xFFFAF6F0));
            win.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            win.setStatusBarColor(0xFFFAF6F0);
        }

        FrameLayout root = new FrameLayout(ctx);
        root.setBackgroundColor(0xFFFAF6F0);

        // ── ViewPager2 ────────────────────────────────────────────────────────
        ViewPager2 vp = new ViewPager2(ctx);
        vp.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        FullPagerAdapter fullAdapter = new FullPagerAdapter(ctx, urls, dialog);
        vp.setAdapter(fullAdapter);
        fullAdapter.setViewPager(vp);
        vp.setCurrentItem(startIndex, false);

        // ── Close button — top right ──────────────────────────────────────────
        ImageButton btnClose = new ImageButton(ctx);
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(
                dpToPx(ctx, 48), dpToPx(ctx, 48), Gravity.TOP | Gravity.END);
        closeLp.topMargin  = dpToPx(ctx, 8);
        closeLp.rightMargin = dpToPx(ctx, 8);
        btnClose.setLayoutParams(closeLp);
        btnClose.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        btnClose.setColorFilter(0xFF3D2B1F);
        btnClose.setBackground(null);
        btnClose.setPadding(dpToPx(ctx, 10), dpToPx(ctx, 10),
                            dpToPx(ctx, 10), dpToPx(ctx, 10));
        btnClose.setOnClickListener(v -> dialog.dismiss());

        // ── Dot indicators — bottom center ────────────────────────────────────
        LinearLayout dotsRow = new LinearLayout(ctx);
        dotsRow.setOrientation(LinearLayout.HORIZONTAL);
        dotsRow.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams dotsLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dotsLp.bottomMargin = dpToPx(ctx, 28);
        dotsRow.setLayoutParams(dotsLp);

        int dotSize    = dpToPx(ctx, 7);
        int dotMargin  = dpToPx(ctx, 4);
        int colorActive   = 0xFF3D2B1F;
        int colorInactive = 0x443D2B1F;

        View[] dots = new View[urls.size()];
        for (int i = 0; i < urls.size(); i++) {
            View dot = new View(ctx);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dotSize, dotSize);
            dlp.setMargins(dotMargin, 0, dotMargin, 0);
            dot.setLayoutParams(dlp);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(i == startIndex ? colorActive : colorInactive);
            dot.setBackground(dotBg);
            dots[i] = dot;
            dotsRow.addView(dot);
        }

        // Show dots only when more than 1 image
        dotsRow.setVisibility(urls.size() > 1 ? View.VISIBLE : View.GONE);

        // ── "Vuốt để xem thêm" hint — fades out after 2s ─────────────────────
        TextView tvHint = new TextView(ctx);
        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hintLp.bottomMargin = dpToPx(ctx, 54);
        tvHint.setLayoutParams(hintLp);
        tvHint.setText(ctx.getString(R.string.swipe_hint));
        tvHint.setTextColor(0xAA3D2B1F);
        tvHint.setTextSize(12f);
        tvHint.setVisibility(urls.size() > 1 ? View.VISIBLE : View.GONE);

        // ── Page change: update dots + hide hint after first swipe ────────────
        vp.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            boolean hintDismissed = false;
            @Override public void onPageSelected(int pos) {
                for (int i = 0; i < dots.length; i++) {
                    GradientDrawable d = new GradientDrawable();
                    d.setShape(GradientDrawable.OVAL);
                    d.setColor(i == pos ? 0xFF3D2B1F : 0x443D2B1F);
                    dots[i].setBackground(d);
                }
                if (!hintDismissed) {
                    hintDismissed = true;
                    tvHint.animate().alpha(0f).setDuration(300)
                            .withEndAction(() -> tvHint.setVisibility(View.GONE)).start();
                }
            }
        });

        // Auto-hide hint after 2 seconds
        if (urls.size() > 1) {
            root.postDelayed(() -> {
                if (tvHint.getVisibility() == View.VISIBLE) {
                    tvHint.animate().alpha(0f).setDuration(500)
                            .withEndAction(() -> tvHint.setVisibility(View.GONE)).start();
                }
            }, 2000);
        }

        root.addView(vp);
        root.addView(btnClose);
        root.addView(dotsRow);
        root.addView(tvHint);

        dialog.setContentView(root);
        dialog.show();

        // Must call setLayout AFTER show() for it to take effect
        if (win != null) {
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                          WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    private static int dpToPx(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }

    // ── Adapter for the dialog's ViewPager2 ──────────────────────────────────

    private static class FullPagerAdapter
            extends RecyclerView.Adapter<FullPagerAdapter.VH> {

        private final Context ctx;
        private final List<String> urls;
        private final Dialog dialog;
        private ViewPager2 viewPager;

        FullPagerAdapter(Context ctx, List<String> urls, Dialog dialog) {
            this.ctx    = ctx;
            this.urls   = urls;
            this.dialog = dialog;
        }

        void setViewPager(ViewPager2 vp) { viewPager = vp; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ZoomableImageView iv = new ZoomableImageView(ctx);
            iv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            iv.setZoomCallback(zoomed -> {
                if (viewPager != null) viewPager.setUserInputEnabled(!zoomed);
            });
            return new VH(iv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Glide.with(ctx)
                    .load(urls.get(position))
                    .placeholder(R.drawable.product_placeholder)
                    .error(R.drawable.product_placeholder)
                    .into(holder.iv);
        }

        @Override public int getItemCount() { return urls.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final ZoomableImageView iv;
            VH(ZoomableImageView iv) { super(iv); this.iv = iv; }
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView imageView;
        ViewHolder(ImageView iv) { super(iv); this.imageView = iv; }
    }
}
