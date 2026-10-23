package sa.urtv.screensaver;

import android.app.admin.DeviceAdminReceiver;

/**
 * Device admin with one policy, force-lock (res/xml/device_admin.xml): after an adhan that woke a TV in standby,
 * DevicePolicyManager.lockNow() puts it back to sleep. Made active by optimize/install.sh over ADB
 * (dpm set-active-admin); removed again by the app itself before uninstall (SaverReceiver UNADMIN, reset.sh).
 */
public class AdminReceiver extends DeviceAdminReceiver {
}
