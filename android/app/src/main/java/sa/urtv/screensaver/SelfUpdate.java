package sa.urtv.screensaver;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The app updates itself from the repository's releases: no laptop, no agent. CI publishes both APKs to the
 * release `apk-latest`, whose text says «Both apps at version 3.N»; N is the APKs' versionCode. A newer one is
 * downloaded, checked (same package, newer version) and installed through PackageInstaller. An app may update
 * itself without asking on Android 12+ (USER_ACTION_NOT_REQUIRED, UPDATE_PACKAGES_WITHOUT_USER_ACTION); older
 * TVs show the system's «Install» once. optimize/install.sh allows the app to install apps (REQUEST_INSTALL_PACKAGES).
 * The same file is in both apps (sa.urtv.home has its copy); only the package line differs.
 */
final class SelfUpdate {
    private SelfUpdate() {}

    static final String RELEASE = "https://api.github.com/repos/abdosamo1/UrTV/releases/tags/apk-latest";
    private static final Pattern VERSION = Pattern.compile("version 3\\.(\\d+)");

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("selfupdate", Context.MODE_PRIVATE); }

    static long mine(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return Long.MAX_VALUE;
        }
    }

    /** The last result, for the settings screen ("" before the first check). */
    static String status(Context c) { return sp(c).getString("status", ""); }

    private static String say(Context c, String s) { sp(c).edit().putString("status", s).apply(); return s; }

    /**
     * Blocking (network). apk: this app's file in the release (urtv-screensaver.apk / urtv-home.apk).
     * busy: the app is on screen now; then a found update waits for the next check (unless forced).
     */
    static synchronized String check(Context c, String apk, boolean force, boolean busy) {
        try {
            JSONObject rel = new JSONObject(get(RELEASE));
            Matcher m = VERSION.matcher(rel.optString("body", ""));
            if (!m.find()) return say(c, "تعذر قراءة رقم الإصدار");
            long latest = Long.parseLong(m.group(1)), have = mine(c);
            if (latest <= have) return say(c, "التطبيق محدث (3." + arabic(have) + ")");
            if (busy && !force) return say(c, "يوجد إصدار جديد 3." + arabic(latest) + "، يُثبَّت حين لا يكون على الشاشة");
            String url = null;
            JSONArray assets = rel.optJSONArray("assets");
            for (int i = 0; assets != null && i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                if (apk.equals(a.optString("name"))) url = a.optString("browser_download_url");
            }
            if (url == null) return say(c, "لا يوجد ملف التحديث");
            File f = new File(c.getCacheDir(), "update.apk");
            download(url, f);
            // only ever this app, and only a newer build of it (the installer also refuses another signing key)
            PackageInfo pi = c.getPackageManager().getPackageArchiveInfo(f.getPath(), 0);
            long got = pi == null ? -1 : Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
            if (pi == null || !c.getPackageName().equals(pi.packageName) || got <= have) {
                f.delete();
                return say(c, "ملف التحديث غير صالح");
            }
            install(c, f);
            return say(c, "جارٍ تثبيت الإصدار 3." + arabic(got));
        } catch (Exception e) {
            return say(c, "تعذر التحديث: لا يوجد اتصال");
        }
    }

    private static void install(Context c, File apk) throws Exception {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        p.setAppPackageName(c.getPackageName());
        if (Build.VERSION.SDK_INT >= 31) p.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        int id = pi.createSession(p);
        PackageInstaller.Session s = pi.openSession(id);
        try {
            InputStream in = new FileInputStream(apk);
            OutputStream out = s.openWrite("base.apk", 0, apk.length());
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            s.fsync(out);
            out.close();
            in.close();
            // the system fills in the result: FLAG_MUTABLE is required for that on Android 12+
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            PendingIntent done = PendingIntent.getBroadcast(c, 11,
                new Intent(c, UpdateReceiver.class).setAction(UpdateReceiver.RESULT), flags);
            s.commit(done.getIntentSender());
        } finally {
            s.close();
        }
    }

    /** UpdateReceiver: the installer's answer. Older TVs ask first: show the system's «Install» screen. */
    static void result(Context c, Intent i) {
        int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                try { c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) { }
            }
            say(c, "اضغط «تثبيت» على الشاشة لإكمال التحديث");
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            say(c, "تم التحديث");
            new File(c.getCacheDir(), "update.apk").delete();
        } else {
            String why = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            say(c, "لم يكتمل التحديث" + (why == null ? "" : " (" + why + ")"));
        }
    }

    private static String get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        try {
            con.setConnectTimeout(15000);
            con.setReadTimeout(30000);
            con.setRequestProperty("Accept", "application/vnd.github+json");
            if (con.getResponseCode() != 200) throw new Exception("HTTP " + con.getResponseCode());
            InputStream in = con.getInputStream();
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            in.close();
            return b.toString("UTF-8");
        } finally {
            con.disconnect();
        }
    }

    /** GitHub answers the asset link with a redirect to its storage host: followed by hand (another host). */
    private static void download(String url, File to) throws Exception {
        for (int hop = 0; hop < 5; hop++) {
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setInstanceFollowRedirects(false);
            con.setConnectTimeout(15000);
            con.setReadTimeout(60000);
            int code = con.getResponseCode();
            if (code >= 300 && code < 400) { url = con.getHeaderField("Location"); con.disconnect(); continue; }
            if (code != 200) { con.disconnect(); throw new Exception("HTTP " + code); }
            InputStream in = con.getInputStream();
            OutputStream out = new FileOutputStream(to);
            try {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } finally {
                out.close();
                in.close();
                con.disconnect();
            }
            return;
        }
        throw new Exception("too many redirects");
    }

    static String arabic(long n) {
        StringBuilder b = new StringBuilder();
        for (char ch : String.valueOf(n).toCharArray()) b.append(ch);
        return b.toString();
    }
}
