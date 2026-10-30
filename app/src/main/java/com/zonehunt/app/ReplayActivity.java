package com.zonehunt.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.zonehunt.app.model.GameReplay;
import com.zonehunt.app.model.GameSettings;
import com.zonehunt.app.model.ZoneGeometry;
import com.zonehunt.app.util.Geo;
import com.zonehunt.app.util.ReplayStore;
import com.zonehunt.app.util.ReplayTexts;
import com.zonehunt.app.view.ReplayDotDrawable;
import com.zonehunt.app.view.ZoneOverlay;

public class ReplayActivity extends AppCompatActivity {

    public static final String EXTRA_REPLAY_ID = "replay_id";

    private static final int[] HIDER_COLORS = {
            0xFF5DE8B6, 0xFF63B9FF, 0xFFD7FF5F, 0xFFFF5E6C,
            0xFFC79BFF, 0xFFFFD166, 0xFF4DD0E1, 0xFFFF9EC4
    };

    private static class TrackView {
        GameReplay.PlayerTrack track;
        List<GameReplay.Sample> samples;
        int color;
        Polyline line;
        Polyline shadow;
        Marker marker;
    }

    private final List<TrackView> tracks = new ArrayList<>();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private GameReplay replay;
    private MapView map;
    private ZoneOverlay zoneOverlay;
    private ZoneGeometry zoneGeometry;
    private LinearLayout legend;
    private SeekBar seekBar;
    private MaterialButton btnPlay;
    private MaterialButton btnSpeed;
    private TextView txtElapsed;
    private TextView txtTotal;
    private TextView txtReplayZone;

