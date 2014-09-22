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

package com.orangelabs.rcs.service.api;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import android.content.Intent;
import android.os.IBinder;
import android.view.Surface;

import com.gsma.services.rcs.IJoynServiceRegistrationListener;
import com.gsma.services.rcs.JoynService;
import com.gsma.services.rcs.contacts.ContactId;
import com.gsma.services.rcs.vsh.IVideoPlayer;
import com.gsma.services.rcs.vsh.IVideoSharing;
import com.gsma.services.rcs.vsh.IVideoSharingListener;
import com.gsma.services.rcs.vsh.IVideoSharingService;
import com.gsma.services.rcs.vsh.VideoDescriptor;
import com.gsma.services.rcs.vsh.VideoSharing;
import com.gsma.services.rcs.vsh.VideoSharingIntent;
import com.gsma.services.rcs.vsh.VideoSharingServiceConfiguration;
import com.orangelabs.rcs.core.Core;
import com.orangelabs.rcs.core.content.VideoContent;
import com.orangelabs.rcs.core.ims.service.richcall.video.VideoStreamingSession;
import com.orangelabs.rcs.platform.AndroidFactory;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.provider.sharing.RichCallHistory;
import com.orangelabs.rcs.service.broadcaster.JoynServiceRegistrationEventBroadcaster;
import com.orangelabs.rcs.service.broadcaster.VideoSharingEventBroadcaster;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Rich call API service
 * 
 * @author Jean-Marc AUFFRET
 */
public class VideoSharingServiceImpl extends IVideoSharingService.Stub {

	private final VideoSharingEventBroadcaster mVideoSharingEventBroadcaster = new VideoSharingEventBroadcaster();

	private final JoynServiceRegistrationEventBroadcaster mJoynServiceRegistrationEventBroadcaster = new JoynServiceRegistrationEventBroadcaster();

	/**
	 * List of video sharing sessions
	 */
    private static Hashtable<String, IVideoSharing> videoSharingSessions = new Hashtable<String, IVideoSharing>();

	/**
	 * Lock used for synchronization
	 */
	private final Object lock = new Object();

	/**
	 * The logger
	 */
	private static final  Logger logger = Logger.getLogger(VideoSharingServiceImpl.class.getSimpleName());

	/**
	 * Constructor
	 */
	public VideoSharingServiceImpl() {
		if (logger.isActivated()) {
			logger.info("Video sharing API is loaded");
		}
	}

	/**
	 * Close API
	 */
	public void close() {
		// Clear list of sessions
		videoSharingSessions.clear();
		
		if (logger.isActivated()) {
			logger.info("Video sharing service API is closed");
		}
	}

    /**
     * Add a video sharing session in the list
     * 
     * @param session Video sharing session
     */
	private static void addVideoSharingSession(VideoSharingImpl session) {
		if (logger.isActivated()) {
			logger.debug("Add a video sharing session in the list (size=" + videoSharingSessions.size() + ")");
		}
		
		videoSharingSessions.put(session.getSharingId(), session);
	}

    /**
     * Remove a video sharing session from the list
     * 
     * @param sessionId Session ID
     */
	/* package private */ static void removeVideoSharingSession(String sessionId) {
		if (logger.isActivated()) {
			logger.debug("Remove a video sharing session from the list (size=" + videoSharingSessions.size() + ")");
		}
		
		videoSharingSessions.remove(sessionId);
	}

    /**
     * Returns true if the service is registered to the platform, else returns false
     * 
	 * @return Returns true if registered else returns false
     */
    public boolean isServiceRegistered() {
    	return ServerApiUtils.isImsConnected();
    }

	/**
	 * Registers a listener on service registration events
	 *
	 * @param listener Service registration listener
	 */
	public void addServiceRegistrationListener(IJoynServiceRegistrationListener listener) {
		if (logger.isActivated()) {
			logger.info("Add a service listener");
		}
		synchronized (lock) {
			mJoynServiceRegistrationEventBroadcaster.addServiceRegistrationListener(listener);
		}
	}

	/**
	 * Unregisters a listener on service registration events
	 *
	 * @param listener Service registration listener
	 */
	public void removeServiceRegistrationListener(IJoynServiceRegistrationListener listener) {
		if (logger.isActivated()) {
			logger.info("Remove a service listener");
		}
		synchronized (lock) {
			mJoynServiceRegistrationEventBroadcaster.removeServiceRegistrationListener(listener);
		}
	}

