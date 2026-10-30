package com.zonehunt.app.view;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class PlayerPinDrawable extends Drawable {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path pinPath = new Path();
    private final float density;
    private final String label;
    private final int fillColor;
    private final boolean self;

    public PlayerPinDrawable(float density, String label, int fillColor, boolean self) {
        this.density = density;
        this.label = label == null ? "?" : label;
        this.fillColor = fillColor;
        this.self = self;
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(fillColor);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth((self ? 3f : 2f) * density);
        ringPaint.setColor(self ? Color.WHITE : Color.argb(220, 7, 18, 12));
        textPaint.setColor(Color.rgb(7, 18, 12));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        textPaint.setTextSize(13f * density);
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
        float r = Math.min(w, h * 0.72f) * 0.42f;
        float cy = r + 3 * density;

        pinPath.reset();
        pinPath.addCircle(cx, cy, r, Path.Direction.CW);
        pinPath.moveTo(cx - r * 0.46f, cy + r * 0.82f);
        pinPath.lineTo(cx, h - 2 * density);
        pinPath.lineTo(cx + r * 0.46f, cy + r * 0.82f);
        pinPath.close();
        canvas.drawPath(pinPath, fillPaint);
        canvas.drawPath(pinPath, ringPaint);

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
        return Math.round(54 * density);
    }

    @Override
    public int getIntrinsicHeight() {
        return Math.round(66 * density);
    }
}
