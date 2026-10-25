package sa.urtv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;

/**
 * A mouse pointer for websites (WebAppActivity), drawn over the page like TV Bro's: a clear disc with a ring. The
 * arrows glide it, faster while held; pushed past an edge it drags the page like a finger instead. OK is a
 * real tap at its centre, down on press and up on release, built as TV Bro builds it (a finger, with pressure), so a
 * button clicks and a field opens the keyboard. It fades after a few idle seconds and comes back on the next arrow.
 * Keys the on-screen keyboard sends (its arrows and Enter) are left alone.
 */
final class Cursor extends View {
    private static final long HIDE_AFTER_MS = 6000;

    private final float d;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable fade = () -> animate().alpha(0f).setDuration(400).start();
    private static final int UP = 1, DOWN = 2, LEFT = 4, RIGHT = 8;
    private static final float NUDGE = 6;   // dp, a single short press
    private float x = -1, y = -1;
    private long downAt;
    private boolean pressed;                // OK held: the finger is down
    private int held;                       // the arrows held now (UP | DOWN | LEFT | RIGHT)
    private long heldSince, lastFrame;
    private boolean dragging;               // edge scrolling: a second, invisible finger drags the page
    private float fx, fy, fx0, fy0;         // where that finger is, and where it went down
    private long dragDownAt;
    private WebView web;
    /** Every frame while an arrow is held: glides, from 260 dp/s up to 1100 dp/s after a second held. */
    private final Runnable frame = new Runnable() {
        @Override public void run() {
            if (held == 0 || web == null) { endDrag(); return; }
            long now = SystemClock.uptimeMillis();
            float dt = Math.min(now - lastFrame, 50) / 1000f;
            lastFrame = now;
            float speed = Math.min(260 + (now - heldSince) * 0.84f, 1100) * d * dt;
            float dx = ((held & RIGHT) != 0 ? 1 : 0) - ((held & LEFT) != 0 ? 1 : 0);
            float dy = ((held & DOWN) != 0 ? 1 : 0) - ((held & UP) != 0 ? 1 : 0);
            move(dx * speed, dy * speed, web);
            wake();
            postOnAnimation(this);
        }
    };

    Cursor(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setFocusable(false);
        setClickable(false);
        // like TV Bro's: a half-clear white disc with a grey ring, smaller while OK is held
        fill.setColor(0x80FFFFFF);
        edge.setColor(0xFF888888);
        edge.setStyle(Paint.Style.STROKE);
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
        float r = getWidth() / 100f;   // 19 px on a 1920 px screen, as in TV Bro
        if (pressed) r = Math.max(r - 5 * d, r / 2);
        edge.setStrokeWidth(Math.max(getWidth() / 400f, 1));
        c.drawCircle(x, y, r, fill);
        c.drawCircle(x, y, r, edge);
    }

