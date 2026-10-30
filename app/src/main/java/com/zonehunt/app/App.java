package com.zonehunt.app;

import android.app.Application;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;

import java.io.File;

import com.zonehunt.app.net.GameClient;
import com.zonehunt.app.util.NotificationHelper;

public class App extends Application {

    private GameClient gameClient;

    @Override
    public void onCreate() {
        super.onCreate();
        File base = new File(getCacheDir(), "osmdroid");
        File tiles = new File(base, "tiles");
        //noinspection ResultOfMethodCallIgnored
        tiles.mkdirs();
        IConfigurationProvider config = Configuration.getInstance();
        config.load(this, getSharedPreferences("osmdroid", MODE_PRIVATE));
        config.setOsmdroidBasePath(base);
        config.setOsmdroidTileCache(tiles);
        config.setUserAgentValue(BuildConfig.APPLICATION_ID + "/" + BuildConfig.VERSION_NAME);
        gameClient = new GameClient(this);
        NotificationHelper.createChannels(this);
    }

    public GameClient client() {
        return gameClient;
    }
}
