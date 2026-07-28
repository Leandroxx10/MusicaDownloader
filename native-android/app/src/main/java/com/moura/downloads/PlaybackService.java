package com.moura.downloads;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

@UnstableApi
public class PlaybackService extends MediaSessionService {
    public static final String ACTION_SET_SLEEP_TIMER =
            "com.moura.downloads.SET_SLEEP_TIMER";
    public static final String EXTRA_SLEEP_MINUTES = "sleep_minutes";
    public static final String PLAYER_PREFS = "moura_player";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private MediaSession mediaSession;
    private String observedMediaId;
    private long observedPosition;
    private long observedDuration = C.TIME_UNSET;

    private final Runnable progressSaver = new Runnable() {
        @Override
        public void run() {
            rememberCurrentPosition();
            handler.postDelayed(this, 5000L);
        }
    };

    private final Runnable sleepPause = () -> {
        if (player != null) player.pause();
        getSharedPreferences(PLAYER_PREFS, MODE_PRIVATE).edit()
                .remove("sleep_deadline")
                .putInt("sleep_choice_minutes", 0)
                .apply();
    };

    private final Player.Listener listener = new Player.Listener() {
        @Override
        public void onMediaItemTransition(
                @Nullable MediaItem mediaItem, int reason) {
            saveObservedPosition();
            if (mediaItem == null) return;
            observedMediaId = mediaItem.mediaId;
            observedPosition = 0L;
            observedDuration = C.TIME_UNSET;
            SharedPreferences prefs = getSharedPreferences(PLAYER_PREFS, MODE_PRIVATE);
            int plays = prefs.getInt("play_count_" + observedMediaId, 0);
            prefs.edit()
                    .putString("last_media_id", observedMediaId)
                    .putLong("last_played_" + observedMediaId, System.currentTimeMillis())
                    .putInt("play_count_" + observedMediaId, plays + 1)
                    .apply();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        EnergyAudioProcessor energyProcessor = new EnergyAudioProcessor();
        DefaultRenderersFactory renderersFactory =
                new DefaultRenderersFactory(this) {
                    @Override
                    protected AudioSink buildAudioSink(
                            Context context,
                            boolean enableFloatOutput,
                            boolean enableAudioTrackPlaybackParams) {
                        return new DefaultAudioSink.Builder(context)
                                .setAudioProcessors(new AudioProcessor[]{
                                        energyProcessor
                                })
                                .setEnableFloatOutput(false)
                                .setEnableAudioTrackPlaybackParams(
                                        enableAudioTrackPlaybackParams)
                                .build();
                    }
                };
        player = new ExoPlayer.Builder(this, renderersFactory)
                .setSeekBackIncrementMs(10_000L)
                .setSeekForwardIncrementMs(10_000L)
                .build();
        player.addListener(listener);
        Intent openPlayer = new Intent(this, PlayerActivity.class);
        openPlayer.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent sessionActivity = PendingIntent.getActivity(
                this, 4401, openPlayer,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        mediaSession = new MediaSession.Builder(this, player)
                .setSessionActivity(sessionActivity)
                .build();
        handler.post(progressSaver);
        restoreSleepTimer();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_SET_SLEEP_TIMER.equals(intent.getAction())) {
            scheduleSleepTimer(intent.getIntExtra(EXTRA_SLEEP_MINUTES, 0));
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void scheduleSleepTimer(int minutes) {
        handler.removeCallbacks(sleepPause);
        SharedPreferences.Editor editor =
                getSharedPreferences(PLAYER_PREFS, MODE_PRIVATE).edit();
        if (minutes <= 0) {
            editor.remove("sleep_deadline").apply();
            return;
        }
        long delay = minutes * 60_000L;
        editor.putLong("sleep_deadline", System.currentTimeMillis() + delay).apply();
        handler.postDelayed(sleepPause, delay);
    }

    private void restoreSleepTimer() {
        long deadline = getSharedPreferences(PLAYER_PREFS, MODE_PRIVATE)
                .getLong("sleep_deadline", 0L);
        long remaining = deadline - System.currentTimeMillis();
        if (remaining > 0L) {
            handler.postDelayed(sleepPause, remaining);
        } else {
            getSharedPreferences(PLAYER_PREFS, MODE_PRIVATE).edit()
                    .remove("sleep_deadline").apply();
        }
    }

    private void rememberCurrentPosition() {
        if (player == null || player.getCurrentMediaItem() == null) return;
        String currentId = player.getCurrentMediaItem().mediaId;
        if (observedMediaId != null && !observedMediaId.equals(currentId)) {
            saveObservedPosition();
        }
        observedMediaId = currentId;
        observedPosition = player.getCurrentPosition();
        observedDuration = player.getDuration();
        saveObservedPosition();
    }

    private void saveObservedPosition() {
        if (observedMediaId == null || observedMediaId.isEmpty()) return;
        SharedPreferences.Editor editor =
                getSharedPreferences(PLAYER_PREFS, MODE_PRIVATE).edit();
        if (observedDuration != C.TIME_UNSET && observedDuration > 0L
                && observedPosition >= observedDuration - 5000L) {
            editor.remove("position_" + observedMediaId);
        } else if (observedPosition > 3000L) {
            editor.putLong("position_" + observedMediaId, observedPosition);
        } else {
            editor.remove("position_" + observedMediaId);
        }
        editor.apply();
    }

    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public void onTaskRemoved(@Nullable Intent rootIntent) {
        if (player == null || !player.getPlayWhenReady()) stopSelf();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(progressSaver);
        handler.removeCallbacks(sleepPause);
        rememberCurrentPosition();
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        if (player != null) {
            player.removeListener(listener);
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    @Override
    public @Nullable IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }
}
