package sa.urtv.screensaver;

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
 * The launcher has the same class for its card.
 */
final class FdProxy {
    private FdProxy() {}

    static final String API = "https://api.football-data.org/v4/";

    /** null when the address is not football-data's (the WebView then loads it as usual) */
    static WebResourceResponse serve(Context c, String url, String key) {
        if (url == null || !url.startsWith(API) || key == null || key.isEmpty()) return null;
        int code = 0;
        byte[] body;
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(15000);
            con.setReadTimeout(20000);
            con.setRequestProperty("X-Auth-Token", key);
            con.setRequestProperty("Accept", "application/json");
            code = con.getResponseCode();
            InputStream in = code >= 400 ? con.getErrorStream() : con.getInputStream();
            body = in == null ? new byte[0] : readAll(in);
        } catch (Exception e) {
            note(c, -1, url, "");
            return null;   // no connection: the page's fetch fails and it keeps what it shows
        } finally {
            if (con != null) con.disconnect();
        }
        note(c, code, url, code >= 400 ? reason(body) : "");
        return new WebResourceResponse("application/json", "utf-8", code < 100 ? 502 : code,
            code == 200 ? "OK" : "Error", Collections.singletonMap("Access-Control-Allow-Origin", "*"),
            new ByteArrayInputStream(body));
    }

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("fdproxy", Context.MODE_PRIVATE); }

    private static void note(Context c, int code, String url, String why) {
        // the log: newest first, eight lines «HH:mm code path · reason» (the key is never in the address)
        String line = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.ROOT).format(new java.util.Date())
            + " " + code + " " + url.substring(API.length()) + (why.isEmpty() ? "" : " · " + why);
        String[] old = sp(c).getString("log", "").split("\n");
        StringBuilder log = new StringBuilder(line);
        for (int i = 0; i < old.length && i < 7; i++) if (!old[i].isEmpty()) log.append('\n').append(old[i]);
        sp(c).edit().putInt("code", code).putLong("at", System.currentTimeMillis()).putString("log", log.toString()).apply();
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
