package id.flutter.flutter_background_service;

import static android.os.Build.VERSION.SDK_INT;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.AlarmManagerCompat;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;


import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.CancellationToken;
import com.google.android.gms.tasks.OnTokenCanceledListener;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.UnsatisfiedLinkError;

import io.flutter.FlutterInjector;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.dart.DartExecutor;
import io.flutter.plugin.common.JSONMethodCodec;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.view.FlutterCallbackInformation;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

enum SessionType {
    None,
    PENDING_TRIP, // 1
    CANCELLED_BY_CUSTOMER, // 2
    BOOKING_ACCEPTED_BY_DRIVER, // 3
    BOOKING_PASSED_BY_DRIVER, // 4
    TRIP_STARTED_BY_DRIVER, // 5
    TRIP_COMPLETED_BY_DRIVER, // 6
    TRIP_CANCELLED_IN_BETWEEN_BY_CUSTOMER, // 7
    TRIP_PAYMENT_RECEIVED, // 8
    TRIP_CANCELLED_BY_CUSTOMER_REQUESTED_BY_DRIVER, // 9
    PENDING_CONFIRMATION, // 10
    SCHEDULED_FOR_DRIVER_ASSIGNMENT, // 11
    WAITING_FOR_DRIVER_RESPONSE // 12
}

//D_ID | D_CLIENT_ID | D_NAME | D_CURRENT_LOC_LAT,D_CURRENT_LOC_LNG | D_VEH_ID | D_VEH_TYPE | D_VEH_MODEL | D_VEH_RC_NO | reachingTime | bearing

public class BackgroundService extends Service implements MethodChannel.MethodCallHandler {
    private static final String TAG = "BackgroundService";
    private FlutterEngine backgroundEngine;
    private MethodChannel methodChannel;
    private DartExecutor.DartCallback dartCallback;
    private boolean isManuallyStopped = false;
    Mqtt3AsyncClient hive_client;
    private HashSet<String> topicList;

    String notificationTitle = "Background Service";
    String notificationContent = "Running";
    MediaPlayer finalMediaPlayer;

    private static final String LOCK_NAME = BackgroundService.class.getName() + ".Lock";
    private static volatile WakeLock lockStatic = null; // notice static
    private LocationRequest locationRequest;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;
    private Location currentLocation;
    private PowerManager.WakeLock wakeLock;
    public static String rideReferenceNo;
    public static String messageOnNewTrip;
    public static String tripType;
    public static String tpType;
    public static String customerId = "0";

    {
        topicList = new HashSet<String>();
    }

