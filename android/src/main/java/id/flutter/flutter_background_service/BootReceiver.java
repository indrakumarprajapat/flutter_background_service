package id.flutter.flutter_background_service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.core.content.ContextCompat;

import static android.content.Context.MODE_PRIVATE;


public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        boolean autoStart = pref.getBoolean("auto_start_on_boot",true);
        Log.d("BOOT_SERVICE >>> ","autoStart "+ autoStart);
        if(autoStart) {
            if (BackgroundService.isForegroundService(context)){
                ContextCompat.startForegroundService(context, new Intent(context, BackgroundService.class));
                Log.d("BOOT_SERVICE >>> ","ContextCompat.startForegroundService ");

            } else {
                context.startService(new Intent(context, BackgroundService.class));
                Log.d("BOOT_SERVICE >>> "," context.startService");

            }
        }
    }
}
