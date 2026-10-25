package sa.urtv.home;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Websites on the home screen as apps (optional: none unless added over ADB, optimize/dock.sh webapp add URL).
 * Like «Install app» on a phone: name, icon, colours and start page come from the site's web app manifest; the
 * app opens full screen, with the icon on a splash first (WebAppActivity). In the favourites row and the grid a
 * web app is the entry "web:<id>", next to the package names.
 */
final class WebApps {
    private WebApps() {}

    static final String PREFIX = "web:";

    static final class Site {
        String id, name, url;
        int background = 0xFF0E1410;
        boolean desktop;   // opened as on a computer (WebAppActivity): a desktop browser, a wide page
    }

    static boolean isWeb(String key) { return key != null && key.startsWith(PREFIX); }

    static List<Site> all(Context c) {
        List<Site> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(Dock.prefs(c).getString("webapps", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Site s = new Site();
                s.id = o.getString("id");
                s.name = o.getString("name");
                s.url = o.getString("url");
                s.background = o.optInt("bg", s.background);
                s.desktop = o.optBoolean("desktop", false);
                out.add(s);
            }
        } catch (Exception ignored) { }
        return out;
    }

    static Site get(Context c, String id) {
        for (Site s : all(c)) if (s.id.equals(id)) return s;
        return null;
    }

    private static void store(Context c, List<Site> sites) {
        JSONArray a = new JSONArray();
        try {
            for (Site s : sites)
                a.put(new JSONObject().put("id", s.id).put("name", s.name).put("url", s.url).put("bg", s.background)
                    .put("desktop", s.desktop));
        } catch (Exception ignored) { }
        Dock.prefs(c).edit().putString("webapps", a.toString()).apply();
    }

    static File icon(Context c, String id) { return new File(new File(c.getFilesDir(), "webapps"), id + ".png"); }

    static Bitmap iconBitmap(Context c, String id) {
        File f = icon(c, id);
        return f.isFile() ? BitmapFactory.decodeFile(f.getPath()) : null;
    }

    /**
     * Blocking (network). Reads the page and its manifest, saves the icon, adds the site (or updates it) and puts
     * it first in the favourites row if there is room. `name` overrides the manifest's. Returns a line for ADB.
     */
    static String add(Context c, String url, String name) throws Exception {
        if (!url.matches("https?://.+")) throw new Exception("give a full address, https://…");
        URL page = new URL(url);
        String html = get(page);
        String id = page.getHost().replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "").toLowerCase(Locale.ROOT);
        String title = name, start = url, iconUrl = null;
        int bg = 0xFF0E1410;

