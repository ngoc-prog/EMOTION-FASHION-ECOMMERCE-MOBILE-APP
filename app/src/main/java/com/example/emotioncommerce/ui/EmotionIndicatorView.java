package com.example.emotioncommerce.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import androidx.appcompat.widget.AppCompatTextView;
import com.example.emotioncommerce.emotion.EmotionLabel;

public class EmotionIndicatorView extends AppCompatTextView {

    public EmotionIndicatorView(Context context) {
        super(context);
        init();
    }

    public EmotionIndicatorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setGravity(Gravity.CENTER);
        int padding = (int) (getResources().getDisplayMetrics().density * 8);
        setPadding(padding * 2, padding / 2, padding * 2, padding / 2);
        setTextColor(Color.WHITE);
        setTextSize(12f);
        updateEmotion(EmotionLabel.UNKNOWN);
    }

    public void updateEmotion(EmotionLabel label) {
        int color;
        String text;
        switch (label) {
            case INTERESTED:
                text  = getContext().getString(com.example.emotioncommerce.R.string.emotion_interested);
                color = Color.parseColor("#4CAF50");
                break;
            case HESITANT:
                text  = getContext().getString(com.example.emotioncommerce.R.string.emotion_hesitant);
                color = Color.parseColor("#FF9800");
                break;
            case INDIFFERENT:
                text  = getContext().getString(com.example.emotioncommerce.R.string.emotion_indifferent);
                color = Color.parseColor("#9E9E9E");
                break;
            default:
                text  = getContext().getString(com.example.emotioncommerce.R.string.detecting);
                color = Color.parseColor("#607D8B");
                break;
        }
        setText(text);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(32f);
        setBackground(bg);
    }
}
