/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
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
 ******************************************************************************/

package com.gsma.services.rcs;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.SparseArray;

/**
 * A class to hold the reason code for IMS registration
 * 
 * @author YPLO6403
 */
public class RcsServiceRegistration {
    /**
     * ReasonCode for IMS registration
     */
    public enum ReasonCode implements Parcelable {
        /**
         * No specific reason code specified
         */
        UNSPECIFIED(0),

        /**
         * IMS connection has been lost
         */
        CONNECTION_LOST(1),

        /**
         * Disconnected from RCS platform (Battery low)
         */
        BATTERY_LOW(2);

        private final int mValue;

        private static SparseArray<ReasonCode> mValueToEnum = new SparseArray<ReasonCode>();
        static {
            for (ReasonCode entry : ReasonCode.values()) {
                mValueToEnum.put(entry.toInt(), entry);
            }
        }

        private ReasonCode(int value) {
            mValue = value;
        }

        /**
         * Gets integer value associated to ReasonCode instance
         * 
         * @return value
         */
        public final int toInt() {
            return mValue;
        }

        /**
         * Returns a ReasonCode instance for the specified integer value.
         * 
         * @param value
         * @return instance
         */
        public final static ReasonCode valueOf(int value) {
            ReasonCode entry = mValueToEnum.get(value);
            if (entry != null) {
                return entry;
            }
            throw new IllegalArgumentException(new StringBuilder("No enum const class ")
                    .append(RcsServiceRegistration.class.getName()).append(".").append(value)
                    .append("!")
                    .toString());
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(final Parcel dest, final int flags) {
            dest.writeInt(toInt());
        }

        /**
         * Interface that generates instances of ReasonCode class from a Parcel.
         */
        public static final Creator<ReasonCode> CREATOR = new Creator<ReasonCode>() {
            @Override
            public ReasonCode createFromParcel(final Parcel source) {
                return ReasonCode.valueOf(source.readInt());
            }

            @Override
            public ReasonCode[] newArray(final int size) {
                return new ReasonCode[size];
            }
        };
    }

    private RcsServiceRegistration() {
    }
}
