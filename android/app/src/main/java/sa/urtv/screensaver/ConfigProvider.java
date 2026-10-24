package sa.urtv.screensaver;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * Read-only: hands the launcher (same signing key, see READ_CONFIG in the manifest) what its home-screen
 * card needs: the page settings as a URL fragment and which card to show ("none" = off); and today's Fajr, Dhuhr
 * and Maghrib for its greeting.
 */
public class ConfigProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        Prefs p = new Prefs(getContext());
        // and today's Fajr, Dhuhr and Maghrib (minutes; -1 = not known yet) for the home screen's greeting
        int[] t = Adhan.today(getContext());
        MatrixCursor c = new MatrixCursor(new String[] {"fragment", "home_panel", "fajr", "dhuhr", "maghrib"});
        c.addRow(new Object[] {p.fragment(), p.homePanel(), t == null ? -1 : t[0], t == null ? -1 : t[1], t == null ? -1 : t[2]});
        return c;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