    synchronized private static PowerManager.WakeLock getLock(Context context) {
        if (lockStatic == null) {
            PowerManager mgr = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            lockStatic = mgr.newWakeLock(PowerManager.FULL_WAKE_LOCK, LOCK_NAME);
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

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        notificationContent = "Preparing";
        updateNotificationInfo();

        Log.d(">>> BGS onCreate()", "onCreate() is called");

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);
                onNewLocation(locationResult.getLastLocation());
            }
        };

        createLocationRequest();
        getLastLocation();

        startTracking();
    }

    // Location tracking >>>>>
    private void createLocationRequest() {
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(getInterval(this));
        locationRequest.setFastestInterval(getFastestInterval(this));
        locationRequest.setPriority(getLocationPriority(this));
    }

    private void getLastLocation() {
        try {
            fusedLocationProviderClient.getLastLocation().addOnSuccessListener(lastLocation -> {
                if (lastLocation != null) {
                    currentLocation = lastLocation;
                } else {
                    Log.d(TAG, "Failed to get location.");
                }
            });

        } catch (SecurityException securityException) {
            Log.d(TAG, "Lost location permission.$unlikely");
        }
    }

    private void onNewLocation(Location location) {
        Log.d(TAG, "New location:  onNewLocation");
        this.currentLocation = location;
        updateNotificationInfo(location.toString());
        JSONObject mqData = new JSONObject();

        try {
            mqData.put("responseData", "LocationData");
            String locDetail = location.getLatitude() + "|" +
                    location.getLongitude() + "|" +
                    location.getAltitude() + "|" +
                    location.getAccuracy() + "|" +
                    location.getBearing() + "|" +
                    location.getSpeed() + "|" +
                    location.getProvider();
            mqData.put("LocationValue", locDetail);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (methodChannel != null) {
            try {
                localBroadcastManager(mqData, ">>>BGS onNewLocation", "Send new location to flutter");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        try {
            /*Create handle for the RetrofitInstance interface*/
            String token = getApiTokenValue(this);
            if (token != null && token.length() > 10) {
                ApiEndpoints apiEndpoints = RetrofitClientInstance.getRetrofitInstance(token).create(ApiEndpoints.class);
                Call<DriverLocation> call = apiEndpoints.updateDriverLocation(new DriverLocation(),
                        String.valueOf(location.getLatitude()), String.valueOf(location.getLongitude()),
                        getDriverId(this));
                call.enqueue(new Callback<DriverLocation>() {
                    @Override
                    public void onResponse(Call<DriverLocation> call, Response<DriverLocation> response) {
                        Log.d(">>> ApiCall-Success >", response.toString());
                    }

                    @Override
                    public void onFailure(Call<DriverLocation> call, Throwable t) {
                        Log.d(">>> ApiCall-Failed > ", t.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            String appState = getAppStateValue(this);
            String locUpdatePayload = getLocUpdatePayload(this);
            locUpdatePayload = locUpdatePayload.replaceAll("D_CURRENT_LOC",
                    String.valueOf(location.getLatitude()) + ',' + String.valueOf(location.getLongitude()));
            locUpdatePayload = locUpdatePayload.replaceAll("LOC_BEARING", String.valueOf(location.getBearing()));
            if (appState == "3" || appState == "5") {
                String locUpdateTopicOnride = getLocUpdateTopicOnRide(this);
                if (!locUpdateTopicOnride.isEmpty()) {
                    publishMessage(locUpdateTopicOnride, locUpdatePayload);
                }
            } else {
                String locUpdateTopicOnline = getLocUpdateTopicOnline(this);
                if (!locUpdateTopicOnline.isEmpty()) {
                    publishMessage(locUpdateTopicOnline, locUpdatePayload);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startTracking() {
//        lockStatic.acquire(24 * 60 * 60 * 1000L /*24 hours max */);
        Log.d(TAG, "Requesting location updates");
        try {
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());
        } catch (SecurityException securityException) {
//            if (lockStatic.isHeld() == true) {
//                lockStatic.release();
//            }
            Log.d(TAG, "Lost location permission. Could not request updates");
        }
    }

    private void getCurrentLocation() {
        Log.d(TAG, ">>> Requesting current location");
        try {
            fusedLocationProviderClient.getCurrentLocation(100, new CancellationToken() {
                @NonNull
                @Override
                public CancellationToken onCanceledRequested(@NonNull OnTokenCanceledListener onTokenCanceledListener) {
                    return null;
                }

                @Override
                public boolean isCancellationRequested() {
                    return false;
                }
            }).addOnSuccessListener(location -> {
                if (location != null) {
                    this.currentLocation = location;

                    JSONObject currentLocation = new JSONObject();
                    try {
                        currentLocation.put("responseData", "CurrentLocationData");
                        currentLocation.put("CurrentLocation", location.toString());
                        localBroadcastManager(currentLocation, ">>> BGS CurrentLoc ", "broadcast getCurrentLocation");


                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                }

            });

        } catch (SecurityException securityException) {
            Log.d(TAG, "Lost location permission. Could not request updates");
        }

    }

    private void stopTracking() {
//        if (lockStatic.isHeld() == true) {
//            lockStatic.release();
//        }
        Log.d(TAG, "Removing location updates");
        try {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        } catch (SecurityException unlikely) {
            Log.d(TAG, "Lost location permission. Could not remove updates. $unlikely");
        }
    }

    // Location >>>>>

    private void localBroadcastManager(JSONObject broadcastData, String tag, String logString) {
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        Intent intent = new Intent("id.flutter/background_service");
        intent.putExtra("data", broadcastData.toString());
        manager.sendBroadcast(intent);
        Log.d(tag, logString);
    }

    @Override
    public void onDestroy() {
        Log.d(">>> BGS onDestroy()", "onDestroy() is called || isManuallyStopped value =" + isManuallyStopped);

        if (!isManuallyStopped) {
            enqueue(this);
        } else {
            setManuallyStopped(true);
            Log.d(">>> BGS onDestroy()", "onDestroy() is called || setManuallyStopped = true");

        }
        stopForeground(true);
        isRunning.set(false);

        hive_client.disconnect();

        if (backgroundEngine != null) {
            backgroundEngine.getServiceControlSurface().detachFromService();
            backgroundEngine.destroy();
            backgroundEngine = null;
        }

        methodChannel = null;
        dartCallback = null;
        super.onDestroy();

        Log.d(">>> BGS onDestroy()", "onDestroy() is called");
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
        String packageName = getApplicationContext().getPackageName();
        Intent i = getPackageManager().getLaunchIntentForPackage(packageName);

        PendingIntent pi;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pi = PendingIntent.getActivity(BackgroundService.this, 99778, i, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE);
        } else {
            pi = PendingIntent.getActivity(BackgroundService.this, 99778, i, PendingIntent.FLAG_CANCEL_CURRENT);
        }

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, "FOREGROUND_DEFAULT")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setAutoCancel(true)
                .setOngoing(true)
                .setContentTitle(notificationTitle)
                .setContentText("Boomcabs")
                .setContentIntent(pi);

        startForeground(99778, mBuilder.build());

    }

    protected void updateNotificationInfo(String location) {
        String packageName = getApplicationContext().getPackageName();
        Intent i = getPackageManager().getLaunchIntentForPackage(packageName);

        PendingIntent pi;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pi = PendingIntent.getActivity(BackgroundService.this, 99778, i, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE);
        } else {
            pi = PendingIntent.getActivity(BackgroundService.this, 99778, i, PendingIntent.FLAG_CANCEL_CURRENT);
        }

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, "FOREGROUND_DEFAULT")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setAutoCancel(true)
                .setOngoing(true)
                .setContentTitle(notificationTitle)
                .setContentText(location.toString())
                .setContentIntent(pi);

        startForeground(99778, mBuilder.build());

    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(">>> BGS onStartCommand", "onStartCommand() is called");

        setManuallyStopped(false);
        enqueue(this);
        runService();
        initializeConnection();

        getLock(getApplicationContext()).acquire();

//      monitorNetwork();

        return START_STICKY;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void monitorNetwork() {
        try {
            NetworkRequest networkRequest = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .build();
            ConnectivityManager connectivityManager = (ConnectivityManager) this.getSystemService(this.CONNECTIVITY_SERVICE);
            connectivityManager.requestNetwork(networkRequest, networkCallback);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    AtomicBoolean isRunning = new AtomicBoolean(false);

    private void runService() {
        try {

            Log.d(">>> BGS runService ", "runService is called");
            if (isRunning.get() || (backgroundEngine != null && !backgroundEngine.getDartExecutor().isExecutingDart()))
                return;

            SharedPreferences pref = getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
            long callbackHandle = pref.getLong("callback_handle", 0);

            FlutterInjector.instance().flutterLoader().ensureInitializationComplete(getApplicationContext(), null);
            FlutterCallbackInformation callback = FlutterCallbackInformation.lookupCallbackInformation(callbackHandle);
            if (callback == null) {
                Log.e("BGS >>> callback ", "callback handle not found");
                return;
            }

            isRunning.set(true);

            backgroundEngine = new FlutterEngine(this);
            backgroundEngine.getServiceControlSurface().attachToService(BackgroundService.this, null, isForegroundService(this));

            methodChannel = new MethodChannel(backgroundEngine.getDartExecutor().getBinaryMessenger(), "id.flutter/background_service_bg", JSONMethodCodec.INSTANCE);
            methodChannel.setMethodCallHandler(this);

            dartCallback = new DartExecutor.DartCallback(getAssets(), FlutterInjector.instance().flutterLoader().findAppBundlePath(), callback);
            backgroundEngine.getDartExecutor().executeDartCallback(dartCallback);

            updateNotificationInfo();

        } catch (UnsatisfiedLinkError e) {
            notificationContent = "Error " + e.getMessage();
            updateNotificationInfo();

            Log.w("BGS >>> ERROR", "UnsatisfiedLinkError: After a reboot this may happen for a short period and it is ok to ignore then!" + e.getMessage());
        }
    }

    Timer connectTimer = null;

    private void initializeConnection() {
        if (hive_client == null) {
            String serverHost = BackgroundService.getMqServerHost(this);
            int serverPort = BackgroundService.getMqPort(this);
            String clientId = BackgroundService.getMqClientId(this);
            hive_client = Mqtt3Client.builder()
                    .identifier(clientId)
                    .serverHost(serverHost)
                    .serverPort(serverPort)
                    .addConnectedListener(context -> {
                        Log.d(">>> BGS Connected ", context.toString());
                        if (connectTimer != null) {
                            connectTimer.cancel();
                            connectTimer = null;
                        }

                        onMqttConnected();
                        handleSubscriptionResponse();

                    }).addDisconnectedListener(context -> {
                        Log.d(">>> BGS Disconnected ", context.toString());
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

    private void onMqttConnected() {
        try {
            JSONObject mqData = new JSONObject();
            mqData.put("responseData", "connected");
            if (methodChannel != null) {
                try {

                    localBroadcastManager(mqData, ">>> BGS onMqttConnected", "sent onMqttConnected is connected");

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // Subscribe all last subscribed topics
            subscribeTopicsFromLastSession();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void connectMqtt() {
        if (isRunning.get() && hive_client != null && !hive_client.getState().isConnected()) {
            String username = BackgroundService.getMqUsername(this);
            String password = BackgroundService.getMqPassword(this);
            hive_client.toAsync().connectWith()
                    .simpleAuth()
                    .username(username)
                    .password(password.getBytes())
                    .applySimpleAuth()
                    .keepAlive(10)
                    .send();
            Log.d(">>> BGS connectMqtt", "hive_client.toAsync().connectWith()");

        }
    }

    Timer bookingCounterTimer = null;
    static final int PASS_TIMEOUT = 30;
    static int timerCurrentTick = 30;

    private void startBookingStartProcess(String payload) {
        messageOnNewTrip = payload;
        try {
            String[]  parts = payload.split("#");
            String[] valuesPart1 = parts[1].split("|");
            String[] valuesPart3 = parts[3].split("|");
            // Trip Type coming from Customer Request
            rideReferenceNo = valuesPart3[0];
            tripType = parts[5];
            customerId = valuesPart1[0];
            tpType = valuesPart3[1];
        }catch (Exception e){
            e.printStackTrace();
        }

        if (bookingCounterTimer == null) {
            bookingCounterTimer = new Timer();
            timerCurrentTick = 0;
            bookingCounterTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    timerCurrentTick--;
                    try {
                        JSONObject mqData = new JSONObject();
                        mqData.put("responseData", "bookingTimer");
                        mqData.put("timerTick", timerCurrentTick);
                        localBroadcastManager(mqData, ">>> TickTick > ", " Timer Tick " + timerCurrentTick);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (timerCurrentTick <= 0) {
                        bookingCounterTimer.cancel();
                        bookingCounterTimer = null;
                        // TODO call auto pass
                        autoPassTheBooking();
                    }
                }
            }, 1000, 1000);
        }
    }

    @SuppressLint("NewApi")
    String getPassBookingPayload(String driverId, String messageOnNewTrip, String otp){
        //PART_1 : Customer Detail
        //PART_2 : Driver Detail
        //PART_3 : Trip Details
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        final String formattedDate = formatter.format(new Date());
        if (driverId == null || messageOnNewTrip == null) {
            return "";
        }
        String[] parts = messageOnNewTrip.split("#");
        // Part #2
        List<String> valuesPart3 = new ArrayList<>();
        valuesPart3.add(driverId); //
        valuesPart3.add(driverId); //
        valuesPart3.add(""); // driver username
        valuesPart3.add(currentLocation.getLatitude() + "," + currentLocation.getLongitude()); // D_ID
        valuesPart3.add(""); // Car id
        valuesPart3.add(""); // Car model
        valuesPart3.add(""); // Car no
        valuesPart3.add(formattedDate); // D_ID
        valuesPart3.add(formattedDate); // D_ID
        return "RQAP#" + parts[1] +
                "#" +
                String.join("|",valuesPart3)+
                "#" +
                parts[3] +
                "#0";
    }

    void autoPassTheBooking() {
        try {
            /*Create handle for the RetrofitInstance interface*/
            String token = getApiTokenValue(this);
            String driverId = getDriverId(this);
            if (token != null && token.length() > 10) {
                final RejectRideRequest rideRequest = new RejectRideRequest();
                rideRequest.trip_reference_number_fk = rideReferenceNo;
                rideRequest.driver_id_fk = Integer.parseInt(driverId != "" ? driverId : "0");
                rideRequest.lat = currentLocation.getLatitude();
                rideRequest.lng = currentLocation.getLongitude();
                rideRequest.driver_cancel_reason = "Pass";

                ApiEndpoints apiEndpoints = RetrofitClientInstance.getRetrofitInstance(token).create(ApiEndpoints.class);
                Call<DriverLocation> call = apiEndpoints.rejectRideRequest(rideRequest);
                call.enqueue(new Callback<DriverLocation>() {
                    @Override
                    public void onResponse(Call<DriverLocation> call, Response<DriverLocation> response) {
                        Log.d(">>> ApiCall-Success >", response.toString());
                        // Pass-Cancel Invoice not shown to driver
                        unSubscribeTopic("rd/rq/cl/" + rideReferenceNo);
                        Random rand = new Random();
                        String otp = String.format("%04d", rand.nextInt(10000));
                        messageOnNewTrip = messageOnNewTrip.replaceAll("????", otp);
                        String passpay = getPassBookingPayload(driverId, messageOnNewTrip, otp);

                        if (tpType == "4" || tpType == "5") {
                            // This is for portal
                            publishMessage("rd/rq/ak/ap/"+rideReferenceNo, passpay);
                            publishMessage("rd/rq/ak/"+rideReferenceNo, passpay);
                            try {
                                PassRideRequest passRideRequest = new PassRideRequest();
                                passRideRequest.reference_number = rideReferenceNo;
                                passRideRequest.user_id_fk = Integer.parseInt(customerId != "" ? customerId : "0") ; // customerId
                                passRideRequest.from_latitude =  currentLocation.getLatitude();
                                passRideRequest.from_longitude = currentLocation.getLongitude();
                                passRideRequest.trip_booking_type = "Pass";
                                ApiEndpoints apiEndpoints = RetrofitClientInstance.getRetrofitInstance(token).create(ApiEndpoints.class);
                                Call<DriverLocation> call2 = apiEndpoints.passRideRequest(passRideRequest);
                                call2.enqueue(new Callback<DriverLocation>() {
                                    @Override
                                    public void onResponse(Call<DriverLocation> call, Response<DriverLocation> response) {
                                        Log.d(">>>ApiCall-PassRide>", response.toString());
                                    }
                                    @Override
                                    public void onFailure(Call<DriverLocation> call, Throwable t) {
                                        Log.d(">>> Error-Pass > ", t.getMessage());
                                    }
                                });
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        } else {
                            //sending to customer mobile app
                            publishMessage("rd/rq/ak/"+rideReferenceNo, passpay);
                        }
                    }
                    @Override
                    public void onFailure(Call<DriverLocation> call, Throwable t) {
                        Log.d(">>> ApiCall-Failed > ", t.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void handleSubscriptionResponse() {
        final String ENV_PREFIX = "s";
        hive_client.publishes(MqttGlobalPublishFilter.ALL,
                mqtt3Publish -> {
                    try {
                        Log.d(">>> BGS >>> ", mqtt3Publish.toString());

                        String topic = mqtt3Publish.getTopic().toString();
                        String payload = new String(mqtt3Publish.getPayloadAsBytes());
                        JSONObject mqData = new JSONObject();
                        mqData.put("responseData", "mqttResponse");
                        mqData.put("topic", topic);
                        mqData.put("payload", payload);

                        if (topic.startsWith(ENV_PREFIX + "/rd/rq/cl/")) {
                            showNotification(NotificationType.BOOKING_CANCELLED, topic, payload);
                        } else if (topic.startsWith(ENV_PREFIX + "/rd/rq/")) {
                            showNotification(NotificationType.BOOKING_REQUEST, topic, payload);
                            startBookingStartProcess(payload);
                        } else if (topic.contains(ENV_PREFIX + "/rd/dr/")) {
                            // check in payload its a cancelled booking or not
                            showNotification(NotificationType.BOOKING_CANCELLED, topic, payload);
                        } else if (topic.contains(ENV_PREFIX + "/rd/af/dr/")) {
                            // check in payload its a payment done or not
                            showNotification(NotificationType.PAYMENT_DONE, topic, payload);
                        }

                        if (methodChannel != null) {
                            try {
                                localBroadcastManager(mqData, ">>> BGS >>>  ", "handleSubscriptionResponse broadcast");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                });
    }

    void unSubscribeTopic(String topicName) {
        String top = topicName.replaceAll("=", "/");
        removeTopic(top);
        hive_client.unsubscribeWith().topicFilter(top).send();
    }

    void subscribeTopic(String topicName) {
        Log.d(">>> BGS Topic Plain", "subscribeTopic >>> ( " + topicName + " ) is called");
        String top = topicName.replaceAll("=", "/");
        Log.d(">>> BGS  Topic Replaced", "subscribeTopic >>> ( " + top + " ) is called");

        addTopic(top);
        hive_client.subscribeWith().topicFilter(top).qos(MqttQos.EXACTLY_ONCE).send();
    }

    void publishMessage(String topicName, String message) {
        String top = topicName.replaceAll("=", "/");
        try {
            if (top.startsWith("rd/rq/ak/")) {
                String[] parts = message.split("#");
                if (parts[0] == "RQAA") {
                    //TODO Stop Sound
                    bookingCounterTimer.cancel();
                    bookingCounterTimer = null;
                    if (finalMediaPlayer != null) {
                        finalMediaPlayer.release();
                        finalMediaPlayer = null;
                    }
                } else if (parts[0] == "RQAP") {
                    //TODO Stop Sound
                    bookingCounterTimer.cancel();
                    bookingCounterTimer = null;
                    if (finalMediaPlayer != null) {
                        finalMediaPlayer.release();
                        finalMediaPlayer = null;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        hive_client.toAsync().publishWith()
                .topic(top)
                .payload(message.getBytes())
                .qos(MqttQos.EXACTLY_ONCE)
                .send();
    }

    public void receiveData(JSONObject data) {
        if (methodChannel != null) {
            try {
                String action = data.getString("action");
                if (action.equals("mqPublishMessage")) {
                    String topic = data.getString("topic");
                    String payload = data.getString("payload");
                    publishMessage(topic, payload);
                } else if (action.equals("mqSubscribeTopic")) {
                    String topic = data.getString("topic");
                    subscribeTopic(topic);
                } else if (action.equals("mqUnSubscribeTopic")) {
                    String topic = data.getString("topic");
                    unSubscribeTopic(topic);
                } else if (action.equals("setDriverDetails")) {
                    String driverId = data.getString("driver_id");
                    String apiToken = data.getString("api_token");
                    setDriverId(driverId);
                    setApiTokenValue(apiToken);
                } else if (action.equals("setAppStateValue")) {
                    String appStateValue = data.getString("app_state_value");
                    String locUpdateTopicOnline = data.getString("loc_update_topic_online");
                    String locUpdateTopicOnRide = data.getString("loc_update_topic_on_ride");
                    String locUpdatePayload = data.getString("loc_update_payload");
                    setAppStateValueAndTopics(appStateValue, locUpdateTopicOnline, locUpdateTopicOnRide, locUpdatePayload);
                } else if (action.equals("getCurrentLocation")) {
                    getCurrentLocation();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
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

            if (method.equalsIgnoreCase("setIsServiceStart")) {
                JSONObject arg = (JSONObject) call.arguments;
                boolean value = arg.getBoolean("value");
                setIsServiceStart(value);
                if (value) {
                    // true
                } else {
                    // false
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
                stopTracking();
                stopSelf();
                result.success(true);
                return;
            }

            if (method.equalsIgnoreCase("sendData")) {

                localBroadcastManager(((JSONObject) call.arguments), ">>>BGS sendData",
                        "sendData broadcast form send Data method Channel");
                result.success(true);
                return;
            }

            if (method.equalsIgnoreCase("mqPublishMessage")) {
                JSONObject arg = (JSONObject) call.arguments;
                if (arg.has("topic")) {
                    String topic = arg.getString("topic");
                    String payload = arg.getString("payload");
                    result.success(true);
                    publishMessage(topic, payload);
                }
                return;
            }

            if (method.equalsIgnoreCase("mqSubscribeTopic")) {
                JSONObject arg = (JSONObject) call.arguments;
                if (arg.has("topic")) {
                    String topic = arg.getString("topic");
                    result.success(true);
                    subscribeTopic(topic);
                }
                return;
            }

            if (method.equalsIgnoreCase("mqUnSubscribeTopic")) {
                JSONObject arg = (JSONObject) call.arguments;
                if (arg.has("topic")) {
                    String topic = arg.getString("topic");
                    result.success(true);
                    unSubscribeTopic(topic);
                }
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
            Log.d(">>>> BGS conection >>>", "onAvailable(network)");
        }

        @Override
        public void onLost(@NonNull Network network) {
            super.onLost(network);
            Log.d(">>>> BGS conection >>>", ".onLost(network)");
        }

        @Override
        public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities);
            boolean hasCellular = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
            boolean hasWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        }
    };

    enum NotificationType {
        BOOKING_REQUEST,
        BOOKING_CANCELLED,
        PAYMENT_DONE
    }


    protected void showNotification(NotificationType notificationType, String topic, String payload) {
        if (isForegroundService(this)) {

            String packageName = getApplicationContext().getPackageName();
            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
            intent.putExtra("FOREGROUND_DEFAULT", 0);

            PendingIntent pi;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                pi = PendingIntent.getActivity(BackgroundService.this, 99778, intent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE);
            } else {
                pi = PendingIntent.getActivity(BackgroundService.this, 99778, intent, PendingIntent.FLAG_CANCEL_CURRENT);
            }
            if (notificationType == NotificationType.BOOKING_REQUEST) {

                NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "FOREGROUND_DEFAULT")
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle("New Booking Request")
                        .setContentText(payload)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .addAction(R.drawable.ic_accept, getString(R.string.accept), pi)
                        .addAction(R.drawable.ic_pass, getString(R.string.pass), pi)
                        .setContentIntent(pi)
                        .setAutoCancel(true);

                startForeground(99778, builder.build());

                finalMediaPlayer = MediaPlayer.create(this, R.raw.booking);
                try {
                    if (finalMediaPlayer != null) {//check if it's been already initialized.
                        new Timer().schedule(new TimerTask() {
                            @Override
                            public void run() {
                                finalMediaPlayer.start();
                                finalMediaPlayer.setOnCompletionListener(mp -> {
                                    updateNotificationInfo();
                                    try {
                                        finalMediaPlayer.release();
                                        finalMediaPlayer = null;
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                });
                            }
                        }, 0);
                    }
                } catch (Exception ex) {
                    if (finalMediaPlayer != null) {
                        finalMediaPlayer.release();
                        updateNotificationInfo();
                        finalMediaPlayer = null;
                    }
                }
            } else if (notificationType == NotificationType.BOOKING_CANCELLED) {
                NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "FOREGROUND_DEFAULT")
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle("Booking Cancelled")
                        .setContentText(payload)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .addAction(R.drawable.ic_open_app, getString(R.string.openapp), pi);
                startForeground(99778, builder.build());

            } else if (notificationType == NotificationType.PAYMENT_DONE) {
                NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "FOREGROUND_DEFAULT")
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle("Payment Completed")
                        .setContentText(payload)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .addAction(R.drawable.ic_open_app, getString(R.string.openapp), pi);
                startForeground(99778, builder.build());
            }
        }
    }

    private void addTopic(String topic) {
        topicList = getSharedPreferencesTopicList(this);
        if (!topicList.contains(topic)) {
            topicList.add(topic);
            saveSharedPreferencesTopicList(this, topicList);
        }
    }

    private void removeTopic(String topic) {
        topicList = getSharedPreferencesTopicList(this);
        if (topicList.contains(topic)) {
            topicList.remove(topic);
            saveSharedPreferencesTopicList(this, topicList);
        }
    }

    private void subscribeTopicsFromLastSession() {
        topicList = getSharedPreferencesTopicList(this);
        if (topicList != null && topicList.size() > 0) {
            for (String value : topicList) {
                hive_client.subscribeWith().topicFilter(value).qos(MqttQos.EXACTLY_ONCE).send();
            }
        }
    }

    private HashSet<String> getSharedPreferencesTopicList(Context context) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        HashSet<String> topics = (HashSet<String>) pref.getStringSet("BC_MQ_TOPICS", null);
        if (topics == null) {
            return new HashSet<String>();
        }
        return topics;
    }

    private void saveSharedPreferencesTopicList(Context context, HashSet<String> topicList) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        pref.edit().putStringSet("BC_MQ_TOPICS", topicList).apply();
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

    public void setIsServiceStart(boolean value) {
        SharedPreferences pref = getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        pref.edit().putBoolean("is_service_start", value).apply();
    }

    public static boolean isServiceStart(Context context) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        return pref.getBoolean("is_service_start", true);
    }

    public void setManuallyStopped(boolean value) {
        SharedPreferences pref = getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        pref.edit().putBoolean("is_manually_stopped", value).apply();
    }

    public static boolean isManuallyStopped(Context context) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        return pref.getBoolean("is_manually_stopped", false);
    }

    public void setMqServerCredentials(String host, String username, String password) {
        SharedPreferences pref = getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        pref.edit().putString("mq_server_host", host).apply();
    }

    public void setMqUsername(String host, String username, String password) {
        SharedPreferences pref = getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        pref.edit().putString("mq_username", username).apply();
    }

    public void setMqPassword(String host, String username, String password) {
        SharedPreferences pref = getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        pref.edit().putString("mq_password", password).apply();
    }

    public static String getMqServerHost(Context context) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        return pref.getString("mq_server_host", "");
    }

    public static String getMqClientId(Context context) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        return pref.getString("mq_client_id", "");
    }

    public static int getMqPort(Context context) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        return pref.getInt("mq_port", 1883);
    }

    public static String getMqUsername(Context context) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        return pref.getString("mq_username", "");
    }

    public static String getMqPassword(Context context) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        return pref.getString("mq_password", "");
    }

    public static Long getInterval(Context context) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        return pref.getLong("loc_interval", 0);
    }

    public static Long getFastestInterval(Context context) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        return pref.getLong("loc_fastestInterval", 0);
    }

    public static int getLocationPriority(Context context) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        return pref.getInt("loc_priority", 0);
    }

    public void setDriverId(String driverId) {
        SharedPreferences pref = getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        pref.edit().putString("driver_id", driverId).apply();
    }

    public static String getDriverId(Context context) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        return pref.getString("driver_id", "0");
    }

    public void setAppStateValueAndTopics(String appStateValue, String locUpdateTopicOnline,
                                          String locUpdateTopicOnRide, String locUpdatePayload) {
        SharedPreferences pref = getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        pref.edit().putString("app_state_value", appStateValue).apply();
        pref.edit().putString("loc_update_topic_online", locUpdateTopicOnline).apply();
        pref.edit().putString("loc_update_topic_on_ride", locUpdateTopicOnRide).apply();
        pref.edit().putString("loc_update_payload", locUpdatePayload).apply();
    }

    public static String getAppStateValue(Context context) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        return pref.getString("app_state_value", "0");
    }

    public static String getLocUpdateTopicOnline(Context context) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        return pref.getString("loc_update_topic_online", "");
    }

    public static String getLocUpdateTopicOnRide(Context context) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        return pref.getString("loc_update_topic_on_ride", "");
    }

    public static String getLocUpdatePayload(Context context) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        return pref.getString("loc_update_payload", "");
    }

    public void setApiTokenValue(String token) {
        SharedPreferences pref = getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        pref.edit().putString("api_token", token).apply();
    }

    public static String getApiTokenValue(Context context) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        return pref.getString("api_token", "");
    }


}
