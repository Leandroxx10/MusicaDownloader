package com.moura.downloads;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String APP_HOST = "music-bd7a7.web.app";
    private static final String APP_ORIGIN = "https://" + APP_HOST + "/";
    private static final int STORAGE_PERMISSION_REQUEST = 40;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 41;
    private static final int EDITOR_MEDIA_REQUEST = 42;
    private static final int EDITOR_AUDIO_REQUEST = 43;
    private static final String MESSAGE_CHANNEL_ID = "moura_messages";
    private static final String PREFS = "moura_library";
    private static final String PLAYER_PREFS = "moura_player";
    private static final String UPDATE_PREFS = "moura_updates";
    static final String THEME_PREFS = "moura_theme";
    static final String THEME_COLOR_KEY = "accent_color";
    private static final String UPDATE_MANIFEST_URL =
            "https://github.com/Leandroxx10/MusicaDownloader/releases/download/latest/update.json";
    private static final String APP_DOWNLOAD_URL =
            "https://github.com/Leandroxx10/MusicaDownloader/releases/download/latest/moura-downloads.apk";
    private static final String PLAY_STORE_URL =
            "https://play.google.com/store/apps/details?id=com.moura.downloads";

    private WebView webView;
    private FrameLayout root;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private String pendingSharedText;
    private String pendingOpenView;
    private String pendingUpdateUrl;
    private String pendingUpdateSha256;
    private String pendingUpdateVersion;
    private boolean activityVisible;
    private boolean refreshUpdatesOnResume;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private UiUpdateManager uiUpdateManager;

    private int validatedThemeColor(String color) {
        try {
            if (color != null && color.matches("^#[0-9a-fA-F]{6}$")) {
                return Color.parseColor(color);
            }
        } catch (Exception ignored) { }
        return Color.rgb(66, 245, 123);
    }

    private void applyWindowTheme(String color) {
        int accent = validatedThemeColor(color);
        int dark = Color.rgb(
                Math.max(3, (int) (Color.red(accent) * 0.07f)),
                Math.max(7, (int) (Color.green(accent) * 0.07f)),
                Math.max(5, (int) (Color.blue(accent) * 0.07f)));
        getWindow().setStatusBarColor(dark);
        getWindow().setNavigationBarColor(dark);
    }

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String payload = intent.getStringExtra(DownloadService.EXTRA_PAYLOAD);
            if (payload == null) return;
            callJavascript("window.onNativeDownloadEvent", payload);
        }
    };

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String payload = intent.getStringExtra(UpdateService.EXTRA_PAYLOAD);
            if (payload != null) callJavascript("window.MouraUpdate.onProgress", payload);
            String path = intent.getStringExtra(UpdateService.EXTRA_FILE_PATH);
            if (path != null && activityVisible && canInstallPackages()) {
                openUpdateInstaller(path);
            }
        }
    };

    private final BroadcastReceiver editorReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String payload = intent.getStringExtra(VideoEditorService.EXTRA_PAYLOAD);
            if (payload != null) {
                callJavascript("window.MouraEditor.onEvent", payload);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createMessageNotificationChannel();
        applyWindowTheme(getSharedPreferences(THEME_PREFS, MODE_PRIVATE)
                .getString(THEME_COLOR_KEY, "#42f57b"));
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(5, 11, 8));
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(5, 11, 8));
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });
        setContentView(root);
        ViewCompat.requestApplyInsets(root);

        configureWebView();
        uiUpdateManager = new UiUpdateManager(this);
        discardInterfaceFromAnotherNativeRevision();
        registerAppReceivers();
        requestRuntimePermissions();
        readSharedText(getIntent());
        readOpenView(getIntent());
        webView.loadUrl(APP_ORIGIN + "index.html?app=android"
                + (BuildConfig.DEBUG ? "&preview=editor" : ""));
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setUserAgentString(settings.getUserAgentString() + " MouraDownloadsAndroid/4.0");

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }
        webView.setWebChromeClient(new AppWebChromeClient());
        webView.setWebViewClient(new LocalAssetClient());
    }

    private void registerAppReceivers() {
        IntentFilter downloadFilter = new IntentFilter(DownloadService.ACTION_DOWNLOAD_EVENT);
        IntentFilter updateFilter = new IntentFilter(UpdateService.ACTION_UPDATE_EVENT);
        IntentFilter editorFilter = new IntentFilter(VideoEditorService.ACTION_EDITOR_EVENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, downloadFilter, Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(updateReceiver, updateFilter, Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(editorReceiver, editorFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(downloadReceiver, downloadFilter);
            registerReceiver(updateReceiver, updateFilter);
            registerReceiver(editorReceiver, editorFilter);
        }
    }

    private void requestRuntimePermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]),
                    permissions.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            ? STORAGE_PERMISSION_REQUEST : NOTIFICATION_PERMISSION_REQUEST);
        }
    }

    private void readSharedText(Intent intent) {
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction()) &&
                intent.getType() != null && intent.getType().startsWith("text/")) {
            pendingSharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        }
    }

    private void readOpenView(Intent intent) {
        if (intent == null) return;
        String requested = intent.getStringExtra("open_view");
        if ("conta".equals(requested)) pendingOpenView = requested;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readSharedText(intent);
        readOpenView(intent);
        injectSharedText();
        injectOpenView();
    }

    private void injectSharedText() {
        if (pendingSharedText == null || pendingSharedText.trim().isEmpty()) return;
        String encoded = Base64.encodeToString(
                pendingSharedText.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        String js = "(function(){const t=decodeURIComponent(escape(atob('" + encoded + "')));" +
                "const m=t.match(/https?:\\/\\/[^\\s]+/i);if(m){const i=document.getElementById('mediaUrl');" +
                "if(i){i.value=m[0];i.dispatchEvent(new Event('input'));}}})();";
        webView.evaluateJavascript(js, null);
        pendingSharedText = null;
    }

    private void injectOpenView() {
        if (!"conta".equals(pendingOpenView)) return;
        callJavascript("window.MouraUI.showView", "\"conta\"");
        pendingOpenView = null;
    }

    private void callJavascript(String functionName, String jsonPayload) {
        if (webView == null) return;
        String encoded = Base64.encodeToString(
                jsonPayload.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        String js = "(function(){const parts='" + functionName + "'.split('.');let target=window;" +
                "for(const part of parts){if(part==='window')continue;target=target&&target[part];}" +
                "if(typeof target==='function'){target(JSON.parse(decodeURIComponent(escape(atob('" +
                encoded + "')))));}})();";
        runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }

    private String displayNameForUri(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) { }
        return uri.getLastPathSegment() == null ? "Mídia" : uri.getLastPathSegment();
    }

    private JSONObject editorItem(Uri uri) {
        JSONObject item = new JSONObject();
        try {
            String mime = getContentResolver().getType(uri);
            if (mime == null) mime = "application/octet-stream";
            item.put("uri", uri.toString());
            item.put("name", displayNameForUri(uri));
            item.put("mime", mime);
            String preview = editorPreviewData(uri, mime);
            if (!preview.isEmpty()) item.put("preview", preview);
        } catch (Exception ignored) { }
        return item;
    }

    private String editorPreviewData(Uri uri, String mime) {
        if (mime == null || (!mime.startsWith("image/") && !mime.startsWith("video/"))) {
            return "";
        }
        Bitmap source = null;
        try {
            if (mime.startsWith("video/")) {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                try {
                    retriever.setDataSource(this, uri);
                    source = retriever.getFrameAtTime(
                            0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                } finally {
                    try { retriever.release(); } catch (Exception ignored) { }
                }
            } else {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input != null) BitmapFactory.decodeStream(input, null, bounds);
                }
                int sample = 1;
                while (Math.max(bounds.outWidth, bounds.outHeight) / sample > 720) {
                    sample *= 2;
                }
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = Math.max(1, sample);
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input != null) source = BitmapFactory.decodeStream(input, null, options);
                }
            }
            if (source == null) return "";
            int width = source.getWidth();
            int height = source.getHeight();
            float factor = Math.min(1f, 360f / Math.max(width, height));
            Bitmap preview = factor < 1f
                    ? Bitmap.createScaledBitmap(
                            source,
                            Math.max(1, Math.round(width * factor)),
                            Math.max(1, Math.round(height * factor)),
                            true)
                    : source;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            preview.compress(Bitmap.CompressFormat.JPEG, 68, output);
            if (preview != source) preview.recycle();
            source.recycle();
            return "data:image/jpeg;base64,"
                    + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
        } catch (Exception ignored) {
            if (source != null && !source.isRecycled()) source.recycle();
            return "";
        }
    }

    private void persistEditorPermission(Intent data, Uri uri) {
        try {
            int flags = data.getFlags() & (
                    Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(
                    uri, flags & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) { }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == EDITOR_MEDIA_REQUEST) {
            List<Uri> selected = new ArrayList<>();
            ClipData clip = data.getClipData();
            if (clip != null) {
                int count = Math.min(12, clip.getItemCount());
                for (int index = 0; index < count; index++) {
                    Uri uri = clip.getItemAt(index).getUri();
                    persistEditorPermission(data, uri);
                    selected.add(uri);
                }
            } else if (data.getData() != null) {
                Uri uri = data.getData();
                persistEditorPermission(data, uri);
                selected.add(uri);
            }
            executor.execute(() -> {
                JSONArray items = new JSONArray();
                for (Uri uri : selected) items.put(editorItem(uri));
                JSONObject payload = new JSONObject();
                try { payload.put("items", items); } catch (Exception ignored) { }
                callJavascript("window.MouraEditor.onMediaSelected", payload.toString());
            });
        } else if (requestCode == EDITOR_AUDIO_REQUEST && data.getData() != null) {
            Uri uri = data.getData();
            persistEditorPermission(data, uri);
            executor.execute(() -> callJavascript(
                    "window.MouraEditor.onAudioSelected",
                    editorItem(uri).toString()));
        }
    }

    private void createMessageNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                MESSAGE_CHANNEL_ID,
                "Mensagens do Moura",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Avisos e mensagens enviados pelo administrador do aplicativo.");
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private void postMessageNotification(String rawTitle, String rawBody) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        String title = rawTitle == null || rawTitle.trim().isEmpty()
                ? "Nova mensagem no Moura" : rawTitle.trim();
        String body = rawBody == null ? "" : rawBody.trim();
        Intent openApp = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("open_view", "conta");
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                74,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(
                this, MESSAGE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(title.substring(0, Math.min(title.length(), 90)))
                .setContentText(body.substring(0, Math.min(body.length(), 180)))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        body.substring(0, Math.min(body.length(), 1200))))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify((int) (System.currentTimeMillis() & 0x7fffffff),
                    notification.build());
        }
    }

    private File outputDirectory() {
        return DownloadService.getOutputDirectory(this);
    }

    private String encodeFileId(File file) throws IOException {
        return Base64.encodeToString(file.getCanonicalPath().getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private File fileFromId(String id) throws IOException {
        String decoded = new String(Base64.decode(id, Base64.URL_SAFE | Base64.NO_WRAP), StandardCharsets.UTF_8);
        File base = outputDirectory().getCanonicalFile();
        File target = new File(decoded).getCanonicalFile();
        if (!target.getPath().startsWith(base.getPath() + File.separator)) {
            throw new SecurityException("Arquivo fora da biblioteca do aplicativo.");
        }
        return target;
    }

    private String metadataKey(String prefix, File file) throws IOException {
        return prefix + encodeFileId(file);
    }

    private String categoryFor(File file) throws IOException {
        String saved = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(metadataKey("cat_", file), null);
        if (saved != null && !saved.trim().isEmpty()) return saved;
        String mime = mimeForFile(file);
        return mime.startsWith("audio/") ? "Músicas" : mime.startsWith("video/") ? "Vídeos" : "Outros";
    }

    private boolean favoriteFor(File file) throws IOException {
        return getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(metadataKey("fav_", file), false);
    }

    private void setCategory(File file, String category) throws IOException {
        String clean = sanitizeCategory(category);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(metadataKey("cat_", file), clean).apply();
    }

    private void setFavorite(File file, boolean value) throws IOException {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(metadataKey("fav_", file), value).apply();
    }

    private void migrateMetadata(File oldFile, File newFile) throws IOException {
        String oldId = encodeFileId(oldFile);
        String newId = encodeFileId(newFile);
        String oldCatKey = metadataKey("cat_", oldFile);
        String oldFavKey = metadataKey("fav_", oldFile);
        String category = getSharedPreferences(PREFS, MODE_PRIVATE).getString(oldCatKey, null);
        boolean favorite = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(oldFavKey, false);
        android.content.SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .remove(oldCatKey).remove(oldFavKey);
        if (category != null) editor.putString(metadataKey("cat_", newFile), category);
        if (favorite) editor.putBoolean(metadataKey("fav_", newFile), true);
        editor.apply();

        android.content.SharedPreferences playerPrefs =
                getSharedPreferences(PLAYER_PREFS, MODE_PRIVATE);
        android.content.SharedPreferences.Editor playerEditor = playerPrefs.edit();
        long position = playerPrefs.getLong("position_" + oldId, 0L);
        long lastPlayed = playerPrefs.getLong("last_played_" + oldId, 0L);
        int playCount = playerPrefs.getInt("play_count_" + oldId, 0);
        if (position > 0L) playerEditor.putLong("position_" + newId, position);
        if (lastPlayed > 0L) playerEditor.putLong("last_played_" + newId, lastPlayed);
        if (playCount > 0) playerEditor.putInt("play_count_" + newId, playCount);
        if (oldId.equals(playerPrefs.getString("last_media_id", null))) {
            playerEditor.putString("last_media_id", newId);
        }
        playerEditor.remove("position_" + oldId)
                .remove("last_played_" + oldId)
                .remove("play_count_" + oldId)
                .apply();
    }

    private String libraryJson() {
        JSONArray items = new JSONArray();
        try {
            android.content.SharedPreferences playerPrefs =
                    getSharedPreferences(PLAYER_PREFS, MODE_PRIVATE);
            File dir = outputDirectory();
            File[] files = dir.listFiles(file -> file.isFile() && !file.getName().startsWith("."));
            if (files == null) files = new File[0];
            Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
            for (File file : files) {
                JSONObject item = new JSONObject();
                String mime = mimeForFile(file);
                String id = encodeFileId(file);
                item.put("id", id);
                item.put("name", file.getName());
                item.put("mime", mime);
                item.put("type", mime.startsWith("audio/") ? "audio" : mime.startsWith("video/") ? "video" : "file");
                item.put("size", file.length());
                item.put("modified", file.lastModified());
                item.put("category", categoryFor(file));
                item.put("favorite", favoriteFor(file));
                item.put("resumePosition",
                        playerPrefs.getLong("position_" + id, 0L));
                item.put("lastPlayed",
                        playerPrefs.getLong("last_played_" + id, 0L));
                item.put("playCount",
                        playerPrefs.getInt("play_count_" + id, 0));
                items.put(item);
            }
        } catch (Exception error) {
            try {
                JSONObject failure = new JSONObject();
                failure.put("error", safeMessage(error));
                items.put(failure);
            } catch (Exception ignored) { }
        }
        return items.toString();
    }

    private String mimeForFile(File file) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(file).toString());
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase(Locale.ROOT));
        if (mime != null) return mime;
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".opus") || name.endsWith(".wav")) return "audio/*";
        if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm") || name.endsWith(".mov")) return "video/*";
        return "application/octet-stream";
    }

    private File[] playableFiles() {
        File[] files = outputDirectory().listFiles(file -> {
            if (!file.isFile() || file.getName().startsWith(".")) return false;
            String mime = mimeForFile(file);
            return mime.startsWith("audio/") || mime.startsWith("video/");
        });
        if (files == null) files = new File[0];
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        return files;
    }

    private Intent playerIntent(File selected, boolean shuffle) throws Exception {
        String selectedId = encodeFileId(selected);
        String selectedMime = mimeForFile(selected);
        Uri selectedUri = FileProvider.getUriForFile(
                this, getPackageName() + ".fileprovider", selected);
        JSONArray queue = new JSONArray();
        File[] files = playableFiles();
        for (int index = 0; index < files.length && index < 200; index++) {
            File file = files[index];
            JSONObject item = new JSONObject();
            item.put("id", encodeFileId(file));
            item.put("name", file.getName());
            item.put("mime", mimeForFile(file));
            item.put("uri", FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", file).toString());
            queue.put(item);
        }

        Intent player = new Intent(this, PlayerActivity.class);
        player.putExtra(PlayerActivity.EXTRA_MEDIA_URI, selectedUri.toString());
        player.putExtra(PlayerActivity.EXTRA_MEDIA_ID, selectedId);
        player.putExtra(PlayerActivity.EXTRA_MEDIA_NAME, selected.getName());
        player.putExtra(PlayerActivity.EXTRA_MEDIA_MIME, selectedMime);
        player.putExtra(PlayerActivity.EXTRA_QUEUE_JSON, queue.toString());
        player.putExtra(PlayerActivity.EXTRA_SHUFFLE, shuffle);
        player.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return player;
    }

    private String sanitizeFilename(String value, String currentExtension) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .replaceAll("[\\p{Cntrl}/\\\\:*?\"<>|]", " ")
                .replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) normalized = "Moura download";
        if (normalized.length() > 120) normalized = normalized.substring(0, 120).trim();
        if (!currentExtension.isEmpty() && !normalized.toLowerCase(Locale.ROOT).endsWith(currentExtension.toLowerCase(Locale.ROOT))) {
            normalized += currentExtension;
        }
        return normalized;
    }

    private String sanitizeCategory(String value) {
        String clean = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .replaceAll("[\\p{Cntrl}/\\\\]", " ")
                .replaceAll("\\s+", " ").trim();
        if (clean.isEmpty()) clean = "Outros";
        return clean.length() > 40 ? clean.substring(0, 40).trim() : clean;
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot) : "";
    }

    private JSONObject actionResult(boolean success, String message) {
        JSONObject result = new JSONObject();
        try {
            result.put("success", success);
            result.put("message", message);
        } catch (Exception ignored) { }
        return result;
    }

    private String qrCodeDataUrl(String value) throws Exception {
        int size = 720;
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);
        BitMatrix matrix = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size, hints);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                pixels[y * size + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        bitmap.recycle();
        return "data:image/png;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
    }

    private String safeMessage(Throwable error) {
        String text = error.getMessage();
        return text == null || text.trim().isEmpty() ? "Não foi possível concluir a ação." : text;
    }

    private Intent shareIntentFor(File file) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeForFile(file));
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_TEXT, "Compartilhado pelo Moura Downloads");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    private boolean canResolve(Intent intent) {
        return intent.resolveActivity(getPackageManager()) != null;
    }

    private String fetchUpdateManifest() throws Exception {
        HttpURLConnection connection =
                (HttpURLConnection) new URL(UPDATE_MANIFEST_URL).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(18000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "MouraDownloadsAndroid/4.0");
        connection.connect();
        int responseCode = connection.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            connection.disconnect();
            throw new IllegalStateException("Servidor de atualização respondeu com código " + responseCode + ".");
        }
        try (InputStream input = connection.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            int total = 0;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > 256 * 1024) {
                    throw new IllegalStateException("O arquivo de atualização é maior que o esperado.");
                }
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        } finally {
            connection.disconnect();
        }
    }

    private String preferredApkKey() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equalsIgnoreCase(abi)) return "arm64";
            if ("armeabi-v7a".equalsIgnoreCase(abi)) return "armeabi";
        }
        return "universal";
    }

    private JSONObject updateStatus() {
        JSONObject result = new JSONObject();
        try {
            JSONObject manifest = new JSONObject(fetchUpdateManifest());
            int latestCode = manifest.optInt("versionCode", 0);
            String latestName = manifest.optString("versionName", "");
            int latestNativeRevision = manifest.optInt("nativeRevision", latestCode);
            int installedContentVersion = installedContentVersion();
            boolean nativeAvailable = latestNativeRevision > BuildConfig.NATIVE_REVISION;
            JSONObject interfaceBundle = manifest.optJSONObject("interfaceBundle");
            int latestContentVersion = interfaceBundle == null
                    ? 0 : interfaceBundle.optInt("contentVersion", 0);
            int requiredNativeRevision = interfaceBundle == null
                    ? Integer.MAX_VALUE
                    : interfaceBundle.optInt("requiredNativeRevision", Integer.MAX_VALUE);
            boolean interfaceAvailable = !nativeAvailable
                    && requiredNativeRevision == BuildConfig.NATIVE_REVISION
                    && latestContentVersion > installedContentVersion;
            boolean available = nativeAvailable || interfaceAvailable;
            JSONObject apks = manifest.optJSONObject("apks");
            JSONObject selected = apks == null ? null : apks.optJSONObject(preferredApkKey());
            if (selected == null && apks != null) selected = apks.optJSONObject("universal");

            result.put("success", true);
            result.put("available", available);
            result.put("updateType", nativeAvailable
                    ? "full" : interfaceAvailable ? "interface" : "none");
            result.put("currentVersionCode", BuildConfig.VERSION_CODE);
            result.put("currentVersionName", BuildConfig.VERSION_NAME);
            result.put("currentNativeRevision", BuildConfig.NATIVE_REVISION);
            result.put("currentContentVersion", installedContentVersion);
            result.put("versionCode", latestCode);
            result.put("versionName", latestName);
            result.put("nativeRevision", latestNativeRevision);
            result.put("contentVersion", latestContentVersion);
            result.put("notes", manifest.optString("notes", ""));
            result.put("mandatory", manifest.optBoolean("mandatory", false));
            result.put("publishedAt", manifest.optString("publishedAt", ""));
            result.put("autoUpdate", autoUpdatesEnabled());
            result.put("unmetered", isUnmeteredConnection());
            result.put("canInstall", canInstallPackages());
            if (nativeAvailable && selected != null) {
                result.put("apkUrl", selected.optString("url", ""));
                result.put("sha256", selected.optString("sha256", ""));
                result.put("size", selected.optLong("size", 0L));
                result.put("architecture", selected.optString("architecture", preferredApkKey()));
            } else if (interfaceAvailable && interfaceBundle != null) {
                result.put("bundleUrl", interfaceBundle.optString("url", ""));
                result.put("sha256", interfaceBundle.optString("sha256", ""));
                result.put("size", interfaceBundle.optLong("size", 0L));
            }
        } catch (Exception error) {
            try {
                result.put("success", false);
                result.put("available", false);
                result.put("currentVersionCode", BuildConfig.VERSION_CODE);
                result.put("currentVersionName", BuildConfig.VERSION_NAME);
                result.put("message", safeMessage(error));
            } catch (Exception ignored) { }
        }
        return result;
    }

    private int installedContentVersion() {
        return getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE)
                .getInt("content_version", BuildConfig.PACKAGED_CONTENT_VERSION);
    }

    private void discardInterfaceFromAnotherNativeRevision() {
        android.content.SharedPreferences preferences =
                getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE);
        File currentInterface = UiUpdateManager.currentDirectory(this);
        if (!currentInterface.isDirectory()) return;
        int contentNativeRevision = preferences.getInt(
                "content_native_revision", -1);
        if (contentNativeRevision == BuildConfig.NATIVE_REVISION) return;
        deleteRecursively(currentInterface);
        preferences.edit()
                .remove("content_version")
                .remove("content_native_revision")
                .apply();
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

    private boolean autoUpdatesEnabled() {
        return getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE)
                .getBoolean("auto_updates", true);
    }

    private boolean isUnmeteredConnection() {
        ConnectivityManager manager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        return manager != null && !manager.isActiveNetworkMetered();
    }

    private boolean canInstallPackages() {
        return !BuildConfig.PLAY_STORE_BUILD
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || getPackageManager().canRequestPackageInstalls());
    }

    private void maybeStartAutomaticUpdate(JSONObject status) {
        if (BuildConfig.PLAY_STORE_BUILD
                || !status.optBoolean("success")
                || !status.optBoolean("available")
                || !autoUpdatesEnabled()
                || !isUnmeteredConnection()) return;
        String updateType = status.optString("updateType", "full");
        if ("full".equals(updateType) && !canInstallPackages()) return;
        String version = status.optString("versionName", "");
        String url = "interface".equals(updateType)
                ? status.optString("bundleUrl", "")
                : status.optString("apkUrl", "");
        if (url.isEmpty()) return;

        android.content.SharedPreferences preferences =
                getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE);
        String attemptedVersion = preferences.getString("last_auto_version", "");
        long attemptedAt = preferences.getLong("last_auto_attempt", 0L);
        long sixHours = 6L * 60L * 60L * 1000L;
        if (version.equals(attemptedVersion)
                && System.currentTimeMillis() - attemptedAt < sixHours) return;
        preferences.edit()
                .putString("last_auto_version", version)
                .putLong("last_auto_attempt", System.currentTimeMillis())
                .apply();
        if ("interface".equals(updateType)) {
            startUiUpdate(url, status.optString("sha256", ""),
                    status.optInt("contentVersion", 0));
        } else {
            startUpdateService(url, status.optString("sha256", ""), version);
        }
    }

    private void checkForUpdatesAsync() {
        if (BuildConfig.PLAY_STORE_BUILD) {
            JSONObject status = new JSONObject();
            try {
                status.put("success", true);
                status.put("available", false);
                status.put("currentVersionCode", BuildConfig.VERSION_CODE);
                status.put("currentVersionName", BuildConfig.VERSION_NAME);
                status.put("distribution", "play");
                status.put("message", "Atualizações gerenciadas pela Google Play.");
            } catch (Exception ignored) { }
            callJavascript("window.MouraUpdate.onCheckResult", status.toString());
            return;
        }
        executor.execute(() -> {
            JSONObject status = updateStatus();
            callJavascript("window.MouraUpdate.onCheckResult", status.toString());
            maybeStartAutomaticUpdate(status);
        });
    }

    private void startUpdateService(String url, String sha256, String version) {
        Intent service = new Intent(this, UpdateService.class);
        service.setAction(UpdateService.ACTION_START);
        service.putExtra(UpdateService.EXTRA_URL, url);
        service.putExtra(UpdateService.EXTRA_SHA256, sha256);
        service.putExtra(UpdateService.EXTRA_VERSION, version);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
        else startService(service);
    }

    private boolean startUiUpdate(String url, String sha256, int contentVersion) {
        if (uiUpdateManager == null) return false;
        return uiUpdateManager.start(url, sha256, contentVersion,
                new UiUpdateManager.Listener() {
                    @Override
                    public void onEvent(String status, int progress, String message) {
                        JSONObject payload = new JSONObject();
                        try {
                            payload.put("status", status);
                            payload.put("progress", progress);
                            payload.put("message", message);
                        } catch (Exception ignored) { }
                        callJavascript("window.MouraUpdate.onProgress", payload.toString());
                    }

                    @Override
                    public void onActivated(int activatedVersion) {
                        getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE).edit()
                                .putInt("content_version", activatedVersion)
                                .putInt("content_native_revision", BuildConfig.NATIVE_REVISION)
                                .remove("last_auto_version")
                                .apply();
                        runOnUiThread(() -> {
                            if (webView != null) webView.postDelayed(webView::reload, 900L);
                        });
                    }
                });
    }

    private void requestInstallPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivity(settings);
        } catch (Exception ignored) {
            startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
        }
    }

    private void openUpdateInstaller(String path) {
        try {
            File base = new File(
                    getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS),
                    "updates").getCanonicalFile();
            File apk = new File(path).getCanonicalFile();
            if (!apk.exists() || !apk.getPath().startsWith(base.getPath() + File.separator)) {
                throw new SecurityException("Arquivo de atualização inválido.");
            }
            Uri uri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", apk);
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(uri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(install);
        } catch (Exception error) {
            Toast.makeText(this, safeMessage(error), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityVisible = true;
        if (webView != null) {
            callJavascript("window.onNativeDownloadEvent",
                    "{\"status\":\"library-ready\"}");
        }
        if (pendingUpdateUrl != null && canInstallPackages()) {
            String url = pendingUpdateUrl;
            String sha256 = pendingUpdateSha256;
            String version = pendingUpdateVersion;
            pendingUpdateUrl = null;
            pendingUpdateSha256 = null;
            pendingUpdateVersion = null;
            startUpdateService(url, sha256, version);
        } else if (refreshUpdatesOnResume && canInstallPackages()) {
            refreshUpdatesOnResume = false;
            checkForUpdatesAsync();
        }
    }

    @Override
    protected void onPause() {
        activityVisible = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(downloadReceiver); } catch (Exception ignored) { }
        try { unregisterReceiver(updateReceiver); } catch (Exception ignored) { }
        try { unregisterReceiver(editorReceiver); } catch (Exception ignored) { }
        executor.shutdownNow();
        if (uiUpdateManager != null) uiUpdateManager.shutdown();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (fullscreenView != null) {
            hideFullscreenVideo();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private void hideFullscreenVideo() {
        if (fullscreenView == null || root == null) return;
        root.removeView(fullscreenView);
        fullscreenView = null;
        webView.setVisibility(View.VISIBLE);
        if (fullscreenCallback != null) fullscreenCallback.onCustomViewHidden();
        fullscreenCallback = null;
    }

    public class AndroidBridge {
        @JavascriptInterface
        public String appMode() {
            return "android-local";
        }

        @JavascriptInterface
        public boolean debugMode() {
            return BuildConfig.DEBUG;
        }

        @JavascriptInterface
        public String readClipboard() {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) return "";
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return "";
            CharSequence text = clip.getItemAt(0).coerceToText(MainActivity.this);
            return text == null ? "" : text.toString();
        }

        @JavascriptInterface
        public String selectEditorMedia() {
            runOnUiThread(() -> {
                Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                picker.addCategory(Intent.CATEGORY_OPENABLE);
                picker.setType("*/*");
                picker.putExtra(Intent.EXTRA_MIME_TYPES,
                        new String[]{"video/*", "image/*"});
                picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(
                        Intent.createChooser(picker, "Escolher vídeo ou fotos"),
                        EDITOR_MEDIA_REQUEST);
            });
            return actionResult(true, "Escolha um vídeo ou até 12 fotos.").toString();
        }

        @JavascriptInterface
        public String selectEditorAudio() {
            runOnUiThread(() -> {
                Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                picker.addCategory(Intent.CATEGORY_OPENABLE);
                picker.setType("audio/*");
                picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(
                        Intent.createChooser(picker, "Escolher música"),
                        EDITOR_AUDIO_REQUEST);
            });
            return actionResult(true, "Escolha uma música do celular.").toString();
        }

        @JavascriptInterface
        public String startVideoEditor(String configJson) {
            try {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                        checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                != PackageManager.PERMISSION_GRANTED) {
                    runOnUiThread(() -> requestPermissions(
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            STORAGE_PERMISSION_REQUEST));
                    return actionResult(false,
                            "Autorize o armazenamento e toque em Criar vídeo novamente.").toString();
                }
                JSONObject config = new JSONObject(configJson == null ? "{}" : configJson);
                JSONArray media = config.optJSONArray("media");
                if (media == null || media.length() == 0 || media.length() > 12) {
                    return actionResult(false,
                            "Escolha um vídeo ou de 1 a 12 imagens.").toString();
                }
                Intent editor = new Intent(MainActivity.this, VideoEditorService.class);
                editor.setAction(VideoEditorService.ACTION_START);
                editor.putExtra(VideoEditorService.EXTRA_CONFIG, config.toString());
                runOnUiThread(() -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(editor);
                    } else {
                        startService(editor);
                    }
                });
                return actionResult(true,
                        "Criação iniciada. Você pode continuar usando o app.").toString();
            } catch (Exception error) {
                return actionResult(false, "Configuração do projeto inválida.").toString();
            }
        }

        @JavascriptInterface
        public String cancelVideoEditor() {
            Intent cancel = new Intent(MainActivity.this, VideoEditorService.class);
            cancel.setAction(VideoEditorService.ACTION_CANCEL);
            runOnUiThread(() -> startService(cancel));
            return actionResult(true, "Cancelando a criação do vídeo.").toString();
        }

        private Uri validEditorOutput(String rawUri) {
            if (rawUri == null || rawUri.length() > 2048) return null;
            Uri uri = Uri.parse(rawUri);
            return "content".equalsIgnoreCase(uri.getScheme()) ? uri : null;
        }

        @JavascriptInterface
        public String openEditorOutput(String rawUri) {
            try {
                Uri uri = validEditorOutput(rawUri);
                if (uri == null) return actionResult(false, "Vídeo não encontrado.").toString();
                Intent open = new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "video/mp4")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                runOnUiThread(() -> startActivity(
                        Intent.createChooser(open, "Reproduzir vídeo")));
                return actionResult(true, "Abrindo seu vídeo.").toString();
            } catch (Exception error) {
                return actionResult(false, safeMessage(error)).toString();
            }
        }

        @JavascriptInterface
        public String shareEditorOutput(String rawUri) {
            try {
                Uri uri = validEditorOutput(rawUri);
                if (uri == null) return actionResult(false, "Vídeo não encontrado.").toString();
                Intent share = new Intent(Intent.ACTION_SEND)
                        .setType("video/mp4")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                runOnUiThread(() -> startActivity(
                        Intent.createChooser(share, "Compartilhar seu vídeo")));
                return actionResult(true, "Escolha onde compartilhar.").toString();
            } catch (Exception error) {
                return actionResult(false, safeMessage(error)).toString();
            }
        }

        @JavascriptInterface
        public String setThemeColor(String color) {
            if (color == null || !color.matches("^#[0-9a-fA-F]{6}$")) {
                return actionResult(false, "Cor inválida.").toString();
            }
            getSharedPreferences(THEME_PREFS, MODE_PRIVATE).edit()
                    .putString(THEME_COLOR_KEY, color.toLowerCase(Locale.ROOT)).apply();
            runOnUiThread(() -> applyWindowTheme(color));
            return actionResult(true, "Tema completo atualizado.").toString();
        }

        @JavascriptInterface
        public String getInstalledVersion() {
            JSONObject info = new JSONObject();
            try {
                info.put("versionCode", BuildConfig.VERSION_CODE);
                info.put("versionName", BuildConfig.VERSION_NAME);
                info.put("nativeRevision", BuildConfig.NATIVE_REVISION);
                info.put("contentVersion", installedContentVersion());
                info.put("autoUpdate", autoUpdatesEnabled());
                info.put("canInstall", canInstallPackages());
                info.put("distribution",
                        BuildConfig.PLAY_STORE_BUILD ? "play" : "sideload");
            } catch (Exception ignored) { }
            return info.toString();
        }

        @JavascriptInterface
        public void checkForUpdates() {
            checkForUpdatesAsync();
        }

        @JavascriptInterface
        public String setAutoUpdatesEnabled(boolean enabled) {
            if (BuildConfig.PLAY_STORE_BUILD) {
                return actionResult(true,
                        "As atualizações são gerenciadas pela Google Play.").toString();
            }
            getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE).edit()
                    .putBoolean("auto_updates", enabled).apply();
            return actionResult(true, enabled
                    ? "Atualizações automáticas ativadas no Wi-Fi."
                    : "Atualizações automáticas desativadas.").toString();
        }

        @JavascriptInterface
        public String prepareAppUpdates() {
            if (BuildConfig.PLAY_STORE_BUILD) {
                return actionResult(false,
                        "Esta versão recebe atualizações pela Google Play.").toString();
            }
            if (canInstallPackages()) {
                return actionResult(true, "O aparelho já está preparado para atualizações.").toString();
            }
            refreshUpdatesOnResume = true;
            runOnUiThread(MainActivity.this::requestInstallPermission);
            return actionResult(true,
                    "Ative “Permitir desta fonte” e volte ao Moura Downloads.").toString();
        }

        @JavascriptInterface
        public String startAppUpdate(String url, String sha256, String version) {
            try {
                if (BuildConfig.PLAY_STORE_BUILD) {
                    return actionResult(false,
                            "Esta versão recebe atualizações pela Google Play.").toString();
                }
                if (url == null || !url.startsWith(
                        "https://github.com/Leandroxx10/MusicaDownloader/releases/download/")) {
                    return actionResult(false, "Endereço de atualização inválido.").toString();
                }
                if (!canInstallPackages()) {
                    pendingUpdateUrl = url;
                    pendingUpdateSha256 = sha256;
                    pendingUpdateVersion = version;
                    JSONObject result = actionResult(true,
                            "Ative “Permitir desta fonte”. O download começará ao voltar.");
                    result.put("permissionRequired", true);
                    runOnUiThread(MainActivity.this::requestInstallPermission);
                    return result.toString();
                }
                runOnUiThread(() -> startUpdateService(url, sha256, version));
                return actionResult(true,
                        "Atualização iniciada. Você pode continuar usando o app.").toString();
            } catch (Exception error) {
                return actionResult(false, safeMessage(error)).toString();
            }
        }

        @JavascriptInterface
        public String startInterfaceUpdate(
                String url, String sha256, int contentVersion) {
            try {
                if (BuildConfig.PLAY_STORE_BUILD) {
                    return actionResult(false,
                            "Esta versão recebe atualizações pela Google Play.").toString();
                }
                boolean started = startUiUpdate(url, sha256, contentVersion);
                return actionResult(started, started
                        ? "Atualização rápida iniciada."
                        : "Já existe uma atualização rápida em andamento.").toString();
            } catch (Exception error) {
                return actionResult(false, safeMessage(error)).toString();
            }
        }

        @JavascriptInterface
        public String cancelAppUpdate() {
            if (uiUpdateManager != null) uiUpdateManager.cancel();
            Intent cancel = new Intent(MainActivity.this, UpdateService.class);
            cancel.setAction(UpdateService.ACTION_CANCEL);
            runOnUiThread(() -> startService(cancel));
            return actionResult(true, "Cancelando atualização.").toString();
        }

        @JavascriptInterface
        public void startLocalDownload(String url, String format, String category, String quality) {
            runOnUiThread(() -> {
                if (url == null || (!url.startsWith("https://") && !url.startsWith("http://"))) {
                    Toast.makeText(MainActivity.this, "Link inválido.", Toast.LENGTH_LONG).show();
                    return;
                }
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                        checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST);
                    Toast.makeText(MainActivity.this, "Autorize o armazenamento e tente novamente.", Toast.LENGTH_LONG).show();
                    return;
                }
                Intent service = new Intent(MainActivity.this, DownloadService.class);
                service.setAction(DownloadService.ACTION_START);
                service.putExtra(DownloadService.EXTRA_URL, url);
                service.putExtra(DownloadService.EXTRA_FORMAT,
                        "mp3".equalsIgnoreCase(format) ? "mp3" : "mp4");
                service.putExtra(DownloadService.EXTRA_CATEGORY, sanitizeCategory(category));
                service.putExtra(DownloadService.EXTRA_QUALITY,
                        "best".equalsIgnoreCase(quality) || "data".equalsIgnoreCase(quality)
                                ? quality.toLowerCase(Locale.ROOT) : "fast");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
                else startService(service);
            });
        }

        @JavascriptInterface
        public String cancelLocalDownload() {
            Intent cancel = new Intent(MainActivity.this, DownloadService.class);
            cancel.setAction(DownloadService.ACTION_CANCEL);
            runOnUiThread(() -> startService(cancel));
            return actionResult(true, "Cancelando download.").toString();
        }

        @JavascriptInterface
        public String listDownloads() {
            return libraryJson();
        }

        @JavascriptInterface
        public String renameDownload(String id, String newName) {
            try {
                File oldFile = fileFromId(id);
                if (!oldFile.exists()) return actionResult(false, "Arquivo não encontrado.").toString();
                String finalName = sanitizeFilename(newName, extensionOf(oldFile.getName()));
                File newFile = new File(oldFile.getParentFile(), finalName);
                if (newFile.exists()) return actionResult(false, "Já existe um arquivo com esse nome.").toString();
                String oldPath = oldFile.getAbsolutePath();
                if (!oldFile.renameTo(newFile)) return actionResult(false, "O Android não permitiu renomear o arquivo.").toString();
                migrateMetadata(oldFile, newFile);
                android.media.MediaScannerConnection.scanFile(MainActivity.this,
                        new String[]{oldPath, newFile.getAbsolutePath()}, new String[]{mimeForFile(newFile), mimeForFile(newFile)}, null);
                return actionResult(true, "Arquivo renomeado.").toString();
            } catch (Exception error) {
                return actionResult(false, safeMessage(error)).toString();
            }
        }

        @JavascriptInterface
        public String deleteDownload(String id) {
            try {
                File file = fileFromId(id);
                if (!file.exists()) return actionResult(false, "Arquivo não encontrado.").toString();
                String catKey = metadataKey("cat_", file);
                String favKey = metadataKey("fav_", file);
                String deletedPath = file.getAbsolutePath();
                if (!file.delete()) return actionResult(false, "O Android não permitiu excluir o arquivo.").toString();
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(catKey).remove(favKey).apply();
                getSharedPreferences(PLAYER_PREFS, MODE_PRIVATE).edit()
                        .remove("position_" + id)
                        .remove("last_played_" + id)
                        .remove("play_count_" + id)
                        .apply();
                android.media.MediaScannerConnection.scanFile(MainActivity.this, new String[]{deletedPath}, null, null);
                return actionResult(true, "Arquivo excluído do celular.").toString();
            } catch (Exception error) {
                return actionResult(false, safeMessage(error)).toString();
            }
        }

        @JavascriptInterface
        public String setDownloadCategory(String id, String category) {
            try {
                File file = fileFromId(id);
                if (!file.exists()) return actionResult(false, "Arquivo não encontrado.").toString();
                setCategory(file, category);
                return actionResult(true, "Categoria alterada.").toString();
            } catch (Exception error) {
                return actionResult(false, safeMessage(error)).toString();
            }
        }

        @JavascriptInterface
        public String toggleFavorite(String id) {
            try {
                File file = fileFromId(id);
                if (!file.exists()) return actionResult(false, "Arquivo não encontrado.").toString();
                boolean next = !favoriteFor(file);
                setFavorite(file, next);
                JSONObject result = actionResult(true, next ? "Adicionado aos favoritos." : "Removido dos favoritos.");
                result.put("favorite", next);
                return result.toString();
            } catch (Exception error) {
                return actionResult(false, safeMessage(error)).toString();
            }
        }

        @JavascriptInterface
        public String openDownload(String id) {
            try {
                File file = fileFromId(id);
                if (!file.exists()) return actionResult(false, "Arquivo não encontrado.").toString();
                Intent intent = new Intent(Intent.ACTION_VIEW);
                Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", file);
                intent.setDataAndType(uri, mimeForFile(file));
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                runOnUiThread(() -> startActivity(Intent.createChooser(intent, "Abrir arquivo")));
                return actionResult(true, "Abrindo arquivo.").toString();
            } catch (Exception error) {
                return actionResult(false, safeMessage(error)).toString();
            }
        }

        @JavascriptInterface
        public String playDownload(String id) {
            try {
                File file = fileFromId(id);
                if (!file.exists()) return actionResult(false, "Arquivo não encontrado.").toString();
                String mime = mimeForFile(file);
                if (!mime.startsWith("audio/") && !mime.startsWith("video/")) {
                    return actionResult(false, "Este tipo de arquivo não pode ser reproduzido no app.").toString();
                }
                Intent player = playerIntent(file, false);
                runOnUiThread(() -> startActivity(player));
                return actionResult(true, "Abrindo o reprodutor do Moura.").toString();
            } catch (Exception error) {
                return actionResult(false, safeMessage(error)).toString();
            }
        }

        @JavascriptInterface
        public String playSmartMix(String mode) {
            try {
                File[] files = playableFiles();
                if (files.length == 0) {
                    return actionResult(false,
                            "Baixe uma música ou vídeo para criar sua Mix.").toString();
                }
                File selected = files[0];
                String lastId = getSharedPreferences(PLAYER_PREFS, MODE_PRIVATE)
                        .getString("last_media_id", null);
                if ("continue".equalsIgnoreCase(mode) && lastId != null) {
                    try {
                        File previous = fileFromId(lastId);
                        if (previous.exists()) selected = previous;
                    } catch (Exception ignored) { }
                }
                if ("rediscover".equalsIgnoreCase(mode)) {
                    android.content.SharedPreferences playerPrefs =
                            getSharedPreferences(PLAYER_PREFS, MODE_PRIVATE);
                    int lowestPlays = Integer.MAX_VALUE;
                    long oldestPlay = Long.MAX_VALUE;
                    for (File candidate : files) {
                        String candidateId = encodeFileId(candidate);
                        int plays = playerPrefs.getInt(
                                "play_count_" + candidateId, 0);
                        long lastPlayed = playerPrefs.getLong(
                                "last_played_" + candidateId, 0L);
                        if (plays < lowestPlays
                                || (plays == lowestPlays && lastPlayed < oldestPlay)) {
                            selected = candidate;
                            lowestPlays = plays;
                            oldestPlay = lastPlayed;
                        }
                    }
                }
                Intent player = playerIntent(
                        selected, "shuffle".equalsIgnoreCase(mode)
                                || "rediscover".equalsIgnoreCase(mode));
                runOnUiThread(() -> startActivity(player));
                return actionResult(true,
                        "Minha Mix aberta no player em segundo plano.").toString();
            } catch (Exception error) {
                return actionResult(false, safeMessage(error)).toString();
            }
        }

        @JavascriptInterface
        public String shareDownload(String id) {
            try {
                File file = fileFromId(id);
                if (!file.exists()) return actionResult(false, "Arquivo não encontrado.").toString();
                Intent share = shareIntentFor(file);
                runOnUiThread(() -> startActivity(Intent.createChooser(share, "Compartilhar arquivo")));
                return actionResult(true, "Menu de compartilhamento aberto.").toString();
            } catch (Exception error) {
                return actionResult(false, safeMessage(error)).toString();
            }
        }

        @JavascriptInterface
        public String shareNearby(String id) {
            try {
                File file = fileFromId(id);
                if (!file.exists()) return actionResult(false, "Arquivo não encontrado.").toString();
                Intent share = shareIntentFor(file);
                share.putExtra(Intent.EXTRA_TITLE, "Enviar para aparelho próximo");
                runOnUiThread(() -> startActivity(Intent.createChooser(
                        share, "Enviar para aparelho próximo")));
                return actionResult(true,
                        "Escolha Quick Share, Bluetooth ou outro aparelho próximo.").toString();
            } catch (Exception error) {
                return actionResult(false, safeMessage(error)).toString();
            }
        }

        @JavascriptInterface
        public String showMessageNotification(String title, String body) {
            try {
                runOnUiThread(() -> postMessageNotification(title, body));
                return actionResult(true, "Notificação exibida.").toString();
            } catch (Exception error) {
                return actionResult(false, safeMessage(error)).toString();
            }
        }

        @JavascriptInterface
        public String shareWhatsApp(String id) {
            try {
                File file = fileFromId(id);
                if (!file.exists()) return actionResult(false, "Arquivo não encontrado.").toString();
                Intent regular = shareIntentFor(file);
                regular.setPackage("com.whatsapp");
                if (canResolve(regular)) {
                    runOnUiThread(() -> startActivity(regular));
                    return actionResult(true, "Abrindo WhatsApp.").toString();
                }
                Intent business = shareIntentFor(file);
                business.setPackage("com.whatsapp.w4b");
                if (canResolve(business)) {
                    runOnUiThread(() -> startActivity(business));
                    return actionResult(true, "Abrindo WhatsApp Business.").toString();
                }
                return actionResult(false, "WhatsApp não está instalado neste celular.").toString();
            } catch (Exception error) {
                return actionResult(false, safeMessage(error)).toString();
            }
        }

        @JavascriptInterface
        public String getAppShareInfo() {
            JSONObject info = new JSONObject();
            try {
                info.put("version", BuildConfig.VERSION_NAME);
                info.put("developer", "Leandro Moura");
                info.put("qrDataUrl", qrCodeDataUrl(
                        BuildConfig.PLAY_STORE_BUILD ? PLAY_STORE_URL : APP_DOWNLOAD_URL));
            } catch (Exception error) {
                try { info.put("error", safeMessage(error)); } catch (Exception ignored) { }
            }
            return info.toString();
        }
    }

    private class LocalAssetClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (APP_HOST.equals(uri.getHost())) {
                String path = uri.getPath();
                if (path == null || path.equals("/") || path.trim().isEmpty()) path = "/index.html";
                path = path.replaceFirst("^/", "");
                if (path.contains("..")) {
                    return new WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", null, null);
                }
                try {
                    File contentRoot = UiUpdateManager.currentDirectory(
                            MainActivity.this).getCanonicalFile();
                    File updatedAsset = new File(contentRoot, path).getCanonicalFile();
                    if (updatedAsset.isFile()
                            && updatedAsset.getPath().startsWith(
                            contentRoot.getPath() + File.separator)) {
                        return new WebResourceResponse(
                                mimeForAsset(path), "UTF-8",
                                new FileInputStream(updatedAsset));
                    }
                    InputStream stream = getAssets().open("www/" + path);
                    return new WebResourceResponse(mimeForAsset(path), "UTF-8", stream);
                } catch (IOException ignored) {
                    try {
                        InputStream stream = getAssets().open("www/offline.html");
                        return new WebResourceResponse("text/html", "UTF-8", 404, "Not Found", null, stream);
                    } catch (IOException fatal) {
                        return null;
                    }
                }
            }
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (APP_HOST.equals(uri.getHost())) return false;
            if (!request.isForMainFrame()) return false;
            if ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            }
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            injectSharedText();
            injectOpenView();
            callJavascript("window.onNativeDownloadEvent", "{\"status\":\"library-ready\"}");
        }

        private String mimeForAsset(String path) {
            String extension = MimeTypeMap.getFileExtensionFromUrl(path.toLowerCase(Locale.ROOT));
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime != null) return mime;
            if (path.endsWith(".webmanifest")) return "application/manifest+json";
            return "application/octet-stream";
        }
    }

    private class AppWebChromeClient extends WebChromeClient {
        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (fullscreenView != null) {
                callback.onCustomViewHidden();
                return;
            }
            fullscreenView = view;
            fullscreenCallback = callback;
            webView.setVisibility(View.GONE);
            root.addView(view, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }

        @Override
        public void onHideCustomView() {
            hideFullscreenVideo();
        }
    }
}
