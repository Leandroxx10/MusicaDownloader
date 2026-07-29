package com.moura.downloads;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import com.yausername.ffmpeg.FFmpeg;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VideoEditorService extends Service {
    public static final String ACTION_START =
            "com.moura.downloads.action.EDITOR_START";
    public static final String ACTION_CANCEL =
            "com.moura.downloads.action.EDITOR_CANCEL";
    public static final String ACTION_EDITOR_EVENT =
            "com.moura.downloads.action.EDITOR_EVENT";
    public static final String EXTRA_CONFIG = "editor_config";
    public static final String EXTRA_PAYLOAD = "editor_payload";

    private static final String CHANNEL_ID = "moura_studio";
    private static final int NOTIFICATION_ID = 4401;
    private static final Pattern FFMPEG_TIME = Pattern.compile(
            "time=(\\d{2}):(\\d{2}):(\\d{2}(?:\\.\\d+)?)");
    private static final long MAX_INPUT_BYTES = 1_500_000_000L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running;
    private volatile boolean cancelRequested;
    private volatile Process activeProcess;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_CANCEL.equals(action)) {
            cancelRequested = true;
            Process process = activeProcess;
            if (process != null) process.destroy();
            sendEvent("cancelling", 0,
                    "Interrompendo a criação e removendo arquivos temporários.", null);
            updateNotification("Cancelando vídeo", 0, true);
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(action)) return START_NOT_STICKY;
        if (running) {
            sendEvent("error", 0,
                    "Já existe um vídeo sendo criado. Aguarde ou cancele.", null);
            return START_NOT_STICKY;
        }
        String config = intent.getStringExtra(EXTRA_CONFIG);
        running = true;
        cancelRequested = false;
        startForeground(NOTIFICATION_ID,
                buildNotification("Preparando seu vídeo", 0, true));
        executor.execute(() -> createVideo(config));
        return START_NOT_STICKY;
    }

    private void createVideo(String configJson) {
        File workDirectory = new File(getCacheDir(), "moura-studio-current");
        File output = new File(workDirectory, "studio-output.mp4");
        try {
            deleteTree(workDirectory);
            if (!workDirectory.mkdirs() && !workDirectory.isDirectory()) {
                throw new Exception("O Android não conseguiu preparar o Estúdio.");
            }
            JSONObject config = new JSONObject(configJson == null ? "{}" : configJson);
            JSONArray media = config.optJSONArray("media");
            if (media == null || media.length() == 0) {
                throw new Exception("Escolha um vídeo ou algumas fotos.");
            }

            sendEvent("preparing", 2,
                    "Copiando as mídias para a área privada do Estúdio.", null);
            List<File> inputs = new ArrayList<>();
            List<String> mimes = new ArrayList<>();
            for (int index = 0; index < Math.min(12, media.length()); index++) {
                ensureNotCancelled();
                JSONObject item = media.optJSONObject(index);
                if (item == null) continue;
                String mime = item.optString("mime", "");
                if (!mime.startsWith("video/") && !mime.startsWith("image/")) continue;
                if (!inputs.isEmpty() && mimes.get(0).startsWith("video/")) break;
                if (mime.startsWith("video/") && !inputs.isEmpty()) continue;
                File copied = copyUri(
                        Uri.parse(item.optString("uri")),
                        new File(workDirectory, String.format(
                                Locale.ROOT, "media-%02d.%s", index,
                                extensionFor(mime, item.optString("name")))));
                inputs.add(copied);
                mimes.add(mime);
                if (mime.startsWith("video/")) break;
                sendEvent("preparing", Math.min(15, 3 + index),
                        "Adicionando cena " + (index + 1) + " ao projeto.", null);
            }
            if (inputs.isEmpty()) throw new Exception("Nenhuma mídia compatível foi encontrada.");

            JSONObject audioObject = config.optJSONObject("audio");
            File audio = null;
            if (audioObject != null && audioObject.optString("mime").startsWith("audio/")) {
                audio = copyUri(
                        Uri.parse(audioObject.optString("uri")),
                        new File(workDirectory, "soundtrack." + extensionFor(
                                audioObject.optString("mime"),
                                audioObject.optString("name"))));
            }

            ensureNotCancelled();
            FFmpeg.getInstance().init(this);
            File ffmpegPackage = new File(
                    getNoBackupFilesDir(), "youtubedl-android/packages/ffmpeg");
            File ffmpeg = findFfmpegBinary(ffmpegPackage);
            if (ffmpeg == null) {
                ffmpeg = new File(getApplicationInfo().nativeLibraryDir, "libffmpeg.so");
            }
            if (ffmpeg == null || !ffmpeg.exists()) {
                throw new Exception("O motor de vídeo não está disponível neste aparelho.");
            }
            if (!ffmpeg.canExecute()) ffmpeg.setExecutable(true, false);

            double speed = clamp(config.optDouble("speed", 1d), .5d, 2d);
            double imageDuration = clamp(config.optDouble("imageDuration", 3d), 1d, 8d);
            boolean sourceIsVideo = mimes.get(0).startsWith("video/");
            double sourceDuration = sourceIsVideo
                    ? mediaDurationSeconds(inputs.get(0))
                    : inputs.size() * imageDuration;
            double outputDuration = Math.max(1d, sourceDuration / speed);
            List<String> command = buildCommand(
                    ffmpeg, inputs, sourceIsVideo, audio, output, config,
                    speed, imageDuration, outputDuration);

            sendEvent("running", 17,
                    "Aplicando cortes, cores, velocidade e trilha sonora.", null);
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workDirectory);
            builder.redirectErrorStream(true);
            File sharedLibraries = new File(ffmpegPackage, "usr/lib");
            if (sharedLibraries.isDirectory()) {
                String existing = builder.environment().get("LD_LIBRARY_PATH");
                builder.environment().put("LD_LIBRARY_PATH",
                        sharedLibraries.getAbsolutePath()
                                + (existing == null || existing.isEmpty()
                                ? "" : ":" + existing));
            }
            activeProcess = builder.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(activeProcess.getInputStream()))) {
                String line;
                int lastProgress = 17;
                while ((line = reader.readLine()) != null) {
                    ensureNotCancelled();
                    Matcher matcher = FFMPEG_TIME.matcher(line);
                    if (matcher.find()) {
                        double seconds = Integer.parseInt(matcher.group(1)) * 3600d
                                + Integer.parseInt(matcher.group(2)) * 60d
                                + Double.parseDouble(matcher.group(3));
                        int progress = Math.max(17, Math.min(92,
                                17 + (int) Math.round(seconds / outputDuration * 75d)));
                        if (progress >= lastProgress + 2) {
                            lastProgress = progress;
                            sendEvent("running", progress,
                                    "Criando o vídeo no próprio celular.", null);
                            updateNotification("Criando seu vídeo", progress, false);
                        }
                    }
                }
            }
            int exitCode = activeProcess.waitFor();
            activeProcess = null;
            ensureNotCancelled();
            if (exitCode != 0 || !output.exists() || output.length() < 1024L) {
                throw new Exception("Não foi possível combinar estes arquivos. Tente outras mídias.");
            }

            sendEvent("saving", 95, "Salvando o resultado na galeria.", null);
            updateNotification("Salvando na galeria", 95, false);
            String fileName = safeName(config.optString("name", "Meu vídeo Moura"))
                    + "-" + System.currentTimeMillis() + ".mp4";
            Uri saved = saveToGallery(output, fileName);
            JSONObject result = new JSONObject();
            result.put("uri", saved.toString());
            result.put("name", fileName);
            result.put("size", output.length());
            sendEvent("success", 100,
                    "Vídeo salvo em Filmes/Moura Studio.", result);
            updateNotification("Vídeo pronto na galeria", 100, false);
        } catch (CancelledException ignored) {
            sendEvent("cancelled", 0, "Criação cancelada.", null);
        } catch (Exception error) {
            sendEvent("error", 0, friendlyError(error), null);
            updateNotification("Não foi possível criar o vídeo", 0, true);
        } finally {
            Process process = activeProcess;
            if (process != null) process.destroy();
            activeProcess = null;
            running = false;
            cancelRequested = false;
            deleteTree(workDirectory);
            stopForeground(false);
            stopSelf();
        }
    }

    private List<String> buildCommand(
            File ffmpeg,
            List<File> inputs,
            boolean sourceIsVideo,
            @Nullable File audio,
            File output,
            JSONObject config,
            double speed,
            double imageDuration,
            double outputDuration) {
        List<String> command = new ArrayList<>();
        command.add(ffmpeg.getAbsolutePath());
        command.add("-y");
        if (sourceIsVideo) {
            command.add("-i");
            command.add(inputs.get(0).getAbsolutePath());
        } else {
            for (File input : inputs) {
                command.add("-loop");
                command.add("1");
                command.add("-t");
                command.add(decimal(imageDuration));
                command.add("-i");
                command.add(input.getAbsolutePath());
            }
        }
        int audioIndex = inputs.size();
        if (sourceIsVideo) audioIndex = 1;
        if (audio != null) {
            command.add("-stream_loop");
            command.add("-1");
            command.add("-i");
            command.add(audio.getAbsolutePath());
        }

        String ratio = config.optString("ratio", "9:16");
        int width = "16:9".equals(ratio) ? 1280 : 720;
        int height = "16:9".equals(ratio) ? 720
                : "1:1".equals(ratio) ? 720 : 1280;
        String scale = "scale=" + width + ":" + height
                + ":force_original_aspect_ratio=increase,crop=" + width + ":" + height
                + ",setsar=1,fps=30";
        String effects = effectFilter(config);
        StringBuilder filter = new StringBuilder();
        if (sourceIsVideo) {
            filter.append("[0:v]").append(scale).append(",")
                    .append(effects).append(",setpts=PTS/")
                    .append(decimal(speed)).append("[v]");
        } else {
            for (int index = 0; index < inputs.size(); index++) {
                filter.append("[").append(index).append(":v]")
                        .append(scale).append(",trim=duration=")
                        .append(decimal(imageDuration))
                        .append(",setpts=PTS-STARTPTS[v").append(index).append("];");
            }
            for (int index = 0; index < inputs.size(); index++) {
                filter.append("[v").append(index).append("]");
            }
            filter.append("concat=n=").append(inputs.size())
                    .append(":v=1:a=0[sequence];[sequence]")
                    .append(effects).append(",setpts=PTS/")
                    .append(decimal(speed)).append("[v]");
        }
        command.add("-filter_complex");
        command.add(filter.toString());
        command.add("-map");
        command.add("[v]");

        if (audio != null) {
            command.add("-map");
            command.add(audioIndex + ":a:0?");
            command.add("-t");
            command.add(decimal(outputDuration));
            if (config.optBoolean("fade", true)) {
                command.add("-af");
                command.add("afade=t=in:st=0:d=0.8,afade=t=out:st="
                        + decimal(Math.max(.8d, outputDuration - .8d)) + ":d=0.8");
            }
            command.add("-c:a");
            command.add("aac");
            command.add("-b:a");
            command.add("192k");
        } else if (sourceIsVideo) {
            command.add("-map");
            command.add("0:a:0?");
            command.add("-af");
            command.add("atempo=" + decimal(speed));
            command.add("-c:a");
            command.add("aac");
            command.add("-b:a");
            command.add("160k");
        }
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("veryfast");
        command.add("-crf");
        command.add("23");
        command.add("-pix_fmt");
        command.add("yuv420p");
        command.add("-movflags");
        command.add("+faststart");
        command.add(output.getAbsolutePath());
        return command;
    }

    private String effectFilter(JSONObject config) {
        double brightness = clamp(config.optDouble("brightness", 0d), -100d, 100d)
                / 100d * .55d;
        double contrast = 1d + clamp(config.optDouble("contrast", 0d), -100d, 100d)
                / 100d * .75d;
        double saturation = 1d + clamp(config.optDouble("saturation", 0d), -100d, 100d)
                / 100d;
        String preset = config.optString("effect", "normal");
        if ("vivid".equals(preset)) {
            contrast *= 1.08d;
            saturation *= 1.28d;
        } else if ("mono".equals(preset)) {
            saturation = 0d;
        } else if ("vintage".equals(preset)) {
            contrast *= .94d;
            saturation *= .82d;
        }
        String filter = "eq=brightness=" + decimal(brightness)
                + ":contrast=" + decimal(clamp(contrast, .25d, 2d))
                + ":saturation=" + decimal(clamp(saturation, 0d, 2.5d));
        if ("warm".equals(preset)) {
            filter += ",colorbalance=rs=.10:gs=.02:bs=-.08";
        } else if ("cool".equals(preset)) {
            filter += ",colorbalance=rs=-.06:gs=.01:bs=.10";
        } else if ("vintage".equals(preset)) {
            filter += ",colorbalance=rs=.10:gs=.025:bs=-.07";
        }
        return filter;
    }

    private File copyUri(Uri uri, File destination) throws Exception {
        if (uri == null || !"content".equalsIgnoreCase(uri.getScheme())) {
            throw new Exception("Uma das mídias selecionadas não está mais disponível.");
        }
        long total = 0L;
        try (InputStream input = getContentResolver().openInputStream(uri);
             OutputStream output = new FileOutputStream(destination)) {
            if (input == null) throw new Exception("Não foi possível abrir uma das mídias.");
            byte[] buffer = new byte[128 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                ensureNotCancelled();
                if (read == 0) continue;
                total += read;
                if (total > MAX_INPUT_BYTES) {
                    throw new Exception("Uma das mídias é grande demais para este aparelho.");
                }
                output.write(buffer, 0, read);
            }
        }
        return destination;
    }

    private Uri saveToGallery(File source, String name) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/Moura Studio");
            values.put(MediaStore.Video.Media.IS_PENDING, 1);
            Uri uri = getContentResolver().insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new Exception("A galeria recusou o novo vídeo.");
            try (InputStream input = new FileInputStream(source);
                 OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new Exception("Não foi possível salvar o vídeo.");
                copyStream(input, output);
            } catch (Exception error) {
                getContentResolver().delete(uri, null, null);
                throw error;
            }
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.Video.Media.IS_PENDING, 0);
            getContentResolver().update(uri, ready, null, null);
            return uri;
        }
        File directory = new File(
                Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_MOVIES), "Moura Studio");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new Exception("Não foi possível criar a pasta Moura Studio.");
        }
        File destination = new File(directory, name);
        try (InputStream input = new FileInputStream(source);
             OutputStream output = new FileOutputStream(destination)) {
            copyStream(input, output);
        }
        android.media.MediaScannerConnection.scanFile(
                this, new String[]{destination.getAbsolutePath()},
                new String[]{"video/mp4"}, null);
        return FileProvider.getUriForFile(
                this, getPackageName() + ".fileprovider", destination);
    }

    private void copyStream(InputStream input, OutputStream output) throws Exception {
        byte[] buffer = new byte[128 * 1024];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            ensureNotCancelled();
            if (read > 0) output.write(buffer, 0, read);
        }
    }

    private double mediaDurationSeconds(File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String value = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION);
            return Math.max(1d, Double.parseDouble(value) / 1000d);
        } catch (Exception ignored) {
            return 30d;
        } finally {
            try { retriever.release(); } catch (Exception ignored) { }
        }
    }

    private File findFfmpegBinary(File directory) {
        if (directory == null || !directory.exists()) return null;
        File[] files = directory.listFiles();
        if (files == null) return null;
        for (File file : files) {
            if (file.isFile() && ("ffmpeg".equals(file.getName())
                    || "ffmpeg.bin".equals(file.getName()))) {
                return file;
            }
        }
        for (File file : files) {
            if (file.isDirectory()) {
                File found = findFfmpegBinary(file);
                if (found != null) return found;
            }
        }
        return null;
    }

    private String extensionFor(String mime, String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot >= 0 && lower.length() - dot <= 6) {
            String extension = lower.substring(dot + 1).replaceAll("[^a-z0-9]", "");
            if (!extension.isEmpty()) return extension;
        }
        if (mime.startsWith("image/png")) return "png";
        if (mime.startsWith("image/webp")) return "webp";
        if (mime.startsWith("image/")) return "jpg";
        if (mime.startsWith("audio/mpeg")) return "mp3";
        if (mime.startsWith("audio/mp4")) return "m4a";
        if (mime.startsWith("audio/")) return "aac";
        return "mp4";
    }

    private String safeName(String value) {
        String clean = value == null ? "" : value.trim()
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ")
                .replaceAll("\\s+", " ");
        if (clean.isEmpty()) clean = "Meu vídeo Moura";
        return clean.substring(0, Math.min(70, clean.length()));
    }

    private String friendlyError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Não foi possível criar o vídeo neste aparelho.";
        }
        return message.length() > 220 ? message.substring(0, 220) : message;
    }

    private String decimal(double value) {
        return String.format(Locale.US, "%.3f", value);
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private void ensureNotCancelled() throws CancelledException {
        if (cancelRequested || Thread.currentThread().isInterrupted()) {
            throw new CancelledException();
        }
    }

    private void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        file.delete();
    }

    private void sendEvent(
            String status, int progress, String message,
            @Nullable JSONObject extra) {
        JSONObject payload = extra == null ? new JSONObject() : extra;
        try {
            payload.put("status", status);
            payload.put("progress", progress);
            payload.put("message", message);
        } catch (Exception ignored) { }
        Intent event = new Intent(ACTION_EDITOR_EVENT)
                .setPackage(getPackageName())
                .putExtra(EXTRA_PAYLOAD, payload.toString());
        sendBroadcast(event);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Estúdio Moura",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Progresso da criação de vídeos no aparelho.");
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private android.app.Notification buildNotification(
            String title, int progress, boolean indeterminate) {
        Intent openApp = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 4402, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent cancel = new Intent(this, VideoEditorService.class)
                .setAction(ACTION_CANCEL);
        PendingIntent cancelIntent = PendingIntent.getService(
                this, 4403, cancel,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String theme = getSharedPreferences(
                MainActivity.THEME_PREFS, MODE_PRIVATE)
                .getString(MainActivity.THEME_COLOR_KEY, "#42f57b");
        int color;
        try { color = Color.parseColor(theme); }
        catch (Exception ignored) { color = Color.rgb(66, 245, 123); }
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_slideshow)
                .setContentTitle("Moura Studio")
                .setContentText(title)
                .setColor(color)
                .setOnlyAlertOnce(true)
                .setOngoing(progress < 100)
                .setProgress(100, Math.max(0, progress), indeterminate)
                .setContentIntent(contentIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                        "Cancelar", cancelIntent)
                .build();
    }

    private void updateNotification(String title, int progress, boolean indeterminate) {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID,
                    buildNotification(title, progress, indeterminate));
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Process process = activeProcess;
        if (process != null) process.destroy();
        executor.shutdownNow();
        super.onDestroy();
    }

    private static final class CancelledException extends Exception { }
}
