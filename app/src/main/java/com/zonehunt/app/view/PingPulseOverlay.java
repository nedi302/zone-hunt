package com.zonehunt.app.view;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;

import java.util.ArrayList;
import java.util.List;

/**
 * Expanding rings at the player positions when a ping happens. The rings
 * disappear after a moment and make the periodic reveal unmistakable.
 */
public class PingPulseOverlay extends Overlay {

    public static class PulsePoint {
        public final double lat;
        public final double lng;
        public final int color;

        public PulsePoint(double lat, double lng, int color) {
            this.lat = lat;
            this.lng = lng;
            this.color = color;
        }
    }

    private final float density;
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<PulsePoint> points = new ArrayList<>();
    private ValueAnimator animator;
    private float fraction;

    public PingPulseOverlay(float density) {
        this.density = density;
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(2.4f * density);
    }

    public void show(List<PulsePoint> newPoints) {
        points.clear();
        if (newPoints != null) {
            points.addAll(newPoints);
        }
        if (points.isEmpty()) {
            return;
        }
        if (animator != null) {
            animator.cancel();
        }
        fraction = 0f;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1400L);
        animator.addUpdateListener(animation ->
                fraction = (float) animation.getAnimatedValue());
        animator.start();
    }

    public void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        points.clear();
    }

    public boolean isAnimating() {
        return animator != null && animator.isRunning();
    }

    public float fraction() {
        return fraction;
    }

    @Override
    public void draw(Canvas canvas, MapView mapView, boolean shadow) {
        if (shadow || points.isEmpty() || !isAnimating()) {
            return;
        }
        float t = Math.min(1f, Math.max(0f, fraction));
        float radius = (14f + 34f * t) * density;
        int alpha = Math.round(185 * (1f - t));
        Point p = new Point();
        for (PulsePoint point : points) {
            mapView.getProjection().toPixels(
                    new GeoPoint(point.lat, point.lng), p);
            ringPaint.setColor(Color.argb(alpha, Color.red(point.color),
                    Color.green(point.color), Color.blue(point.color)));
            canvas.drawCircle(p.x, p.y, radius, ringPaint);
            if (t < 0.55f) {
                int innerAlpha = Math.round(120 * (1f - t / 0.55f));
                float innerRadius = (8f + 18f * t) * density;
                ringPaint.setColor(Color.argb(innerAlpha, Color.red(point.color),
                        Color.green(point.color), Color.blue(point.color)));
                canvas.drawCircle(p.x, p.y, innerRadius, ringPaint);
            }
        }
        mapView.postInvalidateOnAnimation();
    }
}