        Matcher m = Pattern.compile("<link[^>]*rel=[\"']?manifest[\"']?[^>]*>", Pattern.CASE_INSENSITIVE).matcher(html);
        if (m.find()) {
            Matcher h = Pattern.compile("href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(m.group());
            if (h.find()) {
                URL mf = new URL(page, h.group(1));
                JSONObject j = new JSONObject(get(mf));
                if (title == null || title.isEmpty()) title = j.optString("name", j.optString("short_name", ""));
                if (j.has("start_url")) start = new URL(mf, j.getString("start_url")).toString();
                bg = color(j.optString("background_color", ""), bg);
                iconUrl = bestIcon(mf, j.optJSONArray("icons"));
            }
        }
        if (iconUrl == null) {   // no manifest: the touch icon, else the page's own icon
            Matcher a = Pattern.compile("<link[^>]*rel=[\"'][^\"']*(apple-touch-icon|icon)[^\"']*[\"'][^>]*>",
                Pattern.CASE_INSENSITIVE).matcher(html);
            while (a.find()) {
                Matcher h = Pattern.compile("href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(a.group());
                if (h.find()) { iconUrl = new URL(page, h.group(1)).toString(); if (a.group(1).startsWith("apple")) break; }
            }
        }
        if (title == null || title.isEmpty()) {
            Matcher t = Pattern.compile("<title>([^<]*)</title>", Pattern.CASE_INSENSITIVE).matcher(html);
            title = t.find() ? t.group(1).trim() : page.getHost();
        }
        if (iconUrl != null) {
            Bitmap b = BitmapFactory.decodeStream(new java.io.ByteArrayInputStream(bytes(new URL(iconUrl))));
            if (b != null) {
                File f = icon(c, id);
                f.getParentFile().mkdirs();
                FileOutputStream o = new FileOutputStream(f);
                try { b.compress(Bitmap.CompressFormat.PNG, 100, o); } finally { o.close(); }
            }
        }

        List<Site> sites = all(c);
        for (int i = sites.size() - 1; i >= 0; i--) if (sites.get(i).id.equals(id)) sites.remove(i);
        Site s = new Site();
        s.id = id;
        s.name = title.replace(",", " ");
        s.url = start;
        s.background = bg;
        sites.add(s);
        store(c, sites);

        // always in the favourites row, first: a full row gives up its last app for it
        List<String> row = new ArrayList<>(Dock.shown(c, Dock.launchable(c)));
        row.remove(PREFIX + id);
        row.add(0, PREFIX + id);
        while (row.size() > Dock.MAX) row.remove(row.size() - 1);
        Dock.save(c, row);   // also redraws a running home screen
        return "added " + PREFIX + id + " | " + s.name + " | " + s.url + (iconUrl == null ? " (no icon found)" : "");
    }

    static boolean remove(Context c, String id) {
        if (id.startsWith(PREFIX)) id = id.substring(PREFIX.length());
        List<Site> sites = all(c);
        boolean gone = false;
        for (int i = sites.size() - 1; i >= 0; i--) if (sites.get(i).id.equals(id)) { sites.remove(i); gone = true; }
        store(c, sites);
        icon(c, id).delete();
        List<String> row = new ArrayList<>(Dock.saved(c));
        row.remove(PREFIX + id);
        Dock.save(c, row);
        return gone;
    }

    /** The computer version of the site (true) or the one its own layout picks for the TV's width (false). */
    static boolean setDesktop(Context c, String id, boolean on) {
        if (id.startsWith(PREFIX)) id = id.substring(PREFIX.length());
        List<Site> sites = all(c);
        boolean found = false;
        for (Site s : sites) if (s.id.equals(id)) { s.desktop = on; found = true; }
        if (found) store(c, sites);
        return found;
    }

    static Intent intent(Context c, String id) {
        return new Intent(c, WebAppActivity.class).putExtra("id", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** The largest PNG/any icon of the manifest (512 rather than 192), resolved against the manifest's address. */
    private static String bestIcon(URL base, JSONArray icons) throws Exception {
        if (icons == null) return null;
        String best = null;
        int bestSize = -1;
        for (int i = 0; i < icons.length(); i++) {
            JSONObject o = icons.getJSONObject(i);
            if (o.optString("purpose", "any").contains("monochrome")) continue;
            int size = 0;
            Matcher m = Pattern.compile("(\\d+)x\\d+").matcher(o.optString("sizes", ""));
            while (m.find()) size = Math.max(size, Integer.parseInt(m.group(1)));
            if (size > bestSize) { bestSize = size; best = new URL(base, o.getString("src")).toString(); }
        }
        return best;
    }

    private static int color(String css, int fallback) {
        try { return css.isEmpty() ? fallback : Color.parseColor(css.trim()); } catch (Exception e) { return fallback; }
    }

    private static String get(URL u) throws Exception { return new String(bytes(u), "UTF-8"); }

    private static byte[] bytes(URL u) throws Exception {
        HttpURLConnection con = (HttpURLConnection) u.openConnection();
        try {
            con.setConnectTimeout(15000);
            con.setReadTimeout(20000);
            con.setInstanceFollowRedirects(true);
            con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) UrTV");
            if (con.getResponseCode() >= 400) throw new Exception("HTTP " + con.getResponseCode() + " for " + u);
            InputStream in = con.getInputStream();
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            in.close();
            return b.toByteArray();
        } finally {
            con.disconnect();
        }
    }
}
