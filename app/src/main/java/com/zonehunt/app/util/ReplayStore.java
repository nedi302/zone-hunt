package com.zonehunt.app.util;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.zonehunt.app.model.GameReplay;

/**
 * App-private storage for round recordings. Files live in files/replays and
 * never leave the device.
 */
public final class ReplayStore {

    private static final String DIR = "replays";
    private static final int MAX_REPLAYS = 50;

    private ReplayStore() {
    }

    private static File dir(Context context) {
        File dir = new File(context.getFilesDir(), DIR);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    public static String save(Context context, GameReplay replay) throws IOException, JSONException {
        if (replay == null) {
            throw new IOException("Replay fehlt");
        }
        String id = sanitize(replay.id);
        if (id.isEmpty()) {
            id = "replay-" + System.currentTimeMillis();
        }
        replay.id = id;
        File target = new File(dir(context), id + ".json");
        File temp = new File(dir(context), id + ".tmp");
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(temp), StandardCharsets.UTF_8)) {
            writer.write(replay.toJson().toString());
        }
        if (!temp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new IOException("Replay konnte nicht gespeichert werden");
        }
        prune(context);
        return id;
    }

    public static List<GameReplay> list(Context context) {
        List<GameReplay> result = new ArrayList<>();
        File[] files = dir(context).listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) {
            return result;
        }
        for (File file : files) {
            GameReplay replay = read(file);
            if (replay != null) {
                result.add(replay);
            }
        }
        result.sort((a, b) -> Long.compare(b.startedAt, a.startedAt));
        return result;
    }

    public static GameReplay load(Context context, String id) {
        String clean = sanitize(id);
        if (clean.isEmpty()) {
            return null;
        }
        File file = new File(dir(context), clean + ".json");
        if (!file.exists()) {
            return null;
        }
        return read(file);
    }

    public static boolean delete(Context context, String id) {
        String clean = sanitize(id);
        if (clean.isEmpty()) {
            return false;
        }
        File dir = dir(context);
        boolean deleted = new File(dir, clean + ".json").delete();
        //noinspection ResultOfMethodCallIgnored
        new File(dir, clean + ".tmp").delete();
        return deleted;
    }

    public static int count(Context context) {
        File[] files = dir(context).listFiles((d, name) -> name.endsWith(".json"));
        return files == null ? 0 : files.length;
    }

    private static GameReplay read(File file) {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                text.append(buffer, 0, read);
            }
            GameReplay replay = GameReplay.fromJson(new JSONObject(text.toString()));
            if (replay.id == null || replay.id.isEmpty()) {
                replay.id = file.getName().replace(".json", "");
            }
            return replay;
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    private static void prune(Context context) {
        File[] files = dir(context).listFiles((d, name) -> name.endsWith(".json"));
        if (files == null || files.length <= MAX_REPLAYS) {
            return;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        int remove = files.length - MAX_REPLAYS;
        for (int i = 0; i < remove; i++) {
            //noinspection ResultOfMethodCallIgnored
            files[i].delete();
        }
    }

    private static String sanitize(String id) {
        if (id == null) {
            return "";
        }
        return id.replaceAll("[^A-Za-z0-9_-]", "");
    }
}
