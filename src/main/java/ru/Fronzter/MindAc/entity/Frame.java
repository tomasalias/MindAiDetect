package ru.Fronzter.MindAc.entity;

public final class Frame {
    private final float x; // deltaYaw
    private final float y; // deltaPitch

    public Frame(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public float getX() { return x; }
    public float getY() { return y; }
}