    /** True when the key was the pointer's (arrows, OK), else the activity handles it (Back, media keys…). */
    boolean handle(KeyEvent e, WebView web) {
        if ((e.getFlags() & KeyEvent.FLAG_SOFT_KEYBOARD) != 0) return false;   // the on-screen keyboard's own keys
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
            int bit = k == KeyEvent.KEYCODE_DPAD_UP ? UP : k == KeyEvent.KEYCODE_DPAD_DOWN ? DOWN
                : k == KeyEvent.KEYCODE_DPAD_LEFT ? LEFT : RIGHT;
            if (down && e.getRepeatCount() == 0) {   // the key's own repeats are ignored: the frames move it
                wake();
                this.web = web;
                boolean start = held == 0;
                if (start) { heldSince = SystemClock.uptimeMillis(); lastFrame = heldSince; }
                held |= bit;
                // a short press still moves a little, at once
                move(bit == LEFT ? -NUDGE * d : bit == RIGHT ? NUDGE * d : 0, bit == UP ? -NUDGE * d : bit == DOWN ? NUDGE * d : 0, web);
                if (start) postOnAnimation(frame);   // one glide loop, however many arrows are held
            } else if (!down) {
                held &= ~bit;
            }
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            wake();
            // some remotes send OK and Enter for one click: one finger down and up all the same
            if (down && e.getRepeatCount() == 0 && !pressed) {
                pressed = true;
                downAt = SystemClock.uptimeMillis();
                touch(web, MotionEvent.ACTION_DOWN);
                invalidate();
            } else if (!down && pressed) {
                pressed = false;
                touch(web, MotionEvent.ACTION_UP);
                logTarget(web);
                invalidate();
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
        if (sx != 0 || sy != 0) drag(sx * 2, sy * 2, web);
        invalidate();
    }

    /**
     * Scrolls as a finger would, like TV Bro: a drag from the middle of the screen, against the pointer's push, so
     * whatever scrolls under it (the page, a site's own list or panel) moves natively. Lifted and started again
     * from the middle when the finger nears an edge, and lifted still (no fling) when the arrow is let go.
     */
    private void drag(float dx, float dy, WebView web) {
        long now = SystemClock.uptimeMillis();
        if (!dragging) {
            fx = fx0 = getWidth() / 2f;
            fy = fy0 = getHeight() / 2f;
            dragDownAt = now;
            finger(web, MotionEvent.ACTION_DOWN, fx, fy, dragDownAt, now);
            dragging = true;
        }
        fx -= dx;
        fy -= dy;
        finger(web, MotionEvent.ACTION_MOVE, fx, fy, dragDownAt, now);
        if (fx < getWidth() * 0.1f || fx > getWidth() * 0.9f || fy < getHeight() * 0.1f || fy > getHeight() * 0.9f) endDrag();
    }

    private void endDrag() {
        if (!dragging || web == null) { dragging = false; return; }
        long now = SystemClock.uptimeMillis();
        if (Math.hypot(fx - fx0, fy - fy0) < 12 * d) {
            // too short to be a drag: cancelled, so the page never takes it for a tap in the middle
            finger(web, MotionEvent.ACTION_CANCEL, fx, fy, dragDownAt, now);
        } else {
            // a pause before lifting, so the page sees no speed and doesn't fling on
            finger(web, MotionEvent.ACTION_MOVE, fx, fy, dragDownAt, now + 120);
            finger(web, MotionEvent.ACTION_UP, fx, fy, dragDownAt, now + 140);
        }
        dragging = false;
    }

    /** For the log (urtv-web): what the tap hit, as the page sees it. */
    @SuppressWarnings("deprecation")
    private void logTarget(WebView web) {
        float scale = web.getScale() > 0 ? web.getScale() : d;
        Log.d("urtv-web", "tap " + Math.round(x) + "," + Math.round(y));
        web.evaluateJavascript("(function(){var e=document.elementFromPoint(" + Math.round(x / scale) + ","
            + Math.round(y / scale) + ");console.log('tap hit '+(e?e.outerHTML.slice(0,160):'nothing'));})()", null);
    }

    private void touch(WebView web, int action) {
        if (action == MotionEvent.ACTION_DOWN) endDrag();
        finger(web, action, x, y, downAt, SystemClock.uptimeMillis());
    }

    private static void finger(WebView web, int action, float px, float py, long down, long at) {
        MotionEvent.PointerProperties pp = new MotionEvent.PointerProperties();
        pp.id = 0;
        pp.toolType = MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
        pc.x = px;
        pc.y = py;
        pc.pressure = 1f;
        pc.size = 1f;
        MotionEvent ev = MotionEvent.obtain(down, at, action, 1,
            new MotionEvent.PointerProperties[]{pp}, new MotionEvent.PointerCoords[]{pc}, 0, 0, 1f, 1f, 0, 0,
            InputDevice.SOURCE_TOUCHSCREEN, 0);
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
    public void onWindowFocusChanged(boolean has) {
        super.onWindowFocusChanged(has);
        if (!has) { held = 0; endDrag(); }   // a key-up that went elsewhere must not leave it gliding or dragging
    }

    @Override
    protected void onDetachedFromWindow() {
        h.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }
}
