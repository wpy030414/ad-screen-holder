package im.xrl.ad_screen_holder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

/**
 * 开机自启：启动主界面和定时服务。
 */
public class BootReceiver extends BroadcastReceiver {

    private static final long AUTO_POWER_SERVICE_DELAY_MS = 2000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        Intent activityIntent = new Intent(context, MainActivity.class);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(activityIntent);

        final Context appContext = context.getApplicationContext();
        new Handler().postDelayed(new DelayedServiceStarter(appContext), AUTO_POWER_SERVICE_DELAY_MS);
    }

    private static class DelayedServiceStarter implements Runnable {
        private final Context mContext;

        DelayedServiceStarter(Context context) {
            this.mContext = context;
        }

        @Override
        public void run() {
            mContext.startService(new Intent(mContext, AutoPowerService.class));
        }
    }
}
