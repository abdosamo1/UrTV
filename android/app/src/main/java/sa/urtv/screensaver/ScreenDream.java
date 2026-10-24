package sa.urtv.screensaver;

import android.service.dreams.DreamService;
import android.webkit.WebView;

/** The system screensaver: starts on the TV's own idle timer, any key wakes it. */
public class ScreenDream extends DreamService {
    private WebView web;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        ScreenView.onScreen++;
        web = ScreenView.create(this);
        setContentView(web);
    }

    @Override
    public void onDetachedFromWindow() {
        if (web != null) { web.destroy(); web = null; }
        ScreenView.onScreen = Math.max(0, ScreenView.onScreen - 1);
        super.onDetachedFromWindow();
    }
}
