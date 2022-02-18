package id.flutter.flutter_background_service;

import static android.content.Context.MODE_PRIVATE;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.service.ServiceAware;
import io.flutter.embedding.engine.plugins.service.ServicePluginBinding;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.plugin.common.JSONMethodCodec;

/**
 * FlutterBackgroundServicePlugin
 */
public class FlutterBackgroundServicePlugin extends BroadcastReceiver implements FlutterPlugin, MethodCallHandler, ServiceAware, EventChannel.StreamHandler{
    private static final String TAG = "BackgroundServicePlugin";
    private static final List<FlutterBackgroundServicePlugin> _instances = new ArrayList<>();

    public FlutterBackgroundServicePlugin() {
        _instances.add(this);
    }

    private MethodChannel channel;
    private Context context;
    private BackgroundService service;
    private EventChannel eventChannel;
    private EventChannel.EventSink mqttResponse;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        this.context = flutterPluginBinding.getApplicationContext();
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this.context);
        localBroadcastManager.registerReceiver(this, new IntentFilter("id.flutter/background_service"));

        channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "id.flutter/background_service", JSONMethodCodec.INSTANCE);
        channel.setMethodCallHandler(this);

        eventChannel = new EventChannel(flutterPluginBinding.getBinaryMessenger(), "id.flutter/background_service_bg_event", JSONMethodCodec.INSTANCE);
        eventChannel.setStreamHandler(this);

    }


    private static void configure(Context context, long callbackHandleId, boolean isForeground, boolean autoStartOnBoot) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        pref.edit()
                .putLong("callback_handle", callbackHandleId)
                .putBoolean("is_foreground", isForeground)
                .putBoolean("auto_start_on_boot", autoStartOnBoot)
                .apply();
    }

    private void start() {
        BackgroundService.enqueue(context);
        boolean isForeground = BackgroundService.isForegroundService(context);
        Intent intent = new Intent(context, BackgroundService.class);
        if (isForeground) {
            ContextCompat.startForegroundService(context, intent);
        } else {
            context.startService(intent);
        }
    }


    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        String method = call.method;
        JSONObject arg = (JSONObject) call.arguments;

        try {
            if ("configure".equals(method)) {
                long callbackHandle = arg.getLong("handle");
                boolean isForeground = arg.getBoolean("is_foreground_mode");
                boolean autoStartOnBoot = arg.getBoolean("auto_start_on_boot");

                configure(context, callbackHandle, isForeground, autoStartOnBoot);
                if (autoStartOnBoot) {
                    start();
                }

                result.success(true);
                return;
            }

            if ("start".equals(method)) {
                start();
                result.success(true);
                return;
            }

            if (method.equalsIgnoreCase("mqttSendData")) {
                for (FlutterBackgroundServicePlugin plugin : _instances) {
                    if (plugin.service != null) {
                        plugin.service.receiveData((JSONObject) call.arguments);
                        break;
                    }
                }

                result.success(true);
                return;
            }

            if (method.equalsIgnoreCase("sendData")) {
                for (FlutterBackgroundServicePlugin plugin : _instances) {
                    if (plugin.service != null) {
                        plugin.service.receiveData((JSONObject) call.arguments);
                        break;
                    }
                }

                result.success(true);
                return;
            }

            if (method.equalsIgnoreCase("isServiceRunning")) {
                ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                    if (BackgroundService.class.getName().equals(service.service.getClassName())) {
                        result.success(true);
                        return;
                    }
                }
                result.success(false);
                return;
            }

            result.notImplemented();
        } catch (Exception e) {
            result.error("100", "Failed read arguments", null);
        }
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
        eventChannel.setStreamHandler(null);
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this.context);
        localBroadcastManager.unregisterReceiver(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() == null) return;

        if (intent.getAction().equalsIgnoreCase("id.flutter/background_service")) {
            String data = intent.getStringExtra("data");
            try {
                JSONObject jData = new JSONObject(data);
                if (channel != null) {
                    channel.invokeMethod("onReceiveData", jData);
                }

                if ("mqttResponse".equals(jData.getString("mqdata"))) {
                    if (eventChannel != null && mqttResponse != null) {
                        mqttResponse.success(jData);
                    }
                }

            } catch (JSONException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }


        }
    }

    @Override
    public void onAttachedToService(@NonNull ServicePluginBinding binding) {
        Log.d(TAG, "onAttachedToService");
        this.service = (BackgroundService) binding.getService();
    }

    @Override
    public void onDetachedFromService() {
        this.service = null;
        Log.d(TAG, "onDetachedFromService");
    }



    @Override
    public void onListen(Object arguments, EventChannel.EventSink events) {
        mqttResponse = events;
    }

    @Override
    public void onCancel(Object arguments) {

    }
}