    private long durationMs = 1;
    private long currentMs;
    private long playBaseWall;
    private boolean playing;
    private int speed = 1;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!playing) {
                return;
            }
            long now = SystemClock.elapsedRealtime();
            currentMs = (long) ((now - playBaseWall) * speed);
            if (currentMs >= durationMs) {
                currentMs = durationMs;
                render(currentMs);
                pausePlayback();
                return;
            }
            render(currentMs);
            ui.postDelayed(this, 80L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_replay);

        String id = getIntent().getStringExtra(EXTRA_REPLAY_ID);
        replay = ReplayStore.load(this, id);
        if (replay == null) {
            Toast.makeText(this, R.string.replay_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        durationMs = Math.max(1000, Math.max(replay.durationMs, replay.maxSampleTime()));

        map = findViewById(R.id.replayMap);
        legend = findViewById(R.id.replayLegend);
        seekBar = findViewById(R.id.seekReplay);
        btnPlay = findViewById(R.id.btnReplayPlay);
        btnSpeed = findViewById(R.id.btnReplaySpeed);
        txtElapsed = findViewById(R.id.txtReplayElapsed);
        txtTotal = findViewById(R.id.txtReplayTotal);
        txtReplayZone = findViewById(R.id.txtReplayZone);
        TextView txtTitle = findViewById(R.id.txtReplayTitle);
        TextView txtMeta = findViewById(R.id.txtReplayMeta);
        TextView txtResult = findViewById(R.id.txtReplayResult);
        TextView txtAttribution = findViewById(R.id.txtReplayAttribution);

        txtTotal.setText(Geo.formatClock(durationMs));
        long timestamp = replay.startedAt > 0 ? replay.startedAt : replay.endedAt;
        txtTitle.setText(timestamp > 0
                ? getString(R.string.replay_hunt_from, formatDate(timestamp))
                : getString(R.string.replay_recorded));
        String meta = getString(R.string.replay_meta, replay.code,
                getResources().getQuantityString(R.plurals.players_count,
                        replay.players.size(), replay.players.size()),
                Geo.formatClock(durationMs));
        txtMeta.setText(meta + " · " + ReplayTexts.modeLabel(this, replay.mode));
        if (txtResult != null) {
            txtResult.setText(ReplayTexts.outcome(this, replay));
        }

        long huntingDuration = replay.roundDurationMs > 0
                ? replay.roundDurationMs
                : Math.max(0, replay.durationMs - Math.max(0, replay.hidePhaseMs));
        boolean legacyZone = replay.zoneSeed == Long.MIN_VALUE;
        long zoneSeed = legacyZone ? 0L : replay.zoneSeed;
        zoneGeometry = new ZoneGeometry(replay.centerLat, replay.centerLng,
                Math.max(GameSettings.ABSOLUTE_MIN_RADIUS, replay.initialRadiusMeters),
                Math.max(0, replay.shrinkMetersPerMinute), huntingDuration, zoneSeed,
                !legacyZone);

        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setBuiltInZoomControls(false);
        map.setTilesScaledToDpi(true);
        map.setMinZoomLevel(3.0);
        map.setMaxZoomLevel(20.0);
        map.getController().setZoom(16.0);
        map.getController().setCenter(new GeoPoint(replay.centerLat, replay.centerLng));

        float density = getResources().getDisplayMetrics().density;
        zoneOverlay = new ZoneOverlay(density);
        ZoneGeometry.Frame frame = zoneGeometry.frameAt(0);
        zoneOverlay.setZones(
                new GeoPoint(frame.current.lat, frame.current.lng), frame.current.radiusMeters,
                new GeoPoint(frame.next.lat, frame.next.lng), frame.next.radiusMeters);
        map.getOverlays().add(zoneOverlay);

        buildTracks(density);
        buildLegend(density);

        findViewById(R.id.btnReplayBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnReplayFit).setOnClickListener(v -> fitAll());
        btnPlay.setOnClickListener(v -> togglePlay());
        btnSpeed.setOnClickListener(v -> cycleSpeed());
        txtAttribution.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.openstreetmap.org/copyright")));
            } catch (Exception ignored) {
            }
        });

        seekBar.setMax(1000);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentMs = durationMs * progress / 1000L;
                    render(currentMs);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                pausePlayback();
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });

        render(0);
        updatePlayUi();
        map.postDelayed(this::fitAll, 650L);
    }

    private void buildTracks(float density) {
        int hiderIndex = 0;
        for (GameReplay.PlayerTrack track : replay.players) {
            TrackView view = new TrackView();
            view.track = track;
            view.samples = cleanSamples(track);
            boolean seeker = "SEEKER".equals(track.role);
            if (seeker) {
                view.color = ContextCompat.getColor(this, R.color.orange);
            } else {
                view.color = HIDER_COLORS[hiderIndex % HIDER_COLORS.length];
                hiderIndex++;
            }

            if (view.samples.size() >= 2) {
                List<GeoPoint> points = new ArrayList<>(view.samples.size());
                for (GameReplay.Sample sample : view.samples) {
                    points.add(new GeoPoint(sample.lat, sample.lng));
                }

                Polyline shadow = new Polyline(map);
                shadow.setPoints(points);
                shadow.setGeodesic(false);
                Paint shadowPaint = shadow.getOutlinePaint();
                shadowPaint.setStyle(Paint.Style.STROKE);
                shadowPaint.setColor(Color.argb(120, 7, 18, 12));
                shadowPaint.setStrokeWidth((seeker ? 9.5f : 8f) * density);
                shadowPaint.setStrokeCap(Paint.Cap.ROUND);
                shadowPaint.setStrokeJoin(Paint.Join.ROUND);
                view.shadow = shadow;

                Polyline line = new Polyline(map);
                line.setPoints(points);
                line.setGeodesic(false);
                Paint paint = line.getOutlinePaint();
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(Color.argb(225, Color.red(view.color), Color.green(view.color),
                        Color.blue(view.color)));
                paint.setStrokeWidth((seeker ? 6f : 4.5f) * density);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                line.setTitle(track.name);
                view.line = line;
            }

            Marker marker = new Marker(map);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            marker.setIcon(new ReplayDotDrawable(density, track.name, view.color));
            marker.setTitle(track.name + " · " + getString(
                    ReplayTexts.roleLabelRes(replay.mode, track.role)));
            marker.setEnabled(false);
            view.marker = marker;
            tracks.add(view);
        }

        // Dark underlay first, coloured tracks on top, markers last.
        for (TrackView view : tracks) {
            if (view.shadow != null) {
                map.getOverlays().add(view.shadow);
            }
        }
        for (TrackView view : tracks) {
            if (view.line != null) {
                map.getOverlays().add(view.line);
            }
        }
        for (TrackView view : tracks) {
            map.getOverlays().add(view.marker);
        }
    }

    /**
     * GPS fixes can jump wildly, which used to draw long zig-zag lines all
     * over the replay. Drop impossible jumps and smooth the remaining path.
     */
    private List<GameReplay.Sample> cleanSamples(GameReplay.PlayerTrack track) {
        List<GameReplay.Sample> kept = new ArrayList<>();
        boolean hasCenter = Math.abs(replay.centerLat) > 0.0001
                || Math.abs(replay.centerLng) > 0.0001;
        double maxDistance = Math.max(3000.0, replay.initialRadiusMeters * 10.0);

        int startIndex = 0;
        if (track.samples.size() >= 3) {
            GameReplay.Sample first = track.samples.get(0);
            GameReplay.Sample second = track.samples.get(1);
            GameReplay.Sample third = track.samples.get(2);
            long dtFirst = second.t - first.t;
            long dtSecond = third.t - second.t;
            if (dtFirst > 0 && dtSecond > 0) {
                double jump = Geo.distanceMeters(first.lat, first.lng,
                        second.lat, second.lng);
                double later = Geo.distanceMeters(second.lat, second.lng,
                        third.lat, third.lng);
                if (jump > 20 && jump / (dtFirst / 1000.0) > 25.0
                        && later / (dtSecond / 1000.0) <= 15.0) {
                    // A stale last-known fix right at the start; the next
                    // two samples agree with each other.
                    startIndex = 1;
                }
            }
        }

        for (int i = startIndex; i < track.samples.size(); i++) {
            GameReplay.Sample sample = track.samples.get(i);
            if (sample.lat < -90 || sample.lat > 90
                    || sample.lng < -180 || sample.lng > 180) {
                continue;
            }
            if (Math.abs(sample.lat) < 0.0001 && Math.abs(sample.lng) < 0.0001) {
                continue;
            }
            if (hasCenter && Geo.distanceMeters(replay.centerLat, replay.centerLng,
                    sample.lat, sample.lng) > maxDistance) {
                continue;
            }
            if (kept.isEmpty()) {
                kept.add(sample);
                continue;
            }
            GameReplay.Sample previous = kept.get(kept.size() - 1);
            long deltaMs = sample.t - previous.t;
            if (deltaMs <= 0) {
                continue;
            }
            double distance = Geo.distanceMeters(previous.lat, previous.lng,
                    sample.lat, sample.lng);
            double speed = distance / Math.max(0.001, deltaMs / 1000.0);
            if (distance > 8 && speed > 25.0) {
                continue;
            }
            if (distance < 0.75 && deltaMs < 8000) {
                continue;
            }
            kept.add(sample);
        }

        if (kept.size() < 3) {
            return kept;
        }
        List<GameReplay.Sample> smoothed = new ArrayList<>(kept.size());
        smoothed.add(kept.get(0));
        for (int i = 1; i < kept.size() - 1; i++) {
            GameReplay.Sample before = kept.get(i - 1);
            GameReplay.Sample current = kept.get(i);
            GameReplay.Sample after = kept.get(i + 1);
            long gapBefore = current.t - before.t;
            long gapAfter = after.t - current.t;
            if (gapBefore > 15000 || gapAfter > 15000) {
                smoothed.add(current);
                continue;
            }
            double lat = before.lat * 0.2 + current.lat * 0.6 + after.lat * 0.2;
            double lng = before.lng * 0.2 + current.lng * 0.6 + after.lng * 0.2;
            smoothed.add(new GameReplay.Sample(current.t, lat, lng));
        }
        smoothed.add(kept.get(kept.size() - 1));
        return smoothed;
    }

    private void buildLegend(float density) {
        legend.removeAllViews();
        for (TrackView view : tracks) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(0, 0, Math.round(14 * density), 0);

            View dot = new View(this);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(view.color);
            shape.setStroke(Math.round(density), Color.argb(120, 245, 249, 244));
            dot.setBackground(shape);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                    Math.round(9 * density), Math.round(9 * density));
            item.addView(dot, dotParams);

            TextView label = new TextView(this);
            int suffixRes = ReplayTexts.legendSuffixRes(replay.mode, view.track.role);
            label.setText(view.track.name + (suffixRes == 0 ? "" : getString(suffixRes)));
            label.setTextColor(ContextCompat.getColor(this, R.color.text_soft));
            label.setTextSize(11f);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelParams.setMarginStart(Math.round(5 * density));
            item.addView(label, labelParams);
            legend.addView(item);
        }
    }

    private void render(long timeMs) {
        currentMs = Math.max(0, Math.min(durationMs, timeMs));
        for (TrackView view : tracks) {
            int index = indexAt(view.samples, currentMs);
            if (index < 0) {
                view.marker.setEnabled(false);
                continue;
            }
            GameReplay.Sample sample = view.samples.get(index);
            view.marker.setPosition(new GeoPoint(sample.lat, sample.lng));
            view.marker.setEnabled(true);
        }

        long huntingMs = Math.max(0, currentMs - Math.max(0, replay.hidePhaseMs));
        ZoneGeometry.Frame frame = zoneGeometry.frameAt(huntingMs);
        zoneOverlay.setZones(
                new GeoPoint(frame.current.lat, frame.current.lng), frame.current.radiusMeters,
                new GeoPoint(frame.next.lat, frame.next.lng), frame.next.radiusMeters);
        zoneOverlay.setDanger(false);

        String zoneText = getString(R.string.replay_zone_now_next,
                Geo.formatDistance(frame.current.radiusMeters),
                Geo.formatDistance(frame.next.radiusMeters));
        long until = frame.remainingMs();
        if (currentMs < replay.hidePhaseMs) {
            until += replay.hidePhaseMs - currentMs;
        }
        boolean stays = replay.shrinkMetersPerMinute <= 0
                || frame.next.radiusMeters >= frame.current.radiusMeters;
        if (stays) {
            zoneText += getString(R.string.replay_zone_stays);
        } else if (frame.lastStage) {
            zoneText += until > 0
                    ? getString(R.string.replay_zone_endzone, Geo.formatClock(until))
                    : getString(R.string.replay_zone_endzone_no_time);
        } else {
            zoneText += getString(R.string.replay_zone_in, Geo.formatClock(until));
        }
        if (txtReplayZone != null) {
            txtReplayZone.setText(zoneText);
        }

        txtElapsed.setText(Geo.formatClock(currentMs));
        seekBar.setProgress((int) (currentMs * 1000L / Math.max(1, durationMs)));
        map.invalidate();
    }

    private void togglePlay() {
        if (playing) {
            pausePlayback();
            return;
        }
        if (currentMs >= durationMs - 100L) {
            currentMs = 0;
            render(0);
        }
        playing = true;
        playBaseWall = SystemClock.elapsedRealtime() - (long) (currentMs / (double) speed);
        updatePlayUi();
        ui.post(tickRunnable);
    }

    private void pausePlayback() {
        playing = false;
        ui.removeCallbacks(tickRunnable);
        updatePlayUi();
    }

    private void updatePlayUi() {
        if (btnPlay == null) {
            return;
        }
        btnPlay.setText(playing ? R.string.replay_pause : R.string.replay_play);
        btnPlay.setIconResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    private void cycleSpeed() {
        speed = speed == 1 ? 2 : speed == 2 ? 4 : 1;
        btnSpeed.setText(speed + "×");
        if (playing) {
            playBaseWall = SystemClock.elapsedRealtime() - (long) (currentMs / (double) speed);
        }
    }

    private void fitAll() {
        int initialRadius = Math.max(50, replay.initialRadiusMeters);
        double initialLatOff = Geo.latitudeOffset(initialRadius);
        double initialLngOff = Geo.longitudeOffset(replay.centerLat, initialRadius);
        double minLat = replay.centerLat - initialLatOff;
        double maxLat = replay.centerLat + initialLatOff;
        double minLng = replay.centerLng - initialLngOff;
        double maxLng = replay.centerLng + initialLngOff;
        for (TrackView view : tracks) {
            for (GameReplay.Sample sample : view.samples) {
                minLat = Math.min(minLat, sample.lat);
                maxLat = Math.max(maxLat, sample.lat);
                minLng = Math.min(minLng, sample.lng);
                maxLng = Math.max(maxLng, sample.lng);
            }
        }
        double centerLat = (minLat + maxLat) / 2.0;
        double centerLng = (minLng + maxLng) / 2.0;
        double latSpan = Math.max(0.0009, (maxLat - minLat) * 1.35);
        double lngSpan = Math.max(0.0009, (maxLng - minLng) * 1.35);
        BoundingBox box = new BoundingBox(
                centerLat + latSpan / 2.0,
                centerLng + lngSpan / 2.0,
                centerLat - latSpan / 2.0,
                centerLng - lngSpan / 2.0
        );
        map.zoomToBoundingBox(box, true);
        map.invalidate();
    }

    private int indexAt(List<GameReplay.Sample> samples, long t) {
        int low = 0;
        int high = samples.size() - 1;
        int result = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (samples.get(mid).t <= t) {
                result = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return result;
    }

    private String formatDate(long millis) {
        if (millis <= 0) {
            return getString(R.string.replay_unknown);
        }
        String pattern = android.text.format.DateFormat.getBestDateTimePattern(
                Locale.getDefault(), "yMMMd");
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(millis));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) {
            map.onResume();
        }
    }

    @Override
    protected void onPause() {
        pausePlayback();
        if (map != null) {
            map.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        if (map != null) {
            map.onDetach();
        }
        super.onDestroy();
    }
}
