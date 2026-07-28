package com.moura.downloads;

import android.content.Context;
import android.net.Uri;

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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class UiUpdateManager {
    interface Listener {
        void onEvent(String status, int progress, String message);
        void onActivated(int contentVersion);
    }

    private static final long MAX_DOWNLOAD_BYTES = 8L * 1024L * 1024L;
    private static final long MAX_EXTRACTED_BYTES = 16L * 1024L * 1024L;
    private static final int MAX_FILES = 160;

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean cancelled;
    private volatile boolean running;

    UiUpdateManager(Context context) {
        this.context = context.getApplicationContext();
    }

    synchronized boolean start(
            String url,
            String expectedSha256,
            int contentVersion,
            Listener listener
    ) {
        if (running) return false;
        if (!isTrustedUrl(url) || !isSha256(expectedSha256) || contentVersion <= 0) {
            listener.onEvent("error", 0, "Pacote de interface inválido.");
            return false;
        }
        running = true;
        cancelled = false;
        executor.execute(() -> downloadAndActivate(
                url, expectedSha256.toLowerCase(Locale.ROOT), contentVersion, listener));
        return true;
    }

    void cancel() {
        cancelled = true;
    }

    void shutdown() {
        cancelled = true;
        executor.shutdownNow();
    }

    static File currentDirectory(Context context) {
        return new File(new File(context.getFilesDir(), "ui_content"), "current");
    }

    private boolean isTrustedUrl(String value) {
        if (value == null) return false;
        try {
            Uri uri = Uri.parse(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "github.com".equalsIgnoreCase(uri.getHost())
                    && uri.getPath() != null
                    && uri.getPath().startsWith(
                    "/Leandroxx10/MusicaDownloader/releases/download/")
                    && uri.getPath().endsWith("/moura-interface.zip");
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isSha256(String value) {
        return value != null && value.matches("(?i)^[0-9a-f]{64}$");
    }

    private void downloadAndActivate(
            String url,
            String expectedSha256,
            int contentVersion,
            Listener listener
    ) {
        File root = new File(context.getFilesDir(), "ui_content");
        File archive = new File(root, "interface.part");
        File staging = new File(root, "staging");
        File current = currentDirectory(context);
        File previous = new File(root, "previous");
        try {
            if (!root.exists() && !root.mkdirs()) {
                throw new IllegalStateException("Não foi possível preparar a atualização rápida.");
            }
            deleteRecursively(archive);
            deleteRecursively(staging);
            if (!staging.mkdirs()) {
                throw new IllegalStateException("Não foi possível preparar os novos arquivos.");
            }

            listener.onEvent("ui-downloading", 0, "Preparando atualização rápida.");
            download(url, archive, listener);
            if (cancelled) throw new UpdateCancelledException();

            listener.onEvent("ui-verifying", 96, "Verificando a segurança do pacote.");
            String actualSha256 = sha256(archive);
            if (!actualSha256.equals(expectedSha256)) {
                throw new SecurityException("A verificação de segurança da interface falhou.");
            }

            extractSafely(archive, staging);
            validateBundle(staging);
            if (cancelled) throw new UpdateCancelledException();

            deleteRecursively(previous);
            if (current.exists() && !current.renameTo(previous)) {
                throw new IllegalStateException("Não foi possível guardar a interface anterior.");
            }
            if (!staging.renameTo(current)) {
                if (previous.exists()) previous.renameTo(current);
                throw new IllegalStateException("Não foi possível ativar a nova interface.");
            }
            deleteRecursively(previous);
            deleteRecursively(archive);
            listener.onEvent("ui-ready", 100, "Atualização rápida aplicada.");
            listener.onActivated(contentVersion);
        } catch (UpdateCancelledException ignored) {
            deleteRecursively(archive);
            deleteRecursively(staging);
            listener.onEvent("cancelled", 0, "Atualização rápida cancelada.");
        } catch (Exception error) {
            deleteRecursively(archive);
            deleteRecursively(staging);
            String message = error.getMessage();
            listener.onEvent("error", 0, message == null || message.trim().isEmpty()
                    ? "Não foi possível aplicar a atualização rápida." : message);
        } finally {
            running = false;
        }
    }

    private void download(String url, File target, Listener listener) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(45_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "MouraDownloadsAndroid/4.1");
        connection.setRequestProperty("Accept", "application/zip");
        connection.connect();
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IllegalStateException(
                        "Servidor respondeu com código " + responseCode + ".");
            }
            long total = connection.getContentLengthLong();
            if (total > MAX_DOWNLOAD_BYTES) {
                throw new IllegalStateException("O pacote rápido é maior que o limite seguro.");
            }
            long downloaded = 0L;
            int lastProgress = -1;
            byte[] buffer = new byte[32 * 1024];
            try (BufferedInputStream input =
                         new BufferedInputStream(connection.getInputStream());
                 FileOutputStream output = new FileOutputStream(target)) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (cancelled) throw new UpdateCancelledException();
                    downloaded += count;
                    if (downloaded > MAX_DOWNLOAD_BYTES) {
                        throw new IllegalStateException(
                                "O pacote rápido excedeu o limite seguro.");
                    }
                    output.write(buffer, 0, count);
                    int progress = total > 0
                            ? (int) Math.min(95, Math.round(downloaded * 95d / total))
                            : Math.min(90, (int) (downloaded / (64 * 1024)));
                    if (progress != lastProgress) {
                        lastProgress = progress;
                        String message = total > 0
                                ? humanSize(downloaded) + " de " + humanSize(total)
                                : humanSize(downloaded) + " baixados";
                        listener.onEvent("ui-downloading", progress, message);
                    }
                }
                output.getFD().sync();
            }
        } finally {
            connection.disconnect();
        }
    }

    private void extractSafely(File archive, File destination) throws Exception {
        String rootPath = destination.getCanonicalPath() + File.separator;
        long extractedBytes = 0L;
        int fileCount = 0;
        byte[] buffer = new byte[32 * 1024];
        try (ZipInputStream input =
                     new ZipInputStream(new BufferedInputStream(new FileInputStream(archive)))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (cancelled) throw new UpdateCancelledException();
                if (entry.getName().contains("\\") || entry.getName().startsWith("/")) {
                    throw new SecurityException("O pacote contém um caminho inválido.");
                }
                File target = new File(destination, entry.getName()).getCanonicalFile();
                if (!target.getPath().startsWith(rootPath)) {
                    throw new SecurityException("O pacote tentou acessar uma pasta indevida.");
                }
                if (entry.isDirectory()) {
                    if (!target.exists() && !target.mkdirs()) {
                        throw new IllegalStateException("Não foi possível criar uma pasta.");
                    }
                    continue;
                }
                fileCount += 1;
                if (fileCount > MAX_FILES) {
                    throw new SecurityException("O pacote contém arquivos demais.");
                }
                File parent = target.getParentFile();
                if (parent == null || (!parent.exists() && !parent.mkdirs())) {
                    throw new IllegalStateException("Não foi possível preparar um arquivo.");
                }
                try (FileOutputStream output = new FileOutputStream(target)) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        extractedBytes += count;
                        if (extractedBytes > MAX_EXTRACTED_BYTES) {
                            throw new SecurityException(
                                    "O conteúdo extraído excedeu o limite seguro.");
                        }
                        output.write(buffer, 0, count);
                    }
                    output.getFD().sync();
                }
                input.closeEntry();
            }
        }
    }

    private void validateBundle(File directory) {
        String[] required = {"index.html", "app.js", "styles.css", "download.css"};
        for (String name : required) {
            File file = new File(directory, name);
            if (!file.isFile() || file.length() == 0L) {
                throw new IllegalStateException(
                        "A atualização rápida está incompleta: " + name + ".");
            }
        }
    }

    private String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[32 * 1024];
        try (FileInputStream input = new FileInputStream(file)) {
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder value = new StringBuilder();
        for (byte item : digest.digest()) {
            value.append(String.format(Locale.ROOT, "%02x", item));
        }
        return value.toString();
    }

    private String humanSize(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double value = bytes / 1024d;
        if (value < 1024d) return String.format(Locale.getDefault(), "%.1f KB", value);
        value /= 1024d;
        return String.format(Locale.getDefault(), "%.1f MB", value);
    }

    private void deleteRecursively(File target) {
        if (target == null || !target.exists()) return;
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        target.delete();
    }

    private static class UpdateCancelledException extends Exception { }
}
