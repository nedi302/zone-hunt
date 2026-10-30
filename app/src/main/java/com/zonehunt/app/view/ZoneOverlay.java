package com.zonehunt.app.view;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Point;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;

/**
 * Draws the current zone and the next zone. The current zone is solid, the
 * next one is dashed orange (like the countdown on the HUD).
 */
public class ZoneOverlay extends Overlay {

    private static final float MAX_PIXEL_RADIUS = 60_000f;

    private final float density;
    private final Paint currentFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint currentStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint currentHalo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nextFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nextStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint currentCenterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nextCenterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private GeoPoint currentCenter;
    private int currentRadiusMeters;
    private GeoPoint nextCenter;
    private int nextRadiusMeters;
    private boolean danger;

    public ZoneOverlay(float density) {
        this.density = density;

        currentStroke.setStyle(Paint.Style.STROKE);
        currentStroke.setStrokeWidth(2.2f * density);

        currentHalo.setStyle(Paint.Style.STROKE);
        currentHalo.setStrokeWidth(1.0f * density);
        currentHalo.setPathEffect(new DashPathEffect(new float[]{7 * density, 7 * density}, 0));

        nextStroke.setStyle(Paint.Style.STROKE);
        nextStroke.setStrokeWidth(1.8f * density);
        nextStroke.setPathEffect(new DashPathEffect(new float[]{8 * density, 6 * density}, 0));

        currentCenterPaint.setStyle(Paint.Style.STROKE);
        currentCenterPaint.setStrokeWidth(1.5f * density);
        nextCenterPaint.setStyle(Paint.Style.STROKE);
        nextCenterPaint.setStrokeWidth(1.2f * density);

        updateColors();
    }

    public void setZones(GeoPoint currentCenter, int currentRadiusMeters,
                         GeoPoint nextCenter, int nextRadiusMeters) {
        this.currentCenter = currentCenter;
        this.currentRadiusMeters = Math.max(0, currentRadiusMeters);
        this.nextCenter = nextCenter;
        this.nextRadiusMeters = Math.max(0, nextRadiusMeters);
    }

    public void setDanger(boolean danger) {
        if (this.danger != danger) {
            this.danger = danger;
            updateColors();
        }
    }

    private void updateColors() {
        int r;
        int g;
        int b;
        if (danger) {
            r = 255;
            g = 94;
            b = 108;
        } else {
            r = 215;
            g = 255;
            b = 95;
        }
        currentFill.setStyle(Paint.Style.FILL);
        currentFill.setColor(Color.argb(danger ? 35 : 28, r, g, b));
        currentStroke.setColor(Color.argb(230, r, g, b));
        currentHalo.setColor(Color.argb(90, r, g, b));
        currentCenterPaint.setColor(Color.argb(200, r, g, b));

        int nr = 255;
        int ng = 139;
        int nb = 76;
        nextFill.setStyle(Paint.Style.FILL);
        nextFill.setColor(Color.argb(14, nr, ng, nb));
        nextStroke.setColor(Color.argb(225, nr, ng, nb));
        nextCenterPaint.setColor(Color.argb(210, nr, ng, nb));
    }

    @Override
    public void draw(Canvas canvas, MapView mapView, boolean shadow) {
        if (shadow) {
            return;
        }
        if (currentCenter != null && currentRadiusMeters > 0) {
            drawZone(canvas, mapView, currentCenter, currentRadiusMeters,
                    currentFill, currentStroke, currentHalo);
        }
        if (nextCenter != null && nextRadiusMeters > 0 && !sameZone()) {
            drawZone(canvas, mapView, nextCenter, nextRadiusMeters,
                    nextFill, nextStroke, null);
            drawCenter(canvas, mapView, nextCenter, nextCenterPaint);
        }
        if (currentCenter != null) {
            drawCenter(canvas, mapView, currentCenter, currentCenterPaint);
        }
    }

    private boolean sameZone() {
        return currentCenter != null && nextCenter != null
                && currentRadiusMeters == nextRadiusMeters
                && Math.abs(currentCenter.getLatitude() - nextCenter.getLatitude()) < 1e-7
                && Math.abs(currentCenter.getLongitude() - nextCenter.getLongitude()) < 1e-7;
    }

    private void drawZone(Canvas canvas, MapView mapView, GeoPoint center, int radiusMeters,
                          Paint fill, Paint stroke, Paint halo) {
        Point p = new Point();
        mapView.getProjection().toPixels(center, p);
        float pixelRadius = pixelRadius(mapView, center, p, radiusMeters);
        if (pixelRadius < 1) {
            return;
        }
        canvas.drawCircle(p.x, p.y, pixelRadius, fill);
        canvas.drawCircle(p.x, p.y, pixelRadius, stroke);
        if (halo != null) {
            canvas.drawCircle(p.x, p.y, pixelRadius + 6 * density, halo);
        }
    }

    private float pixelRadius(MapView mapView, GeoPoint center, Point centerPixel, int radiusMeters) {
        double lonOffset = radiusMeters / (111_320.0 * Math.max(0.2,
                Math.cos(Math.toRadians(center.getLatitude()))));
        GeoPoint east = new GeoPoint(center.getLatitude(), center.getLongitude() + lonOffset);
        Point e = new Point();
        mapView.getProjection().toPixels(east, e);
        float radius = Math.abs(e.x - centerPixel.x);
        return Math.min(radius, MAX_PIXEL_RADIUS);
    }

    private void drawCenter(Canvas canvas, MapView mapView, GeoPoint center, Paint paint) {
        Point p = new Point();
        mapView.getProjection().toPixels(center, p);
        canvas.drawCircle(p.x, p.y, 3.5f * density, paint);
        canvas.drawLine(p.x - 8 * density, p.y, p.x + 8 * density, p.y, paint);
        canvas.drawLine(p.x, p.y - 8 * density, p.x, p.y + 8 * density, paint);
    }
}
