package com.example.emotioncommerce.emotion;

public class EmotionResult {
    public final EmotionLabel label;
    public final String displayText;

    public EmotionResult(EmotionLabel label, String displayText) {
        this.label = label;
        this.displayText = displayText;
    }
}
