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
import io.flutter.plugin.common.JSONMethodCodec;

/**
 * FlutterBackgroundServicePlugin
 */
public class FlutterBackgroundServicePlugin extends BroadcastReceiver implements FlutterPlugin, MethodCallHandler, ServiceAware, EventChannel.StreamHandler {
    static final String TAG = "BACKGROUNDSERVICEPLUGIN";
    static final List<FlutterBackgroundServicePlugin> _instances = new ArrayList<>();

    public FlutterBackgroundServicePlugin() {
        _instances.add(this);
    }

    private MethodChannel channel;
    private Context context;
    private BackgroundService service;
    private EventChannel eventChannel;
    private EventChannel.EventSink mqttResponseEventSink;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        Log.d(TAG, "onAttachedToEngine() is called");

        this.context = flutterPluginBinding.getApplicationContext();
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this.context);
        localBroadcastManager.registerReceiver(this, new IntentFilter("id.flutter/background_service"));

        channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "id.flutter/background_service", JSONMethodCodec.INSTANCE);
        channel.setMethodCallHandler(this);

        eventChannel = new EventChannel(flutterPluginBinding.getBinaryMessenger(), "id.flutter/background_service_bg_event", JSONMethodCodec.INSTANCE);
        eventChannel.setStreamHandler(this);

    }

    private static void configure(Context context, long callbackHandleId, boolean isForeground, boolean isServiceStart, boolean autoStartOnBoot,
                                  String mqServerHost, int mqPort, String mqUsername, String mqPassword, String mqClientId,
                                  long setInterval, long setFastestInterval, int setPriority, String driverId, String apiToken) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        pref.edit()
                .putLong("callback_handle", callbackHandleId)
                .putBoolean("is_foreground", isForeground)
                .putBoolean("is_service_start", isServiceStart)
                .putBoolean("auto_start_on_boot", autoStartOnBoot)
                .putString("mq_server_host", mqServerHost)
                .putInt("mq_port", mqPort)
                .putString("mq_username", mqUsername)
                .putString("mq_password", mqPassword)
                .putString("mq_client_id", mqClientId)
                .putLong("loc_interval", setInterval)
                .putLong("loc_fastestInterval", setFastestInterval)
                .putInt("loc_priority", setPriority)
                .putString("driver_id", driverId)
                .putString("api_token", apiToken)
                .apply();
        Log.d(TAG, "configure() is called from flutter bg plugin");

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
                boolean isServiceStart = arg.getBoolean("is_service_start");
                boolean autoStartOnBoot = arg.getBoolean("auto_start_on_boot");

                String serverHost = arg.getString("mq_server_host");
                String clientId = arg.getString("mq_client_id");
                int serverPort = arg.getInt("mq_port");
                String username = arg.getString("mq_username");
                String password = arg.getString("mq_password");
                long setInterval = arg.getLong("loc_interval");
                long setFastestInterval = arg.getLong("loc_fastestInterval");
                int setPriority = arg.getInt("loc_priority");
                String driverId = arg.getString("driver_id");
                String apiToken = arg.getString("api_token");

                configure(context, callbackHandle, isForeground, isServiceStart, autoStartOnBoot, serverHost, serverPort,
                        username, password, clientId, setInterval, setFastestInterval, setPriority,
                        driverId, apiToken);

                if (autoStartOnBoot && isServiceStart) {
                    start();
                    Log.d(TAG, "onMethodCall >>> configure is called with >>> autoStartOnBoot && isServiceStart >>>");

                }

                result.success(true);
                return;
            }

            if ("start".equalsIgnoreCase(method)) {
                start();
                result.success(true);
                Log.d(TAG, "onMethodCall >>> start is called");

                for (FlutterBackgroundServicePlugin plugin : _instances) {
                    if (plugin.service != null) {
                        plugin.service.receiveData((JSONObject) call.arguments);
                        Log.d(TAG, "onMethodCall >>> start is called with plugin.service not null ");
                        break;
                    }
                }
                return;
            }

            if (method.equalsIgnoreCase("mqttSendData")) {
                for (FlutterBackgroundServicePlugin plugin : _instances) {
                    if (plugin.service != null) {
                        plugin.service.receiveData((JSONObject) call.arguments);
                        Log.d(TAG, "onMethodCall >>> mqttSendData is called with plugin.service not null ");

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
                        Log.d(TAG, "onMethodCall >>> sendData is called with plugin.service not null ");

                        break;
                    }
                }
                result.success(true);
                return;
            }
            if (method.equalsIgnoreCase("mqPublishMessage")) {
                for (FlutterBackgroundServicePlugin plugin : _instances) {
                    if (plugin.service != null) {
                        Log.d(TAG, "onMethodCall >>> sendData is called with plugin.service not null ");

                        plugin.service.receiveData((JSONObject) call.arguments);
                        break;
                    }
                }
                result.success(true);
                return;
            }
            if (method.equalsIgnoreCase("getCurrentLocation")) {
                for (FlutterBackgroundServicePlugin plugin : _instances) {
                    if (plugin.service != null) {
                        Log.d(TAG, "onMethodCall >>> get Current location is called");

                        plugin.service.receiveData((JSONObject) call.arguments);
                        break;
                    }
                }
                result.success(true);
                return;
            }
            if (method.equalsIgnoreCase("mqSubscribeTopic")) {
                for (FlutterBackgroundServicePlugin plugin : _instances) {
                    if (plugin.service != null) {
                        Log.d(TAG, "onMethodCall >>> mqSubscribeTopic is called with plugin.service not null ");

                        plugin.service.receiveData((JSONObject) call.arguments);
                        break;
                    }
                }
                result.success(true);
                return;
            }
            if (method.equalsIgnoreCase("mqUnSubscribeTopic")) {
                for (FlutterBackgroundServicePlugin plugin : _instances) {
                    if (plugin.service != null) {
                        Log.d(TAG, "onMethodCall >>> mqUnSubscribeTopic is called with plugin.service not null ");

                        plugin.service.receiveData((JSONObject) call.arguments);
                        break;
                    }
                }
                result.success(true);
                return;
            }
            if (method.equalsIgnoreCase("stopBookingSound")) {
                for (FlutterBackgroundServicePlugin plugin : _instances) {
                    if (plugin.service != null) {
                        Log.d(TAG, "onMethodCall >>> stopBookingSound >>>");
                        plugin.service.receiveData((JSONObject) call.arguments);
                        break;
                    }
                }
                result.success(true);
                return;
            }
            if (method.equalsIgnoreCase("acceptBooking")) {
                for (FlutterBackgroundServicePlugin plugin : _instances) {
                    if (plugin.service != null) {
                        Log.d(TAG, "onMethodCall >>> acceptBooking >>>");
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
                    Log.d(">>> onReceiveData >>", "" + jData);

                }
//                try {
//                    if (jData.has("mqdata") && "mqttResponse".equals(jData.getString("mqdata"))) {
//                        if (eventChannel != null && mqttResponseEventSink != null) {
//                            mqttResponseEventSink.success(jData);
//                            Log.d(">>> mqttResponse", " mqttResponseEventSink.success(jData);");
//                        }
//                    }
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//                try {
//                    if (jData.has("onMqConnected") && "connected".equals(jData.getString("onMqConnected"))) {
//                        if (eventChannel != null && mqttResponseEventSink != null) {
//                            mqttResponseEventSink.success(jData);
//                        }
//                    }
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }

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
        mqttResponseEventSink = events;
    }

    @Override
    public void onCancel(Object arguments) {

    }
}
