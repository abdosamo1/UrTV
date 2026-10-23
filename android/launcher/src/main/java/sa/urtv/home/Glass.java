package sa.urtv.home;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * iPhone-style glass for the home screen's buttons, dock, icons and menus.
 *
 * Frosted: behind the surface it draws its own part of a small, blurred copy of the wallpaper (with the screen's
 * darkening baked in), so the photo shows through softly, as through glass. Over that a tint, a gloss on the upper
 * part, and a rim that catches the light on the top-left edge and again on the bottom-right one.
 * Without `frosted` (cards that scroll, art that fills the card) it is only the tint, gloss and rim.
 */
final class Glass extends Drawable {
    // ------------------------------------------------------------ the shared frosted wallpaper

    private static Bitmap backdrop;
    private static int screenW = 1, screenH = 1;
    private static final List<WeakReference<View>> HOSTS = new ArrayList<>();
    private static final int SCALE = 12;   // the backdrop is 1/12 of the screen: blur is cheap, and soft

    /** A new wallpaper: frost it (on the UI thread: about 160x90 pixels) and repaint every glass surface. */
    static void setWallpaper(Bitmap wall, int sw, int sh, int[] scrim) {
        if (wall == null || sw <= 0 || sh <= 0) return;
        screenW = sw;
        screenH = sh;
        backdrop = frost(wall, sw, sh, scrim);
        for (Iterator<WeakReference<View>> it = HOSTS.iterator(); it.hasNext(); ) {
            View v = it.next().get();
            if (v == null) it.remove();
            else v.invalidate();
        }
    }

    /** Centre-cropped like the wallpaper ImageView, shrunk, darkened like the screen's scrim, then box-blurred. */
    static Bitmap frost(Bitmap src, int sw, int sh, int[] scrim) {
        int w = Math.max(16, sw / SCALE), h = Math.max(9, sh / SCALE);
        Bitmap small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(small);
        float k = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
        float dw = src.getWidth() * k, dh = src.getHeight() * k;
        c.drawBitmap(src, null, new RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2),
            new Paint(Paint.FILTER_BITMAP_FLAG));
        if (scrim != null) {
            Paint p = new Paint();
            p.setShader(new LinearGradient(0, 0, 0, h, scrim, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
        }
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) {   // three box blurs ≈ a gaussian
            boxBlur(px, w, h, 3, true);
            boxBlur(px, w, h, 3, false);
        }
        small.setPixels(px, 0, w, 0, 0, w, h);
        return small;
    }

    private static void boxBlur(int[] px, int w, int h, int r, boolean horizontal) {
        int len = horizontal ? w : h, lines = horizontal ? h : w;
        int[] line = new int[len];
        for (int l = 0; l < lines; l++) {
            for (int i = 0; i < len; i++) line[i] = px[horizontal ? l * w + i : i * w + l];
            for (int i = 0; i < len; i++) {
                int a = 0, rr = 0, g = 0, b = 0, n = 0;
                for (int j = Math.max(0, i - r); j <= Math.min(len - 1, i + r); j++) {
                    int c = line[j];
                    a += c >>> 24; rr += (c >> 16) & 0xFF; g += (c >> 8) & 0xFF; b += c & 0xFF; n++;
                }
                px[horizontal ? l * w + i : i * w + l] = (a / n) << 24 | (rr / n) << 16 | (g / n) << 8 | (b / n);
            }
        }
    }

    // ------------------------------------------------------------ one surface

    private final View host;
    private final float radius, rimWidth;
    private final boolean frosted;
    private int tint;
    private boolean lit;   // focused: brighter rim and gloss

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gloss = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF box = new RectF();
    private final Rect src = new Rect();
    private final int[] loc = new int[2];

    private boolean trim = true;   // gloss and rim (off for an icon's frosted backing: the card's overlay has them)

    /** host: the view this is the background (or foreground) of; it is repainted when the wallpaper changes. */
    Glass(View host, float radius, float rimWidth, int tint, boolean frosted) {
        this.host = host;
        this.radius = radius;
        this.rimWidth = rimWidth;
        this.tint = tint;
        this.frosted = frosted;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(rimWidth);
        if (frosted) HOSTS.add(new WeakReference<>(host));
    }

    /** only the frosted backdrop and the tint */
    Glass plain() { trim = false; return this; }

    void setGlassTint(int c) { tint = c; invalidateSelf(); }

    void setLit(boolean v) { lit = v; invalidateSelf(); }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        box.set(b);
        clip.reset();
        clip.addRoundRect(box, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);

        if (frosted) {
            if (backdrop != null && host.isAttachedToWindow()) {
                host.getLocationOnScreen(loc);
                float k = backdrop.getWidth() / (float) screenW;
                float l = loc[0] * k, t = loc[1] * k;
                float r = l + b.width() * host.getScaleX() * k, bt = t + b.height() * host.getScaleY() * k;
                src.set(Math.round(l), Math.round(t), Math.round(r), Math.round(bt));
                if (src.left >= 0 && src.top >= 0 && src.right <= backdrop.getWidth() && src.bottom <= backdrop.getHeight()
                        && src.width() > 0 && src.height() > 0) {
                    canvas.drawBitmap(backdrop, src, box, bitmapPaint);
                } else {
                    canvas.drawColor(0x8C000000);   // off screen (scrolled): plain smoked glass
                }
            } else {
                canvas.drawColor(0x66000000);       // no wallpaper yet
            }
        }

        fill.setColor(tint);
        canvas.drawRect(box, fill);

        if (!trim) { canvas.restore(); return; }
        // the gloss: light from above, fading out before the middle
        gloss.setShader(new LinearGradient(0, b.top, 0, b.top + b.height() * 0.6f,
            lit ? 0x52FFFFFF : 0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawRect(box, gloss);
        canvas.restore();

        // the rim: bright where the light hits (top-left), faint along the sides, bright again bottom-right
        float half = rimWidth / 2;
        RectF r = new RectF(box.left + half, box.top + half, box.right - half, box.bottom - half);
        rim.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom,
            lit ? new int[] {0xF2FFFFFF, 0x40FFFFFF, 0x26FFFFFF, 0xB3FFFFFF}
                : new int[] {0xA6FFFFFF, 0x1AFFFFFF, 0x0DFFFFFF, 0x59FFFFFF},
            new float[] {0f, 0.35f, 0.65f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(r, radius - half, radius - half, rim);
    }

    /** the rounded shape, so an elevation casts a soft shadow of it */
    @Override
    public void getOutline(Outline o) {
        o.setRoundRect(getBounds(), radius);
        o.setAlpha(0.6f);
    }

    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter cf) { }
    @SuppressWarnings("deprecation")
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    /** a colour with this alpha (0-1) */
    static int alpha(int c, float a) { return (Math.round(a * 255) << 24) | (c & 0xFFFFFF); }

    static final int CLEAR = Color.TRANSPARENT;
}
