package com.moura.downloads;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.PlayerView;

import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

@UnstableApi
public class PlayerActivity extends Activity {
    public static final String EXTRA_MEDIA_URI = "media_uri";
    public static final String EXTRA_MEDIA_ID = "media_id";
    public static final String EXTRA_MEDIA_NAME = "media_name";
    public static final String EXTRA_MEDIA_MIME = "media_mime";
    public static final String EXTRA_QUEUE_JSON = "queue_json";
    public static final String EXTRA_SHUFFLE = "shuffle";

    private static final String LIBRARY_PREFS = "moura_library";
    private static final String STATE_POSITION = "position";
    private static final String STATE_MEDIA_ID = "media_id";

    private PlayerView playerView;
    private EnergyVisualizerView energyVisualizer;
    private LinearLayout audioControls;
    private SeekBar positionSeekBar;
    private TextView elapsedView;
    private TextView durationView;
    private Button playPauseButton;
    private Player player;
    private MediaController controller;
    private ListenableFuture<MediaController> controllerFuture;
    private TextView titleView;
    private TextView nowPlayingView;
    private TextView queueStatusView;
    private Button favoriteButton;
    private Button shuffleButton;
    private Button repeatButton;
    private Button speedButton;
    private Button sleepButton;
    private Button visualButton;
    private Button bookmarkButton;
    private Button jumpBookmarkButton;
    private Button moreOptionsButton;
    private LinearLayout advancedControls;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Uri mediaUri;
    private String mediaId;
    private String mediaName;
    private String mediaMime;
    private String queueJson;
    private boolean initialShuffle;
    private boolean restoreExistingSession;
    private boolean userSeeking;
    private long playbackPosition;
    private int speedIndex;
    private int visualThemeIndex;
    private int accentColor = Color.rgb(66, 245, 123);
    private int accentDark = Color.rgb(8, 24, 14);
    private int accentPanel = Color.rgb(10, 30, 18);

    private final float[] speeds = {1f, 1.25f, 1.5f, 2f};
    private final int[] sleepOptions = {0, 15, 30, 45, 60};
    private final String[] visualThemeNames = {
            "Energia", "Neon", "Aurora"
    };

    private final Runnable positionUpdater = new Runnable() {
        @Override
        public void run() {
            updatePositionUi();
            uiHandler.postDelayed(this, 250L);
        }
    };

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onMediaItemTransition(MediaItem mediaItem, int reason) {
            updateCurrentMedia(mediaItem);
        }

        @Override
        public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            updatePlayerButtons();
        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {
            updatePlayerButtons();
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            updatePlaybackUi();
        }

        @Override
        public void onPlaybackStateChanged(int playbackState) {
            updatePlaybackUi();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            accentColor = Color.parseColor(getSharedPreferences(
                    MainActivity.THEME_PREFS, MODE_PRIVATE).getString(
                    MainActivity.THEME_COLOR_KEY, "#42f57b"));
        } catch (Exception ignored) {
            accentColor = Color.rgb(66, 245, 123);
        }
        accentDark = mix(Color.rgb(3, 8, 6), accentColor, .09f);
        accentPanel = mix(Color.rgb(7, 17, 11), accentColor, .14f);
        getWindow().setStatusBarColor(accentDark);
        getWindow().setNavigationBarColor(accentDark);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        Intent intent = getIntent();
        String uriValue = intent.getStringExtra(EXTRA_MEDIA_URI);
        mediaId = intent.getStringExtra(EXTRA_MEDIA_ID);
        mediaName = intent.getStringExtra(EXTRA_MEDIA_NAME);
        mediaMime = intent.getStringExtra(EXTRA_MEDIA_MIME);
        queueJson = intent.getStringExtra(EXTRA_QUEUE_JSON);
        initialShuffle = intent.getBooleanExtra(EXTRA_SHUFFLE, false);

        if (uriValue != null) mediaUri = Uri.parse(uriValue);
        if (mediaName == null || mediaName.trim().isEmpty()) {
            mediaName = "Moura Player";
        }
        if (mediaMime == null || mediaMime.trim().isEmpty()) {
            mediaMime = "application/octet-stream";
        }

