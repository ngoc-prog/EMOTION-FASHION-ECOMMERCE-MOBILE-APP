package com.example.emotioncommerce.emotion;

public class EmotionFeatures {
    private float browRaiseRatio;
    private float eyeAspectRatio;
    private float mouthCurvature;
    private float mouthAspectRatio;
    private float browFurrowDistance;
    private boolean valid;

    public EmotionFeatures() {}

    public EmotionFeatures(float browRaiseRatio, float eyeAspectRatio, float mouthCurvature,
                           float mouthAspectRatio, float browFurrowDistance, boolean valid) {
        this.browRaiseRatio    = browRaiseRatio;
        this.eyeAspectRatio    = eyeAspectRatio;
        this.mouthCurvature    = mouthCurvature;
        this.mouthAspectRatio  = mouthAspectRatio;
        this.browFurrowDistance = browFurrowDistance;
        this.valid             = valid;
    }

    public float getBrowRaiseRatio()    { return browRaiseRatio; }
    public void  setBrowRaiseRatio(float v)  { browRaiseRatio = v; }

    public float getEyeAspectRatio()    { return eyeAspectRatio; }
    public void  setEyeAspectRatio(float v)  { eyeAspectRatio = v; }

    public float getMouthCurvature()    { return mouthCurvature; }
    public void  setMouthCurvature(float v)  { mouthCurvature = v; }

    public float getMouthAspectRatio()  { return mouthAspectRatio; }
    public void  setMouthAspectRatio(float v) { mouthAspectRatio = v; }

    public float getBrowFurrowDistance()  { return browFurrowDistance; }
    public void  setBrowFurrowDistance(float v) { browFurrowDistance = v; }

    public boolean isValid()            { return valid; }
    public void    setValid(boolean v)  { valid = v; }
}
