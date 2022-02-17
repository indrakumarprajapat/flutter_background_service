package id.flutter.flutter_background_service;

import static android.os.Build.VERSION.SDK_INT;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.AlarmManagerCompat;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;


import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.UnsatisfiedLinkError;

import io.flutter.FlutterInjector;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.dart.DartExecutor;
import io.flutter.plugin.common.JSONMethodCodec;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.view.FlutterCallbackInformation;

public class BackgroundService extends Service implements MethodChannel.MethodCallHandler {
    private static final String TAG = "BackgroundService";
    private FlutterEngine backgroundEngine;
    private MethodChannel methodChannel;
    private DartExecutor.DartCallback dartCallback;
    private boolean isManuallyStopped = false;
    Mqtt3AsyncClient hive_client;


    String notificationTitle = "Background Service";
    String notificationContent = "Running";
    private static final String LOCK_NAME = BackgroundService.class.getName() + ".Lock";
    private static volatile WakeLock lockStatic = null; // notice static

    synchronized private static PowerManager.WakeLock getLock(Context context) {
        if (lockStatic == null) {
            PowerManager mgr = (PowerManager) context
                    .getSystemService(Context.POWER_SERVICE);
            lockStatic = mgr.newWakeLock(PowerManager.FULL_WAKE_LOCK,
                    LOCK_NAME);
            lockStatic.setReferenceCounted(true);
        }
        return (lockStatic);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void enqueue(Context context) {
        Intent intent = new Intent(context, WatchdogReceiver.class);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent pIntent = PendingIntent.getBroadcast(context, 111, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            AlarmManagerCompat.setAndAllowWhileIdle(manager, AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 5000, pIntent);
            return;
        }

        PendingIntent pIntent = PendingIntent.getBroadcast(context, 111, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManagerCompat.setAndAllowWhileIdle(manager, AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 5000, pIntent);
    }

    public void setAutoStartOnBootMode(boolean value) {
        SharedPreferences pref = getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        pref.edit().putBoolean("auto_start_on_boot", value).apply();
    }

    public static boolean isAutoStartOnBootMode(Context context) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        return pref.getBoolean("auto_start_on_boot", true);
    }

    public void setForegroundServiceMode(boolean value) {
        SharedPreferences pref = getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        pref.edit().putBoolean("is_foreground", value).apply();
    }

    public static boolean isForegroundService(Context context) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        return pref.getBoolean("is_foreground", true);
    }

    public void setManuallyStopped(boolean value) {
        SharedPreferences pref = getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        pref.edit().putBoolean("is_manually_stopped", value).apply();
    }

