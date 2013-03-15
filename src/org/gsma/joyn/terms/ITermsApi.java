/*
 * Copyright 2013, France Telecom
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *    http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gsma.joyn.terms;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import java.lang.String;

/**
 * Interface ITermsApi.
 * <p>
 * Generated from AIDL.
 */
public interface ITermsApi extends IInterface {
    /**
     *
     * @param arg1 The arg1.
     * @param arg2 The arg2.
     * @return  The boolean.
     */
    public boolean acceptTerms(String arg1, String arg2) throws RemoteException;

    /**
     *
     * @param arg1 The arg1.
     * @param arg2 The arg2.
     * @return  The boolean.
     */
    public boolean rejectTerms(String arg1, String arg2) throws RemoteException;

    /**
     * Class Stub.
     */
    public abstract static class Stub extends Binder implements ITermsApi {
        /**
         * Creates a new instance of Stub.
         */
        public Stub() {
            super();
        }

        /**
         *
         * @return  The i binder.
         */
        public IBinder asBinder() {
            return (IBinder) null;
        }

        /**
         *
         * @param code
         * @param data
         * @param reply
         * @param flags
         * @return  The boolean.
         */
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            return false;
        }

        /**
         *
         * @param binder
         * @return  The i terms api.
         */
        public static ITermsApi asInterface(IBinder binder) {
            return (ITermsApi) null;
        }

    }

}