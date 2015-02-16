/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * NOTE: This file has been modified by Sony Mobile Communications Inc.
 * Modifications are licensed under the License.
 ******************************************************************************/

package com.gsma.rcs.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Bundle;

import com.gsma.services.rcs.Intents;
import com.gsma.rcs.provider.LocalContentResolver;
import com.gsma.rcs.provider.settings.RcsSettings;
import com.gsma.rcs.provider.settings.RcsSettingsData.EnableRcseSwitch;
import com.gsma.rcs.utils.logger.Logger;

/**
 * A class to control the service activation.
 * 
 * @author yplo6403
 */
public class RcsServiceControlReceiver extends BroadcastReceiver {

    private final static Logger sLogger = Logger.getLogger(RcsServiceControlReceiver.class
            .getSimpleName());

    private RcsSettings mRcsSettings;

    private Context mContext;

    private boolean getActivationModeChangeable() {
        EnableRcseSwitch enableRcseSwitch = mRcsSettings.getEnableRcseSwitch();
        switch (enableRcseSwitch) {
            case ALWAYS_SHOW:
                return true;
            case ONLY_SHOW_IN_ROAMING:
                return IsDataRoamingEnabled();
            case NEVER_SHOW:
            default:
                return false;
        }
    }

    private boolean getActivationMode(Context context) {
        return mRcsSettings.isServiceActivated();
    }

    private boolean IsDataRoamingEnabled() {
        ConnectivityManager cm = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm.getActiveNetworkInfo() == null) {
            return false;
        }
        return cm.getActiveNetworkInfo().isRoaming();
    }

    private void setActivationMode(Context context, boolean active) {
        if (!getActivationModeChangeable()) {
            if (sLogger.isActivated()) {
                sLogger.error("Cannot set activation mode: permission denied");
            }
            return;
        }
        if (mRcsSettings.isServiceActivated() == active) {
            if (sLogger.isActivated()) {
                sLogger.warn("Activation mode already set to ".concat(String.valueOf(active)));
            }
            return;
        }
        if (sLogger.isActivated()) {
            sLogger.debug("setActivationMode: ".concat(String.valueOf(active)));
        }
        mRcsSettings.setServiceActivationState(active);
        if (active) {
            LauncherUtils.launchRcsService(mContext, false, true, mRcsSettings);
        } else {
            LauncherUtils.stopRcsService(mContext);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        LocalContentResolver localContentResolver = new LocalContentResolver(context);
        RcsSettings.createInstance(localContentResolver);
        mRcsSettings = RcsSettings.getInstance();
        mContext = context;

        if (Intents.Service.ACTION_GET_ACTIVATION_MODE_CHANGEABLE.equals(intent.getAction())) {
            Bundle results = getResultExtras(true);
            if (results == null) {
                return;
            }
            results.putBoolean(Intents.Service.EXTRA_GET_ACTIVATION_MODE_CHANGEABLE,
                    getActivationModeChangeable());
            setResultExtras(results);
        } else if (Intents.Service.ACTION_GET_ACTIVATION_MODE.equals(intent.getAction())) {
            Bundle results = getResultExtras(true);
            if (results == null) {
                return;
            }
            results.putBoolean(Intents.Service.EXTRA_GET_ACTIVATION_MODE,
                    getActivationMode(context));
            setResultExtras(results);
        } else if (Intents.Service.ACTION_SET_ACTIVATION_MODE.equals(intent.getAction())) {
            boolean active = intent
                    .getBooleanExtra(Intents.Service.EXTRA_SET_ACTIVATION_MODE, true);
            setActivationMode(context, active);
        }
    }

}