	/**
	 * Receive registration event
	 *
	 * @param state Registration state
	 */
	public void notifyRegistrationEvent(boolean state) {
		// Notify listeners
		synchronized (lock) {
			if (state) {
				mJoynServiceRegistrationEventBroadcaster.broadcastServiceRegistered();
			} else {
				mJoynServiceRegistrationEventBroadcaster.broadcastServiceUnRegistered();
			}
		}
	}

	/**
     * Get the remote contact Id involved in the current call
     * 
     * @return ContactId or null if there is no call in progress
     * @throws ServerApiException
     */
	public ContactId getRemotePhoneNumber() throws ServerApiException {
		if (logger.isActivated()) {
			logger.info("Get remote phone number");
		}

		// Test core availability
		ServerApiUtils.testCore();

		try {
			return Core.getInstance().getImsModule().getCallManager().getContact();
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Unexpected error", e);
			}
			throw new ServerApiException(e.getMessage());
		}
	}

	/**
     * Receive a new video sharing invitation
     * 
     * @param session Video sharing session
     */
    public void receiveVideoSharingInvitation(VideoStreamingSession session) {
		ContactId contact = session.getRemoteContact();
		if (logger.isActivated()) {
			logger.info("Receive video sharing invitation from " + contact);
		}

		// Update rich call history
        VideoContent content = (VideoContent)session.getContent();
		RichCallHistory.getInstance().addVideoSharing(contact, session.getSessionID(),
				VideoSharing.Direction.INCOMING,
				content,
    			VideoSharing.State.INVITED);
		// TODO : Update displayName of remote contact
		/*
		 * ContactsManager.getInstance().setContactDisplayName(contact,
		 * session.getRemoteDisplayName());
		 */
		// Add session in the list
		VideoSharingImpl sessionApi = new VideoSharingImpl(session, mVideoSharingEventBroadcaster);
		VideoSharingServiceImpl.addVideoSharingSession(sessionApi);

		// Broadcast intent related to the received invitation
		Intent intent = new Intent(VideoSharingIntent.ACTION_NEW_INVITATION);
		intent.addFlags(Intent.FLAG_EXCLUDE_STOPPED_PACKAGES);
		intent.putExtra(VideoSharingIntent.EXTRA_SHARING_ID, session.getSessionID());
		AndroidFactory.getApplicationContext().sendBroadcast(intent);
    }
    
    /**
     * Returns the configuration of video sharing service
     * 
     * @return Configuration
     */
    public VideoSharingServiceConfiguration getConfiguration() {
    	return new VideoSharingServiceConfiguration(
    			RcsSettings.getInstance().getMaxVideoShareDuration());    	
	}

	/**
	 * Shares a live video with a contact by using an external video player.
	 * An exception if thrown if there is no ongoing CS call. The parameter
	 * contact supports the following formats: MSISDN in national or international
	 * format, SIP address, SIP-URI or Tel-URI. If the format of the contact is
	 * not supported an exception is thrown.
	 * 
	 * @param contact Contact identifier
	 * @param player Video player
	 * @return Video sharing
	 * @throws ServerApiException
     */
    public IVideoSharing shareVideo(ContactId contact, IVideoPlayer player) throws ServerApiException {
		if (logger.isActivated()) {
			logger.info("Initiate a live video session with " + contact + " (external player)");
		}

		// Test IMS connection
		ServerApiUtils.testIms();

		// Test if a player is configured
		if (player == null) {
			throw new ServerApiException("Missing video player");
		}

		try {
		     // Initiate a new session
            final VideoStreamingSession session = Core.getInstance().getRichcallService().initiateLiveVideoSharingSession(contact, player);

			// Update rich call history
			RichCallHistory.getInstance().addVideoSharing(contact, session.getSessionID(),
					VideoSharing.Direction.OUTGOING, (VideoContent)session.getContent(),
					VideoSharing.State.INITIATED);

			// Add session listener
			VideoSharingImpl sessionApi = new VideoSharingImpl(session, mVideoSharingEventBroadcaster);
			
			// Start the session
	        Thread t = new Thread() {
	    		public void run() {
	    			session.startSession();
	    		}
	    	};
	    	t.start();	
	    	
			// Add session in the list
			addVideoSharingSession(sessionApi);
			return sessionApi;
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Unexpected error", e);
			}
			throw new ServerApiException(e.getMessage());
		}
	}
    
	/**
	 * Shares a live video with a contact by using the default video player.
	 * An exception if thrown if there is no ongoing CS call. The parameter
	 * contact supports the following formats: MSISDN in national or international
	 * format, SIP address, SIP-URI or Tel-URI. If the format of the contact is
	 * not supported an exception is thrown.
	 * 
	 * @param contact Contact identifier
	 * @param descriptor Video descriptor
	 * @param surface Video surface view
	 * @return Video sharing
	 * @throws ServerApiException
	 */
	public IVideoSharing shareVideo2(ContactId contact, VideoDescriptor descriptor, Surface surface) throws ServerApiException {
		if (logger.isActivated()) {
			logger.info("Initiate a live video session with " + contact + " (default player)");
		}

		// Test IMS connection
		ServerApiUtils.testIms();

		// Test if a descriptor and surface are configured
		if (descriptor == null) {
			throw new ServerApiException("Missing video descriptor");
		}
		if (surface == null) {
			throw new ServerApiException("Missing video surface");
		}
		
		try {
			// Initiate a new session
            final VideoStreamingSession session = Core.getInstance().getRichcallService().initiateLiveVideoSharingSession(contact, descriptor, surface);

			// Update rich call history
			RichCallHistory.getInstance().addVideoSharing(contact, session.getSessionID(),
					VideoSharing.Direction.OUTGOING, (VideoContent)session.getContent(),
					VideoSharing.State.INITIATED);

			// Add session listener
			VideoSharingImpl sessionApi = new VideoSharingImpl(session, mVideoSharingEventBroadcaster);
			
			// Start the session
	        Thread t = new Thread() {
	    		public void run() {
	    			session.startSession();
	    		}
	    	};
	    	t.start();	
	    	
			// Add session in the list
			addVideoSharingSession(sessionApi);
			return sessionApi;
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Unexpected error", e);
			}
			throw new ServerApiException(e.getMessage());
		}
	}

    /**
     * Returns a current video sharing from its unique ID
     * 
     * @return Video sharing or null if not found
     * @throws ServerApiException
     */
    public IVideoSharing getVideoSharing(String sharingId) throws ServerApiException {
		if (logger.isActivated()) {
			logger.info("Get video sharing session " + sharingId);
		}

		return videoSharingSessions.get(sharingId);
	}

    /**
     * Returns the list of video sharings in progress
     * 
     * @return List of video sharings
     * @throws ServerApiException
     */
    public List<IBinder> getVideoSharings() throws ServerApiException {
    	if (logger.isActivated()) {
			logger.info("Get video sharing sessions");
		}

		try {
			ArrayList<IBinder> result = new ArrayList<IBinder>(videoSharingSessions.size());
			for (Enumeration<IVideoSharing> e = videoSharingSessions.elements() ; e.hasMoreElements() ;) {
				IVideoSharing sessionApi = e.nextElement() ;
				result.add(sessionApi.asBinder());
			}
			return result;
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Unexpected error", e);
			}
			throw new ServerApiException(e.getMessage());
		}		
	}

	/**
	 * Adds an event listener on video sharing events
	 * 
	 * @param listener Listener
	 */
	public void addEventListener(IVideoSharingListener listener) {
		if (logger.isActivated()) {
			logger.info("Add a video sharing event listener");
		}
		synchronized (lock) {
			mVideoSharingEventBroadcaster.addEventListener(listener);
		}
	}

	/**
	 * Removes an event listener from video sharing events
	 * 
	 * @param listener Listener
	 */
	public void removeEventListener(IVideoSharingListener listener) {
		if (logger.isActivated()) {
			logger.info("Remove a video sharing event listener");
		}
		synchronized (lock) {
			mVideoSharingEventBroadcaster.removeEventListener(listener);
		}
	}

	/**
	 * Returns service version
	 * 
	 * @return Version
	 * @see JoynService.Build.VERSION_CODES
	 * @throws ServerApiException
	 */
	public int getServiceVersion() throws ServerApiException {
		return JoynService.Build.API_VERSION;
	}
}
