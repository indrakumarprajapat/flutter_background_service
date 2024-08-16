/*******************************************************************************
 * Copyright (c) 1999, 2014 IBM Corp.
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

import android.os.Binder;

import id.flutter.flutter_background_service.BackgroundService;

/**
 * What the Service passes to the Activity on binding:-
 * <ul>
 * <li>a reference to the Service
 * <li>the activityToken provided when the Service was started
 * </ul>
 * 
 */
public class MqttServiceBinder extends Binder {

	private BackgroundService mqttService;
	private String activityToken;

	public MqttServiceBinder(BackgroundService mqttService) {
		this.mqttService = mqttService;
	}

	/**
	 * @return a reference to the Service
	 */
	public BackgroundService getService() {
		return mqttService;
	}

	public void setActivityToken(String activityToken) {
		this.activityToken = activityToken;
	}

	/**
	 * @return the activityToken provided when the Service was started
	 */
	public String getActivityToken() {
		return activityToken;
	}

}
