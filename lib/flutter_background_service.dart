import 'dart:async';
import 'dart:io';
import 'dart:ui';

import 'package:flutter/services.dart';

class IosConfiguration {
  /// must be a top level or static method
  /// this method will be executed when app is in foreground
  final Function onForeground;

  /// must be a top level or static method
  /// this method will be executed by background fetch
  /// make sure you don't execute long running task there because of limitations on ios
  /// recommended maximum executed duration is only 15-20 seconds.
  final Function onBackground;

  /// wheter service auto start after configure.
  final bool autoStart;

  IosConfiguration({
    required this.onForeground,
    required this.onBackground,
    this.autoStart = true,
  });
}

class AndroidConfiguration {
  /// must be a top level or static method
  final Function onStart;

  ///
  final Function? onMqConnected;

  ///
  final Function? onMqDisconnected;

  /// wheter service can started automatically on boot and after configure
  final bool autoStart;

  /// wheter service is foreground or background mode
  final bool isForegroundMode;

  final String? foregroundServiceNotificationTitle;
  final String? foregroundServiceNotificationContent;

  /// wheter service is foreground or background mode
  final bool isServiceStart;

  final String mqServerHost;
  final String mqUsername;
  final String mqPassword;
  final int mqServerPort;
  final String mqClientId;
  final String driverId;
  final String apiToken;

  final int locSetInterval;
  final int locSetFastestInterval;
  final int locSetPriority;

  AndroidConfiguration({
    required this.onStart,
    this.onMqConnected,
    this.onMqDisconnected,
    this.autoStart = true,
    this.isServiceStart = false,
    required this.isForegroundMode,
    this.foregroundServiceNotificationContent,
    this.foregroundServiceNotificationTitle,
    required this.mqServerHost,
    required this.mqUsername,
    required this.mqPassword,
    required this.mqServerPort,
    required this.mqClientId,
    required this.locSetInterval,
    required this.locSetFastestInterval,
    required this.locSetPriority,
    required this.driverId,
    required this.apiToken
  });
}


class FlutterBackgroundService {
  bool _isFromInitialization = false;
  bool _isRunning = false;
  bool _isMainChannel = false;

  static const MethodChannel _backgroundChannel = const MethodChannel(
    'id.flutter/background_service_bg', JSONMethodCodec(),);

  static const MethodChannel _mainChannel = const MethodChannel(
    'id.flutter/background_service', JSONMethodCodec(),);

  static const EventChannel _eventChannel = const EventChannel(
    'id.flutter/background_service_bg_event', JSONMethodCodec(),);

  static FlutterBackgroundService _instance = FlutterBackgroundService
      ._internal()
    .._setupBackground();

  FlutterBackgroundService._internal();

  factory FlutterBackgroundService() => _instance;

  void _setupMain() {
    _isFromInitialization = true;
    _isRunning = true;
    _isMainChannel = true;
    _mainChannel.setMethodCallHandler(_handle);
  }

  void _setupBackground() {
    _isRunning = true;
    _backgroundChannel.setMethodCallHandler(_handle);
  }

  Future<dynamic> _handle(MethodCall call) async {
    switch (call.method) {
      case "onReceiveData":
        _streamController.sink.add(call.arguments);
        break;
      default:
    }
    return true;
  }

  Future<bool> start() async {
    if (!_isMainChannel) {
      throw Exception(
          'This method only allowed from UI. Please call configure() first.');
    }

    final result = await _mainChannel.invokeMethod('start');
    return result ?? false;
  }

  Future<bool> configure({
    required IosConfiguration iosConfiguration,
    required AndroidConfiguration androidConfiguration,}) async {
    if (Platform.isAndroid) {
      final CallbackHandle? handle = PluginUtilities.getCallbackHandle(
          androidConfiguration.onStart);
      if (handle == null) {
        return false;
      }

      final service = FlutterBackgroundService();
      service._setupMain();
      final result = await _mainChannel.invokeMethod(
        "configure",
        {
          "handle": handle.toRawHandle(),
          "is_foreground_mode": androidConfiguration.isForegroundMode,
          "is_service_start": androidConfiguration.isServiceStart,
          "auto_start_on_boot": androidConfiguration.autoStart,
          "mq_server_host": androidConfiguration.mqServerHost,
          "mq_port": androidConfiguration.mqServerPort,
          "mq_username": androidConfiguration.mqUsername,
          "mq_password": androidConfiguration.mqPassword,
          "mq_client_id": androidConfiguration.mqClientId,
          "loc_interval": androidConfiguration.locSetInterval,
          "loc_fastestInterval": androidConfiguration.locSetFastestInterval,
          "loc_priority": androidConfiguration.locSetPriority,
          "driver_id": androidConfiguration.driverId,
          "api_token": androidConfiguration.apiToken
        },
      );

      return result ?? false;
    }

    if (Platform.isIOS) {
      final CallbackHandle? backgroundHandle =
      PluginUtilities.getCallbackHandle(iosConfiguration.onBackground);
      if (backgroundHandle == null) {
        return false;
      }

      final CallbackHandle? foregroundHandle =
      PluginUtilities.getCallbackHandle(iosConfiguration.onForeground);
      if (foregroundHandle == null) {
        return false;
      }

      final service = FlutterBackgroundService();
      service._setupMain();

      final result = await _mainChannel.invokeMethod(
        "configure",
        {
          "background_handle": backgroundHandle.toRawHandle(),
          "foreground_handle": foregroundHandle.toRawHandle(),
          "auto_start": iosConfiguration.autoStart,
        },
      );

      return result ?? false;
    }

    return false;
  }


