package com.zonehunt.app.view;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Compact map marker used while replaying a recorded round.
 */
public class ReplayDotDrawable extends Drawable {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final String label;

    public ReplayDotDrawable(float density, String label, int fillColor) {
        this.density = density;
        this.label = label == null || label.isEmpty() ? "?" : label.substring(0, 1);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(fillColor);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(2f * density);
        ringPaint.setColor(Color.argb(235, 245, 249, 244));
        textPaint.setColor(Color.rgb(7, 18, 12));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        textPaint.setTextSize(11f * density);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        float w = bounds.width();
        float h = bounds.height();
        if (w <= 0 || h <= 0) {
            return;
        }
        int save = canvas.save();
        canvas.translate(bounds.left, bounds.top);

        float cx = w / 2f;
        float cy = h / 2f;
        float radius = Math.min(cx, cy) - 2.5f * density;
        canvas.drawCircle(cx, cy, radius, fillPaint);
        canvas.drawCircle(cx, cy, radius, ringPaint);
        canvas.drawText(label, cx, cy + textPaint.getTextSize() * 0.36f, textPaint);
        canvas.restoreToCount(save);
    }

    @Override
    public void setAlpha(int alpha) {
        fillPaint.setAlpha(alpha);
        ringPaint.setAlpha(alpha);
        textPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable android.graphics.ColorFilter colorFilter) {
        fillPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return android.graphics.PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return Math.round(30 * density);
    }

    @Override
    public int getIntrinsicHeight() {
        return Math.round(30 * density);
    }
}
