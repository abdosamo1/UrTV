package sa.urtv.screensaver;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * The volume over any app (IdleWatch adds it once, in an accessibility overlay): a glass capsule, «الصوت» on top,
 * a drum of numbers in Thmanyah turning to the new one (the current large, the one before and after small and
 * faded, tilted away), a speaker at the bottom. Drawn natively: it must be there the moment a key comes (a WebView
 * made on the first key held the key past the system's wait, and the service lost the keys).
 */
final class VolumeWheel extends View {
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG),
        lens = new Paint(Paint.ANTI_ALIAS_FLAG), lensEdge = new Paint(Paint.ANTI_ALIAS_FLAG),
        num = new Paint(Paint.ANTI_ALIAS_FLAG), word = new Paint(Paint.ANTI_ALIAS_FLAG), icon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path clip = new Path(), p = new Path();
    private float pos = 0;          // the drum, animated to the volume
    private int value = 0, max = 100;
    private boolean muted;
    private ValueAnimator turn;
    private final Runnable hide = () -> animate().alpha(0f).translationX(-getWidth() * .3f).scaleX(.94f).scaleY(.94f)
        .setDuration(350).setInterpolator(new DecelerateInterpolator()).start();

    VolumeWheel(Context c) {
        super(c);
        Typeface display = font(c, "fonts/ThmanyahDisplay-Bold.otf", "fonts/ThmanyahDisplay-Bold.ttf");
        Typeface medium = font(c, "fonts/Thmanyah-Medium.otf", "fonts/Thmanyah-Medium.ttf");
        num.setTypeface(display);
        num.setTextAlign(Paint.Align.CENTER);
        word.setTypeface(medium);
        word.setTextAlign(Paint.Align.CENTER);
        word.setColor(0xFFF1CF8A);
        edge.setStyle(Paint.Style.STROKE);
        edge.setColor(0x42FFFFFF);
        lens.setColor(0x12FFFFFF);
        lensEdge.setStyle(Paint.Style.STROKE);
        lensEdge.setColor(0x24FFFFFF);
        icon.setStyle(Paint.Style.STROKE);
        icon.setStrokeCap(Paint.Cap.ROUND);
        icon.setStrokeJoin(Paint.Join.ROUND);
        icon.setColor(0xFFF1CF8A);
        setAlpha(0f);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    /** a font from the app's assets (the launcher's .otf, or the page's), else the system's */
    private static Typeface font(Context c, String... names) {
        for (String n : names) try { return Typeface.createFromAsset(c.getAssets(), n); } catch (Exception ignored) { }
        return Typeface.DEFAULT_BOLD;
    }

    /** the volume now: the drum turns to it, the capsule shows and goes after 2 s without a key */
    void show(int v, int mx, boolean isMuted) {
        if (mx > 0) max = mx;
        int old = value;
        value = Math.max(0, Math.min(max, v));
        muted = isMuted;
        if (getAlpha() < .05f) pos = value;                 // appearing: no spin from an old number
        if (turn != null) turn.cancel();
        turn = ValueAnimator.ofFloat(pos, value);
        turn.setDuration(Math.abs(value - old) > 3 ? 180 : 300);
        turn.setInterpolator(new DecelerateInterpolator(1.6f));
        turn.addUpdateListener(a -> { pos = (float) a.getAnimatedValue(); invalidate(); });
        turn.start();
        removeCallbacks(hide);
        if (getAlpha() < 1f) {
            setPivotX(0); setPivotY(getHeight() / 2f);
            animate().alpha(1f).translationX(0).scaleX(1f).scaleY(1f).setDuration(420)
                .setInterpolator(new OvershootInterpolator(1.15f)).start();
        }
        postDelayed(hide, 2000);
        invalidate();
    }

    private static String ar(int n) {
        StringBuilder b = new StringBuilder();
        for (char ch : String.valueOf(n).toCharArray()) b.append((char) ('٠' + (ch - '0')));
        return b.toString();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float rad = w / 2f;
        r.set(1, 1, w - 1, h - 1);
        // the glass: darker than the page's (no photo behind an overlay to blur), so it reads over anything
        bg.setShader(new LinearGradient(0, 0, w * .4f, h, new int[] {0xCC3C423E, 0xC7161A18, 0xCC121614, 0xD1303432},
            new float[] {0f, .45f, .7f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, bg);
        edge.setStrokeWidth(Math.max(1f, w * .012f));
        c.drawRoundRect(r, rad, rad, edge);
        clip.reset();
        clip.addRoundRect(r, rad, rad, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        // the lens around the current number
        float lh = h * .22f, ly = h / 2f;
        r.set(w * .12f, ly - lh / 2, w * .88f, ly + lh / 2);
        float lr = Math.min(lh, w) * .3f;
        c.drawRoundRect(r, lr, lr, lens);
        lensEdge.setStrokeWidth(Math.max(1f, w * .01f));
        c.drawRoundRect(r, lr, lr, lensEdge);
        // the word
        word.setTextSize(w * .16f);
        c.drawText("الصوت", w / 2f, h * .06f + word.getTextSize(), word);
        // the drum: higher numbers above, tilted away, the near ones small and faded
        float fs = w * .46f, R = h * .40f;
        num.setTextSize(fs);
        Paint.FontMetrics fm = num.getFontMetrics();
        float base = -(fm.ascent + fm.descent) / 2f;
        for (int i = (int) Math.floor(pos) - 2; i <= (int) Math.ceil(pos) + 2; i++) {
            if (i < 0 || i > max) continue;
            float d = i - pos, a = Math.abs(d);
            if (a > 1.6f) continue;
            double ang = Math.toRadians(Math.max(-85, Math.min(85, d * 34)));
            float y = ly - (float) Math.sin(ang) * R;
            float s = a <= 1 ? 1f - .38f * a : .62f - .16f * (a - 1);
            float alpha = a <= 1 ? 1f - .45f * a : Math.max(0f, .55f * (1.6f - a) / .6f);
            if (muted && a < .5f) alpha *= .45f;
            num.setColor(a < .5f ? 0xFFFFFDF6 : 0xFFE9DFC8);
            num.setAlpha(Math.round(255 * alpha));
            if (a < .5f) num.setShadowLayer(fs * .12f, 0, fs * .02f, 0x73000000); else num.clearShadowLayer();
            c.save();
            c.translate(w / 2f, y);
            c.scale(s, s * (float) Math.max(.25, Math.cos(ang)));
            c.drawText(ar(i), 0, base, num);
            c.restore();
        }
        num.clearShadowLayer();
        // the speaker
        float sz = w * .34f, cx = w / 2f, cy = h * .95f - sz / 2f, u = sz / 24f;
        icon.setStrokeWidth(2 * u);
        c.save();
        c.translate(cx - sz / 2f, cy - sz / 2f);
        p.reset();
        p.moveTo(4 * u, 9.5f * u); p.lineTo(7.5f * u, 9.5f * u); p.lineTo(12 * u, 5.5f * u); p.lineTo(12 * u, 18.5f * u);
        p.lineTo(7.5f * u, 14.5f * u); p.lineTo(4 * u, 14.5f * u); p.close();
        c.drawPath(p, icon);
        if (muted || value == 0) {
            c.drawLine(16 * u, 9.5f * u, 21 * u, 14.5f * u, icon);
            c.drawLine(21 * u, 9.5f * u, 16 * u, 14.5f * u, icon);
        } else {
            r.set(11.5f * u, 8f * u, 16.5f * u, 16f * u); c.drawArc(r, -55, 110, false, icon);
            if (value > max / 3) { r.set(11 * u, 5 * u, 19 * u, 19 * u); c.drawArc(r, -50, 100, false, icon); }
            if (value > max * 2 / 3) { r.set(10.3f * u, 2.2f * u, 22.5f * u, 21.8f * u); c.drawArc(r, -48, 96, false, icon); }
        }
        c.restore();
        c.restore();
    }
}
