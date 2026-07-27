package com.moura.downloads;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateService extends Service {
    public static final String ACTION_START = "com.moura.downloads.action.START_UPDATE";
    public static final String ACTION_CANCEL = "com.moura.downloads.action.CANCEL_UPDATE";
    public static final String ACTION_UPDATE_EVENT = "com.moura.downloads.UPDATE_EVENT";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_SHA256 = "sha256";
    public static final String EXTRA_VERSION = "version";
    public static final String EXTRA_PAYLOAD = "payload";
    public static final String EXTRA_FILE_PATH = "file_path";

    private static final String CHANNEL_ID = "moura_updates";
    private static final int NOTIFICATION_ID = 3302;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running;
    private volatile boolean cancelled;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (ACTION_CANCEL.equals(intent.getAction())) {
            cancelled = true;
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(intent.getAction()) || running) return START_NOT_STICKY;

        String url = intent.getStringExtra(EXTRA_URL);
        String sha256 = intent.getStringExtra(EXTRA_SHA256);
        String version = intent.getStringExtra(EXTRA_VERSION);
        if (!isTrustedUpdateUrl(url)) {
            sendEvent("error", 0, "Endereço de atualização inválido.", null);
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        running = true;
        cancelled = false;
        startForeground(NOTIFICATION_ID,
                buildProgressNotification("Preparando atualização", 0, true));
        executor.execute(() -> downloadUpdate(url, sha256, version, startId));
        return START_NOT_STICKY;
    }

    private boolean isTrustedUpdateUrl(String value) {
        if (value == null) return false;
        try {
            Uri uri = Uri.parse(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "github.com".equalsIgnoreCase(uri.getHost())
                    && uri.getPath() != null
                    && uri.getPath().startsWith("/Leandroxx10/MusicaDownloader/releases/download/");
        } catch (Exception ignored) {
            return false;
        }
    }

    private void downloadUpdate(String url, String expectedSha256, String version, int startId) {
        File temp = null;
        try {
            File directory = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates");
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IllegalStateException("Não foi possível preparar a pasta de atualização.");
            }
            temp = new File(directory, "moura-downloads-update.apk.part");
            File target = new File(directory, "moura-downloads-update.apk");
            if (temp.exists() && !temp.delete()) {
                throw new IllegalStateException("Não foi possível limpar a atualização anterior.");
            }

            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(45000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "MouraDownloadsAndroid/4.0");
            connection.setRequestProperty("Accept", "application/vnd.android.package-archive");
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IllegalStateException("Servidor respondeu com código " + responseCode + ".");
            }

            long total = connection.getContentLengthLong();
            long downloaded = 0L;
            int lastProgress = -1;
            long lastUpdate = 0L;
            byte[] buffer = new byte[64 * 1024];
            try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream output = new FileOutputStream(temp)) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (cancelled) throw new UpdateCancelledException();
                    output.write(buffer, 0, count);
                    downloaded += count;
                    int progress = total > 0
                            ? (int) Math.min(99, Math.round(downloaded * 100d / total)) : 0;
                    long now = System.currentTimeMillis();
                    if (progress != lastProgress && (progress % 2 == 0 || now - lastUpdate > 900L)) {
                        lastProgress = progress;
                        lastUpdate = now;
                        String message = total > 0
                                ? humanSize(downloaded) + " de " + humanSize(total)
                                : humanSize(downloaded) + " baixados";
                        updateProgress("Baixando atualização " + safeVersion(version), progress, total <= 0);
                        sendEvent("downloading", progress, message, null);
                    }
                }
                output.getFD().sync();
            } finally {
                connection.disconnect();
            }

            if (cancelled) throw new UpdateCancelledException();
            if (expectedSha256 != null && !expectedSha256.trim().isEmpty()) {
                sendEvent("verifying", 99, "Verificando a segurança do arquivo.", null);
                String actual = sha256(temp);
                if (!actual.equalsIgnoreCase(expectedSha256.trim())) {
                    throw new SecurityException("A verificação de segurança da atualização falhou.");
                }
            }
            if (target.exists() && !target.delete()) {
                throw new IllegalStateException("Não foi possível substituir a atualização anterior.");
            }
            if (!temp.renameTo(target)) {
                throw new IllegalStateException("Não foi possível finalizar o arquivo da atualização.");
            }

            Notification ready = buildReadyNotification(target, version);
            NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(NOTIFICATION_ID, ready);
            sendEvent("ready", 100,
                    "Atualização pronta. Toque em instalar para concluir.", target.getAbsolutePath());
        } catch (UpdateCancelledException ignored) {
            if (temp != null && temp.exists()) temp.delete();
            sendEvent("cancelled", 0, "Atualização cancelada.", null);
            removeNotification();
        } catch (Exception error) {
            if (temp != null && temp.exists()) temp.delete();
            String message = safeMessage(error);
            sendEvent("error", 0, message, null);
            showFailureNotification(message);
        } finally {
            running = false;
            stopForeground(false);
            stopSelf(startId);
        }
    }

    private void updateProgress(String title, int progress, boolean indeterminate) {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID,
                    buildProgressNotification(title, progress, indeterminate));
        }
    }

    private Notification buildProgressNotification(String title, int progress, boolean indeterminate) {
        Intent cancel = new Intent(this, UpdateService.class);
        cancel.setAction(ACTION_CANCEL);
        PendingIntent cancelIntent = PendingIntent.getService(
                this, 3303, cancel,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Moura Downloads")
                .setContentText(title)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, progress, indeterminate)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                        "Cancelar", cancelIntent)
                .build();
    }

    private Notification buildReadyNotification(File apk, String version) {
        PendingIntent installIntent = PendingIntent.getActivity(
                this, 3304, installerIntent(apk),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Atualização " + safeVersion(version) + " pronta")
                .setContentText("Toque para instalar a nova versão do Moura Downloads.")
                .setContentIntent(installIntent)
                .setAutoCancel(true)
                .setOngoing(false)
                .setProgress(0, 0, false)
                .build();
    }

    private Intent installerIntent(File apk) {
        Uri uri = FileProvider.getUriForFile(
                this, getPackageName() + ".fileprovider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private void showFailureNotification(String message) {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        Notification failure = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Não foi possível atualizar")
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .build();
        manager.notify(NOTIFICATION_ID, failure);
    }

    private void removeNotification() {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
    }

    private void sendEvent(String status, int progress, String message, String filePath) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("status", status);
            payload.put("progress", progress);
            payload.put("message", message);
            if (filePath != null) payload.put("filePath", filePath);
        } catch (Exception ignored) { }
        Intent event = new Intent(ACTION_UPDATE_EVENT);
        event.setPackage(getPackageName());
        event.putExtra(EXTRA_PAYLOAD, payload.toString());
        if (filePath != null) event.putExtra(EXTRA_FILE_PATH, filePath);
        sendBroadcast(event);
    }

    private String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[64 * 1024];
        try (FileInputStream input = new FileInputStream(file)) {
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder value = new StringBuilder();
        for (byte item : digest.digest()) value.append(String.format(Locale.ROOT, "%02x", item));
        return value.toString();
    }

    private String humanSize(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double value = bytes / 1024d;
        if (value < 1024d) return String.format(Locale.getDefault(), "%.1f KB", value);
        value /= 1024d;
        return String.format(Locale.getDefault(), "%.1f MB", value);
    }

    private String safeVersion(String version) {
        return version == null || version.trim().isEmpty() ? "" : version.trim();
    }

    private String safeMessage(Throwable error) {
        String text = error.getMessage();
        return text == null || text.trim().isEmpty()
                ? "Não foi possível baixar a atualização." : text;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Atualizações do aplicativo", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Download e instalação de novas versões do Moura Downloads.");
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    @Override
    public void onDestroy() {
        cancelled = true;
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static class UpdateCancelledException extends Exception { }
}
