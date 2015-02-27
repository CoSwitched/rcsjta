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
package com.orangelabs.rcs.ri.extension.streaming;

import android.content.Intent;
import android.os.Parcelable;

import com.gsma.services.rcs.contacts.ContactId;
import com.orangelabs.rcs.ri.extension.InitiateMultimediaSession;
/**
 * Initiate streaming session
 *  
 * @author Jean-Marc AUFFRET
 */
public class InitiateStreamingSession extends InitiateMultimediaSession {

	/**
	 * Initiate session
	 * 
	 * @param contact Remote contact
	 */
	public void initiateSession(ContactId contact, String extension) {
		// Display session view
		Intent intent = new Intent(InitiateStreamingSession.this, StreamingSessionView.class);
    	intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    	intent.putExtra(StreamingSessionView.EXTRA_MODE, StreamingSessionView.MODE_OUTGOING);
    	intent.putExtra(StreamingSessionView.EXTRA_CONTACT, (Parcelable)contact);
		startActivity(intent);
	}
}