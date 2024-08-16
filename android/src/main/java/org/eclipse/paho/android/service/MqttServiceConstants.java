/*******************************************************************************
 * Copyright (c) 1999, 2016 IBM Corp.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution. 
 *
 * The Eclipse Public License is available at 
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at 
 *   http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.eclipse.paho.android.service;

import id.flutter.flutter_background_service.BackgroundService;

/**
 * Various strings used to identify operations or data in the Android MQTT
 * service, mainly used in Intents passed between Activities and the Service.
 */
public interface MqttServiceConstants {

	/*
	 * Version information
	 */
	
	String VERSION = "v0";
	
  /*
   * Attributes of messages <p> Used for the column names in the database
   */
  String DUPLICATE = "duplicate";
  String RETAINED = "retained";
  String QOS = "qos";
  String PAYLOAD = "payload";
  String DESTINATION_NAME = "destinationName";
  String CLIENT_HANDLE = "clientHandle";
  String MESSAGE_ID = "messageId";

  /* Tags for actions passed between the Activity and the Service */
  String SEND_ACTION = "send";
  String UNSUBSCRIBE_ACTION = "unsubscribe";
  String SUBSCRIBE_ACTION = "subscribe";
  String DISCONNECT_ACTION = "disconnect";
  String CONNECT_ACTION = "connect";
  String CONNECT_EXTENDED_ACTION = "connectExtended";
  String MESSAGE_ARRIVED_ACTION = "messageArrived";
  String MESSAGE_DELIVERED_ACTION = "messageDelivered";
  String ON_CONNECTION_LOST_ACTION = "onConnectionLost";
  String TRACE_ACTION = "trace";

  /* Identifies an Intent which calls back to the Activity */
  String CALLBACK_TO_ACTIVITY = BackgroundService.TAG
                                             + ".callbackToActivity"+"."+VERSION;

  /* Identifiers for extra data on Intents broadcast to the Activity */
  String CALLBACK_ACTION = BackgroundService.TAG + ".callbackAction";
  String CALLBACK_STATUS = BackgroundService.TAG + ".callbackStatus";
  String CALLBACK_CLIENT_HANDLE = BackgroundService.TAG + "."
                                               + CLIENT_HANDLE;
  String CALLBACK_ERROR_MESSAGE = BackgroundService.TAG
                                               + ".errorMessage";
  String CALLBACK_EXCEPTION_STACK = BackgroundService.TAG
                                                 + ".exceptionStack";
  String CALLBACK_INVOCATION_CONTEXT = BackgroundService.TAG + "."
                                                    + "invocationContext";
  String CALLBACK_ACTIVITY_TOKEN = BackgroundService.TAG + "."
                                                + "activityToken";
  String CALLBACK_DESTINATION_NAME = BackgroundService.TAG + '.'
                                                  + DESTINATION_NAME;
  String CALLBACK_MESSAGE_ID = BackgroundService.TAG + '.'
                                            + MESSAGE_ID;
  String CALLBACK_RECONNECT = BackgroundService.TAG + ".reconnect";
  String CALLBACK_SERVER_URI = BackgroundService.TAG + ".serverURI";
  String CALLBACK_MESSAGE_PARCEL = BackgroundService.TAG + ".PARCEL";
  String CALLBACK_TRACE_SEVERITY = BackgroundService.TAG
                                                + ".traceSeverity";
  String CALLBACK_TRACE_TAG = BackgroundService.TAG + ".traceTag";
  String CALLBACK_TRACE_ID = BackgroundService.TAG + ".traceId";
  String CALLBACK_ERROR_NUMBER = BackgroundService.TAG
                                              + ".ERROR_NUMBER";

  String CALLBACK_EXCEPTION = BackgroundService.TAG + ".exception";
  
  //Intent prefix for Ping sender.
  String PING_SENDER = BackgroundService.TAG + ".pingSender.";
  
  //Constant for wakelock
  String PING_WAKELOCK = BackgroundService.TAG + ".client.";
  String WAKELOCK_NETWORK_INTENT = BackgroundService.TAG + "";

  //Trace severity levels  
  String TRACE_ERROR = "error";
  String TRACE_DEBUG = "debug";
  String TRACE_EXCEPTION = "exception";
  
  
  //exception code for non MqttExceptions
  int NON_MQTT_EXCEPTION = -1;

}