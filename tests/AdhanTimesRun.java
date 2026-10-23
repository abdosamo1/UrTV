package sa.urtv.screensaver;

import java.util.Calendar;
import java.util.TimeZone;

/** Checks AdhanTimes (the adhan's arithmetic) on a JVM. Run by tests/adhan_test.py. */
public class AdhanTimesRun {
    static int failed = 0;
    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "PASS " : "FAIL ") + name + (ok ? "" : "  -> " + detail));
        if (!ok) failed++;
    }
    static final TimeZone BERLIN = TimeZone.getTimeZone("Europe/Berlin");
    static long at(int y, int m, int d, int h, int mi) {
        Calendar c = Calendar.getInstance(BERLIN);
        c.clear();
        c.set(y, m - 1, d, h, mi, 0);
        return c.getTimeInMillis();
    }
    static String show(long[] n) {
        if (n == null) return "null";
        Calendar c = Calendar.getInstance(BERLIN);
        c.setTimeInMillis(n[0]);
        return String.format("%s %02d-%02d %02d:%02d", AdhanTimes.NAMES[(int) n[1]], c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    public static void main(String[] a) {
        // every day: 05:10 / 07:00 / 12:30 / 15:40 / 18:20 / 20:05, as a Mawaqit calendar has it
        AdhanTimes.Days same = (y, m, d) -> new String[] {"05:10", "07:00", "12:30", "15:40", "18:20", "20:05"};
        String s;
        s = show(AdhanTimes.next(same, at(2026, 10, 24, 13, 0), BERLIN, true));
        check("after Dhuhr: Asr", s.equals("العصر 10-24 15:40"), s);
        s = show(AdhanTimes.next(same, at(2026, 10, 24, 15, 40), BERLIN, true));
        check("exactly at Asr: the next one (Maghrib)", s.equals("المغرب 10-24 18:20"), s);
        s = show(AdhanTimes.next(same, at(2026, 10, 24, 21, 0), BERLIN, true));
        check("after Isha, Fajr on: tomorrow's Fajr", s.equals("الفجر 10-25 05:10"), s);
        s = show(AdhanTimes.next(same, at(2026, 10, 24, 21, 0), BERLIN, false));
        check("after Isha, Fajr off: tomorrow's Dhuhr", s.equals("الظهر 10-25 12:30"), s);
        s = show(AdhanTimes.next(same, at(2026, 10, 24, 4, 0), BERLIN, false));
        check("before Fajr, Fajr off: Dhuhr", s.equals("الظهر 10-24 12:30"), s);
        // the clocks go back on 25 October 2026 (03:00 -> 02:00): Fajr is still at 05:10 local time
        long[] n = AdhanTimes.next(same, at(2026, 10, 24, 21, 0), BERLIN, true);
        check("over the clock change: local 05:10, 8 h 10 min + 1 h later", n != null && n[0] - at(2026, 10, 24, 21, 0) == (8 * 60 + 10 + 60) * 60_000L, show(n));
        // Aladhan's format, and a day missing (offline, month not fetched)
        AdhanTimes.Days aladhan = (y, m, d) -> d == 24 ? null : new String[] {"05:11 (CET)", "07:01 (CET)", "12:31 (CET)", "15:41 (CET)", "18:21 (CET)", "20:06 (CET)"};
        s = show(AdhanTimes.next(aladhan, at(2026, 10, 24, 13, 0), BERLIN, true));
        check("Aladhan times with «(CET)»; a missing day is skipped", s.equals("الفجر 10-25 05:11"), s);
        check("nothing known: null", AdhanTimes.next((y, m, d) -> null, at(2026, 10, 24, 13, 0), BERLIN, true) == null, "");
        check("hm: bad input is null", AdhanTimes.hm("--:--") == null && AdhanTimes.hm("25:00") == null && AdhanTimes.hm(null) == null, "");
        // on time: a TV that slept through the alarm doesn't call a late adhan
        long t = at(2026, 10, 24, 15, 40);
        check("on time: 0 s and 9 min late play; 11 min late and 2 min early don't",
            AdhanTimes.onTime(t, t) && AdhanTimes.onTime(t, t + 9 * 60_000L)
            && !AdhanTimes.onTime(t, t + 11 * 60_000L) && !AdhanTimes.onTime(t, t - 2 * 60_000L), "");
        String conf = AdhanTimes.confData("<script>\n    let confData = {\"calendar\":[{\"1\":[\"05:10\"]}],\"x\":\"a;b\"};\n    let y = 2;\n</script>");
        check("confData from a Mawaqit page", "{\"calendar\":[{\"1\":[\"05:10\"]}],\"x\":\"a;b\"}".equals(conf), String.valueOf(conf));
        check("clock in Arabic digits", AdhanTimes.clock(t, BERLIN).equals("٣:٤٠ م"), AdhanTimes.clock(t, BERLIN));
        System.out.println(failed == 0 ? "\nall adhan time checks passed" : "\n" + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
