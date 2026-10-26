package sa.urtv.screensaver;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The adhan's arithmetic, without Android: which prayer is next and when (tests/adhan_test.sh runs it on a JVM). */
final class AdhanTimes {
    private AdhanTimes() {}

    /** One day's times, "HH:MM" (Aladhan adds " (CEST)"), as a Mawaqit calendar has them:
     *  [fajr, shuruq, dhuhr, asr, maghrib, isha]; null when that day is not known. */
    interface Days { String[] day(int year, int month, int dayOfMonth); }

    static final String[] NAMES = {"الفجر", "الظهر", "العصر", "المغرب", "العشاء"};
    private static final int[] COL = {0, 2, 3, 4, 5};   // the five adhans in a day (shuruq has none)

    /** {time in ms, prayer 0-4} of the first adhan strictly after `after`, up to 3 days ahead; null if none known.
     *  Fajr only when it is wanted. */
    static long[] next(Days days, long after, TimeZone tz, boolean fajr) {
        Calendar now = Calendar.getInstance(tz);
        now.setTimeInMillis(after);
        for (int k = 0; k < 3; k++) {
            Calendar d = (Calendar) now.clone();
            d.add(Calendar.DAY_OF_MONTH, k);
            String[] t = days.day(d.get(Calendar.YEAR), d.get(Calendar.MONTH) + 1, d.get(Calendar.DAY_OF_MONTH));
            if (t == null || t.length < 6) continue;
            for (int i = 0; i < 5; i++) {
                if (i == 0 && !fajr) continue;
                int[] hm = hm(t[COL[i]]);
                if (hm == null) continue;
                Calendar a = (Calendar) d.clone();
                a.set(Calendar.HOUR_OF_DAY, hm[0]);
                a.set(Calendar.MINUTE, hm[1]);
                a.set(Calendar.SECOND, 0);
                a.set(Calendar.MILLISECOND, 0);
                if (a.getTimeInMillis() > after) return new long[] {a.getTimeInMillis(), i};
            }
        }
        return null;
    }

    private static final Pattern HM = Pattern.compile("^\\s*(\\d{1,2}):(\\d{2})");

    /** "05:10" or "05:10 (CEST)" -> {5, 10}; null if it is not a time */
    static int[] hm(String s) {
        if (s == null) return null;
        Matcher m = HM.matcher(s);
        if (!m.find()) return null;
        int h = Integer.parseInt(m.group(1)), mi = Integer.parseInt(m.group(2));
        return h < 24 && mi < 60 ? new int[] {h, mi} : null;
    }

    private static final Pattern CONF = Pattern.compile("confData\\s*=\\s*(\\{.*?\\});\\s*\\n", Pattern.DOTALL);

    /** A Mawaqit mosque page -> the text of its `confData` object (the year's timetable), or null */
    static String confData(String html) {
        if (html == null) return null;
        Matcher m = CONF.matcher(html);
        return m.find() ? m.group(1) : null;
    }

    /** Play only near the time: a TV that slept through the alarm must not call a late adhan when it wakes. */
    static boolean onTime(long at, long now) {
        return now >= at - 60_000L && now <= at + 10 * 60_000L;
    }

    /** "3:49 م", like the screen */
    static String clock(long ms, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(ms);
        int h = c.get(Calendar.HOUR_OF_DAY), mi = c.get(Calendar.MINUTE);
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return arabic(h12 + ":" + (mi < 10 ? "0" : "") + mi) + (h < 12 ? " ص" : " م");
    }

    static String arabic(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            if (ch >= '٠' && ch <= '٩') b.append((char) ('0' + (ch - '٠')));
            else if (ch >= '۰' && ch <= '۹') b.append((char) ('0' + (ch - '۰')));
            else b.append(ch);
        }
        return b.toString();
    }
}
