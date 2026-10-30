package com.zonehunt.app;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.zonehunt.app.model.LobbySnapshot;
import com.zonehunt.app.net.GameClient;
import com.zonehunt.app.util.UiUtils;

public class JoinGameActivity extends AppCompatActivity implements GameClient.Listener {

    private EditText inputCode;
    private TextInputEditText inputName;
    private TextInputLayout nameLayout;
    private View overlay;
    private TextView overlayStatus;
    private View progressRow;
    private TextView progressText;
    private boolean connecting;
    private boolean launchedLobby;
    private String playerName;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_game);

        playerName = getIntent().getStringExtra("player_name");
        if (playerName == null) {
            playerName = "";
        }
        inputCode = findViewById(R.id.inputCode);
        inputName = findViewById(R.id.inputName);
        nameLayout = findViewById(R.id.nameLayout);
        overlay = findViewById(R.id.connectingOverlay);
        overlayStatus = findViewById(R.id.txtOverlayStatus);
        progressRow = findViewById(R.id.progressRow);
        progressText = findViewById(R.id.txtProgress);
        inputName.setText(playerName);

        findViewById(R.id.btnBack).setOnClickListener(v -> cancelAndFinish());
        findViewById(R.id.btnJoinLobby).setOnClickListener(v -> beginJoinFlow());

        inputCode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String digits = s.toString().replaceAll("\\D", "");
                if (digits.length() > 4) {
                    digits = digits.substring(0, 4);
                }
                if (!digits.equals(s.toString())) {
                    inputCode.setText(digits);
                    inputCode.setSelection(digits.length());
                }
            }
        });

        inputName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                nameLayout.setError(null);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            boolean fine = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
            if (fine) {
                performJoin();
            } else {
                showPermissionDialog();
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                cancelAndFinish();
            }
        });

        inputCode.requestFocus();
    }

    private void beginJoinFlow() {
        if (connecting) {
            return;
        }
        String code = inputCode.getText().toString().replaceAll("\\D", "");
        String name = inputName.getText() == null ? "" : inputName.getText().toString().trim();
        if (code.length() != 4) {
            inputCode.requestFocus();
            Toast.makeText(this, R.string.join_need_code, Toast.LENGTH_SHORT).show();
            return;
        }
        if (name.length() < 2) {
            nameLayout.setError(getString(R.string.join_name_too_short));
            inputName.requestFocus();
            return;
        }
        playerName = name;
        if (!hasLocationPermission()) {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
            return;
        }
        if (!GameClient.isLocationEnabled(this)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.location_off_title)
                    .setMessage(R.string.location_off_message)
                    .setPositiveButton(R.string.permission_settings, (d, w) -> {
                        try {
                            startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        } catch (Exception ignored) {
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }
        performJoin();
    }

    private void performJoin() {
        String code = inputCode.getText().toString().replaceAll("\\D", "");
        UiUtils.hideKeyboard(inputCode);
        connecting = true;
        overlay.setVisibility(View.VISIBLE);
        overlayStatus.setText(getString(R.string.join_searching, code));
        progressRow.setVisibility(View.VISIBLE);
        progressText.setText(R.string.join_connecting_server);
        ((App) getApplication()).client().setListener(this);
        ((App) getApplication()).client().joinGame(playerName, code, this);

        timeoutHandler.postDelayed(() -> {
            if (connecting && !launchedLobby && !isFinishing()) {
                connecting = false;
                overlay.setVisibility(View.GONE);
                progressRow.setVisibility(View.GONE);
                ((App) getApplication()).client().shutdown();
                showError(getString(R.string.join_code_not_found));
            }
        }, 18000);
    }

    @Override
    public void onLobbyChanged(LobbySnapshot snapshot) {
        if (snapshot == null || launchedLobby || connecting == false || isFinishing()) {
            return;
        }
        if (snapshot.hasPlayer(((App) getApplication()).client().getPlayerId())
                && LobbySnapshot.STATE_LOBBY.equals(snapshot.state)) {
            launchedLobby = true;
            connecting = false;
            timeoutHandler.removeCallbacksAndMessages(null);
            overlayStatus.setText(R.string.join_joined);
            startActivity(new Intent(this, LobbyActivity.class));
            finish();
        }
    }

    @Override
    public void onError(String message) {
        if (launchedLobby || isFinishing()) {
            return;
        }
        connecting = false;
        overlay.setVisibility(View.GONE);
        progressRow.setVisibility(View.GONE);
        showError(message);
    }

    @Override
    public void onConnectionChanged(boolean connected) {
        if (connecting) {
            overlayStatus.setText(connected
                    ? getString(R.string.join_searching_lobby)
                    : getString(R.string.game_reconnecting));
            progressText.setText(connected
                    ? getString(R.string.join_connected_searching)
                    : getString(R.string.game_reconnecting));
        }
    }

    private void showError(String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.join_not_possible_title)
                .setMessage(message)
                .setPositiveButton(R.string.retry, (d, w) -> {
                    connecting = false;
                    beginJoinFlow();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void cancelAndFinish() {
        timeoutHandler.removeCallbacksAndMessages(null);
        ((App) getApplication()).client().clearListener(this);
        ((App) getApplication()).client().shutdown();
        finish();
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void showPermissionDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permission_title)
                .setMessage(R.string.permission_message)
                .setPositiveButton(R.string.permission_settings, (d, w) -> {
                    Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    i.setData(android.net.Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        timeoutHandler.removeCallbacksAndMessages(null);
        ((App) getApplication()).client().clearListener(this);
        super.onDestroy();
    }
}
