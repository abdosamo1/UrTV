package sa.urtv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;

/**
 * A mouse pointer for websites (WebAppActivity), drawn over the page. The arrows move it, faster while held; past an
 * edge the page (or the scrolling box under the pointer) scrolls instead. OK is a real tap at its tip: down on press,
 * up on release, so a site sees a finger, a button clicks and a field opens the keyboard. It fades after a few idle
 * seconds and comes back on the next arrow. Keys the on-screen keyboard sends (its arrows and Enter) are left alone.
 */
final class Cursor extends View {
    private static final long HIDE_AFTER_MS = 6000;

    private final float d;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable fade = () -> animate().alpha(0f).setDuration(400).start();
    private float x = -1, y = -1;
    private long downAt;

    Cursor(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setFocusable(false);
        setClickable(false);
        fill.setColor(0xFFFFFFFF);
        edge.setColor(0xFF1A1A1A);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(1.6f * d);
        edge.setStrokeJoin(Paint.Join.ROUND);
        // the classic pointer, its tip at (0, 0)
        float s = 1.5f * d;
        arrow.moveTo(0, 0);
        arrow.lineTo(0, 17 * s);
        arrow.lineTo(4.5f * s, 13 * s);
        arrow.lineTo(7.5f * s, 20 * s);
        arrow.lineTo(10 * s, 19 * s);
        arrow.lineTo(7 * s, 12.5f * s);
        arrow.lineTo(12.5f * s, 12.5f * s);
        arrow.close();
        wake();
    }

    @Override
    protected void onSizeChanged(int w, int hh, int ow, int oh) {
        if (x < 0) { x = w / 2f; y = hh / 2f; }   // starts in the middle of the screen
        x = Math.min(x, w - 1f);
        y = Math.min(y, hh - 1f);
    }

    @Override
    protected void onDraw(Canvas c) {
        c.save();
        c.translate(x, y);
        c.drawPath(arrow, fill);
        c.drawPath(arrow, edge);
        c.restore();
    }

    /** True when the key was the pointer's (arrows, OK), else the activity handles it (Back, media keys…). */
    boolean handle(KeyEvent e, WebView web) {
        if ((e.getFlags() & KeyEvent.FLAG_SOFT_KEYBOARD) != 0) return false;   // the on-screen keyboard's own keys
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (down) {
                wake();
                // 9 dp a press, faster the longer it is held, up to 48 dp a step
                float step = Math.min(9 + e.getRepeatCount() * 3, 48) * d;
                float dx = k == KeyEvent.KEYCODE_DPAD_LEFT ? -step : k == KeyEvent.KEYCODE_DPAD_RIGHT ? step : 0;
                float dy = k == KeyEvent.KEYCODE_DPAD_UP ? -step : k == KeyEvent.KEYCODE_DPAD_DOWN ? step : 0;
                move(dx, dy, web);
            }
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            wake();
            if (down && e.getRepeatCount() == 0) {
                downAt = SystemClock.uptimeMillis();
                touch(web, MotionEvent.ACTION_DOWN);
            } else if (!down && downAt != 0) {
                touch(web, MotionEvent.ACTION_UP);
                downAt = 0;
            }
            return true;
        }
        return false;
    }

    private void move(float dx, float dy, WebView web) {
        float nx = x + dx, ny = y + dy;
        float maxX = getWidth() - 1f, maxY = getHeight() - 1f;
        // what doesn't fit on the screen scrolls the page instead (at the edges)
        float sx = nx < 0 ? nx : nx > maxX ? nx - maxX : 0;
        float sy = ny < 0 ? ny : ny > maxY ? ny - maxY : 0;
        x = Math.max(0, Math.min(nx, maxX));
        y = Math.max(0, Math.min(ny, maxY));
        if (sx != 0 || sy != 0) scroll(web, sx * 2, sy * 2);
        invalidate();
    }

    /** Scrolls the nearest scrolling box under the pointer (a site's own list, a dialog), else the page. */
    @SuppressWarnings("deprecation")
    private void scroll(WebView web, float dx, float dy) {
        float scale = web.getScale() > 0 ? web.getScale() : d;   // screen pixels per CSS pixel
        String js = "(function(x,y,dx,dy){var e=document.elementFromPoint(x,y);"
            + "while(e&&e!==document.body&&e!==document.documentElement){var s=getComputedStyle(e);"
            + "if((dy&&e.scrollHeight>e.clientHeight&&/(auto|scroll)/.test(s.overflowY))"
            + "||(dx&&e.scrollWidth>e.clientWidth&&/(auto|scroll)/.test(s.overflowX))){e.scrollBy(dx,dy);return;}"
            + "e=e.parentElement;}window.scrollBy(dx,dy);})("
            + Math.round(x / scale) + "," + Math.round(y / scale) + ","
            + Math.round(dx / scale) + "," + Math.round(dy / scale) + ")";
        web.evaluateJavascript(js, null);
    }

    private void touch(WebView web, int action) {
        MotionEvent ev = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action, x, y, 0);
        ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        web.dispatchTouchEvent(ev);
        ev.recycle();
    }

    private void wake() {
        h.removeCallbacks(fade);
        animate().cancel();
        setAlpha(1f);
        h.postDelayed(fade, HIDE_AFTER_MS);
    }

    @Override
    protected void onDetachedFromWindow() {
        h.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }
}