  // Send data from UI to Service, or from Service to UI
  void sendData(Map<String, dynamic> data) async {
    if (!(await (isServiceRunning()))) {
      dispose();
      return;
    }

    if (_isFromInitialization) {
      _mainChannel.invokeMethod("sendData", data);
      return;
    }

    _backgroundChannel.invokeMethod("sendData", data);
  }

  // Send data from UI to Service, or from Service to UI
  void mqPublishMessage(String topic, String payload) async {
    if (!(await (isServiceRunning()))) {
      dispose();
      return;
    }
    if (_isFromInitialization) {
      _mainChannel.invokeMethod("mqPublishMessage", {
        "action": "mqPublishMessage",
        "topic": topic,
        "payload": payload,
      });
      return;
    }
    _backgroundChannel.invokeMethod("mqPublishMessage", {
      "action": "mqPublishMessage",
      "topic": topic,
      "payload": payload,
    });
  }

  // Send data from UI to Service, or from Service to UI
  void getCurrentLocation() async {
    if (!(await (isServiceRunning()))) {
      dispose();
      return;
    }
    if (_isFromInitialization) {
      _mainChannel.invokeMethod("getCurrentLocation", {
        "action": "getCurrentLocation",
      });
      return;
    }
    _backgroundChannel.invokeMethod("getCurrentLocation", {
      "action": "getCurrentLocation",
    });
  }


  // Send data from UI to Service, or from Service to UI
  void mqSubscribeTopic(String topic) async {
    if (!(await (isServiceRunning()))) {
      dispose();
      return;
    }
    if (_isFromInitialization) {
      _mainChannel.invokeMethod("mqSubscribeTopic", {
        "action": "mqSubscribeTopic",
        "topic": topic
      });
      return;
    }
    _backgroundChannel.invokeMethod("mqSubscribeTopic", {
      "action": "mqSubscribeTopic",
      "topic": topic
    });
  }

  // Send data from UI to Service, or from Service to UI
  void mqUnSubscribeTopic(String topic) async {
    if (!(await (isServiceRunning()))) {
      dispose();
      return;
    }
    if (_isFromInitialization) {
      _mainChannel.invokeMethod("mqUnSubscribeTopic", {
        "action": "mqUnSubscribeTopic",
        "topic": topic
      });
      return;
    }
    _backgroundChannel.invokeMethod("mqUnSubscribeTopic", {
      "action": "mqUnSubscribeTopic",
      "topic": topic
    });
  }

  // stop booking request sound on Accept and Pass
  void stopBookingSound() async {
    if (!(await (isServiceRunning()))) {
      dispose();
      return;
    }
    if (_isFromInitialization) {
      _mainChannel.invokeMethod("stopBookingSound", {
        "action": "stopBookingSound",
      });
      return;
    }
    _backgroundChannel.invokeMethod("stopBookingSound", {
      "action": "stopBookingSound",
    });
  }

  // Set Foreground Notification Information
  // Only available when foreground mode is true
  void setNotificationInfo({String? title, String? content}) {
    if (Platform.isAndroid)
      _backgroundChannel.invokeMethod("setNotificationInfo", {
        "title": title,
        "content": content,
      });
  }

  // Set Foreground Mode
  // Only for Android
  void setForegroundMode(bool value) {
    if (Platform.isAndroid)
      _backgroundChannel.invokeMethod("setForegroundMode", {"value": value,});
  }

  // Set Foreground Mode
  // Only for Android
  void setIsServiceStart(bool value) {
    if (Platform.isAndroid)
      _backgroundChannel.invokeMethod("setIsServiceStart", {"value": value,});
  }

  Future<bool> isServiceRunning() async {
    if (_isMainChannel) {
      var result = await _mainChannel.invokeMethod("isServiceRunning");
      return result ?? false;
    } else {
      return _isRunning;
    }
  }

  // StopBackgroundService from Running
  void stopBackgroundService() {
    _backgroundChannel.invokeMethod("stopService");
    _isRunning = false;
  }

  void setAutoStartOnBootMode(bool value) {
    if (Platform.isAndroid)
      _backgroundChannel.invokeMethod("setAutoStartOnBootMode", {
        "value": value,
      });
  }


  StreamController<Map<String, dynamic>?> _streamController = StreamController
      .broadcast();

  Stream<Map<String, dynamic>?> get onDataReceived => _streamController.stream;

  void dispose() {
    _streamController.close();
  }

  //
  // Future<Stream<Map<String, dynamic>?>> getMqttResponseData() async {
  //   try {
  //     return _eventChannel.receiveBroadcastStream().map<Map<String, dynamic>?>((value) => value);
  //   } on PlatformException catch (e) {
  //     batteryLevel = "Failed to get battery level: '${e.message}'.";
  //   }
  // }


  Stream<Map<String, dynamic>?> get onDataMqttReceived =>
      _eventChannel.receiveBroadcastStream().map<Map<String, dynamic>?>((
          value) => value);
}
