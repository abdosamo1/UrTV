package sa.urtv.live;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A MAG (Stalker / Ministra) portal, as a MAG box talks to it: the box's MAC in a cookie, a handshake for a token,
 * then load.php?type=…&action=… with that token. The provider registers the MAC on his portal; nothing else (no
 * user, no password) is stored here. The MAC is made once from this TV's Android id, so it stays the same after a
 * reinstall, in the MAG range 00:1A:79.
 */
final class Portal {
    private static final String UA = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) "
        + "MAG200 stbapp ver: 2 rev: 250 Safari/533.3";

    private final Context c;
    private String api, token;

    Portal(Context c) { this.c = c.getApplicationContext(); }

    private SharedPreferences sp() { return c.getSharedPreferences("portal", Context.MODE_PRIVATE); }

    String url() { return sp().getString("url", ""); }

    synchronized void setUrl(String u) {
        sp().edit().putString("url", u == null ? "" : u.trim()).remove("api").apply();
        api = null; token = null;
    }

    /** 00:1A:79:XX:XX:XX from this TV's Android id (the same for this app after a reinstall) */
    String mac() {
        String m = sp().getString("mac", "");
        if (!m.isEmpty()) return m;
        String id = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ANDROID_ID);
        byte[] h = hash("SHA-256", "urtv-live:" + (id == null ? java.util.UUID.randomUUID().toString() : id));
        m = String.format(Locale.ROOT, "00:1A:79:%02X:%02X:%02X", h[0] & 0xff, h[1] & 0xff, h[2] & 0xff);
        sp().edit().putString("mac", m).apply();
        return m;
    }

    private static byte[] hash(String alg, String s) {
        try { return MessageDigest.getInstance(alg).digest(s.getBytes("UTF-8")); }
        catch (Exception e) { return new byte[32]; }
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format(Locale.ROOT, "%02X", x & 0xff));
        return s.toString();
    }

    /** where the portal's address may lead: …/c/ → …/portal.php or …/server/load.php or …/stalker_portal/server/load.php */
    private String[] candidates() {
        String u = url();
        if (!u.startsWith("http")) u = "http://" + u;
        if (u.contains("load.php") || u.contains("portal.php")) return new String[] {u.replaceAll("\\?.*$", "")};
        if (!u.endsWith("/")) u += "/";
        String root = u.endsWith("/c/") ? u.substring(0, u.length() - 2) : u;
        String host = root.replaceAll("^(https?://[^/]+/).*$", "$1");
        return new String[] {root + "portal.php", root + "server/load.php", host + "stalker_portal/server/load.php",
            host + "portal.php", host + "server/load.php"};
    }

    /** the portal's address up to its host: logos and posters given as paths are under it */
    String root() {
        String u = url();
        if (!u.startsWith("http")) u = "http://" + u;
        return u.replaceAll("^(https?://[^/]+).*$", "$1");
    }

    private String cookie() {
        try {
            return "mac=" + URLEncoder.encode(mac(), "UTF-8") + "; stb_lang=en; timezone="
                + URLEncoder.encode(TimeZone.getDefault().getID(), "UTF-8");
        } catch (Exception e) { return "mac=" + mac(); }
    }

    private int lastCode;

    private String fetch(String full, boolean auth) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(full).openConnection();
        try {
            con.setConnectTimeout(12000);
            con.setReadTimeout(30000);
            con.setRequestProperty("User-Agent", UA);
            con.setRequestProperty("X-User-Agent", "Model: MAG250; Link: WiFi");
            con.setRequestProperty("Cookie", cookie());
            con.setRequestProperty("Referer", url());
            con.setRequestProperty("Accept", "*/*");
            if (auth && token != null) con.setRequestProperty("Authorization", "Bearer " + token);
            lastCode = con.getResponseCode();
            InputStream in = lastCode >= 400 ? con.getErrorStream() : con.getInputStream();
            if (in == null) return "";
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            in.close();
            return b.toString("UTF-8");
        } finally {
            con.disconnect();
        }
    }

    private static Object js(String body) throws Exception {
        String t = body.trim();
        int i = t.indexOf('{');
        if (i > 0) t = t.substring(i);
        JSONObject o = new JSONObject(t);
        return o.opt("js");
    }

    /** the handshake and the profile: a token for this MAC. Throws with a message for the setup screen. */
    synchronized void login() throws Exception {
        if (url().isEmpty()) throw new Exception("لم يُدخل رابط البوابة");
        String saved = sp().getString("api", "");
        String[] list = saved.isEmpty() ? candidates() : new String[] {saved};
        Exception last = null;
        for (String a : list) {
            try {
                token = null;
                Object j = js(fetch(a + "?type=stb&action=handshake&token=&prehash=0&JsHttpRequest=1-xml", false));
                String t = j instanceof JSONObject ? ((JSONObject) j).optString("token", "") : "";
                if (t.isEmpty()) { last = new Exception("البوابة لم تُعطِ رمز دخول"); continue; }
                token = t;
                api = a;
                String sn = hex(hash("MD5", mac())).substring(0, 13);
                String dev = hex(hash("SHA-256", mac()));
                Object p = js(fetch(a + "?type=stb&action=get_profile&hd=1&ver=ImageDescription:%200.2.18-r23-250;"
                    + "%20ImageDate:%20Wed%20Aug%2029%2010:49:53%20EEST%202018;%20PORTAL%20version:%205.6.2;"
                    + "%20API%20Version:%20JS%20API%20version:%20343&num_banks=2&sn=" + sn
                    + "&stb_type=MAG250&client_type=STB&image_version=218&video_out=hdmi&device_id=" + dev
                    + "&device_id2=" + dev + "&signature=" + dev + "&auth_second_step=1&hw_version=1.7-BD-00"
                    + "&not_valid_token=0&JsHttpRequest=1-xml", true));
                if (p instanceof JSONObject) {
                    JSONObject pr = (JSONObject) p;
                    // a MAC the provider has not registered: the portal says so in the profile
                    int status = pr.optInt("status", 0);
                    String block = pr.optString("blocked", "0");
                    if ("1".equals(block)) throw new Exception("الجهاز موقوف لدى المزوّد");
                    if (status == 1 && pr.optString("id", "").isEmpty()) throw new Exception("الجهاز غير مسجّل لدى المزوّد");
                }
                sp().edit().putString("api", a).apply();
                return;
            } catch (Exception e) {
                last = e;
                if (e.getMessage() != null && e.getMessage().startsWith("الجهاز")) throw e;
            }
        }
        token = null; api = null;
        sp().edit().remove("api").apply();
        throw last != null && last.getMessage() != null && !last.getMessage().isEmpty() && last.getMessage().matches(".*[\\u0600-\\u06FF].*")
            ? last : new Exception("تعذّر الاتصال بالبوابة، تحقق من الرابط");
    }

    /** load.php?<query>: the "js" part as JSON text. Logs in first, and once more if the token has gone stale. */
    String call(String query) throws Exception {
        if (token == null) login();
        for (int attempt = 0; attempt < 2; attempt++) {
            String body = fetch(api + "?" + query + (query.contains("JsHttpRequest") ? "" : "&JsHttpRequest=1-xml"), true);
            boolean stale = lastCode == 401 || lastCode == 403 || body.contains("Authorization failed") || body.trim().isEmpty();
            if (stale && attempt == 0) { synchronized (this) { token = null; } login(); continue; }
            Object j = js(body);
            return j == null ? "null" : j.toString();
        }
        throw new Exception("رفضت البوابة الطلب");
    }
}
