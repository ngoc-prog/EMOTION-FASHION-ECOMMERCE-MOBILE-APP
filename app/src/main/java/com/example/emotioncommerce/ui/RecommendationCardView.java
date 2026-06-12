package com.example.emotioncommerce.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import androidx.cardview.widget.CardView;
import com.example.emotioncommerce.R;

public class RecommendationCardView extends CardView {

    private TextView tvRecommendation;

    public RecommendationCardView(Context context) {
        super(context);
        init(context);
    }

    public RecommendationCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_recommendation_card, this, true);
        tvRecommendation = findViewById(R.id.tv_recommendation);
        setVisibility(View.GONE);

        float dp = context.getResources().getDisplayMetrics().density;
        setRadius(12f * dp);
        setCardElevation(0f);
        setCardBackgroundColor(android.graphics.Color.parseColor("#FDF6EE"));
    }

    public void showRecommendation(String text) {
        tvRecommendation.setText(text);
        setVisibility(View.VISIBLE);
        setAlpha(0f);
        setTranslationY(40f);
        animate().alpha(1f).translationY(0f).setDuration(400).start();
    }

    public void hide() {
        animate().alpha(0f).translationY(40f).setDuration(300)
                .withEndAction(() -> setVisibility(View.GONE)).start();
    }
}
