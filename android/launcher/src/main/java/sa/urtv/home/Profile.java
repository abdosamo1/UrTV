package sa.urtv.home;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

/**
 * The owner on the home screen (optional, set at setup): a name after the greeting («صباح الخير، يا محمد») and a
 * round photo at the top right. The name comes over ADB (DockReceiver PROFILE); the photo through this provider:
 *   content write --uri content://sa.urtv.home.profile/photo < /data/local/tmp/urtv-photo
 * Only the shell may write (android.permission.DUMP, see the manifest). optimize/dock.sh profile does both.
 */
public class Profile extends ContentProvider {
    static String name(Context c) { return Dock.prefs(c).getString("ownerName", ""); }

    static void setName(Context c, String n) {
        Dock.prefs(c).edit().putString("ownerName", n == null ? "" : n.trim()).apply();
        changed(c);
    }

    static File photo(Context c) { return new File(c.getFilesDir(), "avatar.png"); }

    static void clear(Context c) {
        setName(c, "");
        photo(c).delete();
        changed(c);
    }

    private static void changed(Context c) {
        c.sendBroadcast(new Intent(Dock.CHANGED).setPackage(c.getPackageName()));   // the home screen redraws
    }

    /** The photo as written, made a 384 px square (centre crop) once: the home screen draws it in a circle. */
    static void accept(Context c, File raw) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(raw.getPath(), o);
            int side = Math.min(o.outWidth, o.outHeight), sample = 1;
            while (side / (sample * 2) >= 384) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            Bitmap b = BitmapFactory.decodeFile(raw.getPath(), o);
            if (b == null) return;
            int s = Math.min(b.getWidth(), b.getHeight());
            Matrix m = new Matrix();
            float k = 384f / s;
            m.postScale(k, k);
            Bitmap sq = Bitmap.createBitmap(b, (b.getWidth() - s) / 2, (b.getHeight() - s) / 2, s, s, m, true);
            FileOutputStream out = new FileOutputStream(photo(c));
            try { sq.compress(Bitmap.CompressFormat.PNG, 100, out); } finally { out.close(); }
            changed(c);
        } catch (Exception ignored) {
        } finally {
            raw.delete();
        }
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"/photo".equals(uri.getPath()) || !mode.contains("w")) throw new FileNotFoundException("write-only: /photo");
        final Context c = getContext();
        final File raw = new File(c.getFilesDir(), "avatar-new");
        try {
            return ParcelFileDescriptor.open(raw,
                ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE,
                new Handler(Looper.getMainLooper()), e -> new Thread(() -> accept(c, raw)).start());
        } catch (java.io.IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri u) { return "image/*"; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
