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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;

import com.gsma.services.rcs.JoynServiceException;
import com.gsma.services.rcs.extension.MultimediaStreamingSession;
import com.orangelabs.rcs.ri.R;
import com.orangelabs.rcs.ri.extension.MultimediaSessionList;
import com.orangelabs.rcs.ri.utils.Utils;

/**
 * List of streaming sessions in progress
 * 
 * @author Jean-Marc AUFFRET
 */
public class StreamingSessionList extends MultimediaSessionList {
	/**
	 * List of sessions
	 */
	private List<MultimediaStreamingSession> sessions = new ArrayList<MultimediaStreamingSession>();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
        // Set title
        setTitle(R.string.menu_streaming_sessions_list);
	}
	
	/**
	 * Display a session
	 * 
	 * @param sessionId Session ID
	 */
	public void displaySession(int position) {
		try {
			Intent intent = new Intent(this, StreamingSessionView.class);
			String sessionId = sessions.get(position).getSessionId();
			intent.putExtra(StreamingSessionView.EXTRA_MODE, StreamingSessionView.MODE_OPEN);
			intent.putExtra(StreamingSessionView.EXTRA_SESSION_ID, sessionId);
			startActivity(intent);
		} catch(JoynServiceException e) {
			e.printStackTrace();
			Utils.showMessageAndExit(StreamingSessionList.this, getString(R.string.label_api_failed));
		}
	}
	
    /**
     * Update the displayed list
     */
    public void updateList() {
		try {
			// Reset the list
			sessions.clear();

			if (apiEnabled) {
		    	// Get list of pending sessions
		    	Set<MultimediaStreamingSession> currentSessions = sessionApi.getStreamingSessions(StreamingSessionUtils.SERVICE_ID);
		    	sessions = new ArrayList<MultimediaStreamingSession>(currentSessions);
				if (sessions.size() > 0){
			        String[] items = new String[sessions.size()];    
			        for (int i = 0; i < items.length; i++) {
						items[i] = getString(R.string.label_session, sessions.get(i).getSessionId());
			        }
					setListAdapter(new ArrayAdapter<String>(StreamingSessionList.this, android.R.layout.simple_list_item_1, items));
				} else {
					setListAdapter(null);
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
			Utils.showMessageAndExit(StreamingSessionList.this, getString(R.string.label_api_failed));
		}
    }
}