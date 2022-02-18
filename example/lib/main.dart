import 'dart:async';

import 'package:awesome_notifications/awesome_notifications.dart';
import 'package:flutter/material.dart';
import 'package:flutter_background_service/flutter_background_service.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  initializeService();
  // AwesomeNotifications().initialize(
  //     'resource://drawable/ic_launcher',
  //     [
  //       NotificationChannel(
  //           icon: 'resource://drawable/ic_launcher',
  //           channelKey: "accept_pass_key",
  //           channelName: "Booking notifications",
  //           channelDescription: "",
  //           playSound: true,
  //           soundSource: 'resource://raw/booking',
  //           defaultColor: Colors.blueAccent,
  //           ledColor: Colors.red,
  //           vibrationPattern: lowVibrationPattern)
  //     ],
  //     debug: true);

  runApp(MyApp());
}

Future<void> initializeService() async {
  final service = FlutterBackgroundService();

  await service.configure(
    androidConfiguration: AndroidConfiguration(
      // this will executed when app is in foreground or background in separated isolate
      onStart: onStart,
      // auto start service
      autoStart: true,
      isForegroundMode: true,
      mqServerHost: '',
      mqUsername: '',
      mqPassword: '',
      mqServerPort: 1883,
      mqClientId: '',
    ),
    iosConfiguration: IosConfiguration(
      // auto start service
      autoStart: true,
      // this will executed when app is in foreground in separated isolate
      onForeground: onStart,
      // you have to enable background fetch capability on xcode project
      onBackground: onIosBackground,
    ),
  );

  AwesomeNotifications().isNotificationAllowed().then((isAllowed) {
    if (!isAllowed) {
      // This is just a basic example. For real apps, you must show some
      // friendly dialog box before call the request method.
      // This is very important to not harm the user experience
      AwesomeNotifications().requestPermissionToSendNotifications();
    }
  });
}

// to ensure this executed
// run app from xcode, then from xcode menu, select Simulate Background Fetch
void onIosBackground() {
  WidgetsFlutterBinding.ensureInitialized();
  print('FLUTTER BACKGROUND FETCH');
}

void onStart() {
  WidgetsFlutterBinding.ensureInitialized();

  final service = FlutterBackgroundService();

  service.onDataReceived.listen((event) {
    if (event!["action"] == "setAsForeground") {
      service.setForegroundMode(true);
      return;
    }

    if (event["action"] == "setAsBackground") {
      service.setForegroundMode(false);
    }

    if (event["action"] == "stopService") {
      service.stopBackgroundService();
    }
  });

  FlutterBackgroundService().onDataMqttReceived.listen((value) {
    print('>>> Value from controller >>> : $value');
    //
    // AwesomeNotifications().isNotificationAllowed().then((isAllowed) {
    //   if (!isAllowed) {
    //     AwesomeNotifications().requestPermissionToSendNotifications();
    //   } else {
    //     AwesomeNotifications().createNotification(
    //         actionButtons: [
    //           new NotificationActionButton(
    //             label: "Accept",
    //             key: 'accept',
    //             //icon: 'resource://drawable/accept',
    //             autoDismissible: false,
    //             buttonType: ActionButtonType.KeepOnTop,
    //           ),
    //           new NotificationActionButton(
    //             label: "Pass",
    //             key: 'pass',
    //             //icon: 'resource://drawable/reject',
    //             autoDismissible: false,
    //             buttonType: ActionButtonType.KeepOnTop,
    //           )
    //         ],
    //         content: NotificationContent(
    //             locked: false,
    //             id: 10,
    //             hideLargeIconOnExpand: false,
    //             channelKey: 'accept_pass_key',
    //             autoDismissible: false,
    //             notificationLayout: NotificationLayout.BigText,
    //             displayOnForeground: true,
    //             displayOnBackground: true,
    //             body: 'Simple body'));
    //   }
    // });
  });

  // bring to foreground
  service.setForegroundMode(true);
  // Timer.periodic(Duration(seconds: 5), (timer) async {
  //   if (!(await service.isServiceRunning())) timer.cancel();
  //
  //   service.setNotificationInfo(
  //     title: "My App Service",
  //     content: "Updated at ${DateTime.now()}",
  //   );
  //
  //
  //   service.sendData(
  //     {"current_date": DateTime.now().toIso8601String()},
  //   );
  //
  //
  //   service.mqttSendData(
  //       {"mqttSendData": DateTime.now().toIso8601String()}
  //   );
  // });
}

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {


  String text = "Stop Service";

  @override
  Widget build(BuildContext context) {

    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Service App'),
        ),
        body: Column(
          children: [
            ElevatedButton(
              child: Text("Foreground Mode"),
              onPressed: () {
                FlutterBackgroundService()
                    .sendData({"action": "setAsForeground"});
              },
            ),
            ElevatedButton(
              child: Text("Background Mode"),
              onPressed: () {
                FlutterBackgroundService()
                    .sendData({"action": "setAsBackground"});
              },
            ),
            ElevatedButton(
              child: Text(text),
              onPressed: () async {
                final service = FlutterBackgroundService();
                var isRunning = await service.isServiceRunning();
                if (isRunning) {
                  service.sendData(
                    {"action": "stopService"},
                  );
                } else {
                  service.start();
                }

                if (!isRunning) {
                  text = 'Stop Service';
                } else {
                  text = 'Start Service';
                }
                setState(() {});
              },
            ),
          ],
        ),
        floatingActionButton: FloatingActionButton(
          onPressed: () {
            FlutterBackgroundService().sendData({
              "hello": "world",
            });
          },
          child: Icon(Icons.play_arrow),
        ),
      ),
    );
  }
}
