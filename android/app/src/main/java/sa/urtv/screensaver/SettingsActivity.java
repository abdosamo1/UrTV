package sa.urtv.screensaver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Remote-friendly settings: every row is a button that opens a list. */
public class SettingsActivity extends Activity {
    private static final int DIALOG_THEME = android.R.style.Theme_Material_Dialog_Alert;

    private Prefs prefs;
    private LinearLayout list;

    /** Arabic locale for this screen, so every dialog title, list and button is laid out right-to-left. */
    @Override
    protected void attachBaseContext(android.content.Context base) {
        android.content.res.Configuration c = new android.content.res.Configuration(base.getResources().getConfiguration());
        c.setLocale(new java.util.Locale("ar"));
        c.setLayoutDirection(new java.util.Locale("ar"));
        super.attachBaseContext(base.createConfigurationContext(c));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        UpdateJob.schedule(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF0A2016, 0xFF04120C, 0xFF020806}));
        scroll.setVerticalScrollBarEnabled(false);
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        list.setPadding(dp(8), dp(36), dp(8), dp(48));
        int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(64), dp(860));
        frame.addView(list, new android.widget.FrameLayout.LayoutParams(width, -2, Gravity.CENTER_HORIZONTAL));
        scroll.addView(frame);
        setContentView(scroll);
        render();
    }

    @Override
    protected void onPause() {   // a new mosque or city moves the adhan times
        super.onPause();
        Adhan.update(this, false);
    }

    @Override
    protected void onResume() {   // back in front (from the TV's settings, or opened again over itself): show what is set now
        super.onResume();
        render();
    }

    private void render() {
        list.removeAllViews();
        TextView title = new TextView(this);
        title.setText("تلفزيونك");
        title.setTextColor(0xFFF6EFE0);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        title.setTypeface(font("ThmanyahDisplay-Bold"));
        list.addView(title);
        TextView sub = new TextView(this);
        sub.setText("إعدادات شاشة التوقف");
        sub.setTextColor(0xFFC6BBA4);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        sub.setTypeface(font("Thmanyah-Regular"));
        sub.setPadding(0, 0, 0, dp(18));
        list.addView(sub);

        row("معاينة الشاشة", "شاهد الشاشة الآن", R.drawable.ic_play, new Runnable() { public void run() {
            startActivity(new Intent(SettingsActivity.this, MainActivity.class)); } }).requestFocus();

        header("الموقع");
        row("المدينة", prefs.autoLocation() ? "تلقائي حسب الاتصال" : prefs.city()[0], R.drawable.ic_place,
            new Runnable() { public void run() { pickCity(); } });

        header("مواقيت الصلاة");
        row("الجامع", prefs.mosque()[0].isEmpty() ? "بدون جامع · حساب فلكي" : prefs.mosque()[1] + " · من مواقيت",
            R.drawable.ic_place, new Runnable() { public void run() { pickMosque(); } });

        header("الأذان");
        row("الأذان عند دخول الوقت", prefs.adhanOn() ? "يعمل · فوق أي تطبيق، ويوقف الفيديو ثم يكمله" : "متوقف",
            R.drawable.ic_screensaver, new Runnable() { public void run() {
                prefs.setAdhanOn(!prefs.adhanOn()); Adhan.update(SettingsActivity.this, false); render(); } });
        // the notice before each adhan has its own switch: it works with the adhan off too
        int rm = prefs.reminderMinutes();
        row("تنبيه قبل الأذان", rm <= 0 ? "متوقف" : "قبل " + AdhanTimes.arabic(String.valueOf(rm)) + " دقيقة · أعلى يمين الشاشة"
                + (Reminder.canDraw(this) ? "" : " (كإشعار: إذن الظهور فوق التطبيقات غير مفعّل)"),
            R.drawable.ic_eye, new Runnable() { public void run() { pickReminder(); } });
        if (prefs.adhanOn()) {
            String next = prefs.nextAdhan();
            row("صوت الأذان", Adhan.label(prefs.adhanVoice()) + " · للظهر والعصر والمغرب والعشاء",
                R.drawable.ic_star, new Runnable() { public void run() { pickAdhanVoice(false); } });
            row("أذان الفجر", prefs.adhanFajr().isEmpty() ? "بدون أذان للفجر" : Adhan.label(prefs.adhanFajr()),
                R.drawable.ic_star, new Runnable() { public void run() { pickAdhanVoice(true); } });
            row("مستوى صوت الأذان", AdhanTimes.arabic(String.valueOf(prefs.adhanVolume()))
                    + " · ثم يرجع الصوت كما كان", R.drawable.ic_sync,
                new Runnable() { public void run() { pickAdhanVolume(); } });
            row("جرّب الأذان الآن", next.isEmpty() ? "يشغّل الصوت المختار بمستواه" : "الأذان القادم: " + next,
                R.drawable.ic_play, new Runnable() { public void run() { testAdhan(); } });
        }

        header("المباريات");
        row("الفرق المتابعة (" + arabicNum(prefs.teams().size()) + "/" + arabicNum(Prefs.MAX_TEAMS) + ")", teamsLabel(),
            R.drawable.ic_trophy, new Runnable() { public void run() { manageTeams(); } });

        header("بجانب الساعة");
        row("المحتوى", Prefs.panelLabel(prefs.panel()), R.drawable.ic_eye, new Runnable() { public void run() { pickPanel(false); } });

        header("الشاشة الرئيسية (تلفزيونك)");
        row("بطاقة على الشاشة الرئيسية", Prefs.panelLabel(prefs.homePanel()), R.drawable.ic_eye,
            new Runnable() { public void run() { pickPanel(true); } });
        row("ترتيب التطبيقات المفضلة", "قائمة ترتّب فيها المفضلة بالأسهم", R.drawable.ic_star,
            new Runnable() { public void run() { openDockSettings(); } });

        header("التوقيت");
        row("تبديل الصورة كل", minutesLabel(Integer.parseInt(prefs.photoMinutes())), R.drawable.ic_sync,
            new Runnable() { public void run() { pickPhotoMinutes(); } });
        row("تعتيم ليلي", prefs.nightDim() ? "يعمل · تخفّ الإضاءة تدريجياً من ٩ م حتى ٧ ص" : "متوقف · الإضاءة كاملة دائماً",
            R.drawable.ic_eye, new Runnable() { public void run() { prefs.setNightDim(!prefs.nightDim()); render(); } });
        row("بدء شاشة التوقف بعد", idleLabel(), R.drawable.ic_screensaver,
            new Runnable() { public void run() { pickIdle(); } });

        header("التطبيق");
        row("التحديث التلقائي للصفحة", prefs.autoUpdate() ? "يعمل · كل ٦ ساعات" : "متوقف", R.drawable.ic_sync, new Runnable() { public void run() {
            prefs.setAutoUpdate(!prefs.autoUpdate()); render(); } });
        row("تحديث الشاشة الآن", Updater.versionNote(this) + " " + "ينزّل أحدث نسخة من الشاشة. تحديث التطبيق نفسه يكون عبر مساعد الذكاء الاصطناعي، لأنه يضبط الشاشة الرئيسية وشاشة التوقف، وإعداداتك كلها تبقى.", R.drawable.ic_sync, new Runnable() { public void run() { updateNow(); } });
    }

    // ---------- pickers ----------

    private void pickCity() {
        final Cities.Region[] regions = Cities.REGIONS;
        String[] labels = new String[regions.length + 2];
        labels[0] = (prefs.autoLocation() ? "✓ " : "") + "تلقائي حسب موقع الاتصال (المدينة والطقس والتوقيت)";
        labels[1] = "بحث عن مدينة أو قرية…";
        for (int i = 0; i < regions.length; i++) labels[i + 2] = regions[i].name + "  (" + regions[i].cities.length + ")";
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle("المدينة")
            .setItems(labels, (d, i) -> {
                if (i == 0) { prefs.setAutoLocation(true); render(); }
                else if (i == 1) searchCity();
                else pickCityIn(regions[i - 2]);
            })
            .show();
    }

    private void pickCityIn(final Cities.Region r) {
        String[] names = new String[r.cities.length];
        int sel = -1;
        for (int i = 0; i < names.length; i++) {
            names[i] = r.cities[i][0];
            if (names[i].equals(prefs.city()[0])) sel = i;
        }
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle(r.name)
            .setSingleChoiceItems(names, sel, (d, i) -> {
                prefs.setCity(r.cities[i][0], r.cities[i][1], r.cities[i][2]);
                d.dismiss();
                render();
            }).show();
    }

    // any place in the world, from Open-Meteo's GeoNames search
    private void searchCity() {
        ask("اسم المدينة أو القرية", q -> fetch("https://geocoding-api.open-meteo.com/v1/search?count=20&language=ar&name="
            + Uri.encode(q), json -> {
                JSONArray a = new JSONObject(json).optJSONArray("results");
                if (a == null || a.length() == 0) { toast("لا توجد نتائج"); return; }
                final int n = a.length();
                final String[] names = new String[n], lats = new String[n], lons = new String[n], rows = new String[n];
                for (int i = 0; i < n; i++) {
                    JSONObject c = a.getJSONObject(i);
                    names[i] = c.optString("name");
                    lats[i] = String.valueOf(c.optDouble("latitude"));
                    lons[i] = String.valueOf(c.optDouble("longitude"));
                    String region = c.optString("admin1", ""), country = c.optString("country", "");
                    rows[i] = names[i] + (region.isEmpty() ? "" : "  ·  " + region) + (country.isEmpty() ? "" : "  ·  " + country);
                }
                new AlertDialog.Builder(this, DIALOG_THEME).setTitle("اختر المكان")
                    .setItems(rows, (d, i) -> { prefs.setCity(names[i], lats[i], lons[i]); render(); }).show();
            }));
    }

    /** The voice for the four prayers, or for Fajr (with «بدون أذان للفجر» first: Fajr is optional). */
    private void pickAdhanVoice(final boolean fajr) {
        final List<String> ids = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        if (fajr) { ids.add(""); labels.add("بدون أذان للفجر"); }
        for (String f : Adhan.FAJR_FIRST) if (fajr) { ids.add(f); labels.add(Adhan.label(f)); }
        for (String[] v : Adhan.VOICES) if (!ids.contains(v[0])) { ids.add(v[0]); labels.add(v[1]); }
        String cur = fajr ? prefs.adhanFajr() : prefs.adhanVoice();
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle(fajr ? "أذان الفجر" : "صوت الأذان")
            .setSingleChoiceItems(labels.toArray(new String[0]), Math.max(0, ids.indexOf(cur)), (d, i) -> {
                if (fajr) prefs.setAdhanFajr(ids.get(i)); else prefs.setAdhanVoice(ids.get(i));
                d.dismiss();
                Adhan.update(this, false);
                render();
                if (!ids.get(i).isEmpty()) toast("اضغط «جرّب الأذان الآن» لتسمعه");
            })
            .show();
    }

    /** Minutes before the adhan for the notice, or off; first a sample of it. */
    private void pickReminder() {
        final List<Integer> vals = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        vals.add(-1); labels.add("اعرض تنبيهاً تجريبياً الآن");
        vals.add(0); labels.add("بدون تنبيه");
        for (int m : Prefs.REMINDER_MINUTES) { vals.add(m); labels.add("قبل " + AdhanTimes.arabic(String.valueOf(m)) + " دقيقة"); }
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle("تنبيه قبل الأذان")
            .setSingleChoiceItems(labels.toArray(new String[0]), Math.max(1, vals.indexOf(prefs.reminderMinutes())), (d, i) -> {
                d.dismiss();
                if (vals.get(i) < 0) {   // the sample: the next prayer's name, as if it were half an hour away
                    Reminder.show(this, 2, System.currentTimeMillis() + Math.max(10, prefs.reminderMinutes()) * 60_000L);
                    return;
                }
                prefs.setReminderMinutes(vals.get(i));
                Adhan.update(this, false);
                render();
            })
            .show();
    }

    /** Volume steps up to the TV's own maximum. */
    private void pickAdhanVolume() {
        android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        int max = am == null ? 100 : am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
        final List<Integer> vals = new ArrayList<>();
        for (int v : Adhan.VOLUMES) if (v <= max) vals.add(v);
        if (!vals.contains(max)) vals.add(max);
        String[] labels = new String[vals.size()];
        int sel = 0;
        for (int i = 0; i < vals.size(); i++) {
            labels[i] = AdhanTimes.arabic(String.valueOf(vals.get(i))) + (vals.get(i) == max ? "  (الأعلى)" : "");
            if (vals.get(i) == prefs.adhanVolume()) sel = i;
        }
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle("مستوى صوت الأذان (من " + AdhanTimes.arabic(String.valueOf(max)) + ")")
            .setSingleChoiceItems(labels, sel, (d, i) -> { prefs.setAdhanVolume(vals.get(i)); d.dismiss(); render(); })
            .show();
    }

    /** Plays the chosen voice now, at the adhan volume; «إيقاف» or Back stops it and puts the volume back. */
    private void testAdhan() {
        AdhanService.start(this, prefs.adhanVoice(), true);
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle(Adhan.label(prefs.adhanVoice()))
            .setMessage("يعمل الأذان الآن")
            .setPositiveButton("إيقاف", null)
            .setOnDismissListener(d -> AdhanService.stop())
            .show();
    }

    /** The mosque whose Mawaqit timetable the screen shows: by its link, or none (calculated times). */
    private void pickMosque() {
        String[] mine = prefs.mosque();
        String[] items = {
            "إدخال رابط جامع من موقع مواقيت (mawaqit.net)",
            (mine[0].isEmpty() ? "✓ " : "") + "بدون جامع · حساب فلكي حسب الموقع",
        };
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle("الجامع")
            .setMessage(mine[0].isEmpty() ? null : "الحالي: " + mine[1])
            .setItems(items, (d, i) -> {
                if (i == 0) askMosque();
                else { prefs.setMosque("", ""); render(); }
            })
            .show();
    }

    // The link of the mosque's page, e.g. https://mawaqit.net/de/masjid-tawasul-kirchheim-unter-teck-73230-germany:
    // fetched once to check it is a Mawaqit mosque with a timetable, and to take its name
    private void askMosque() {
        ask("رابط الجامع من mawaqit.net", q -> {
            final String slug = Prefs.mosqueSlug(q);
            if (slug == null) { toast("الرابط غير صحيح"); return; }
            fetch("https://mawaqit.net/en/" + slug, html -> {
                java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("confData\\s*=\\s*(\\{.*?\\});\\s*\\n", java.util.regex.Pattern.DOTALL).matcher(html);
                if (!m.find()) { toast("لم يُعثر على مواقيت هذا الجامع"); return; }
                JSONObject c = new JSONObject(m.group(1));
                if (c.optJSONArray("calendar") == null) { toast("لا توجد مواقيت لهذا الجامع"); return; }
                String name = Prefs.mosqueLabel(c.optString("name", slug));
                prefs.setMosque(slug, name);
                toast("تم اختيار " + name);
                render();
            });
        });
    }

    /** Followed teams: untick to remove, or add one (presets, league browsing, search). Up to 10. */
    private void manageTeams() {
        final List<String> cur = prefs.teams();
        String[] labels = new String[cur.size()];
        final boolean[] keep = new boolean[cur.size()];
        for (int i = 0; i < cur.size(); i++) { labels[i] = cur.get(i).split("\\|", 2)[1]; keep[i] = true; }
        AlertDialog.Builder b = new AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle(cur.isEmpty() ? "لا توجد فرق متابعة" : "الفرق المتابعة (ألغِ التحديد للحذف)")
            .setPositiveButton("حفظ", (d, w) -> {
                List<String> v = new ArrayList<>();
                for (int i = 0; i < cur.size(); i++) if (keep[i]) v.add(cur.get(i));
                prefs.setList("teams", v);
                render();
            })
            .setNegativeButton("إلغاء", null);
        if (cur.size() < Prefs.MAX_TEAMS) b.setNeutralButton("إضافة فريق", (d, w) -> pickTeam(0));
        if (!cur.isEmpty()) b.setMultiChoiceItems(labels, keep, (d, i, checked) -> keep[i] = checked);
        b.show();
    }

    private void addTeam(String id, String name) {
        if (prefs.addTeam(id, name)) toast("تمت متابعة " + name);
        else toast(prefs.teams().size() >= Prefs.MAX_TEAMS ? "الحد الأعلى ١٠ فرق" : "متابَع مسبقاً");
        render();
    }

    private void pickTeam(final int slot) {
        final List<String[]> opts = new ArrayList<>();
        for (String[] t : Prefs.TEAM_PRESETS) opts.add(t);
        String[] labels = new String[opts.size() + 2];
        for (int i = 0; i < opts.size(); i++) labels[i] = opts.get(i)[1];
        labels[opts.size()] = "تصفح الدوريات…";
        labels[opts.size() + 1] = "بحث عن أي فريق أو منتخب…";
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle("إضافة فريق")
            .setItems(labels, (d, i) -> {
                if (i < opts.size()) addTeam(opts.get(i)[0], opts.get(i)[1]);
                else if (i == opts.size()) pickLeague(slot);
                else searchTeam(slot);
            }).show();
    }

    /** Our bundled list first (every club of these leagues, offline); TheSportsDB only if that is missing. */
    private void pickLeague(final int slot) {
        final String[] labels = new String[Prefs.LEAGUES.length + 1];
        for (int i = 0; i < Prefs.LEAGUES.length; i++) labels[i] = Prefs.LEAGUES[i][1];
        labels[Prefs.LEAGUES.length] = "المنتخبات";
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle("الدوري")
            .setItems(labels, (d, i) -> {
                String league = i < Prefs.LEAGUES.length ? Prefs.LEAGUES[i][0] : Teams.NATIONAL;
                List<TeamSearch.Team> clubs = Teams.inLeague(this, league);
                if (!clubs.isEmpty()) showOurs(clubs, labels[i], false);
                else if (i < Prefs.LEAGUES.length)
                    fetch(SPORTS + "search_all_teams.php?l=" + Uri.encode(league), json -> showTeams(slot, json, labels[i], league));
                else toast("تعذّر تحميل القائمة، جرّب البحث عن الفريق");
            }).show();
    }

    private void searchTeam(final int slot) {
        ask("اسم الفريق بالعربية أو الإنجليزية (مثل الهلال أو Real Madrid)", q -> {
            List<TeamSearch.Team> found = Teams.search(this, q);
            if (!found.isEmpty()) showOurs(found.subList(0, Math.min(found.size(), 30)), "اختر الفريق", true);
            else if (!Arabic.hasArabic(q))   // a club outside our list: TheSportsDB's search, in English only
                fetch(SPORTS + "searchteams.php?t=" + Uri.encode(q), json -> showTeams(slot, json, "اختر الفريق", null));
            else toast("لا توجد نتائج، جرّب اسم الفريق بالإنجليزية");
        });
    }

    /** Clubs from our list: their Arabic names are already there, so following one needs no network. */
    private void showOurs(final List<TeamSearch.Team> clubs, String title, boolean withLeague) {
        String[] rows = new String[clubs.size()];
        for (int i = 0; i < rows.length; i++) {
            TeamSearch.Team t = clubs.get(i);
            rows[i] = Teams.label(this, t);
            if (withLeague && t.leagues.length > 0)
                rows[i] += "  ·  " + (Teams.NATIONAL.equals(t.leagues[0]) ? "منتخب" : leagueAr(t.leagues[0]));
        }
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle(title)
            .setItems(rows, (d, i) -> {
                TeamSearch.Team t = clubs.get(i);
                addTeam(t.id, t.ar.isEmpty() ? Arabic.translit(t.en) : t.ar);
            }).show();
    }

    private static final String SPORTS = "https://www.thesportsdb.com/api/v1/json/123/";

    /** TheSportsDB's answer, when our list has nothing. league != null: keep only clubs that really play in it. */
    private void showTeams(final int slot, String json, String title, String league) throws Exception {
        JSONArray all = new JSONObject(json).optJSONArray("teams");
        JSONArray a = new JSONArray();
        for (int i = 0; all != null && i < all.length(); i++) {
            JSONObject t = all.getJSONObject(i);
            if (!"Soccer".equalsIgnoreCase(t.optString("strSport", "Soccer"))) continue;
            boolean inLeague = league == null;
            for (int k = 1; !inLeague && k <= 7; k++)
                inLeague = league.equalsIgnoreCase(t.optString(k == 1 ? "strLeague" : "strLeague" + k));
            if (inLeague) a.put(t);
        }
        if (a.length() == 0) {
            toast(league == null ? "لا توجد نتائج" : "تعذّر تحميل هذا الدوري، جرّب البحث عن الفريق");
            return;
        }
        final int n = Math.min(a.length(), 60);
        final String[] ids = new String[n], names = new String[n], rows = new String[n];
        final TeamSearch.Team[] ours = new TeamSearch.Team[n];
        for (int i = 0; i < n; i++) {
            JSONObject t = a.getJSONObject(i);
            ids[i] = t.optString("idTeam");
            names[i] = t.optString("strTeam");
            ours[i] = Teams.byId(this, ids[i]);
            rows[i] = (ours[i] != null ? Teams.label(this, ours[i]) : Arabic.team(this, names[i]))
                + "  ·  " + leagueAr(t.optString("strLeague", ""));
        }
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle(title)
            .setItems(rows, (d, i) -> {
                if (ours[i] != null && !ours[i].ar.isEmpty()) addTeam(ids[i], ours[i].ar);
                else inArabic(names[i], Arabic.TEAM, ar -> addTeam(ids[i], ar));
            })
            .show();
    }

    /** «ترتيب المفضلة» lives in the launcher (it owns the row). */
    private void openDockSettings() {
        try {
            startActivity(new Intent().setClassName("sa.urtv.home", "sa.urtv.home.DockActivity"));
        } catch (RuntimeException e) {
            toast("ثبّت الواجهة الرئيسية «تلفزيونك» أولاً");
        }
    }

    /** The card beside the screensaver's clock, or (home) the one on the launcher's home screen. */
    private void pickPanel(final boolean home) {
        String[] labels = new String[Prefs.PANELS.length];
        String current = home ? prefs.homePanel() : prefs.panel();
        int sel = 0;
        for (int i = 0; i < labels.length; i++) {
            labels[i] = Prefs.PANELS[i][1];
            if (Prefs.PANELS[i][0].equals(current)) sel = i;
        }
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle(home ? "بطاقة على الشاشة الرئيسية" : "بجانب الساعة")
            .setSingleChoiceItems(labels, sel, (d, i) -> {
                if (home) prefs.setHomePanel(Prefs.PANELS[i][0]); else prefs.setPanel(Prefs.PANELS[i][0]);
                d.dismiss();
                render();
            })
            .show();
    }

    private void pickPhotoMinutes() {
        String[] labels = new String[Prefs.PHOTO_MINUTES.length];
        int sel = 0;
        for (int i = 0; i < labels.length; i++) {
            labels[i] = minutesLabel(Integer.parseInt(Prefs.PHOTO_MINUTES[i]));
            if (Prefs.PHOTO_MINUTES[i].equals(prefs.photoMinutes())) sel = i;
        }
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle("تبديل الصورة كل")
            .setSingleChoiceItems(labels, sel, (d, i) -> { prefs.setPhotoMinutes(Prefs.PHOTO_MINUTES[i]); d.dismiss(); render(); })
            .show();
    }

    /** The TV's own idle delay. Writing it needs WRITE_SETTINGS, which TVs rarely grant: then its own screen opens. */
    private void pickIdle() {
        if (!Prefs.canWriteSettings(this)) {
            toast("غيّرها من إعدادات التلفاز، أو بأمر من مساعدك");
            openDreamSettings();
            return;
        }
        String[] labels = new String[Prefs.IDLE_MINUTES.length];
        int sel = -1;
        long now = Prefs.idleMs(this);
        for (int i = 0; i < labels.length; i++) {
            labels[i] = minutesLabel(Integer.parseInt(Prefs.IDLE_MINUTES[i]));
            if (Long.parseLong(Prefs.IDLE_MINUTES[i]) * 60000 == now) sel = i;
        }
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle("بدء شاشة التوقف بعد")
            .setSingleChoiceItems(labels, sel, (d, i) -> {
                try {
                    Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT,
                        Integer.parseInt(Prefs.IDLE_MINUTES[i]) * 60000);
                } catch (SecurityException e) {
                    toast("غيّرها من إعدادات التلفاز، أو بأمر من مساعدك");
                }
                d.dismiss();
                render();
            })
            .show();
    }

    private String idleLabel() {
        long ms = Prefs.idleMs(this);
        if (ms <= 0) return "يحدده التلفاز";
        if (ms % 60000 != 0) return arabicNum((int) (ms / 1000)) + " ثانية";
        return minutesLabel((int) (ms / 60000));
    }

    /** دقيقة واحدة · دقيقتان · ٥ دقائق · ١٥ دقيقة */
    private static String minutesLabel(int n) {
        if (n == 1) return "دقيقة واحدة";
        if (n == 2) return "دقيقتان";
        return arabicNum(n) + (n <= 10 ? " دقائق" : " دقيقة");
    }

    private void updateNow() {
        toast("جارٍ التحقق…");
        new Thread(() -> {
            final String msg = Updater.check(getApplicationContext(), true);
            runOnUiThread(() -> { toast(msg); if (!isFinishing()) render(); });
        }).start();
    }

    private void openDreamSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    // ---------- labels ----------

    /** Wikidata's Arabic name (or a transliteration) fetched off the UI thread. */
    private void inArabic(final String en, final java.util.regex.Pattern kind, final Text then) {
        toast("جارٍ جلب الاسم بالعربية…");
        new Thread(() -> {
            String k = kind == Arabic.TEAM ? Arabic.known(getApplicationContext(), en) : null;
            final String ar = k != null ? k : Arabic.lookup(en, kind);
            runOnUiThread(() -> { if (!isFinishing()) then.got(ar); });
        }).start();
    }

    private static String leagueAr(String en) {
        for (String[] l : Prefs.LEAGUES) if (l[0].equalsIgnoreCase(en)) return l[1];
        return Arabic.translit(en);
    }

    private String teamsLabel() {
        List<String> names = new ArrayList<>();
        for (String t : prefs.teams()) names.add(t.split("\\|", 2)[1]);
        return names.isEmpty() ? "لا شيء" : Prefs.join(names, "، ");
    }

    private static String arabicNum(int n) {
        StringBuilder b = new StringBuilder();
        for (char c : String.valueOf(n).toCharArray()) b.append((char) ('٠' + (c - '0')));
        return b.toString();
    }

    // ---------- small UI helpers ----------

    private final java.util.Map<String, Typeface> fonts = new java.util.HashMap<>();

    /** Thmanyah, shared with the launcher's assets; falls back to the system font. */
    private Typeface font(String name) {
        Typeface tf = fonts.get(name);
        if (tf == null) {
            try { tf = Typeface.createFromAsset(getAssets(), "fonts/" + name + ".otf"); }
            catch (RuntimeException e) { tf = Typeface.DEFAULT; }
            fonts.put(name, tf);
        }
        return tf;
    }

    private void header(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFD6AF6B);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTypeface(font("Thmanyah-Medium"));
        t.setLetterSpacing(0.05f);
        t.setPadding(dp(6), dp(22), dp(6), dp(8));
        list.addView(t);
    }

    /** A card row: icon, label and its current value on a second line, chevron; gold when focused. */
    private View row(String label, String value, int icon, final Runnable onClick) {
        final LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        card.setPadding(dp(18), dp(12), dp(18), dp(12));
        card.setFocusable(true);
        card.setClickable(true);
        final android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(0x14FFFFFF);
        bg.setStroke(dp(2), 0x1FD6AF6B);
        card.setBackground(bg);

        final android.widget.ImageView ic = new android.widget.ImageView(this);
        final android.graphics.drawable.Drawable d = getDrawable(icon).mutate();
        d.setTint(0xFFD6AF6B);
        ic.setImageDrawable(d);
        card.addView(ic, new LinearLayout.LayoutParams(dp(24), dp(24)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        final TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(0xFFF6EFE0);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        name.setTypeface(font("Thmanyah-Medium"));
        name.setSingleLine(true);
        texts.addView(name);
        final TextView val = new TextView(this);
        if (value != null) {
            val.setText(value);
            val.setTextColor(0xFFC6BBA4);
            val.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            val.setTypeface(font("Thmanyah-Regular"));
            val.setSingleLine(true);
            val.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(val);
        }
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
        tp.setMarginStart(dp(16));
        card.addView(texts, tp);

        final TextView chevron = new TextView(this);
        chevron.setText("›");   // shown mirrored (‹) in the RTL row
        chevron.setTextColor(0x99C6BBA4);
        chevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        card.addView(chevron);

        card.setOnClickListener(v -> onClick.run());
        card.setOnFocusChangeListener((v, has) -> {
            // No scaling: a scaled rounded card overlapped its neighbours and blurred the text.
            bg.setColor(has ? 0x33D6AF6B : 0x14FFFFFF);
            bg.setStroke(dp(2), has ? 0xFFD6AF6B : 0x1FD6AF6B);
            chevron.setTextColor(has ? 0xFFD6AF6B : 0x99C6BBA4);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        list.addView(card, lp);
        return card;
    }

    interface Text { void got(String s); }
    interface Json { void got(String json) throws Exception; }

    private void ask(String hint, final Text then) {
        final EditText in = new EditText(this);
        in.setHint(hint);
        in.setSingleLine(true);
        in.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle("بحث").setView(in)
            .setPositiveButton("بحث", (d, w) -> {
                String q = in.getText().toString().trim();
                if (!q.isEmpty()) then.got(q);
            })
            .setNegativeButton("إلغاء", null).show();
        in.requestFocus();
    }

    /** GET on a background thread, result handled on the UI thread. */
    private void fetch(final String url, final Json then) {
        toast("جارٍ البحث…");
        new Thread(() -> {
            String body = null;
            HttpURLConnection con = null;
            try {
                con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(12000);
                con.setReadTimeout(15000);
                InputStream in = con.getInputStream();
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                in.close();
                body = b.toString("UTF-8");
            } catch (Exception ignored) {
            } finally {
                if (con != null) con.disconnect();
            }
            final String result = body;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (result == null) { toast("لا يوجد اتصال"); return; }
                try { then.got(result); } catch (Exception e) { toast("تعذرت قراءة النتائج"); }
            });
        }).start();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