        if (savedInstanceState != null) {
            playbackPosition = savedInstanceState.getLong(STATE_POSITION, 0L);
            mediaId = savedInstanceState.getString(STATE_MEDIA_ID, mediaId);
            restoreExistingSession = true;
        } else if (mediaId != null) {
            playbackPosition = getSharedPreferences(
                    PlaybackService.PLAYER_PREFS, MODE_PRIVATE)
                    .getLong(positionKey(mediaId), 0L);
        }

        LinearLayout layout = buildLayout();
        ViewCompat.setOnApplyWindowInsetsListener(layout, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });
        setContentView(layout);
        ViewCompat.requestApplyInsets(layout);
    }

    private LinearLayout buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(accentDark);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(8), dp(10), dp(8));
        header.setBackgroundColor(accentPanel);

        Button backButton = headerButton("‹ Voltar");
        backButton.setOnClickListener(view -> finish());
        header.addView(backButton);

        titleView = new TextView(this);
        titleView.setText(mediaName);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(15);
        titleView.setGravity(Gravity.CENTER);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(0, dp(48), 1f);
        titleParams.setMargins(dp(8), 0, dp(8), 0);
        header.addView(titleView, titleParams);

        Button shareButton = headerButton("Compartilhar");
        shareButton.setOnClickListener(view -> shareMedia());
        header.addView(shareButton);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout mediaStage = new FrameLayout(this);
        mediaStage.setBackgroundColor(mix(Color.BLACK, accentColor, .045f));

        energyVisualizer = new EnergyVisualizerView(this);
        energyVisualizer.setAccentColor(accentColor);
        mediaStage.addView(energyVisualizer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        playerView = new PlayerView(this);
        playerView.setBackgroundColor(Color.TRANSPARENT);
        playerView.setShutterBackgroundColor(Color.TRANSPARENT);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        mediaStage.addView(playerView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        audioControls = buildAudioControls();
        FrameLayout.LayoutParams audioParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        audioParams.setMargins(dp(10), 0, dp(10), dp(8));
        mediaStage.addView(audioControls, audioParams);

        root.addView(mediaStage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(buildExperiencePanel(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private LinearLayout buildAudioControls() {
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(10), dp(6), dp(10), dp(8));
        GradientDrawable background = new GradientDrawable();
        background.setColor(withAlpha(accentDark, 226));
        background.setCornerRadius(dp(18));
        background.setStroke(dp(1), withAlpha(accentColor, 125));
        controls.setBackground(background);

        positionSeekBar = new SeekBar(this);
        positionSeekBar.setMax(1000);
        positionSeekBar.setProgressTintList(
                android.content.res.ColorStateList.valueOf(
                        accentColor));
        positionSeekBar.setThumbTintList(
                android.content.res.ColorStateList.valueOf(Color.WHITE));
        positionSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar, int progress, boolean fromUser) {
                        if (!fromUser || player == null) return;
                        long duration = player.getDuration();
                        if (duration > 0L && duration != C.TIME_UNSET) {
                            elapsedView.setText(formatTime(
                                    duration * progress / 1000L));
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        userSeeking = true;
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        if (player != null) {
                            long duration = player.getDuration();
                            if (duration > 0L && duration != C.TIME_UNSET) {
                                player.seekTo(
                                        duration * seekBar.getProgress() / 1000L);
                            }
                        }
                        userSeeking = false;
                    }
                });
        controls.addView(positionSeekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));

        LinearLayout timeRow = new LinearLayout(this);
        elapsedView = timeText("0:00", Gravity.START);
        durationView = timeText("0:00", Gravity.END);
        timeRow.addView(elapsedView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        timeRow.addView(durationView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        controls.addView(timeRow);

        LinearLayout playbackRow = controlRow();
        Button previousButton = audioControlButton("⏮");
        previousButton.setContentDescription("Música anterior");
        previousButton.setOnClickListener(view -> {
            if (player != null && player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem();
            } else if (player != null) {
                player.seekTo(0L);
            }
        });
        Button rewindButton = audioControlButton("↶ 10");
        rewindButton.setContentDescription("Voltar 10 segundos");
        rewindButton.setOnClickListener(view -> seekRelative(-10_000L));
        playPauseButton = audioControlButton("Pausar");
        playPauseButton.setContentDescription("Pausar música");
        playPauseButton.setTextColor(
                luminance(accentColor) > .55f ? Color.rgb(4, 18, 10) : Color.WHITE);
        GradientDrawable playBackground = new GradientDrawable();
        playBackground.setColor(accentColor);
        playBackground.setCornerRadius(dp(15));
        playPauseButton.setBackground(playBackground);
        playPauseButton.setOnClickListener(view -> togglePlayback());
        Button forwardButton = audioControlButton("10 ↷");
        forwardButton.setContentDescription("Avançar 10 segundos");
        forwardButton.setOnClickListener(view -> seekRelative(10_000L));
        Button nextButton = audioControlButton("⏭");
        nextButton.setContentDescription("Próxima música");
        nextButton.setOnClickListener(view -> {
            if (player != null && player.hasNextMediaItem()) {
                player.seekToNextMediaItem();
            }
        });
        playbackRow.addView(previousButton, audioButtonParams());
        playbackRow.addView(rewindButton, audioButtonParams());
        playbackRow.addView(playPauseButton, audioButtonParams());
        playbackRow.addView(forwardButton, audioButtonParams());
        playbackRow.addView(nextButton, audioButtonParams());
        LinearLayout.LayoutParams playbackParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        playbackParams.setMargins(0, dp(3), 0, 0);
        controls.addView(playbackRow, playbackParams);
        return controls;
    }

    private TextView timeText(String value, int gravity) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(Color.rgb(180, 211, 190));
        text.setTextSize(10);
        text.setGravity(gravity);
        text.setPadding(dp(5), 0, dp(5), 0);
        return text;
    }

    private Button audioControlButton(String text) {
        Button button = headerButton(text);
        button.setTextSize(9);
        button.setPadding(dp(2), 0, dp(2), 0);
        return button;
    }

    private LinearLayout.LayoutParams audioButtonParams() {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(0, dp(40), 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private LinearLayout buildExperiencePanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(14));
        panel.setBackgroundColor(accentPanel);

        nowPlayingView = new TextView(this);
        nowPlayingView.setText(mediaName);
        nowPlayingView.setTextColor(Color.WHITE);
        nowPlayingView.setTextSize(14);
        nowPlayingView.setSingleLine(true);
        nowPlayingView.setEllipsize(TextUtils.TruncateAt.END);
        nowPlayingView.setGravity(Gravity.CENTER);
        panel.addView(nowPlayingView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        queueStatusView = new TextView(this);
        queueStatusView.setText("Conectando ao player em segundo plano…");
        queueStatusView.setTextColor(Color.rgb(145, 168, 154));
        queueStatusView.setTextSize(11);
        queueStatusView.setGravity(Gravity.CENTER);
        queueStatusView.setPadding(0, dp(4), 0, dp(10));
        panel.addView(queueStatusView);

        LinearLayout firstRow = controlRow();
        favoriteButton = controlButton("☆ Favoritar");
        favoriteButton.setContentDescription(
                "Favoritar ou remover esta música dos favoritos");
        favoriteButton.setOnClickListener(view -> toggleFavorite());
        shuffleButton = controlButton("Misturar");
        shuffleButton.setContentDescription(
                "Misturar a ordem das músicas");
        shuffleButton.setOnClickListener(view -> toggleShuffle());
        repeatButton = controlButton("Repetir: não");
        repeatButton.setContentDescription(
                "Escolher como as músicas serão repetidas");
        repeatButton.setOnClickListener(view -> cycleRepeat());
        firstRow.addView(favoriteButton, weightedButtonParams());
        firstRow.addView(shuffleButton, weightedButtonParams());
        firstRow.addView(repeatButton, weightedButtonParams());
        panel.addView(firstRow);

        TextView simpleHelp = new TextView(this);
        simpleHelp.setText(
                "Favoritar salva a música. Misturar muda a ordem. "
                        + "Repetir toca novamente.");
        simpleHelp.setTextColor(Color.rgb(151, 177, 160));
        simpleHelp.setTextSize(10);
        simpleHelp.setGravity(Gravity.CENTER);
        simpleHelp.setPadding(dp(6), dp(7), dp(6), dp(4));
        panel.addView(simpleHelp);

        moreOptionsButton = controlButton("Mais opções");
        moreOptionsButton.setContentDescription(
                "Mostrar velocidade, timer, animação e ponto salvo");
        moreOptionsButton.setOnClickListener(view -> {
            boolean show = advancedControls.getVisibility() != View.VISIBLE;
            advancedControls.setVisibility(show ? View.VISIBLE : View.GONE);
            moreOptionsButton.setText(show
                    ? "Ocultar opções extras" : "Mais opções");
        });
        LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        moreParams.setMargins(dp(3), dp(4), dp(3), 0);
        panel.addView(moreOptionsButton, moreParams);

        advancedControls = new LinearLayout(this);
        advancedControls.setOrientation(LinearLayout.VERTICAL);
        advancedControls.setVisibility(View.GONE);

        TextView advancedHelp = new TextView(this);
        advancedHelp.setText(
                "Extras: mude a velocidade, programe para parar, "
                        + "troque a animação ou salve um ponto da música.");
        advancedHelp.setTextColor(Color.rgb(151, 177, 160));
        advancedHelp.setTextSize(10);
        advancedHelp.setGravity(Gravity.CENTER);
        advancedHelp.setPadding(dp(6), dp(8), dp(6), dp(7));
        advancedControls.addView(advancedHelp);

        LinearLayout secondRow = controlRow();
        speedButton = controlButton("Velocidade: 1×");
        speedButton.setContentDescription("Mudar a velocidade da música");
        speedButton.setOnClickListener(view -> cycleSpeed());
        sleepButton = controlButton("Parar depois");
        sleepButton.setContentDescription(
                "Programar o player para pausar sozinho");
        sleepButton.setOnClickListener(view -> cycleSleepTimer());
        Button mixButton = controlButton("Tocar aleatório");
        mixButton.setContentDescription(
                "Tocar todas as músicas em ordem aleatória");
        mixButton.setOnClickListener(view -> {
            if (player == null || player.getMediaItemCount() < 2) {
                Toast.makeText(this,
                        "Adicione mais arquivos para criar uma Mix.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            player.setShuffleModeEnabled(true);
            if (!player.isPlaying()) player.play();
            Toast.makeText(this, "Minha Mix aleatória ativada.",
                    Toast.LENGTH_SHORT).show();
        });
        secondRow.addView(speedButton, weightedButtonParams());
        secondRow.addView(sleepButton, weightedButtonParams());
        secondRow.addView(mixButton, weightedButtonParams());
        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        advancedControls.addView(secondRow, secondParams);

        LinearLayout thirdRow = controlRow();
        visualButton = controlButton("Mudar animação");
        visualButton.setContentDescription(
                "Trocar o estilo da animação que acompanha a música");
        visualButton.setOnClickListener(view -> cycleVisualTheme());
        bookmarkButton = controlButton("Salvar ponto");
        bookmarkButton.setContentDescription(
                "Salvar o trecho atual para voltar depois");
        bookmarkButton.setOnClickListener(view -> saveBookmark());
        jumpBookmarkButton = controlButton("Voltar ao ponto");
        jumpBookmarkButton.setContentDescription(
                "Voltar ao trecho salvo nesta música");
        jumpBookmarkButton.setOnClickListener(view -> jumpToBookmark());
        thirdRow.addView(visualButton, weightedButtonParams());
        thirdRow.addView(bookmarkButton, weightedButtonParams());
        thirdRow.addView(jumpBookmarkButton, weightedButtonParams());
        LinearLayout.LayoutParams thirdParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        thirdParams.setMargins(0, dp(8), 0, 0);
        advancedControls.addView(thirdRow, thirdParams);
        panel.addView(advancedControls);
        return panel;
    }

    private LinearLayout controlRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private LinearLayout.LayoutParams weightedButtonParams() {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(0, dp(42), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private Button controlButton(String text) {
        Button button = headerButton(text);
        button.setTextSize(10);
        button.setPadding(dp(5), 0, dp(5), 0);
        return button;
    }

    private Button headerButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(mix(Color.WHITE, accentColor, .38f));
        button.setTextSize(12);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(mix(Color.rgb(9, 20, 13), accentColor, .16f));
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), mix(Color.rgb(31, 51, 38), accentColor, .34f));
        button.setBackground(background);
        return button;
    }

    private void connectPlayer() {
        if (controllerFuture != null) return;
        SessionToken sessionToken = new SessionToken(
                this, new ComponentName(this, PlaybackService.class));
        controllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                MediaController resolved = controllerFuture.get();
                runOnUiThread(() -> attachController(resolved));
            } catch (Exception error) {
                runOnUiThread(() -> {
                    Toast.makeText(this,
                            "Não foi possível abrir o player em segundo plano.",
                            Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void attachController(MediaController resolved) {
        if (isFinishing()) {
            resolved.release();
            return;
        }
        controller = resolved;
        player = resolved;
        player.addListener(playerListener);
        playerView.setPlayer(player);
        uiHandler.removeCallbacks(positionUpdater);
        uiHandler.post(positionUpdater);

        boolean hasRequestedQueue = mediaUri != null
                || (queueJson != null && !queueJson.trim().isEmpty());
        if ((hasRequestedQueue && !restoreExistingSession)
                || player.getMediaItemCount() == 0) {
            List<MediaItem> items = buildQueue();
            if (items.isEmpty()) {
                Toast.makeText(this, "Nenhum arquivo disponível para reprodução.",
                        Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            int selectedIndex = 0;
            for (int index = 0; index < items.size(); index++) {
                if (items.get(index).mediaId.equals(mediaId)) {
                    selectedIndex = index;
                    break;
                }
            }
            player.setMediaItems(items, selectedIndex, Math.max(0L, playbackPosition));
            restorePlayerPreferences();
            if (initialShuffle) player.setShuffleModeEnabled(true);
            player.prepare();
            player.play();
        } else {
            updateCurrentMedia(player.getCurrentMediaItem());
            restorePlayerPreferences();
        }
        updatePlayerButtons();
        updateMediaMode();
        updatePlaybackUi();
    }

    private List<MediaItem> buildQueue() {
        List<MediaItem> items = new ArrayList<>();
        if (queueJson != null && !queueJson.trim().isEmpty()) {
            try {
                JSONArray queue = new JSONArray(queueJson);
                for (int index = 0; index < queue.length(); index++) {
                    JSONObject entry = queue.getJSONObject(index);
                    String id = entry.optString("id");
                    String uri = entry.optString("uri");
                    String name = entry.optString("name", "Moura Player");
                    String mime = entry.optString("mime", "application/octet-stream");
                    if (id.isEmpty() || uri.isEmpty()) continue;
                    items.add(mediaItem(id, Uri.parse(uri), name, mime));
                }
            } catch (Exception ignored) { }
        }
        if (items.isEmpty() && mediaUri != null && mediaId != null) {
            items.add(mediaItem(mediaId, mediaUri, mediaName, mediaMime));
        }
        return items;
    }

    private MediaItem mediaItem(
            String id, Uri uri, String name, String mime) {
        return new MediaItem.Builder()
                .setUri(uri)
                .setMediaId(id)
                .setMimeType(mime)
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(name)
                        .setArtist("Moura Downloads • Leandro Moura")
                        .build())
                .build();
    }

    private void restorePlayerPreferences() {
        if (player == null) return;
        SharedPreferences prefs =
                getSharedPreferences(PlaybackService.PLAYER_PREFS, MODE_PRIVATE);
        speedIndex = Math.max(0, Math.min(speeds.length - 1,
                prefs.getInt("speed_index", 0)));
        player.setPlaybackSpeed(speeds[speedIndex]);
        player.setRepeatMode(prefs.getInt(
                "repeat_mode", Player.REPEAT_MODE_OFF));
        if (!initialShuffle) {
            player.setShuffleModeEnabled(prefs.getBoolean("shuffle", false));
        }
        visualThemeIndex = Math.max(0, Math.min(
                visualThemeNames.length - 1,
                prefs.getInt("visual_theme", 0)));
        if (energyVisualizer != null) {
            energyVisualizer.setTheme(visualThemeIndex);
        }
        updateSleepButton();
    }

    private void updateCurrentMedia(MediaItem item) {
        if (item == null) return;
        mediaId = item.mediaId;
        CharSequence title = item.mediaMetadata.title;
        mediaName = title == null ? "Moura Player" : title.toString();
        if (item.localConfiguration != null) {
            mediaUri = item.localConfiguration.uri;
            if (item.localConfiguration.mimeType != null) {
                mediaMime = item.localConfiguration.mimeType;
            }
        }
        if (titleView != null) titleView.setText(mediaName);
        if (nowPlayingView != null) nowPlayingView.setText(mediaName);
        updateMediaMode();
        updatePlayerButtons();
    }

    private void updatePlayerButtons() {
        if (player == null) return;
        if (queueStatusView != null) {
            int count = player.getMediaItemCount();
            int current = count == 0 ? 0 : player.getCurrentMediaItemIndex() + 1;
            queueStatusView.setText(count > 1
                    ? "Faixa " + current + " de " + count
                            + " • reprodução continua em segundo plano"
                    : isAudioMedia()
                    ? "Energia ao vivo • continua em segundo plano"
                    : "Reprodução local • continua em segundo plano");
        }
        if (favoriteButton != null && mediaId != null) {
            boolean favorite = getSharedPreferences(LIBRARY_PREFS, MODE_PRIVATE)
                    .getBoolean("fav_" + mediaId, false);
            favoriteButton.setText(favorite ? "★ Favoritada" : "☆ Favoritar");
        }
        if (shuffleButton != null) {
            shuffleButton.setText(player.getShuffleModeEnabled()
                    ? "Misturar: sim" : "Misturar: não");
        }
        if (repeatButton != null) {
            String label = player.getRepeatMode() == Player.REPEAT_MODE_ONE
                    ? "Repetir: uma" : player.getRepeatMode() == Player.REPEAT_MODE_ALL
                    ? "Repetir: tudo" : "Repetir: não";
            repeatButton.setText(label);
        }
        if (speedButton != null) {
            speedButton.setText(
                    "Velocidade: " + formatSpeed(speeds[speedIndex]));
        }
        if (visualButton != null) {
            visualButton.setText(
                    "Animação: " + visualThemeNames[visualThemeIndex]);
        }
        updateBookmarkButtons();
    }

    private void updateMediaMode() {
        if (playerView == null || energyVisualizer == null
                || audioControls == null) return;
        boolean audio = isAudioMedia();
        AudioEnergyBus.setEnabled(audio);
        playerView.setVisibility(audio ? View.INVISIBLE : View.VISIBLE);
        energyVisualizer.setVisibility(audio ? View.VISIBLE : View.GONE);
        audioControls.setVisibility(audio ? View.VISIBLE : View.GONE);
        energyVisualizer.setPlaying(
                audio && player != null && player.isPlaying());
    }

    private boolean isAudioMedia() {
        String mime = mediaMime == null ? "" : mediaMime.toLowerCase();
        if (mime.startsWith("audio/")) return true;
        if (mime.startsWith("video/")) return false;
        String name = mediaName == null ? "" : mediaName.toLowerCase();
        return !(name.endsWith(".mp4")
                || name.endsWith(".mkv")
                || name.endsWith(".webm")
                || name.endsWith(".mov")
                || name.endsWith(".avi"));
    }

    private void updatePlaybackUi() {
        if (playPauseButton != null && player != null) {
            playPauseButton.setText(player.isPlaying() ? "Pausar" : "Tocar");
            playPauseButton.setContentDescription(
                    player.isPlaying() ? "Pausar música" : "Tocar música");
        }
        if (energyVisualizer != null) {
            energyVisualizer.setPlaying(
                    isAudioMedia() && player != null && player.isPlaying());
        }
        updatePositionUi();
    }

    private void updatePositionUi() {
        if (player == null || positionSeekBar == null) return;
        long position = Math.max(0L, player.getCurrentPosition());
        long duration = player.getDuration();
        if (duration == C.TIME_UNSET || duration <= 0L) {
            if (!userSeeking) positionSeekBar.setProgress(0);
            elapsedView.setText(formatTime(position));
            durationView.setText("--:--");
            return;
        }
        if (!userSeeking) {
            positionSeekBar.setProgress((int) Math.min(
                    1000L, position * 1000L / duration));
            elapsedView.setText(formatTime(position));
        }
        durationView.setText(formatTime(duration));
    }

    private void togglePlayback() {
        if (player == null) return;
        if (player.isPlaying()) player.pause();
        else {
            if (player.getPlaybackState() == Player.STATE_ENDED) {
                player.seekTo(0L);
            }
            player.play();
        }
    }

    private void seekRelative(long offsetMs) {
        if (player == null) return;
        long duration = player.getDuration();
        long destination = Math.max(0L, player.getCurrentPosition() + offsetMs);
        if (duration != C.TIME_UNSET && duration > 0L) {
            destination = Math.min(duration, destination);
        }
        player.seekTo(destination);
        updatePositionUi();
    }

    private void cycleVisualTheme() {
        visualThemeIndex =
                (visualThemeIndex + 1) % visualThemeNames.length;
        getSharedPreferences(PlaybackService.PLAYER_PREFS, MODE_PRIVATE)
                .edit().putInt("visual_theme", visualThemeIndex).apply();
        if (energyVisualizer != null) {
            energyVisualizer.setTheme(visualThemeIndex);
        }
        updatePlayerButtons();
        Toast.makeText(this,
                "Visual " + visualThemeNames[visualThemeIndex] + " ativado.",
                Toast.LENGTH_SHORT).show();
    }

    private void saveBookmark() {
        if (player == null || mediaId == null) return;
        long position = Math.max(0L, player.getCurrentPosition());
        getSharedPreferences(PlaybackService.PLAYER_PREFS, MODE_PRIVATE)
                .edit().putLong(bookmarkKey(mediaId), position).apply();
        updateBookmarkButtons();
        Toast.makeText(this,
                "Ponto marcado em " + formatTime(position) + ".",
                Toast.LENGTH_SHORT).show();
    }

    private void jumpToBookmark() {
        if (player == null || mediaId == null) return;
        SharedPreferences prefs =
                getSharedPreferences(PlaybackService.PLAYER_PREFS, MODE_PRIVATE);
        String key = bookmarkKey(mediaId);
        if (!prefs.contains(key)) {
            Toast.makeText(this,
                    "Marque primeiro um ponto desta música.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        long position = Math.max(0L, prefs.getLong(key, 0L));
        player.seekTo(position);
        updatePositionUi();
        Toast.makeText(this,
                "Voltando ao ponto " + formatTime(position) + ".",
                Toast.LENGTH_SHORT).show();
    }

    private void updateBookmarkButtons() {
        if (bookmarkButton == null || jumpBookmarkButton == null
                || mediaId == null) return;
        SharedPreferences prefs =
                getSharedPreferences(PlaybackService.PLAYER_PREFS, MODE_PRIVATE);
        boolean saved = prefs.contains(bookmarkKey(mediaId));
        bookmarkButton.setText(saved ? "Atualizar ponto" : "Salvar ponto");
        jumpBookmarkButton.setEnabled(saved);
        jumpBookmarkButton.setAlpha(saved ? 1f : 0.48f);
    }

    private String bookmarkKey(String id) {
        return "bookmark_" + id;
    }

    private String formatTime(long milliseconds) {
        long totalSeconds = Math.max(0L, milliseconds / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(
                    java.util.Locale.getDefault(),
                    "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(
                java.util.Locale.getDefault(),
                "%d:%02d", minutes, seconds);
    }

    private void toggleFavorite() {
        if (mediaId == null) return;
        SharedPreferences prefs = getSharedPreferences(LIBRARY_PREFS, MODE_PRIVATE);
        String key = "fav_" + mediaId;
        boolean next = !prefs.getBoolean(key, false);
        prefs.edit().putBoolean(key, next).apply();
        updatePlayerButtons();
        Toast.makeText(this,
                next ? "Adicionado aos favoritos." : "Removido dos favoritos.",
                Toast.LENGTH_SHORT).show();
    }

    private void toggleShuffle() {
        if (player == null) return;
        boolean enabled = !player.getShuffleModeEnabled();
        player.setShuffleModeEnabled(enabled);
        getSharedPreferences(PlaybackService.PLAYER_PREFS, MODE_PRIVATE)
                .edit().putBoolean("shuffle", enabled).apply();
        updatePlayerButtons();
    }

    private void cycleRepeat() {
        if (player == null) return;
        int next = player.getRepeatMode() == Player.REPEAT_MODE_OFF
                ? Player.REPEAT_MODE_ALL
                : player.getRepeatMode() == Player.REPEAT_MODE_ALL
                ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF;
        player.setRepeatMode(next);
        getSharedPreferences(PlaybackService.PLAYER_PREFS, MODE_PRIVATE)
                .edit().putInt("repeat_mode", next).apply();
        updatePlayerButtons();
    }

    private void cycleSpeed() {
        if (player == null) return;
        speedIndex = (speedIndex + 1) % speeds.length;
        player.setPlaybackSpeed(speeds[speedIndex]);
        getSharedPreferences(PlaybackService.PLAYER_PREFS, MODE_PRIVATE)
                .edit().putInt("speed_index", speedIndex).apply();
        updatePlayerButtons();
    }

    private void cycleSleepTimer() {
        SharedPreferences prefs =
                getSharedPreferences(PlaybackService.PLAYER_PREFS, MODE_PRIVATE);
        int current = prefs.getInt("sleep_choice_minutes", 0);
        int nextIndex = 0;
        for (int index = 0; index < sleepOptions.length; index++) {
            if (sleepOptions[index] == current) {
                nextIndex = (index + 1) % sleepOptions.length;
                break;
            }
        }
        int minutes = sleepOptions[nextIndex];
        prefs.edit().putInt("sleep_choice_minutes", minutes).apply();
        Intent timer = new Intent(this, PlaybackService.class);
        timer.setAction(PlaybackService.ACTION_SET_SLEEP_TIMER);
        timer.putExtra(PlaybackService.EXTRA_SLEEP_MINUTES, minutes);
        startService(timer);
        updateSleepButton();
        Toast.makeText(this,
                minutes == 0 ? "Timer desligado."
                        : "O player pausará em " + minutes + " minutos.",
                Toast.LENGTH_SHORT).show();
    }

    private void updateSleepButton() {
        if (sleepButton == null) return;
        SharedPreferences prefs =
                getSharedPreferences(PlaybackService.PLAYER_PREFS, MODE_PRIVATE);
        long remaining = prefs.getLong("sleep_deadline", 0L)
                - System.currentTimeMillis();
        if (remaining <= 0L) {
            sleepButton.setText("Parar depois");
        } else {
            long minutes = Math.max(1L, (remaining + 59_999L) / 60_000L);
            sleepButton.setText("Parar em " + minutes + "m");
        }
    }

    private String formatSpeed(float speed) {
        return speed == Math.round(speed)
                ? Math.round(speed) + "×"
                : String.valueOf(speed).replace('.', ',') + "×";
    }

    private void savePosition() {
        if (player == null || player.getCurrentMediaItem() == null) return;
        String id = player.getCurrentMediaItem().mediaId;
        long position = player.getCurrentPosition();
        long duration = player.getDuration();
        SharedPreferences.Editor editor =
                getSharedPreferences(PlaybackService.PLAYER_PREFS, MODE_PRIVATE).edit();
        if (duration != C.TIME_UNSET && duration > 0L
                && position >= duration - 5000L) {
            editor.remove(positionKey(id));
        } else if (position > 3000L) {
            editor.putLong(positionKey(id), position);
        }
        editor.apply();
    }

    private void shareMedia() {
        if (mediaUri == null) {
            Toast.makeText(this, "Arquivo ainda não está pronto para compartilhar.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType(mediaMime);
        share.putExtra(Intent.EXTRA_STREAM, mediaUri);
        share.putExtra(Intent.EXTRA_TEXT,
                "Compartilhado pelo Moura Downloads • Leandro Moura");
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "Compartilhar arquivo"));
    }

    private String positionKey(String id) {
        return "position_" + id;
    }

    private int mix(int base, int accent, float amount) {
        float value = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(
                Math.round(Color.red(base) * (1f - value) + Color.red(accent) * value),
                Math.round(Color.green(base) * (1f - value) + Color.green(accent) * value),
                Math.round(Color.blue(base) * (1f - value) + Color.blue(accent) * value));
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(
                Math.max(0, Math.min(255, alpha)),
                Color.red(color), Color.green(color), Color.blue(color));
    }

    private float luminance(int color) {
        return (Color.red(color) * .2126f + Color.green(color) * .7152f
                + Color.blue(color) * .0722f) / 255f;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onStart() {
        super.onStart();
        connectPlayer();
    }

    @Override
    protected void onStop() {
        savePosition();
        uiHandler.removeCallbacks(positionUpdater);
        AudioEnergyBus.setEnabled(false);
        if (energyVisualizer != null) energyVisualizer.setPlaying(false);
        if (player != null) player.removeListener(playerListener);
        if (playerView != null) playerView.setPlayer(null);
        player = null;
        controller = null;
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
        }
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (player != null && player.getCurrentMediaItem() != null) {
            outState.putLong(STATE_POSITION, player.getCurrentPosition());
            outState.putString(STATE_MEDIA_ID,
                    player.getCurrentMediaItem().mediaId);
        } else {
            outState.putLong(STATE_POSITION, playbackPosition);
            outState.putString(STATE_MEDIA_ID, mediaId);
        }
        super.onSaveInstanceState(outState);
    }
}
