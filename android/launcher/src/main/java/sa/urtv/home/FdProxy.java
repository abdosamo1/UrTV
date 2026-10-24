package sa.urtv.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;

/**
 * football-data.org from the app, not the page: the page (on file://) asks for https://api.football-data.org/v4/…
 * without the key, the WebView hands the request here (shouldInterceptRequest), and this adds the key and asks
 * the site itself. A request with the key in a custom header from the page needed a CORS preflight first, which
 * failed quietly: the site never saw a request. The result of the last one is kept for the settings screen.
 * The screensaver app has the same class; here it serves the home screen's card.
 */
final class FdProxy {
    private FdProxy() {}

    static final String API = "https://api.football-data.org/v4/";

    // The free plan allows 10 requests a minute, and answered more with 400 «Your API token is invalid» (seen in the
    // TV's log: the same request 200, then 400, in one minute). So: one request at a time, 6.5 s apart, and every
    // good answer kept (the screensaver, its preview and the home screen's card ask for the same things).
    private static final long GAP_MS = 6500;
    private static long lastCall;

    /** how long an answer stays good: team lists a week, a live game a minute, the rest 20 minutes */
    private static long ttl(String path) {
        if (path.startsWith("competitions/")) return 7L * 86400_000L;
        if (path.startsWith("matches/")) return 60_000L;
        return 20 * 60_000L;
    }

    private static java.io.File cacheFile(Context c, String path) {
        java.io.File dir = new java.io.File(c.getCacheDir(), "football-data");
        dir.mkdirs();
        return new java.io.File(dir, Integer.toHexString(path.hashCode()) + ".json");
    }

    private static WebResourceResponse json(int code, byte[] body) {
        return new WebResourceResponse("application/json", "utf-8", code < 100 ? 502 : code,
            code == 200 ? "OK" : "Error", Collections.singletonMap("Access-Control-Allow-Origin", "*"),
            new ByteArrayInputStream(body));
    }

    private static byte[] read(java.io.File f) {
        try { return readAll(new java.io.FileInputStream(f)); } catch (Exception e) { return null; }
    }

    /** null when the address is not football-data's (the WebView then loads it as usual) */
    static WebResourceResponse serve(Context c, String url, String key) {
        if (url == null || !url.startsWith(API) || key == null || key.isEmpty()) return null;
        String path = url.substring(API.length());
        Object[] r = get(c, path, key);
        return r == null ? null : json((Integer) r[0], (byte[]) r[1]);
    }

    /** {code, body}, or null (offline, nothing kept). The home screen asks the site for both apps (FdProvider). */
    static synchronized Object[] get(Context c, String path, String key) {
        String url = API + path;
        java.io.File cached = cacheFile(c, path);
        if (cached.isFile() && System.currentTimeMillis() - cached.lastModified() < ttl(path)) {
            byte[] b = read(cached);
            if (b != null) return new Object[] {200, b};   // asked a moment ago (another screen): no new request
        }
        long wait = lastCall + GAP_MS - System.currentTimeMillis();
        if (wait > 0) { try { Thread.sleep(wait); } catch (InterruptedException ignored) { } }
        lastCall = System.currentTimeMillis();
        int code = 0;
        byte[] body;
        String left = "";
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(15000);
            con.setReadTimeout(20000);
            con.setRequestProperty("X-Auth-Token", key);
            con.setRequestProperty("Accept", "application/json");
            code = con.getResponseCode();
            String n = con.getHeaderField("X-Requests-Available-Minute");   // the minute's requests left
            if (n != null) left = "بقي " + n;
            InputStream in = code >= 400 ? con.getErrorStream() : con.getInputStream();
            body = in == null ? new byte[0] : readAll(in);
        } catch (Exception e) {
            note(c, -1, url, "");
            byte[] old = cached.isFile() ? read(cached) : null;
            return old != null ? new Object[] {200, old} : null;   // offline: the last good answer, else the page keeps its own
        } finally {
            if (con != null) con.disconnect();
        }
        String why = code >= 400 ? reason(body) : "";
        note(c, code, url, why.isEmpty() ? left : left.isEmpty() ? why : why + " · " + left);
        if (code == 200) {
            try (java.io.FileOutputStream o = new java.io.FileOutputStream(cached)) { o.write(body); }
            catch (Exception ignored) { }
            return new Object[] {200, body};
        }
        // refused (over the minute's limit, or a filter it does not like): the last good answer if there is one
        byte[] old = cached.isFile() ? read(cached) : null;
        return old != null ? new Object[] {200, old} : new Object[] {code, body};
    }

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("fdproxy", Context.MODE_PRIVATE); }

    private static void note(Context c, int code, String url, String why) {
        // the log: newest first, eight lines «HH:mm code path · reason» (the key is never in the address)
        String line = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.ROOT).format(new java.util.Date())
            + " " + code + " " + url.substring(API.length()) + (why.isEmpty() ? "" : " · " + why);
        String[] old = sp(c).getString("log", "").split("\n");
        StringBuilder log = new StringBuilder(line);
        for (int i = 0; i < old.length && i < 7; i++) if (!old[i].isEmpty()) log.append('\n').append(old[i]);
        SharedPreferences.Editor e = sp(c).edit().putInt("code", code).putLong("at", System.currentTimeMillis())
            .putString("log", log.toString());
        if (code == 200) e.putLong("okAt", System.currentTimeMillis());
        e.apply();
    }

    /** the site's own words: {"error": "..."} or {"message": "..."} */
    private static String reason(byte[] body) {
        try {
            org.json.JSONObject j = new org.json.JSONObject(new String(body, "UTF-8"));
            String r = j.optString("error", j.optString("message", ""));
            return r.length() > 140 ? r.substring(0, 140) + "…" : r;
        } catch (Exception e) {
            return "";
        }
    }

    static String log(Context c) { return sp(c).getString("log", ""); }

    /** «تعمل», «المفتاح مرفوض», …, or "" before the first request */
    static String status(Context c) {
        SharedPreferences p = sp(c);
        if (!p.contains("code")) return "";
        int code = p.getInt("code", 0);
        // working if anything got through in the last half hour (a side request may still be refused)
        if (System.currentTimeMillis() - p.getLong("okAt", 0) < 30 * 60_000L) return "تعمل";
        return code == 200 ? "تعمل"
            : code == 400 ? "طلب مرفوض (٤٠٠)"
            : code == 401 || code == 403 ? "المفتاح مرفوض أو غير مفعّل"
            : code == 429 ? "طلبات كثيرة، تعود بعد دقيقة"
            : code == -1 ? "لا يوجد اتصال"
            : "خطأ " + code;
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toByteArray();
        } finally {
            in.close();
        }
    }
}
