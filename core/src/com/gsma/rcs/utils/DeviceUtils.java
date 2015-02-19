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

package com.gsma.rcs.utils;

import static com.gsma.rcs.utils.StringUtils.UTF8;

import android.content.Context;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.gsma.rcs.provider.settings.RcsSettings;
import com.gsma.services.rcs.RcsServiceException;

import java.lang.reflect.Field;
import java.util.UUID;

/***
 * Device utility functions
 * 
 * @author jexa7410
 */
public class DeviceUtils {

    private static final String URN_IMEI = "\"<urn:gsma:imei:";

    private static final String URN_UUID = "\"<urn:uuid:";

    private static final String GREATER_THAN = ">\"";

    private static final char HYPHEN = '-';

    private static final int START_INDEX = 0;

    private static final int LAST_INDEX = 1;

    private static final int LAST_SEVENTH_INDEX = 7;

    private static final int GINGERBREAD_VERSION_CODE = 9;

    private static final String FIELD_SERIAL = "SERIAL";

    private static final String ERROR_SERIAL_NOT_FOUND = "Failed to get declared serial field";

    private static final String ERROR_SERIAL_NO_ACCESS = "No access to the definition of serial field";
    /**
     * UUID
     */
    private static UUID uuid = null;

    private static String sImei;

    /**
     * Returns unique UUID of the device
     * 
     * @param ctx Context
     * @return UUID
     * @throws RcsServiceException
     */
    public static UUID getDeviceUUID(Context ctx) throws RcsServiceException {
        if (uuid == null) {
            String imei = getImei(ctx);
            if (imei == null) {
                // For compatibility with device without telephony
                uuid = generateUUID();
            } else {
                uuid = UUID.nameUUIDFromBytes(imei.getBytes(UTF8));
            }
        }
        return uuid;
    }

    /**
     * Generate the UUID from system using serial
     *
     * @return generated uuid
     * @throws RcsServiceException
     */
    public static UUID generateUUID() throws RcsServiceException {
        if (Build.VERSION.SDK_INT < GINGERBREAD_VERSION_CODE) {
            /**
             * Since SERIAL is introduced only from API level GINGERBREAD_VERSION_CODE, we need to
             * do nothing if we are running on a version prior to that, So we throw
             * RcsServiceException with error message = ERROR_SERIAL_NOT_FOUND.
             */
            throw new RcsServiceException(ERROR_SERIAL_NOT_FOUND);
        }
        try {
            Build build = new Build();
            Field serialField = build.getClass().getDeclaredField(FIELD_SERIAL);
            final String serial = (String) serialField.get(build);
            if (TextUtils.isEmpty(serial)) {
                throw new RcsServiceException(ERROR_SERIAL_NOT_FOUND);
            }
            return UUID.nameUUIDFromBytes(serial.getBytes(UTF8));
        } catch (NoSuchFieldException e) {
            throw new RcsServiceException(new StringBuilder(ERROR_SERIAL_NOT_FOUND).append(
                    e.getMessage()).toString());
        } catch (IllegalAccessException e) {
            throw new RcsServiceException(new StringBuilder(ERROR_SERIAL_NO_ACCESS).append(
                    e.getMessage()).toString());
        }
    }

    /**
     * Returns the IMEI of the device
     * 
     * @param ctx application context
     * @return IMEI of the device
     */
    private static String getImei(Context ctx) {
        if (sImei == null) {
            final TelephonyManager telephonyManager = (TelephonyManager) ctx
                    .getSystemService(Context.TELEPHONY_SERVICE);
            sImei = telephonyManager.getDeviceId();
            if (sImei == null) {
                return null;
            }
            /**
             * As per 3GPP TS 24.299, IMEI should be in format tac"-"snr"-"spare" where: tac=8
             * digits, snr=6 digits and spare=1 digit
             */
            final String tac = sImei.substring(START_INDEX, sImei.length() - LAST_SEVENTH_INDEX);
            final String snr = sImei.substring(tac.length(), sImei.length() - LAST_INDEX);
            final char spare = sImei.charAt(sImei.length() - LAST_INDEX);

            sImei = new StringBuilder(tac).append(HYPHEN).append(snr).append(HYPHEN).append(spare)
                    .toString();
        }
        return sImei;
    }

    /**
     * Get instance ID for populating SIP Instance
     *
     * @param ctx application context
     * @param rcsSettings
     * @return instance Id
     */
    public static String getInstanceId(Context ctx, RcsSettings rcsSettings) {
        /**
         * In accordance to RCS implementation guidelines v3.5, ID_4_33, embedded clients should use
         * IMEI as SIP instance if it's available and should no longer be dependent on deviceID
         * param. In case IMEI is not available, they should use uuid_Value, provided in
         * configurations or else generate it as per RFC4122, section 4.2
         */
        final String imei = getImei(ctx);
        if (imei != null) {
            return new StringBuilder(URN_IMEI).append(imei).append(GREATER_THAN).toString();
        }
        final String uuidValue = rcsSettings.getUUID();
        return new StringBuilder(URN_UUID).append(uuidValue).append(GREATER_THAN).toString();
    }
}
