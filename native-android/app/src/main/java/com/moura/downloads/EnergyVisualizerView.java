package com.moura.downloads;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

public final class EnergyVisualizerView extends View {
    private static final int[] ACCENTS = {
            Color.rgb(70, 255, 145),
            Color.rgb(85, 195, 255),
            Color.rgb(204, 94, 255)
    };
    private static final int[] SECONDARY = {
            Color.rgb(13, 184, 255),
            Color.rgb(108, 91, 255),
            Color.rgb(255, 82, 159)
    };

    private final Paint backgroundPaint = new Paint();
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spectrumPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint waveformPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path waveform = new Path();
    private final float[] displayedBands =
            new float[AudioEnergyBus.BAND_COUNT];

    private long lastDrawNanos;
    private float displayedEnergy;
    private float phase;
    private boolean playing;
    private int theme;
    private int selectedAccent = Color.rgb(66, 245, 123);
    private Shader backgroundShader;
    private Shader ambientShader;
    private Shader coreShader;

    public EnergyVisualizerView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setContentDescription(
                "Visualizador de energia sincronizado com a música");
        setFocusable(false);
        setClickable(false);
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
        postInvalidateOnAnimation();
    }

    public void setTheme(int theme) {
        this.theme = Math.max(0, Math.min(ACCENTS.length - 1, theme));
        rebuildShaders(getWidth(), getHeight());
        invalidate();
    }

    public void setAccentColor(int accent) {
        selectedAccent = Color.rgb(
                Color.red(accent), Color.green(accent), Color.blue(accent));
        rebuildShaders(getWidth(), getHeight());
        invalidate();
    }

    private int accentForTheme() {
        if (theme == 1) return mix(selectedAccent, Color.WHITE, .18f);
        if (theme == 2) return mix(selectedAccent, Color.WHITE, .34f);
        return selectedAccent;
    }

    private int secondaryForTheme() {
        if (theme == 1) return mix(selectedAccent, Color.WHITE, .42f);
        if (theme == 2) return mix(selectedAccent, Color.rgb(190, 205, 255), .52f);
        return mix(selectedAccent, Color.WHITE, .25f);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        rebuildShaders(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = System.nanoTime();
        float deltaSeconds = lastDrawNanos == 0L
                ? 1f / 60f
                : Math.min(0.05f, (now - lastDrawNanos) / 1_000_000_000f);
        lastDrawNanos = now;
        phase += deltaSeconds * (playing ? 1.8f : 0.55f);

        AudioEnergyBus.Frame frame = AudioEnergyBus.latest();
        boolean fresh = playing
                && now - frame.capturedAtNanos < 350_000_000L;
        float energyTarget = fresh ? frame.energy : 0.035f;
        float energyResponse = energyTarget > displayedEnergy ? 0.30f : 0.10f;
        displayedEnergy += (energyTarget - displayedEnergy) * energyResponse;
        for (int index = 0; index < displayedBands.length; index++) {
            float target = fresh ? frame.bands[index] : 0.018f;
            float response = target > displayedBands[index] ? 0.38f : 0.12f;
            displayedBands[index] +=
                    (target - displayedBands[index]) * response;
        }

        float width = getWidth();
        float height = getHeight();
        float minimum = Math.min(width, height);
        int accent = accentForTheme();
        int secondary = secondaryForTheme();
        drawBackground(canvas, width, height, accent, secondary);

        float centerX = width * 0.5f;
        float centerY = height * 0.34f;
        float baseRadius = minimum * (0.105f + displayedEnergy * 0.018f);
        drawEnergyCore(canvas, centerX, centerY, baseRadius, accent, secondary);
        drawSpectrum(canvas, centerX, centerY, baseRadius, minimum, accent);
        drawParticles(canvas, width, height, centerX, centerY, accent, secondary);
        drawWaveform(canvas, width, height, secondary);
        drawLabels(canvas, width, height, accent);

        if (isAttachedToWindow()) {
            if (playing) postInvalidateOnAnimation();
            else postInvalidateDelayed(48L);
        }
    }

    private void drawBackground(
            Canvas canvas,
            float width,
            float height,
            int accent,
            int secondary) {
        backgroundPaint.setShader(backgroundShader);
        canvas.drawRect(0f, 0f, width, height, backgroundPaint);

        glowPaint.setShader(ambientShader);
        canvas.drawRect(0f, 0f, width, height, glowPaint);
        glowPaint.setShader(null);
    }

    private void drawEnergyCore(
            Canvas canvas,
            float centerX,
            float centerY,
            float radius,
            int accent,
            int secondary) {
        for (int ring = 4; ring >= 1; ring--) {
            float expansion = radius * (1f + ring * 0.28f)
                    + displayedEnergy * radius * ring * 0.16f;
            glowPaint.setStyle(Paint.Style.STROKE);
            glowPaint.setStrokeWidth(dp(1.2f));
            glowPaint.setColor(withAlpha(
                    ring % 2 == 0 ? accent : secondary,
                    Math.max(14, 62 - ring * 10)));
            canvas.drawCircle(centerX, centerY, expansion, glowPaint);
        }

        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setShader(coreShader);
        canvas.drawCircle(
                centerX,
                centerY,
                radius * (1f + displayedEnergy * 0.18f),
                glowPaint);
        glowPaint.setShader(null);
    }

    private void rebuildShaders(int width, int height) {
        if (width <= 0 || height <= 0) return;
        int accent = accentForTheme();
        int secondary = secondaryForTheme();
        float minimum = Math.min(width, height);
        float baseRadius = minimum * 0.105f;
        backgroundShader = new LinearGradient(
                0f, 0f, width, height,
                new int[]{
                        mix(Color.rgb(3, 7, 5), accent, .045f),
                        mix(Color.rgb(5, 11, 8), secondary, 0.12f),
                        mix(Color.rgb(2, 5, 7), accent, .025f)
                },
                new float[]{0f, 0.52f, 1f},
                Shader.TileMode.CLAMP);
        ambientShader = new RadialGradient(
                width * 0.5f,
                height * 0.34f,
                Math.max(width, height) * 0.62f,
                new int[]{
                        withAlpha(accent, 34),
                        withAlpha(secondary, 12),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.46f, 1f},
                Shader.TileMode.CLAMP);
        coreShader = new RadialGradient(
                width * 0.5f - baseRadius * 0.28f,
                height * 0.34f - baseRadius * 0.32f,
                baseRadius * 1.45f,
                new int[]{
                        Color.WHITE,
                        withAlpha(accent, 230),
                        withAlpha(secondary, 165),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.19f, 0.64f, 1f},
                Shader.TileMode.CLAMP);
    }

    private void drawSpectrum(
            Canvas canvas,
            float centerX,
            float centerY,
            float radius,
            float minimum,
            int accent) {
        spectrumPaint.setStrokeCap(Paint.Cap.ROUND);
        spectrumPaint.setStrokeWidth(Math.max(dp(2f), minimum * 0.008f));
        for (int index = 0; index < displayedBands.length; index++) {
            float angle = (float) (
                    -Math.PI / 2.0
                            + (Math.PI * 2.0 * index / displayedBands.length));
            float level = displayedBands[index];
            float inner = radius * 1.48f;
            float length = minimum * (0.025f + level * 0.17f);
            float startX = centerX + (float) Math.cos(angle) * inner;
            float startY = centerY + (float) Math.sin(angle) * inner;
            float endX = centerX + (float) Math.cos(angle) * (inner + length);
            float endY = centerY + (float) Math.sin(angle) * (inner + length);
            spectrumPaint.setColor(withAlpha(
                    accent, 105 + Math.round(level * 150)));
            spectrumPaint.setShadowLayer(
                    dp(5f + level * 8f), 0f, 0f, accent);
            canvas.drawLine(startX, startY, endX, endY, spectrumPaint);
        }
        spectrumPaint.clearShadowLayer();
    }

    private void drawParticles(
            Canvas canvas,
            float width,
            float height,
            float centerX,
            float centerY,
            int accent,
            int secondary) {
        glowPaint.setStyle(Paint.Style.FILL);
        for (int index = 0; index < 26; index++) {
            float orbit = (0.18f + ((index * 37) % 100) / 100f * 0.58f)
                    * Math.min(width, height);
            float angle = phase * (0.18f + (index % 5) * 0.025f)
                    + index * 2.3999f;
            float level = displayedBands[
                    (index * 5) % displayedBands.length];
            float x = centerX + (float) Math.cos(angle) * orbit;
            float y = centerY + (float) Math.sin(angle) * orbit * 0.62f;
            if (x < 0f || x > width || y < 0f || y > height) continue;
            glowPaint.setColor(withAlpha(
                    index % 2 == 0 ? accent : secondary,
                    32 + Math.round(level * 120)));
            canvas.drawCircle(
                    x, y, dp(0.9f + level * 2.4f), glowPaint);
        }
    }

    private void drawWaveform(
            Canvas canvas, float width, float height, int secondary) {
        float baseline = height * 0.60f;
        float amplitude = height * 0.075f;
        waveform.reset();
        for (int point = 0; point <= 72; point++) {
            float progress = point / 72f;
            float bandPosition = progress * (displayedBands.length - 1);
            int lower = (int) bandPosition;
            int upper = Math.min(displayedBands.length - 1, lower + 1);
            float fraction = bandPosition - lower;
            float level = displayedBands[lower] * (1f - fraction)
                    + displayedBands[upper] * fraction;
            float envelope = (float) Math.sin(Math.PI * progress);
            float wave = (float) Math.sin(
                    point * 0.78f + phase * 4.6f);
            float x = width * 0.07f + progress * width * 0.86f;
            float y = baseline + wave * amplitude
                    * envelope * (0.15f + level);
            if (point == 0) waveform.moveTo(x, y);
            else waveform.lineTo(x, y);
        }
        waveformPaint.setStyle(Paint.Style.STROKE);
        waveformPaint.setStrokeCap(Paint.Cap.ROUND);
        waveformPaint.setStrokeJoin(Paint.Join.ROUND);
        waveformPaint.setStrokeWidth(dp(1.8f));
        waveformPaint.setColor(withAlpha(secondary, 188));
        waveformPaint.setShadowLayer(dp(8f), 0f, 0f, secondary);
        canvas.drawPath(waveform, waveformPaint);
        waveformPaint.clearShadowLayer();
    }

    private void drawLabels(
            Canvas canvas, float width, float height, int accent) {
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(withAlpha(accent, 225));
        textPaint.setTextSize(dp(11f));
        textPaint.setFakeBoldText(true);
        textPaint.setLetterSpacing(0.18f);
        canvas.drawText("ENERGIA AO VIVO", width * 0.5f, height * 0.685f, textPaint);
        textPaint.setFakeBoldText(false);
        textPaint.setLetterSpacing(0.04f);
        textPaint.setTextSize(dp(9f));
        textPaint.setColor(Color.argb(165, 215, 235, 222));
        canvas.drawText(
                playing ? "voz e batidas sincronizadas" : "pronto para sentir a música",
                width * 0.5f,
                height * 0.725f,
                textPaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(
                Math.max(0, Math.min(255, alpha)),
                Color.red(color),
                Color.green(color),
                Color.blue(color));
    }

    private static int mix(int first, int second, float amount) {
        float inverse = 1f - amount;
        return Color.rgb(
                Math.round(Color.red(first) * inverse + Color.red(second) * amount),
                Math.round(Color.green(first) * inverse + Color.green(second) * amount),
                Math.round(Color.blue(first) * inverse + Color.blue(second) * amount));
    }
}
