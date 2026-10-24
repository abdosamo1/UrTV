package sa.urtv.home;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * football-data.org for the screensaver app (same signing key): the home screen opens first, so it is the one that
 * asks the site, through its queue and kept answers (FdProxy), and hands the screensaver, its preview and its
 * settings what they need. One line of requests for both apps: the free plan allows 10 a minute.
 *   call("fd", path, {key})  → {code, body} or {offline}
 *   call("fdlog")            → {log, status}
 */
public class FdProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (getContext().checkCallingPermission("sa.urtv.screensaver.READ_CONFIG") != PackageManager.PERMISSION_GRANTED)
            return null;
        Bundle b = new Bundle();
        if ("fdlog".equals(method)) {
            b.putString("log", FdProxy.log(getContext()));
            b.putString("status", FdProxy.status(getContext()));
            return b;
        }
        if (!"fd".equals(method) || arg == null || extras == null) return null;
        String key = extras.getString("key", "");
        if (key.isEmpty()) return null;
        Object[] r = FdProxy.get(getContext(), arg, key);
        if (r == null) { b.putBoolean("offline", true); return b; }
        b.putInt("code", (Integer) r[0]);
        b.putByteArray("body", (byte[]) r[1]);
        return b;
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
