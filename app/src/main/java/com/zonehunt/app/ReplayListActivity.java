package com.zonehunt.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.zonehunt.app.model.GameReplay;
import com.zonehunt.app.model.GameSettings;
import com.zonehunt.app.util.Geo;
import com.zonehunt.app.util.ReplayStore;
import com.zonehunt.app.util.ReplayTexts;

public class ReplayListActivity extends AppCompatActivity {

    private LinearLayout replayList;
    private View emptyState;
    private TextView txtCount;
    private int loadToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_replays);

        replayList = findViewById(R.id.replayList);
        emptyState = findViewById(R.id.emptyReplays);
        txtCount = findViewById(R.id.txtReplayCount);

        findViewById(R.id.btnReplayListBack).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (replayList != null) {
            loadReplays();
        }
    }

    private void loadReplays() {
        final int token = ++loadToken;
        new Thread(() -> {
            List<GameReplay> replays = ReplayStore.list(getApplicationContext());
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || token != loadToken) {
                    return;
                }
                render(replays);
            });
        }, "replay-list").start();
    }

    private void render(List<GameReplay> replays) {
        replayList.removeAllViews();
        txtCount.setText(String.valueOf(replays.size()));
        emptyState.setVisibility(replays.isEmpty() ? View.VISIBLE : View.GONE);

        for (GameReplay replay : replays) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_replay, replayList, false);
            TextView date = row.findViewById(R.id.txtReplayDate);
            TextView details = row.findViewById(R.id.txtReplayDetails);
            TextView result = row.findViewById(R.id.txtReplayResult);
            MaterialButton delete = row.findViewById(R.id.btnDeleteReplay);

            long timestamp = replay.startedAt > 0 ? replay.startedAt : replay.endedAt;
            date.setText(formatDate(timestamp));
            details.setText(getString(R.string.replay_details, replay.code,
                    Geo.formatClock(replay.durationMs),
                    getResources().getQuantityString(R.plurals.players_count,
                            replay.players.size(), replay.players.size()))
                    + " · " + ReplayTexts.modeLabel(this, replay.mode));
            result.setText(resultText(replay));

            row.setOnClickListener(v -> {
                Intent intent = new Intent(this, ReplayActivity.class);
                intent.putExtra(ReplayActivity.EXTRA_REPLAY_ID, replay.id);
                startActivity(intent);
            });
            delete.setOnClickListener(v -> confirmDelete(replay));
            replayList.addView(row);
        }
    }

    private String resultText(GameReplay replay) {
        String outcome = ReplayTexts.outcome(this, replay);
        boolean classic = GameSettings.MODE_CLASSIC.equals(
                GameSettings.normalizeMode(replay.mode));
        if (classic && ("SEEKER".equals(replay.winner) || "HIDERS".equals(replay.winner))) {
            GameReplay.PlayerTrack seeker = replay.seeker();
            String seekerName = seeker == null ? "—" : seeker.name;
            return getString(R.string.replay_result_prefix, seekerName) + " · " + outcome;
        }
        return outcome;
    }

    private void confirmDelete(GameReplay replay) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.replay_delete_title)
                .setMessage(getString(R.string.replay_delete_message, formatDate(replay.startedAt)))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    if (ReplayStore.delete(this, replay.id)) {
                        Toast.makeText(this, R.string.replay_deleted, Toast.LENGTH_SHORT).show();
                        loadReplays();
                    } else {
                        Toast.makeText(this, R.string.replay_delete_failed, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String formatDate(long millis) {
        if (millis <= 0) {
            return getString(R.string.replay_date_unknown);
        }
        String pattern = android.text.format.DateFormat.getBestDateTimePattern(
                Locale.getDefault(), "yMMMd HH:mm");
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(millis));
    }
}
