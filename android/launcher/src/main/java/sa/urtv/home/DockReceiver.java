package sa.urtv.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The favourites row from ADB, for agents and scripts. Only the shell can send these: the manifest requires
 * android.permission.DUMP, which apps can't hold.
 *
 *   am broadcast -n sa.urtv.home/.DockReceiver -a sa.urtv.home.GET_DOCK
 *   am broadcast -n sa.urtv.home/.DockReceiver -a sa.urtv.home.LIST_APPS
 *   am broadcast -n sa.urtv.home/.DockReceiver -a sa.urtv.home.SET_DOCK --es apps pkg1,pkg2,...
 *   am broadcast -n sa.urtv.home/.DockReceiver -a sa.urtv.home.RESET_DOCK
 *   am broadcast -n sa.urtv.home/.DockReceiver -a sa.urtv.home.HIDDEN                 left out, and why
 *   am broadcast -n sa.urtv.home/.DockReceiver -a sa.urtv.home.HIDE --es apps pkg1,pkg2,...
 *   am broadcast -n sa.urtv.home/.DockReceiver -a sa.urtv.home.SHOW --es apps pkg1,pkg2,...
 *   am broadcast -n sa.urtv.home/.DockReceiver -a sa.urtv.home.WEBAPP_ADD --es url https://… [--es name 'اسم']
 *   am broadcast -n sa.urtv.home/.DockReceiver -a sa.urtv.home.WEBAPP_REMOVE --es id web:…
 *   am broadcast -n sa.urtv.home/.DockReceiver -a sa.urtv.home.WEBAPPS
 * A website added as an app goes first in the favourites row, and SET_DOCK keeps it there.
 *   am broadcast -n sa.urtv.home/.DockReceiver -a sa.urtv.home.PROFILE --es name 'محمد'   (the greeting's name)
 *   am broadcast -n sa.urtv.home/.DockReceiver -a sa.urtv.home.PROFILE_CLEAR            (no name, no photo)
 * The photo: content write --uri content://sa.urtv.home.profile/photo < file (Profile).
 *
 * The answer is in the "data" of am's output. optimize/dock.sh wraps them all. People use «ترتيب المفضلة»
 * (DockActivity) instead.
 */
public class DockReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String action = String.valueOf(i.getAction());
        Map<String, String> apps = Dock.launchable(c);
        switch (action) {
            case "sa.urtv.home.LIST_APPS": {
                StringBuilder b = new StringBuilder();
                for (Map.Entry<String, String> e : apps.entrySet()) b.append(e.getKey()).append(" | ").append(e.getValue()).append('\n');
                setResultData(b.toString().trim());
                return;
            }
            case "sa.urtv.home.GET_DOCK":
                setResultData("dock: " + String.join(",", Dock.shown(c, apps)));
                return;
            case "sa.urtv.home.RESET_DOCK":
                Dock.reset(c);
                setResultData("dock: " + String.join(",", Dock.shown(c, apps)) + " (defaults)");
                return;
            case "sa.urtv.home.SET_DOCK": {
                String raw = i.getStringExtra("apps");
                if (raw == null) { setResultData("error: add --es apps pkg1,pkg2,..."); return; }
                List<String> row = new ArrayList<>(), skipped = new ArrayList<>();
                for (String p : raw.split(",")) {
                    p = p.trim();
                    if (p.isEmpty() || row.contains(p)) continue;
                    if (apps.containsKey(p) && row.size() < Dock.MAX) row.add(p); else skipped.add(p);
                }
                if (row.isEmpty()) { setResultData("error: none of these apps are on this TV's home screen"); return; }
                Dock.save(c, Dock.keepWebApps(c, row));
                setResultData("dock: " + String.join(",", Dock.shown(c, apps))
                    + (skipped.isEmpty() ? "" : "  skipped (not installed, hidden or over " + Dock.MAX + "): "
                       + String.join(",", skipped)));
                return;
            }
            case "sa.urtv.home.WEBAPP_ADD": {   // reads the site and its manifest: off the main thread
                final String url = i.getStringExtra("url"), name = i.getStringExtra("name");
                if (url == null) { setResultData("error: add --es url https://…"); return; }
                final PendingResult pending = goAsync();
                final Context app = c.getApplicationContext();
                new Thread(() -> {
                    String out;
                    try { out = WebApps.add(app, url.trim(), name); }
                    catch (Exception e) { out = "error: " + e.getMessage(); }
                    pending.setResultData(out);
                    pending.finish();
                }).start();
                return;
            }
            case "sa.urtv.home.WEBAPP_REMOVE": {
                String id = i.getStringExtra("id");
                if (id == null) { setResultData("error: add --es id web:…"); return; }
                setResultData(WebApps.remove(c, id.trim()) ? "removed " + id : "error: no such web app " + id);
                return;
            }
            case "sa.urtv.home.PROFILE": {
                String n = i.getStringExtra("name");
                if (n == null) { setResultData("error: add --es name 'الاسم'"); return; }
                Profile.setName(c, n.replace(",", " ").trim());
                setResultData("name: " + Profile.name(c) + " | photo: " + (Profile.photo(c).isFile() ? "yes" : "no"));
                return;
            }
            case "sa.urtv.home.PROFILE_CLEAR":
                Profile.clear(c);
                setResultData("profile cleared");
                return;
            case "sa.urtv.home.WEBAPPS": {
                StringBuilder b = new StringBuilder();
                for (WebApps.Site s : WebApps.all(c)) b.append(WebApps.PREFIX).append(s.id).append(" | ").append(s.name).append(" | ").append(s.url).append('\n');
                setResultData(b.length() == 0 ? "no web apps" : b.toString().trim());
                return;
            }
            case "sa.urtv.home.HIDDEN": {
                StringBuilder b = new StringBuilder();
                for (Map.Entry<String, String> e : Dock.leftOut(c).entrySet()) b.append(e.getKey()).append(" | ").append(e.getValue()).append('\n');
                setResultData(b.length() == 0 ? "nothing left out" : b.toString().trim());
                return;
            }
            case "sa.urtv.home.HIDE":
            case "sa.urtv.home.SHOW": {
                String raw = i.getStringExtra("apps");
                if (raw == null) { setResultData("error: add --es apps pkg1,pkg2,..."); return; }
                List<String> pkgs = new ArrayList<>();
                for (String p : raw.split(",")) if (!p.trim().isEmpty()) pkgs.add(p.trim());
                boolean hide = action.endsWith("HIDE");
                Dock.hideOrShow(c, pkgs, hide);
                setResultData((hide ? "hidden: " : "shown: ") + String.join(",", pkgs));
                return;
            }
            default:
                setResultData("error: unknown action " + action);
        }
    }
}
