package com.moura.downloads;

import android.app.Application;

import com.yausername.aria2c.Aria2c;
import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MouraApplication extends Application {
    private final ExecutorService engineExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        engineExecutor.execute(() -> {
            try {
                YoutubeDL.getInstance().init(this);
                FFmpeg.getInstance().init(this);
                Aria2c.getInstance().init(this);
            } catch (Exception ignored) {
                // O serviço tenta novamente e mostra uma mensagem clara se o aparelho não suportar.
            }
        });
    }
}
