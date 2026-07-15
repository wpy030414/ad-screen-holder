package im.xrl.ad_screen_holder;

import android.content.Context;
import android.content.Intent;

/**
 * 设备管理器接收器：用于锁屏熄屏。
 * 需在系统设置中手动激活。
 */
public class DeviceAdminReceiver extends android.app.admin.DeviceAdminReceiver {

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return super.onDisableRequested(context, intent);
    }
}