    public static boolean isManuallyStopped(Context context) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        return pref.getBoolean("is_manually_stopped", false);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        notificationContent = "Preparing";
        updateNotificationInfo();
//        monitorNetwork();
    }

    @Override
    public void onDestroy() {
        if (!isManuallyStopped) {
            enqueue(this);
        } else {
            setManuallyStopped(true);
        }
        stopForeground(true);
        isRunning.set(false);

        if (backgroundEngine != null) {
            backgroundEngine.getServiceControlSurface().detachFromService();
            backgroundEngine.destroy();
            backgroundEngine = null;
        }

        methodChannel = null;
        dartCallback = null;
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Background Service";
            String description = "Executing process in background";

            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel("FOREGROUND_DEFAULT", name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    protected void updateNotificationInfo() {
        if (isForegroundService(this)) {

            String packageName = getApplicationContext().getPackageName();
            Intent i = getPackageManager().getLaunchIntentForPackage(packageName);

            PendingIntent pi;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                pi = PendingIntent.getActivity(BackgroundService.this, 99778, i, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE);
            } else {
                pi = PendingIntent.getActivity(BackgroundService.this, 99778, i, PendingIntent.FLAG_CANCEL_CURRENT);
            }

            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, "FOREGROUND_DEFAULT")
                    .setSmallIcon(R.drawable.ic_bg_service_small)
                    .setAutoCancel(true)
                    .setOngoing(true)
                    .setContentTitle(notificationTitle)
                    .setContentText(notificationContent)
                    .setContentIntent(pi);

            startForeground(99778, mBuilder.build());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        setManuallyStopped(false);
        enqueue(this);
        runService();
        initializeConnection();

        getLock(getApplicationContext()).acquire();


        return START_STICKY;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void monitorNetwork() {
        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build();

        ConnectivityManager connectivityManager = (ConnectivityManager) this.getSystemService(this.CONNECTIVITY_SERVICE);
        connectivityManager.requestNetwork(networkRequest, networkCallback);
    }


    AtomicBoolean isRunning = new AtomicBoolean(false);

    private void runService() {
        try {

            Log.d(TAG, "runService");
            if (isRunning.get() || (backgroundEngine != null && !backgroundEngine.getDartExecutor().isExecutingDart()))
                return;
            updateNotificationInfo();

            SharedPreferences pref = getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
            long callbackHandle = pref.getLong("callback_handle", 0);

            FlutterInjector.instance().flutterLoader().ensureInitializationComplete(getApplicationContext(), null);
            FlutterCallbackInformation callback = FlutterCallbackInformation.lookupCallbackInformation(callbackHandle);
            if (callback == null) {
                Log.e(TAG, "callback handle not found");
                return;
            }

            isRunning.set(true);

            backgroundEngine = new FlutterEngine(this);
            backgroundEngine.getServiceControlSurface().attachToService(BackgroundService.this, null, isForegroundService(this));

            methodChannel = new MethodChannel(backgroundEngine.getDartExecutor().getBinaryMessenger(), "id.flutter/background_service_bg",
                    JSONMethodCodec.INSTANCE);
            methodChannel.setMethodCallHandler(this);

            dartCallback = new DartExecutor.DartCallback(getAssets(), FlutterInjector.instance().flutterLoader().findAppBundlePath(), callback);
            backgroundEngine.getDartExecutor().executeDartCallback(dartCallback);


        } catch (UnsatisfiedLinkError e) {
            notificationContent = "Error " + e.getMessage();
            updateNotificationInfo();

            Log.w(TAG, "UnsatisfiedLinkError: After a reboot this may happen for a short period and it is ok to ignore then!" + e.getMessage());
        }
    }

    Timer connectTimer = null;

    private void initializeConnection() {
        if (hive_client == null) {
            hive_client = Mqtt3Client.builder()
                    .identifier(UUID.randomUUID().toString())
                    .serverHost("blithesome-waiter.cloudmqtt.com")
                    .serverPort(1883)
                    .addConnectedListener(context -> {
                        Log.d(">>> Connected ", context.toString());
                        if (connectTimer != null) {
                            connectTimer.cancel();
                            connectTimer = null;
                        }
                        this.subscribeTopic("testb");
                        handleSubscriptionResponse();
                    }).addDisconnectedListener(context -> {
                        Log.d(">>> Disconnected ", context.toString());
                        int PERIOD = 10;
                        if (connectTimer == null) {
                            connectTimer = new Timer();
                            connectTimer.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    connectMqtt();
                                }
                            }, PERIOD * 1000, PERIOD * 1000);
                        }
                    })
                    .buildAsync();
            connectMqtt();
        }
    }

    private void connectMqtt() {
        if (hive_client != null && !hive_client.getState().isConnected()) {
            hive_client.toAsync().connectWith()
                    .simpleAuth()
                    .username("boomdriver")
                    .password("boom@123456".getBytes())
                    .applySimpleAuth()
                    .keepAlive(10)
                    .send();
        }
    }

    private void publishMessageHive(String msg) {
        publishMessage("a/test", msg);
        this.subscribeTopic("testa");
    }

    void handleSubscriptionResponse() {
        hive_client.publishes(MqttGlobalPublishFilter.ALL,
                mqtt3Publish -> {
                    Log.d(">>> G >>> ", mqtt3Publish.toString());
                    if (methodChannel != null) {
                        try {
                            String topic = mqtt3Publish.getTopic().toString();
                            String payload = new String(mqtt3Publish.getPayloadAsBytes());
                            JSONObject mqData = new JSONObject();
                            mqData.put("topic",topic);
                            mqData.put("payload",payload);
                            LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
                            Intent intent = new Intent("id.flutter/background_service");
                            intent.putExtra("data", mqData.toString());
                            manager.sendBroadcast(intent);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
    }

    void unSubscribeTopic(String topicName) {
        hive_client.unsubscribeWith().
                topicFilter(topicName).send();
    }

    void subscribeTopic(String topicName) {
        hive_client.
                subscribeWith().
                topicFilter(topicName).
                qos(MqttQos.EXACTLY_ONCE).send();
    }

    void publishMessage(String topicName, String message) {
        hive_client.toAsync().publishWith()
                .topic(topicName)
                .payload(message.getBytes())
                .qos(MqttQos.EXACTLY_ONCE)
                .send();
    }


    public void receiveData(JSONObject data) {
        if (methodChannel != null) {
            try {
                methodChannel.invokeMethod("onReceiveData", data);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
        String method = call.method;

        try {
            if (method.equalsIgnoreCase("setNotificationInfo")) {

                JSONObject arg = (JSONObject) call.arguments;
                if (arg.has("title")) {
                    notificationTitle = arg.getString("title");
                    notificationContent = arg.getString("content");
                    updateNotificationInfo();
                    result.success(true);
                    return;
                }
            }

            if (method.equalsIgnoreCase("setAutoStartOnBootMode")) {
                JSONObject arg = (JSONObject) call.arguments;
                boolean value = arg.getBoolean("value");
                setAutoStartOnBootMode(value);
                result.success(true);
                return;
            }

            if (method.equalsIgnoreCase("setForegroundMode")) {
                JSONObject arg = (JSONObject) call.arguments;
                boolean value = arg.getBoolean("value");
                setForegroundServiceMode(value);
                if (value) {
                    updateNotificationInfo();
                } else {
                    stopForeground(true);
                }

                result.success(true);
                return;
            }

            if (method.equalsIgnoreCase("stopService")) {
                isManuallyStopped = true;
                Intent intent = new Intent(this, WatchdogReceiver.class);
                PendingIntent pi;
                if (SDK_INT >= Build.VERSION_CODES.S) {
                    pi = PendingIntent.getBroadcast(getApplicationContext(), 111, intent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE);
                } else {
                    pi = PendingIntent.getBroadcast(getApplicationContext(), 111, intent, PendingIntent.FLAG_CANCEL_CURRENT);
                }

                AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
                alarmManager.cancel(pi);
                stopSelf();
                result.success(true);
                return;
            }

            if (method.equalsIgnoreCase("sendData")) {

                LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
                Intent intent = new Intent("id.flutter/background_service");
                intent.putExtra("data", ((JSONObject) call.arguments).toString());
                manager.sendBroadcast(intent);
                result.success(true);
                return;

            }
            if (method.equalsIgnoreCase("onMqttReceiveData")) {

                LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
                Intent intent = new Intent("id.flutter/background_service");
                intent.putExtra("data", ((JSONObject) call.arguments).toString());
                manager.sendBroadcast(intent);
                result.success(true);
                return;

            }

            if (method.equalsIgnoreCase("mqttSendData")) {
                publishMessageHive(((JSONObject) call.arguments).toString());
                return;
            }

            if (method.equalsIgnoreCase("subscribeTopic")) {
                String arg = (String) call.arguments;
                hive_client.subscribeWith().
                        topicFilter(arg).
                        qos(MqttQos.EXACTLY_ONCE).send();
                return;
            }
            if (method.equalsIgnoreCase("unSubscribeTopic")) {
                String topicName = (String) call.arguments;
                hive_client.unsubscribeWith().topicFilter(topicName).send();
                return;
            }

        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
        }

        result.notImplemented();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            super.onAvailable(network);
            connectMqtt();
            Log.d(">>>> conection >>>", "onAvailable(network)");
        }

        @Override
        public void onLost(@NonNull Network network) {
            super.onLost(network);
            Log.d(">>>> conection >>>", ".onLost(network)");

        }

        @Override
        public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities);
            boolean hasCellular = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
            boolean hasWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        }
    };

}
