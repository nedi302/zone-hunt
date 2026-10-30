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

import java.util.Locale;

/**
 * High-visibility map marker used for the periodic pings. The two initials of
 * the player are the most important part, so the badge is deliberately larger
 * and bolder than the old pin.
 */
public class PingMarkerDrawable extends Drawable {

    private final float density;
    private final String label;
    private final int fillColor;
    private final int haloColor;

    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path pinPath = new Path();

    public PingMarkerDrawable(float density, String label, int fillColor) {
        this.density = density;
        this.fillColor = fillColor;
        this.label = normalize(label);
        this.haloColor = Color.argb(55, Color.red(fillColor), Color.green(fillColor),
                Color.blue(fillColor));

        haloPaint.setStyle(Paint.Style.FILL);
        haloPaint.setColor(haloColor);

        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(Color.argb(95, 0, 0, 0));

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(fillColor);

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(2.5f * density);
        ringPaint.setColor(Color.argb(245, 255, 255, 255));

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(Color.rgb(7, 18, 12));

        textPaint.setColor(Color.rgb(7, 18, 12));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        textPaint.setTextSize((this.label.length() >= 2 ? 16f : 18f) * density);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "?";
        }
        String clean = value.trim().toUpperCase(Locale.GERMANY);
        if (clean.isEmpty()) {
            return "?";
        }
        return clean.length() > 2 ? clean.substring(0, 2) : clean;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        float w = bounds.width();
        float h = bounds.height();
        if (w <= 0 || h <= 0) {
            return;
        }
        // osmdroid hands us absolute screen bounds; drawing must be offset by
        // bounds.left/top or every pin ends up in the top-left corner.
        int save = canvas.save();
        canvas.translate(bounds.left, bounds.top);

        float cx = w / 2f;
        float radius = Math.min(w * 0.36f, h * 0.30f);
        float cy = radius + 7f * density;

        // Soft halo makes the ping stand out against the map.
        canvas.drawCircle(cx, cy, radius + 7f * density, haloPaint);

        pinPath.reset();
        pinPath.addCircle(cx, cy, radius, Path.Direction.CW);
        pinPath.moveTo(cx - radius * 0.48f, cy + radius * 0.80f);
        pinPath.lineTo(cx, h - 4f * density);
        pinPath.lineTo(cx + radius * 0.48f, cy + radius * 0.80f);
        pinPath.close();

        canvas.save();
        canvas.translate(0, 2f * density);
        canvas.drawPath(pinPath, shadowPaint);
        canvas.restore();

        canvas.drawPath(pinPath, fillPaint);
        canvas.drawPath(pinPath, ringPaint);

        // Pinpoint the exact GPS position at the tip of the pin.
        canvas.drawCircle(cx, h - 4f * density, 2.1f * density, dotPaint);

        canvas.drawText(label, cx, cy + textPaint.getTextSize() * 0.36f, textPaint);
        canvas.restoreToCount(save);
    }

    @Override
    public void setAlpha(int alpha) {
        haloPaint.setAlpha((int) (alpha * 0.55f));
        shadowPaint.setAlpha((int) (alpha * 0.37f));
        fillPaint.setAlpha(alpha);
        ringPaint.setAlpha(alpha);
        textPaint.setAlpha(alpha);
        dotPaint.setAlpha(alpha);
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
        return Math.round(62 * density);
    }

    @Override
    public int getIntrinsicHeight() {
        return Math.round(76 * density);
    }
}
