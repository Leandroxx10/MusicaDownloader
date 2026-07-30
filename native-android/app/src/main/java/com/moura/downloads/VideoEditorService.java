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
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;

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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class VideoEditorService extends Service {
    public static final String ACTION_START =
            "com.moura.downloads.action.EDITOR_START";
    public static final String ACTION_CANCEL =
            "com.moura.downloads.action.EDITOR_CANCEL";
    public static final String ACTION_EDITOR_EVENT =
            "com.moura.downloads.action.EDITOR_EVENT";
    public static final String EXTRA_CONFIG = "editor_config";
    public static final String EXTRA_CONFIG_BASE64 = "editor_config_base64";
    public static final String EXTRA_PAYLOAD = "editor_payload";

    private static final String TAG = "MouraStudio";
    private static final String CHANNEL_ID = "moura_studio";
    private static final int NOTIFICATION_ID = 4401;
    private static final Pattern FFMPEG_TIME = Pattern.compile(
            "time=(\\d{2}):(\\d{2}):(\\d{2}(?:\\.\\d+)?)");
    private static final long MAX_INPUT_BYTES = 1_500_000_000L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running;
    private volatile boolean cancelRequested;
    private volatile Process activeProcess;

    private static final class StudioInput {
        final File file;
        final String mime;
        final double sourceDuration;
        double duration;

        StudioInput(File file, String mime, double sourceDuration, double duration) {
            this.file = file;
            this.mime = mime;
            this.sourceDuration = sourceDuration;
            this.duration = duration;
        }

        boolean isVideo() {
            return mime.startsWith("video/");
        }
    }

    private static final class BeatAnalysis {
        final List<Double> beats;
        final double bpm;
        final double duration;

        BeatAnalysis(List<Double> beats, double bpm, double duration) {
            this.beats = beats;
            this.bpm = bpm;
            this.duration = duration;
        }
    }

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
        String encodedConfig = intent.getStringExtra(EXTRA_CONFIG_BASE64);
        if ((config == null || config.isEmpty())
                && encodedConfig != null && !encodedConfig.isEmpty()) {
            try {
                config = new String(Base64.decode(
                        encodedConfig, Base64.NO_WRAP), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception error) {
                sendEvent("error", 0, "Configuração do projeto inválida.", null);
                return START_NOT_STICKY;
            }
        }
        running = true;
        cancelRequested = false;
        startForeground(NOTIFICATION_ID,
                buildNotification("Preparando seu vídeo", 0, true));
        final String editorConfig = config;
        executor.execute(() -> createVideo(editorConfig));
        return START_NOT_STICKY;
    }

    private void createVideo(String configJson) {
        File workDirectory = new File(getCacheDir(), "moura-studio-current");
        File output = new File(workDirectory, "studio-output.mp4");
        BeatAnalysis beatAnalysis = null;
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
            List<StudioInput> inputs = new ArrayList<>();
            double defaultImageDuration = clamp(
                    config.optDouble("imageDuration", 3d), .5d, 12d);
            for (int index = 0; index < Math.min(20, media.length()); index++) {
                ensureNotCancelled();
                JSONObject item = media.optJSONObject(index);
                if (item == null) continue;
                String mime = item.optString("mime", "");
                if (!mime.startsWith("video/") && !mime.startsWith("image/")) continue;
                File copied = copyUri(
                        Uri.parse(item.optString("uri")),
                        new File(workDirectory, String.format(
                                Locale.ROOT, "media-%02d.%s", index,
                                extensionFor(mime, item.optString("name")))));
                boolean video = mime.startsWith("video/");
                double sourceDuration = video ? mediaDurationSeconds(copied) : defaultImageDuration;
                double maximum = video ? Math.min(60d, sourceDuration) : 12d;
                double requested = item.optDouble(
                        "duration", video ? Math.min(sourceDuration, 8d) : defaultImageDuration);
                inputs.add(new StudioInput(
                        copied, mime, sourceDuration, clamp(requested, .5d, maximum)));
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
            sendEvent("preparing", 15,
                    "Preparando o motor de vídeo no celular.", null);
            updateNotification("Preparando o motor de vídeo", 15, true);
            YoutubeDL.getInstance().init(this);
            ensureFfmpegRuntimeLibraries();
            FFmpeg.getInstance().init(this);
            File packagesRoot = new File(
                    getNoBackupFilesDir(), "youtubedl-android/packages");
            File ffmpegPackage = new File(packagesRoot, "ffmpeg");
            File ffmpeg = findFfmpegBinary(ffmpegPackage);
            if (ffmpeg == null) {
                ffmpeg = new File(getApplicationInfo().nativeLibraryDir, "libffmpeg.so");
            }
            if (ffmpeg == null || !ffmpeg.exists()) {
                throw new Exception("O motor de vídeo não está disponível neste aparelho.");
            }
            if (!ffmpeg.canExecute()) ffmpeg.setExecutable(true, false);

            double speed = clamp(config.optDouble("speed", 1d), .5d, 2d);
            String transition = safeTransition(config.optString("transition", "fade"));
            double transitionDuration = "cut".equals(transition) || inputs.size() < 2
                    ? 0d : clamp(config.optDouble("transitionDuration", .35d), .05d, .7d);
            double shortestScene = inputs.get(0).duration;
            for (StudioInput input : inputs) shortestScene = Math.min(shortestScene, input.duration);
            transitionDuration = Math.min(transitionDuration, Math.max(.05d, shortestScene * .45d));

            if (config.optBoolean("beatSync", false) && audio != null && inputs.size() > 1) {
                sendEvent("analyzing", 16,
                        "Ouvindo a música e encontrando os pulsos mais fortes.", null);
                updateNotification("Analisando as batidas", 16, true);
                beatAnalysis = analyzeBeats(
                        ffmpeg, audio, config.optString("beatMode", "balanced"),
                        packagesRoot, ffmpegPackage, workDirectory);
                alignScenesToBeats(inputs, beatAnalysis, transitionDuration);
                transitionDuration = Math.min(
                        transitionDuration, shortestDuration(inputs) * .45d);
                sendEvent("analyzing", 20,
                        beatAnalysis.bpm > 0d
                                ? "Ritmo detectado em " + Math.round(beatAnalysis.bpm) + " BPM."
                                : "Pulsos musicais detectados e cenas alinhadas.", null);
            }

            double outputDuration = projectDuration(inputs, transitionDuration, speed);
            List<String> command = buildCommand(
                    ffmpeg, inputs, audio, output, config, speed,
                    transition, transitionDuration, outputDuration);
            Log.i(TAG, "Iniciando exportação com " + inputs.size()
                    + " cena(s), transição=" + transition + ", áudio=" + (audio != null)
                    + ", duração=" + decimal(outputDuration));

            sendEvent("running", 22,
                    "Aplicando cortes, cores, velocidade e trilha sonora.", null);
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workDirectory);
            builder.redirectErrorStream(true);
            configureFfmpegEnvironment(builder, packagesRoot, ffmpegPackage);
            activeProcess = builder.start();
            StringBuilder ffmpegLog = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(activeProcess.getInputStream()))) {
                String line;
                int lastProgress = 22;
                while ((line = reader.readLine()) != null) {
                    ensureNotCancelled();
                    ffmpegLog.append(line).append('\n');
                    if (ffmpegLog.length() > 12_000) {
                        ffmpegLog.delete(0, ffmpegLog.length() - 8_000);
                    }
                    Matcher matcher = FFMPEG_TIME.matcher(line);
                    if (matcher.find()) {
                        double seconds = Integer.parseInt(matcher.group(1)) * 3600d
                                + Integer.parseInt(matcher.group(2)) * 60d
                                + Double.parseDouble(matcher.group(3));
                        int progress = Math.max(22, Math.min(92,
                                22 + (int) Math.round(seconds / outputDuration * 70d)));
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
                Log.e(TAG, "FFmpeg encerrou com código " + exitCode
                        + " e saída de " + (output.exists() ? output.length() : 0L)
                        + " bytes.\n" + ffmpegLog);
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
            result.put("duration", outputDuration);
            result.put("scenes", inputs.size());
            if (beatAnalysis != null) {
                result.put("bpm", beatAnalysis.bpm);
                result.put("beatCount", beatAnalysis.beats.size());
            }
            sendEvent("success", 100,
                    "Vídeo salvo em Filmes/Moura Studio.", result);
            updateNotification("Vídeo pronto na galeria", 100, false);
        } catch (CancelledException ignored) {
            sendEvent("cancelled", 0, "Criação cancelada.", null);
        } catch (Exception error) {
            Log.e(TAG, "Falha ao criar vídeo", error);
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
            List<StudioInput> inputs,
            @Nullable File audio,
            File output,
            JSONObject config,
            double speed,
            String transition,
            double transitionDuration,
            double outputDuration) {
        List<String> command = new ArrayList<>();
        command.add(ffmpeg.getAbsolutePath());
        command.add("-y");
        for (StudioInput input : inputs) {
            if (!input.isVideo()) {
                command.add("-loop");
                command.add("1");
                command.add("-framerate");
                command.add("30");
                command.add("-t");
                command.add(decimal(input.duration));
            }
            command.add("-i");
            command.add(input.file.getAbsolutePath());
        }
        int audioIndex = inputs.size();
        if (audio != null) {
            command.add("-stream_loop");
            command.add("-1");
            command.add("-i");
            command.add(audio.getAbsolutePath());
        }

        String ratio = config.optString("ratio", "9:16");
        boolean fullHd = "1080".equals(config.optString("quality", "720"));
        int landscapeWidth = fullHd ? 1920 : 1280;
        int portraitWidth = fullHd ? 1080 : 720;
        int width = "16:9".equals(ratio) ? landscapeWidth : portraitWidth;
        int height = "16:9".equals(ratio) ? portraitWidth
                : "1:1".equals(ratio) ? portraitWidth : landscapeWidth;
        String scale = "scale=" + width + ":" + height
                + ":force_original_aspect_ratio=increase,crop=" + width + ":" + height
                + ",setsar=1,fps=30";
        String effects = effectFilter(config);
        StringBuilder filter = new StringBuilder();
        boolean motion = config.optBoolean("motion", true);
        for (int index = 0; index < inputs.size(); index++) {
            StudioInput input = inputs.get(index);
            filter.append("[").append(index).append(":v]")
                    .append(scale).append(",");
            if (motion && !input.isVideo()) {
                filter.append("zoompan=z='min(zoom+0.0008,1.10)'")
                        .append(":x='iw/2-(iw/zoom/2)'")
                        .append(":y='ih/2-(ih/zoom/2)'")
                        .append(":d=1:s=").append(width).append("x").append(height)
                        .append(":fps=30,");
            }
            filter.append("trim=duration=").append(decimal(input.duration))
                    .append(",setpts=PTS-STARTPTS,")
                    .append(effects).append("[v").append(index).append("];");
        }

        String sequenceLabel;
        if (inputs.size() == 1) {
            sequenceLabel = "v0";
        } else if ("cut".equals(transition) || transitionDuration <= 0d) {
            for (int index = 0; index < inputs.size(); index++) {
                filter.append("[v").append(index).append("]");
            }
            filter.append("concat=n=").append(inputs.size())
                    .append(":v=1:a=0[sequence];");
            sequenceLabel = "sequence";
        } else {
            double offset = Math.max(.05d, inputs.get(0).duration - transitionDuration);
            String previous = "v0";
            for (int index = 1; index < inputs.size(); index++) {
                String next = index == inputs.size() - 1 ? "sequence" : "mix" + index;
                filter.append("[").append(previous).append("][v").append(index).append("]")
                        .append("xfade=transition=").append(transition)
                        .append(":duration=").append(decimal(transitionDuration))
                        .append(":offset=").append(decimal(offset))
                        .append("[").append(next).append("];");
                previous = next;
                offset += inputs.get(index).duration - transitionDuration;
            }
            sequenceLabel = "sequence";
        }
        filter.append("[").append(sequenceLabel).append("]")
                .append("setpts=PTS/").append(decimal(speed)).append("[v]");
        command.add("-filter_complex");
        command.add(filter.toString());
        command.add("-map");
        command.add("[v]");

        if (audio != null) {
            command.add("-map");
            command.add(audioIndex + ":a:0?");
            command.add("-t");
            command.add(decimal(outputDuration));
            StringBuilder audioFilter = new StringBuilder("volume=")
                    .append(decimal(clamp(
                            config.optDouble("musicVolume", 100d), 0d, 150d) / 100d));
            if (config.optBoolean("fade", true)) {
                audioFilter.append(",afade=t=in:st=0:d=0.8,afade=t=out:st=")
                        .append(decimal(Math.max(.8d, outputDuration - .8d)))
                        .append(":d=0.8");
            }
            command.add("-af");
            command.add(audioFilter.toString());
            command.add("-c:a");
            command.add("aac");
            command.add("-b:a");
            command.add("192k");
        } else if (inputs.size() == 1 && inputs.get(0).isVideo()) {
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
        command.add(fullHd ? "21" : "23");
        command.add("-pix_fmt");
        command.add("yuv420p");
        command.add("-movflags");
        command.add("+faststart");
        command.add(output.getAbsolutePath());
        return command;
    }

    private void configureFfmpegEnvironment(
            ProcessBuilder builder, File packagesRoot, File ffmpegPackage) {
        File sharedLibraries = new File(ffmpegPackage, "usr/lib");
        File pythonLibraries = new File(packagesRoot, "python/usr/lib");
        String existingLibraryPath = builder.environment().get("LD_LIBRARY_PATH");
        StringBuilder libraryPath = new StringBuilder();
        if (pythonLibraries.isDirectory()) {
            libraryPath.append(pythonLibraries.getAbsolutePath());
        }
        if (sharedLibraries.isDirectory()) {
            if (libraryPath.length() > 0) libraryPath.append(':');
            libraryPath.append(sharedLibraries.getAbsolutePath());
        }
        if (libraryPath.length() > 0) libraryPath.append(':');
        libraryPath.append(getApplicationInfo().nativeLibraryDir);
        if (existingLibraryPath != null && !existingLibraryPath.isEmpty()) {
            libraryPath.append(':').append(existingLibraryPath);
        }
        builder.environment().put("LD_LIBRARY_PATH", libraryPath.toString());
    }

    private void ensureFfmpegRuntimeLibraries() throws Exception {
        File archive = new File(getApplicationInfo().nativeLibraryDir, "libpython.zip.so");
        if (!archive.isFile()) return;
        File pythonLibraries = new File(
                getNoBackupFilesDir(), "youtubedl-android/packages/python/usr/lib");
        if (!pythonLibraries.exists() && !pythonLibraries.mkdirs()) {
            throw new Exception("Não foi possível preparar as bibliotecas do Estúdio.");
        }
        try (ZipFile zip = new ZipFile(archive)) {
            extractRuntimeLibrary(
                    zip, "usr/lib/libc++_shared.so",
                    new File(pythonLibraries, "libc++_shared.so"));
            File expatVersion = new File(pythonLibraries, "libexpat.so.1.11.1");
            extractRuntimeLibrary(zip, "usr/lib/libexpat.so.1.11.1", expatVersion);
            copyRuntimeLibrary(expatVersion, new File(pythonLibraries, "libexpat.so.1"));
            copyRuntimeLibrary(expatVersion, new File(pythonLibraries, "libexpat.so"));
        }
    }

    private void extractRuntimeLibrary(
            ZipFile zip, String entryName, File destination) throws Exception {
        if (destination.isFile() && destination.length() > 1024L) return;
        ZipEntry entry = zip.getEntry(entryName);
        if (entry == null) {
            throw new Exception("O pacote do Estúdio está incompleto.");
        }
        try (InputStream input = zip.getInputStream(entry);
             OutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[128 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                ensureNotCancelled();
                if (read > 0) output.write(buffer, 0, read);
            }
        }
        destination.setReadable(true, false);
    }

    private void copyRuntimeLibrary(File source, File destination) throws Exception {
        if (destination.isFile() && destination.length() == source.length()) return;
        try (InputStream input = new FileInputStream(source);
             OutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[128 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                ensureNotCancelled();
                if (read > 0) output.write(buffer, 0, read);
            }
        }
        destination.setReadable(true, false);
    }

    private BeatAnalysis analyzeBeats(
            File ffmpeg, File audio, String mode,
            File packagesRoot, File ffmpegPackage, File workDirectory) throws Exception {
        List<String> command = new ArrayList<>();
        Collections.addAll(command,
                ffmpeg.getAbsolutePath(), "-v", "error", "-i", audio.getAbsolutePath(),
                "-ac", "1", "-ar", "11025", "-f", "s16le", "pipe:1");
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workDirectory);
        File analysisLog = new File(workDirectory, "beat-analysis.log");
        builder.redirectError(analysisLog);
        configureFfmpegEnvironment(builder, packagesRoot, ffmpegPackage);
        activeProcess = builder.start();

        final int sampleRate = 11025;
        final int windowSize = 512;
        final int maximumWindows = (int) Math.ceil(15d * 60d * sampleRate / windowSize);
        List<Double> energies = new ArrayList<>();
        byte[] buffer = new byte[32 * 1024];
        int carry = -1;
        int samples = 0;
        long amplitude = 0L;
        try (InputStream pcm = activeProcess.getInputStream()) {
            int read;
            while ((read = pcm.read(buffer)) >= 0 && energies.size() < maximumWindows) {
                ensureNotCancelled();
                if (read == 0) continue;
                int index = 0;
                if (carry >= 0 && read > 0) {
                    short sample = (short) (carry | (buffer[0] << 8));
                    amplitude += Math.abs((int) sample);
                    samples++;
                    index = 1;
                    carry = -1;
                    if (samples == windowSize) {
                        energies.add(amplitude / (double) samples);
                        samples = 0;
                        amplitude = 0L;
                    }
                }
                for (; index + 1 < read; index += 2) {
                    short sample = (short) ((buffer[index] & 0xff) | (buffer[index + 1] << 8));
                    amplitude += Math.abs((int) sample);
                    samples++;
                    if (samples == windowSize) {
                        energies.add(amplitude / (double) samples);
                        samples = 0;
                        amplitude = 0L;
                    }
                }
                if (index < read) carry = buffer[index] & 0xff;
            }
        }
        int exitCode = activeProcess.waitFor();
        activeProcess = null;
        ensureNotCancelled();
        if (exitCode != 0 || energies.size() < 12) {
            Log.e(TAG, "Análise musical encerrou com código " + exitCode
                    + ", janelas=" + energies.size() + ".\n"
                    + readTextTail(analysisLog, 6000));
            throw new Exception("Não foi possível analisar as batidas desta música.");
        }

        double threshold = "energetic".equals(mode) ? 1.22d
                : "calm".equals(mode) ? 1.52d : 1.34d;
        double minimumGap = "energetic".equals(mode) ? .18d
                : "calm".equals(mode) ? .48d : .28d;
        double globalAverage = 0d;
        for (double energy : energies) globalAverage += energy;
        globalAverage /= energies.size();
        double noiseGate = Math.max(160d, globalAverage * .48d);
        int radius = 12;
        List<Double> beats = new ArrayList<>();
        double lastBeat = -10d;
        for (int index = radius; index < energies.size() - radius; index++) {
            double localAverage = 0d;
            for (int nearby = index - radius; nearby <= index + radius; nearby++) {
                if (nearby != index) localAverage += energies.get(nearby);
            }
            localAverage /= radius * 2d;
            double energy = energies.get(index);
            if (energy < noiseGate || energy < localAverage * threshold) continue;
            boolean peak = true;
            for (int nearby = index - 2; nearby <= index + 2; nearby++) {
                if (energies.get(nearby) > energy) {
                    peak = false;
                    break;
                }
            }
            double time = index * windowSize / (double) sampleRate;
            if (peak && time - lastBeat >= minimumGap) {
                beats.add(time);
                lastBeat = time;
            }
        }

        double duration = energies.size() * windowSize / (double) sampleRate;
        if (beats.size() < 2) {
            double interval = "energetic".equals(mode) ? .4d
                    : "calm".equals(mode) ? 1d : .5d;
            for (double time = 0d; time < duration; time += interval) beats.add(time);
        }
        double bpm = estimateBpm(beats);
        return new BeatAnalysis(beats, bpm, duration);
    }

    private String readTextTail(File file, int maximumCharacters) {
        if (file == null || !file.isFile()) return "";
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append('\n');
                if (text.length() > maximumCharacters * 2) {
                    text.delete(0, text.length() - maximumCharacters);
                }
            }
        } catch (Exception ignored) { }
        if (text.length() > maximumCharacters) {
            return text.substring(text.length() - maximumCharacters);
        }
        return text.toString();
    }

    private double estimateBpm(List<Double> beats) {
        if (beats.size() < 2) return 0d;
        List<Double> intervals = new ArrayList<>();
        for (int index = 1; index < beats.size(); index++) {
            double interval = beats.get(index) - beats.get(index - 1);
            if (interval <= .12d || interval > 2d) continue;
            while (interval < .32d) interval *= 2d;
            while (interval > .78d) interval /= 2d;
            intervals.add(interval);
        }
        if (intervals.isEmpty()) return 0d;
        Collections.sort(intervals);
        double median = intervals.get(intervals.size() / 2);
        return clamp(60d / median, 60d, 200d);
    }

    private void alignScenesToBeats(
            List<StudioInput> inputs, BeatAnalysis analysis, double transitionDuration) {
        if (analysis == null || analysis.beats.isEmpty() || inputs.size() < 2) return;
        double boundary = 0d;
        for (int index = 0; index < inputs.size() - 1; index++) {
            StudioInput input = inputs.get(index);
            double desiredBoundary = boundary + Math.max(.4d, input.duration - transitionDuration);
            double beat = nearestBeatAfter(
                    analysis, desiredBoundary, boundary + .28d,
                    Math.max(.8d, input.duration * .7d));
            if (beat <= boundary) beat = desiredBoundary;
            double maximum = input.isVideo() ? Math.min(60d, input.sourceDuration) : 12d;
            input.duration = clamp(beat - boundary + transitionDuration, .5d, maximum);
            boundary += input.duration - transitionDuration;
        }
    }

    private double nearestBeatAfter(
            BeatAnalysis analysis, double target, double minimum, double tolerance) {
        double best = -1d;
        double bestDistance = Double.MAX_VALUE;
        int loops = analysis.duration > .1d
                ? Math.max(1, (int) Math.ceil((target + tolerance) / analysis.duration) + 1) : 1;
        for (int loop = 0; loop < loops; loop++) {
            double offset = loop * analysis.duration;
            for (double raw : analysis.beats) {
                double candidate = raw + offset;
                if (candidate < minimum) continue;
                double distance = Math.abs(candidate - target);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
                if (candidate > target + tolerance) break;
            }
        }
        return bestDistance <= tolerance ? best : target;
    }

    private double shortestDuration(List<StudioInput> inputs) {
        double shortest = inputs.get(0).duration;
        for (StudioInput input : inputs) shortest = Math.min(shortest, input.duration);
        return shortest;
    }

    private double projectDuration(
            List<StudioInput> inputs, double transitionDuration, double speed) {
        double duration = 0d;
        for (StudioInput input : inputs) duration += input.duration;
        duration -= transitionDuration * Math.max(0, inputs.size() - 1);
        return Math.max(.5d, duration / speed);
    }

    private String safeTransition(String value) {
        if ("fade".equals(value) || "slideleft".equals(value)
                || "circleopen".equals(value) || "cut".equals(value)) {
            return value;
        }
        return "fade";
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
        if (mime.startsWith("audio/ogg")) return "ogg";
        if (mime.startsWith("audio/wav") || mime.startsWith("audio/x-wav")) return "wav";
        if (mime.startsWith("audio/flac")) return "flac";
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
