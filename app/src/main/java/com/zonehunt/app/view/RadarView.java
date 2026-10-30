package com.zonehunt.app.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

public class RadarView extends View {

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotOuterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float angle;
    private ValueAnimator animator;

    public RadarView(Context context) {
        this(context, null);
    }

    public RadarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(1));
        ringPaint.setColor(Color.argb(38, 215, 255, 95));

        axisPaint.setStyle(Paint.Style.STROKE);
        axisPaint.setStrokeWidth(dp(1));
        axisPaint.setColor(Color.argb(26, 215, 255, 95));

        dotPaint.setColor(Color.argb(220, 215, 255, 95));
        dotOuterPaint.setStyle(Paint.Style.STROKE);
        dotOuterPaint.setStrokeWidth(dp(1));
        dotOuterPaint.setColor(Color.argb(80, 215, 255, 95));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animator == null) {
            animator = ValueAnimator.ofFloat(0f, 360f);
            animator.setDuration(8000L);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(a -> {
                angle = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() * 0.58f;
        float cy = getHeight() * 0.52f;
        float radius = Math.min(getWidth(), getHeight()) * 0.47f;
        int rings = 4;
        for (int i = 1; i <= rings; i++) {
            canvas.drawCircle(cx, cy, radius * i / rings, ringPaint);
        }
        canvas.drawLine(cx - radius, cy, cx + radius, cy, axisPaint);
        canvas.drawLine(cx, cy - radius, cx, cy + radius, axisPaint);
        for (int deg = 0; deg < 360; deg += 30) {
            double rad = Math.toRadians(deg);
            float x1 = cx + (float) Math.cos(rad) * radius;
            float y1 = cy + (float) Math.sin(rad) * radius;
            float x2 = cx + (float) Math.cos(rad) * (radius - dp(7));
            float y2 = cy + (float) Math.sin(rad) * (radius - dp(7));
            canvas.drawLine(x1, y1, x2, y2, axisPaint);
        }

        sweepPaint.setShader(new SweepGradient(cx, cy,
                new int[]{Color.TRANSPARENT, Color.argb(15, 215, 255, 95), Color.argb(95, 215, 255, 95)},
                new float[]{0f, 0.78f, 1f}));
        canvas.save();
        canvas.rotate(angle, cx, cy);
        canvas.drawCircle(cx, cy, radius, sweepPaint);
        canvas.restore();

        drawBlip(canvas, cx + radius * 0.27f, cy - radius * 0.35f, dp(4));
        drawBlip(canvas, cx - radius * 0.52f, cy + radius * 0.18f, dp(3));
        drawBlip(canvas, cx + radius * 0.05f, cy + radius * 0.58f, dp(2.5f));
        drawBlip(canvas, cx + radius * 0.68f, cy + radius * 0.24f, dp(2f));

        Paint center = new Paint(Paint.ANTI_ALIAS_FLAG);
        center.setColor(Color.argb(240, 255, 139, 76));
        canvas.drawCircle(cx, cy, dp(3.5f), center);
        dotOuterPaint.setColor(Color.argb(70, 255, 139, 76));
        canvas.drawCircle(cx, cy, dp(8f), dotOuterPaint);
    }

    private void drawBlip(Canvas canvas, float x, float y, float r) {
        canvas.drawCircle(x, y, r * 2.5f, dotOuterPaint);
        canvas.drawCircle(x, y, r, dotPaint);
    }
}
