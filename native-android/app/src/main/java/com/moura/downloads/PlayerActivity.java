package com.moura.downloads;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

@UnstableApi
public class PlayerActivity extends Activity {
    public static final String EXTRA_MEDIA_URI = "media_uri";
    public static final String EXTRA_MEDIA_ID = "media_id";
    public static final String EXTRA_MEDIA_NAME = "media_name";
    public static final String EXTRA_MEDIA_MIME = "media_mime";

    private static final String PLAYER_PREFS = "moura_player";
    private static final String STATE_POSITION = "position";
    private static final String STATE_PLAY_WHEN_READY = "play_when_ready";

    private PlayerView playerView;
    private ExoPlayer player;
    private Uri mediaUri;
    private String mediaId;
    private String mediaName;
    private String mediaMime;
    private long playbackPosition;
    private boolean playWhenReady = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(5, 11, 8));
        getWindow().setNavigationBarColor(Color.rgb(5, 11, 8));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Intent intent = getIntent();
        String uriValue = intent.getStringExtra(EXTRA_MEDIA_URI);
        mediaId = intent.getStringExtra(EXTRA_MEDIA_ID);
        mediaName = intent.getStringExtra(EXTRA_MEDIA_NAME);
        mediaMime = intent.getStringExtra(EXTRA_MEDIA_MIME);

        if (uriValue == null || mediaId == null) {
            Toast.makeText(this, "Arquivo inválido para reprodução.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        mediaUri = Uri.parse(uriValue);
        if (mediaName == null || mediaName.trim().isEmpty()) mediaName = "Moura Downloads";
        if (mediaMime == null || mediaMime.trim().isEmpty()) mediaMime = "application/octet-stream";

        if (savedInstanceState != null) {
            playbackPosition = savedInstanceState.getLong(STATE_POSITION, 0L);
            playWhenReady = savedInstanceState.getBoolean(STATE_PLAY_WHEN_READY, true);
        } else {
            playbackPosition = getSharedPreferences(PLAYER_PREFS, MODE_PRIVATE)
                    .getLong(positionKey(), 0L);
        }

        setContentView(buildLayout());
    }

    private LinearLayout buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(5, 11, 8));
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(8), dp(10), dp(8));
        header.setBackgroundColor(Color.rgb(8, 20, 13));

        Button backButton = headerButton("‹ Voltar");
        backButton.setOnClickListener(view -> finish());
        header.addView(backButton);

        TextView title = new TextView(this);
        title.setText(mediaName);
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        titleParams.setMargins(dp(8), 0, dp(8), 0);
        header.addView(title, titleParams);

        Button shareButton = headerButton("Compartilhar");
        shareButton.setOnClickListener(view -> shareMedia());
        header.addView(shareButton);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        playerView = new PlayerView(this);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        root.addView(playerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView hint = new TextView(this);
        hint.setText("Reprodutor interno • posição salva automaticamente");
        hint.setTextColor(Color.rgb(145, 168, 154));
        hint.setTextSize(12);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(12), dp(10), dp(12), dp(12));
        root.addView(hint);
        return root;
    }

    private Button headerButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(Color.rgb(202, 255, 216));
        button.setTextSize(12);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(15, 42, 25));
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), Color.rgb(45, 89, 59));
        button.setBackground(background);
        button.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));
        return button;
    }

    private void initializePlayer() {
        if (player != null || mediaUri == null) return;
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        MediaItem item = new MediaItem.Builder()
                .setUri(mediaUri)
                .setMediaId(mediaId)
                .setMimeType(mediaMime)
                .build();
        player.setMediaItem(item);
        player.setPlayWhenReady(playWhenReady);
        player.seekTo(Math.max(0L, playbackPosition));
        player.prepare();
    }

    private void releasePlayer() {
        if (player == null) return;
        playbackPosition = player.getCurrentPosition();
        playWhenReady = player.getPlayWhenReady();
        long duration = player.getDuration();
        SharedPreferences.Editor editor = getSharedPreferences(PLAYER_PREFS, MODE_PRIVATE).edit();
        if (duration != C.TIME_UNSET && duration > 0 && playbackPosition >= duration - 5000L) {
            playbackPosition = 0L;
            editor.remove(positionKey());
        } else {
            editor.putLong(positionKey(), playbackPosition);
        }
        editor.apply();
        playerView.setPlayer(null);
        player.release();
        player = null;
    }

    private void shareMedia() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType(mediaMime);
        share.putExtra(Intent.EXTRA_STREAM, mediaUri);
        share.putExtra(Intent.EXTRA_TEXT, "Compartilhado pelo Moura Downloads");
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "Compartilhar arquivo"));
    }

    private String positionKey() {
        return "position_" + mediaId;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onStart() {
        super.onStart();
        initializePlayer();
    }

    @Override
    protected void onStop() {
        releasePlayer();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (player != null) {
            outState.putLong(STATE_POSITION, player.getCurrentPosition());
            outState.putBoolean(STATE_PLAY_WHEN_READY, player.getPlayWhenReady());
        } else {
            outState.putLong(STATE_POSITION, playbackPosition);
            outState.putBoolean(STATE_PLAY_WHEN_READY, playWhenReady);
        }
        super.onSaveInstanceState(outState);
    }
}
