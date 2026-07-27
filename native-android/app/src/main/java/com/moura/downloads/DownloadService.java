package com.moura.downloads;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import com.yausername.aria2c.Aria2c;
import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import org.json.JSONObject;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadService extends Service {
    public static final String ACTION_START = "com.moura.downloads.START_LOCAL_DOWNLOAD";
    public static final String ACTION_CANCEL = "com.moura.downloads.CANCEL_LOCAL_DOWNLOAD";
    public static final String ACTION_DOWNLOAD_EVENT = "com.moura.downloads.DOWNLOAD_EVENT";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_FORMAT = "format";
    public static final String EXTRA_CATEGORY = "category";
    public static final String EXTRA_QUALITY = "quality";
    public static final String EXTRA_PAYLOAD = "payload";

    private static final String CHANNEL_ID = "moura_downloads";
    private static final int NOTIFICATION_ID = 2201;
    private static final String PREFS = "moura_library";
    private static final String DOWNLOAD_PROCESS_ID = "moura-active-download";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running;
    private volatile boolean cancelRequested;

    public static File getOutputDirectory(Context context) {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File dir = new File(downloads, "Moura Downloads");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (ACTION_CANCEL.equals(intent.getAction())) {
            if (running) {
                cancelRequested = true;
                YoutubeDL.getInstance().destroyProcessById(DOWNLOAD_PROCESS_ID);
                updateNotification("Cancelando download", 0, true, true);
                sendEvent("cancelling", 0, 0,
                        "Cancelando e removendo arquivos temporários.");
            }
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(intent.getAction())) return START_NOT_STICKY;
        if (running) {
            sendEvent("error", 0, 0, "Já existe um download em andamento.");
            return START_NOT_STICKY;
        }
        String url = intent.getStringExtra(EXTRA_URL);
        String format = intent.getStringExtra(EXTRA_FORMAT);
        String category = intent.getStringExtra(EXTRA_CATEGORY);
        String quality = intent.getStringExtra(EXTRA_QUALITY);
        if (url == null || (!url.startsWith("https://") && !url.startsWith("http://"))) {
            sendEvent("error", 0, 0, "Link inválido.");
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        running = true;
        cancelRequested = false;
        startAsForeground(buildNotification("Preparando download", 0, true, true));
        executor.execute(() -> runDownload(url, "mp3".equalsIgnoreCase(format) ? "mp3" : "mp4",
                category == null || category.trim().isEmpty() ? "Outros" : category,
                "best".equalsIgnoreCase(quality) || "data".equalsIgnoreCase(quality)
                        ? quality.toLowerCase(Locale.ROOT) : "fast",
                startId));
        return START_NOT_STICKY;
    }

    private void runDownload(String url, String format, String category, String quality, int startId) {
        File outputDir = getOutputDirectory(this);
        Set<String> before = new HashSet<>();
        File[] existing = outputDir.listFiles();
        if (existing != null) for (File file : existing) before.add(file.getAbsolutePath());
        long startedAt = System.currentTimeMillis();

        try {
            sendEvent("initializing", 1, 0, "Preparando o processador local no celular.");
            YoutubeDL.getInstance().init(this);
            FFmpeg.getInstance().init(this);
            Aria2c.getInstance().init(this);
            ensureNotCancelled();
            updateEngineWhenNeeded();
            ensureNotCancelled();

            YoutubeDLRequest request = new YoutubeDLRequest(url);
            request.addOption("-o", new File(outputDir, "%(title).120B [%(id)s].%(ext)s").getAbsolutePath());
            request.addOption("--no-playlist");
            request.addOption("--no-mtime");
            request.addOption("--force-ipv4");
            request.addOption("--retries", "10");
            request.addOption("--fragment-retries", "10");
            request.addOption("--socket-timeout", "30");
            request.addOption("--concurrent-fragments", "4");
            request.addOption("--embed-metadata");
            request.addOption("--downloader", "libaria2c.so");

            if ("mp3".equals(format)) {
                request.addOption("-x");
                request.addOption("--audio-format", "mp3");
                request.addOption("--audio-quality",
                        "best".equals(quality) ? "0" : "data".equals(quality) ? "7" : "5");
            } else {
                String selector = "best".equals(quality)
                        ? "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/best"
                        : "data".equals(quality)
                        ? "bv*[height<=480][ext=mp4]+ba[ext=m4a]/b[height<=480][ext=mp4]/best[height<=480]"
                        : "bv*[height<=720][ext=mp4]+ba[ext=m4a]/b[height<=720][ext=mp4]/best[height<=720]";
                request.addOption("-f", selector);
                request.addOption("--merge-output-format", "mp4");
            }

            String profileLabel = "best".equals(quality) ? "alta qualidade"
                    : "data".equals(quality) ? "economia de dados" : "modo rápido";
            sendEvent("running", 2, 0,
                    "Download iniciado no próprio celular em " + profileLabel + ".");
            YoutubeDL.getInstance().execute(
                    request, DOWNLOAD_PROCESS_ID, false, (progress, etaInSeconds, line) -> {
                int value = (int) Math.max(0, Math.min(100, Math.round(progress)));
                updateNotification("Baixando no celular", value, value < 100, true);
                sendEvent("running", value, etaInSeconds, "Baixando e processando arquivo.");
                return kotlin.Unit.INSTANCE;
            });
            ensureNotCancelled();

            File[] after = outputDir.listFiles(file -> file.isFile() &&
                    (!before.contains(file.getAbsolutePath()) || file.lastModified() >= startedAt - 2000));
            if (after == null || after.length == 0) {
                throw new IllegalStateException("O download terminou, mas o arquivo final não foi localizado.");
            }
            Arrays.sort(after, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File file : after) {
                saveCategory(file, category);
                MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, null, null);
            }
            File newest = after[0];
            updateNotification("Download concluído", 100, false, false);
            sendEvent("success", 100, 0, newest.getName());
        } catch (Exception error) {
            if (cancelRequested || error instanceof YoutubeDL.CanceledException
                    || error instanceof CancellationException) {
                deleteNewPartialFiles(outputDir, before, startedAt);
                updateNotification("Download cancelado", 0, false, false);
                sendEvent("cancelled", 0, 0,
                        "Download cancelado. Os arquivos temporários foram removidos.");
            } else {
                String message = friendlyError(error);
                updateNotification("Falha no download", 0, false, false);
                sendEvent("error", 0, 0,
                        message.length() > 240 ? message.substring(0, 240) : message);
            }
        } finally {
            YoutubeDL.getInstance().destroyProcessById(DOWNLOAD_PROCESS_ID);
            running = false;
            cancelRequested = false;
            stopForeground(false);
            stopSelf(startId);
        }
    }

    private void ensureNotCancelled() {
        if (cancelRequested || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Download cancelado.");
        }
    }

    private void deleteNewPartialFiles(File outputDir, Set<String> before, long startedAt) {
        File[] created = outputDir.listFiles(file -> file.isFile()
                && !before.contains(file.getAbsolutePath())
                && file.lastModified() >= startedAt - 2000L);
        if (created == null) return;
        for (File file : created) {
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (name.endsWith(".part") || name.endsWith(".ytdl") || name.endsWith(".temp")
                    || name.endsWith(".tmp") || name.matches(".*\\.f\\d+\\..*")) {
                file.delete();
            }
        }
    }

    private String friendlyError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Falha ao processar o link no celular.";
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("unsupported url")) {
            return "Esse link ou site ainda não é compatível com o processador local.";
        }
        if (lower.contains("private video") || lower.contains("login required")
                || lower.contains("sign in")) {
            return "A mídia exige acesso privado ou login e não pode ser baixada pelo app.";
        }
        if (lower.contains("no space left") || lower.contains("enospc")) {
            return "Não há espaço livre suficiente no celular para concluir o download.";
        }
        if (lower.contains("unable to resolve host") || lower.contains("network is unreachable")
                || lower.contains("timed out")) {
            return "A conexão caiu ou demorou demais. Verifique a internet e tente novamente.";
        }
        return message.trim();
    }

    private void updateEngineWhenNeeded() {
        long lastUpdate = getSharedPreferences(PREFS, MODE_PRIVATE).getLong("yt_dlp_last_update", 0L);
        if (lastUpdate == 0L) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putLong("yt_dlp_last_update", System.currentTimeMillis()).apply();
            return;
        }
        long threeDays = 3L * 24L * 60L * 60L * 1000L;
        if (System.currentTimeMillis() - lastUpdate < threeDays) return;
        try {
            sendEvent("initializing", 1, 0, "Verificando atualização do processador local.");
            YoutubeDL.getInstance().updateYoutubeDL(this, YoutubeDL.UpdateChannel._NIGHTLY);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putLong("yt_dlp_last_update", System.currentTimeMillis()).apply();
        } catch (Exception ignored) {
            // Continua com a versão incluída no APK quando não houver atualização disponível.
        }
    }

    private void saveCategory(File file, String category) {
        try {
            String id = android.util.Base64.encodeToString(file.getCanonicalPath().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("cat_" + id, category).apply();
        } catch (Exception ignored) { }
    }

    private void sendEvent(String status, int progress, long eta, String message) {
        try {
            JSONObject json = new JSONObject();
            json.put("status", status);
            json.put("progress", progress);
            json.put("eta", eta);
            json.put("message", message);
            Intent broadcast = new Intent(ACTION_DOWNLOAD_EVENT);
            broadcast.setPackage(getPackageName());
            broadcast.putExtra(EXTRA_PAYLOAD, json.toString());
            sendBroadcast(broadcast);
        } catch (Exception ignored) { }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Downloads",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Progresso dos downloads processados no celular");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(
            String title, int progress, boolean indeterminate, boolean active) {
        Intent openApp = new Intent(this, MainActivity.class);
        openApp.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 2201, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Moura Downloads")
                .setContentText(title)
                .setContentIntent(contentIntent)
                .setAutoCancel(!active)
                .setOnlyAlertOnce(true)
                .setOngoing(active)
                .setProgress(active || progress >= 100 ? 100 : 0, progress,
                        active && indeterminate);
        if (active) {
            Intent cancel = new Intent(this, DownloadService.class);
            cancel.setAction(ACTION_CANCEL);
            PendingIntent cancelIntent = PendingIntent.getService(
                    this, 2202, cancel,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel,
                    "Cancelar", cancelIntent);
        }
        return builder.build();
    }

    private void startAsForeground(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification(
            String title, int progress, boolean indeterminate, boolean active) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID,
                buildNotification(title, progress, indeterminate, active));
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
