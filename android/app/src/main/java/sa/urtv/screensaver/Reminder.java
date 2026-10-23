package sa.urtv.screensaver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.TimeZone;

/**
 * «بعد ٣٠ دقيقة · أذان العصر ٣:٤٩ م» at the top right, over whatever is on, for 12 seconds; it takes no focus
 * and no keys, so the film or the remote carry on. Google TV does not pop up other apps' notifications, so the
 * app draws its own card (SYSTEM_ALERT_WINDOW, granted over ADB by optimize/install.sh); without that permission
 * it posts an ordinary notification instead.
 */
final class Reminder {
    private Reminder() {}

    private static final long SHOW_MS = 12_000L;
    private static final int GOLD = 0xFFD6AF6B;
    private static View current;

    static boolean canDraw(Context c) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c);
    }

    /** prayer 0-4, adhanAt: the adhan's time in ms */
    static void show(Context c, int prayer, long adhanAt) {
        long mins = Math.max(1, Math.round((adhanAt - System.currentTimeMillis()) / 60_000.0));
        String when = "بعد " + AdhanTimes.arabic(String.valueOf(mins)) + " " + (mins >= 3 && mins <= 10 ? "دقائق" : "دقيقة");
        String what = "أذان " + AdhanTimes.NAMES[prayer] + " · " + AdhanTimes.clock(adhanAt, TimeZone.getDefault());
        if (canDraw(c)) overlay(c.getApplicationContext(), when, what);
        else notification(c, when, what);
    }

    private static void overlay(final Context c, String when, String what) {
        final WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;
        if (current != null) { try { wm.removeView(current); } catch (Exception ignored) { } current = null; }
        float d = c.getResources().getDisplayMetrics().density;

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.RIGHT);
        card.setPadding(px(d, 26), px(d, 16), px(d, 26), px(d, 18));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] {0xEE1E2620, 0xEE0E1410});   // dark glass, like the screen's cards
        bg.setCornerRadius(px(d, 22));
        bg.setStroke(px(d, 1), 0x66D6AF6B);
        card.setBackground(bg);
        card.setElevation(px(d, 12));

        TextView top = text(c, 15, "Thmanyah-Medium", GOLD);
        top.setText(when);
        TextView main = text(c, 24, "ThmanyahDisplay-Bold", 0xFFF6EFE0);
        main.setText(what);
        card.addView(top);
        card.addView(main);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                        : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.RIGHT;
        lp.x = px(d, 40);
        lp.y = px(d, 32);
        try {
            wm.addView(card, lp);
        } catch (Exception e) {   // refused after all: the notification instead
            notification(c, when, what);
            return;
        }
        current = card;
        card.setAlpha(0f);
        card.setTranslationX(px(d, 60));
        card.animate().alpha(1f).translationX(0).setDuration(450).start();
        new Handler(Looper.getMainLooper()).postDelayed(() ->
            card.animate().alpha(0f).translationX(px(d, 60)).setDuration(450).withEndAction(() -> {
                try { wm.removeView(card); } catch (Exception ignored) { }
                if (current == card) current = null;
            }).start(), SHOW_MS);
    }

    private static void notification(Context c, String when, String what) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel("reminder", "تنبيه قبل الأذان",
                NotificationManager.IMPORTANCE_HIGH));
            b = new Notification.Builder(c, "reminder");
        } else {
            b = new Notification.Builder(c).setPriority(Notification.PRIORITY_HIGH);
        }
        b.setSmallIcon(R.drawable.ic_screensaver).setContentTitle(what).setContentText(when).setAutoCancel(true);
        if (Build.VERSION.SDK_INT >= 26) b.setTimeoutAfter(15 * 60_000L);
        nm.notify(2, b.build());
    }

    private static TextView text(Context c, float sp, String font, int color) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.RIGHT);
        try { t.setTypeface(Typeface.createFromAsset(c.getAssets(), "fonts/" + font + ".otf")); }
        catch (Exception ignored) { }
        t.setShadowLayer(4, 0, 1, 0x80000000);
        return t;
    }

    private static int px(float density, int dp) { return Math.round(dp * density); }
}
