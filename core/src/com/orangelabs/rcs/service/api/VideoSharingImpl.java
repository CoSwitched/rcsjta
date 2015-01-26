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

import javax2.sip.message.Response;
import android.os.RemoteException;

import com.gsma.services.rcs.RcsCommon.Direction;
import com.gsma.services.rcs.contacts.ContactId;
import com.gsma.services.rcs.vsh.IVideoPlayer;
import com.gsma.services.rcs.vsh.IVideoSharing;
import com.gsma.services.rcs.vsh.VideoCodec;
import com.gsma.services.rcs.vsh.VideoDescriptor;
import com.gsma.services.rcs.vsh.VideoSharing;
import com.gsma.services.rcs.vsh.VideoSharing.ReasonCode;
import com.gsma.services.rcs.vsh.VideoSharing.State;
import com.orangelabs.rcs.core.content.VideoContent;
import com.orangelabs.rcs.core.ims.protocol.sip.SipDialogPath;
import com.orangelabs.rcs.core.ims.service.ImsServiceSession;
import com.orangelabs.rcs.core.ims.service.richcall.ContentSharingError;
import com.orangelabs.rcs.core.ims.service.richcall.RichcallService;
import com.orangelabs.rcs.core.ims.service.richcall.video.VideoSharingPersistedStorageAccessor;
import com.orangelabs.rcs.core.ims.service.richcall.video.VideoStreamingSession;
import com.orangelabs.rcs.core.ims.service.richcall.video.VideoStreamingSessionListener;
import com.orangelabs.rcs.provider.sharing.VideoSharingStateAndReasonCode;
import com.orangelabs.rcs.service.broadcaster.IVideoSharingEventBroadcaster;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Video sharing session
 * 
 * @author Jean-Marc AUFFRET
 */
public class VideoSharingImpl extends IVideoSharing.Stub implements VideoStreamingSessionListener {

	private final String mSharingId;

	private final IVideoSharingEventBroadcaster mBroadcaster;

	private final RichcallService mRichcallService;

	private final VideoSharingPersistedStorageAccessor mPersistentStorage;

	private final VideoSharingServiceImpl mVideoSharingService;

	/**
	 * Lock used for synchronization
	 */
	private final Object mLock = new Object();

	/**
	 * The logger
	 */
	private final Logger logger = Logger.getLogger(getClass().getName());

	/**
	 * Constructor
	 * 
	 * @param sharingId Unique Id of video sharing
	 * @param richcallService RichcallService
	 * @param broadcaster IVideoSharingEventBroadcaster
	 * @param persistentStorage VideoSharingPersistedStorageAccessor
	 * @param videoSharingService VideoSharingServiceImpl
	 */
	public VideoSharingImpl(String sharingId, RichcallService richcallService,
			IVideoSharingEventBroadcaster broadcaster,
			VideoSharingPersistedStorageAccessor persistentStorage, VideoSharingServiceImpl videoSharingService) {
		mSharingId = sharingId;
		mRichcallService = richcallService;
		mBroadcaster = broadcaster;
		mPersistentStorage = persistentStorage;
		mVideoSharingService = videoSharingService;
	}

	private VideoSharingStateAndReasonCode toStateAndReasonCode(ContentSharingError error) {
		int contentSharingError = error.getErrorCode();
		switch (contentSharingError) {
			case ContentSharingError.SESSION_INITIATION_FAILED:
				return new VideoSharingStateAndReasonCode(VideoSharing.State.FAILED,
						ReasonCode.FAILED_INITIATION);
			case ContentSharingError.SESSION_INITIATION_CANCELLED:
			case ContentSharingError.SESSION_INITIATION_DECLINED:
				return new VideoSharingStateAndReasonCode(VideoSharing.State.REJECTED,
						ReasonCode.REJECTED_BY_REMOTE);
			case ContentSharingError.MEDIA_TRANSFER_FAILED:
			case ContentSharingError.MEDIA_STREAMING_FAILED:
			case ContentSharingError.UNSUPPORTED_MEDIA_TYPE:
			case ContentSharingError.MEDIA_PLAYER_NOT_INITIALIZED:
				return new VideoSharingStateAndReasonCode(VideoSharing.State.FAILED,
						ReasonCode.FAILED_SHARING);
			default:
				throw new IllegalArgumentException(
						new StringBuilder(
								"Unknown reason in VideoSharingImpl.toStateAndReasonCode; contentSharingError=")
								.append(contentSharingError).append("!").toString());
		}
	}

	private int imsServiceSessionErrorToReasonCode(int imsServiceSessionError) {
		switch (imsServiceSessionError) {
			case ImsServiceSession.TERMINATION_BY_SYSTEM:
			case ImsServiceSession.TERMINATION_BY_TIMEOUT:
				return ReasonCode.ABORTED_BY_SYSTEM;
			case ImsServiceSession.TERMINATION_BY_USER:
				return ReasonCode.ABORTED_BY_USER;
			default:
				throw new IllegalArgumentException(
						"Unknown imsServiceSessionError=".concat(String.valueOf(imsServiceSessionError)));
		}
	}

	private void handleSessionRejected(int reasonCode) {
		if (logger.isActivated()) {
			logger.info("Session rejected; reasonCode=".concat(String.valueOf(reasonCode)));
		}
		synchronized (mLock) {
			mVideoSharingService.removeVideoSharing(mSharingId);

			mPersistentStorage.setStateReasonCodeAndDuration(VideoSharing.State.REJECTED,
					reasonCode, getCurrentDuration());

			mBroadcaster.broadcastStateChanged(getRemoteContact(),
					mSharingId, VideoSharing.State.ABORTED, reasonCode);
		}
	}

	private long getCurrentDuration() {
		return System.currentTimeMillis() - getTimestamp();
	}
	
    /**
	 * Returns the sharing ID of the video sharing
	 * 
	 * @return Sharing ID
	 */
	public String getSharingId() {
		return mSharingId;
	}
	
	/**
	 * Returns the remote contact ID
	 * 
	 * @return ContactId
	 */
	public ContactId getRemoteContact() {
		VideoStreamingSession session = mRichcallService.getVideoSharingSession(mSharingId);
		if (session == null) {
			return mPersistentStorage.getRemoteContact();
		}
		return session.getRemoteContact();
	}
	
	/**
	 * Returns the state of the sharing
	 * 
	 * @return State
	 * @see State
	 */
	public int getState() {
		VideoStreamingSession session = mRichcallService.getVideoSharingSession(mSharingId);
		if (session == null) {
			return mPersistentStorage.getState();
		}
		SipDialogPath dialogPath = session.getDialogPath();
		if (dialogPath != null && dialogPath.isSessionEstablished()) {
			return VideoSharing.State.STARTED;
			
		} else if (session.isInitiatedByRemote()) {
			if (session.isSessionAccepted()) {
				return VideoSharing.State.ACCEPTING;
			}
			return VideoSharing.State.INVITED;
		}
		return VideoSharing.State.INITIATING;
	}

	/**
	 * Returns the reason code of the state of the video sharing
	 *
	 * @return ReasonCode
	 * @see ReasonCode
	 */
	public int getReasonCode() {
		VideoStreamingSession session = mRichcallService.getVideoSharingSession(mSharingId);
		if (session == null) {
			return mPersistentStorage.getReasonCode();
		}
		return ReasonCode.UNSPECIFIED;
	}
	
	/**
	 * Returns the direction of the sharing (incoming or outgoing)
	 * 
	 * @return Direction
	 * @see Direction
	 */
	public int getDirection() {
		VideoStreamingSession session = mRichcallService.getVideoSharingSession(mSharingId);
		if (session == null) {
			return mPersistentStorage.getDirection();
		}
		if (session.isInitiatedByRemote()) {
			return Direction.INCOMING;
		}
		return Direction.OUTGOING;
	}
	
	/**
	 * Accepts video sharing invitation
	 * 
	 * @param player Video player
	 */
	public void acceptInvitation(IVideoPlayer player) {
		if (logger.isActivated()) {
			logger.info("Accept session invitation");
		}
		final VideoStreamingSession session = mRichcallService.getVideoSharingSession(mSharingId);
		if (session == null) {
			/*
			 * TODO: Throw correct exception as part of CR037 implementation
			 */
			throw new IllegalStateException("No session with sharing ID:".concat(mSharingId));
		}
		// Set the video player
		session.setPlayer(player);
		
		// Accept invitation
        new Thread() {
    		public void run() {
    			session.acceptSession();
    		}
    	}.start();
	}
	
	/**
	 * Rejects video sharing invitation
	 */
	public void rejectInvitation() {
		if (logger.isActivated()) {
			logger.info("Reject session invitation");
		}
		final VideoStreamingSession session = mRichcallService.getVideoSharingSession(mSharingId);
		if (session == null) {
			/*
			 * TODO: Throw correct exception as part of CR037 implementation
			 */
			throw new IllegalStateException("No session with sharing ID:".concat(mSharingId));
		}
		// Reject invitation
        new Thread() {
    		public void run() {
    			session.rejectSession(Response.DECLINE);
    		}
    	}.start();
	}

	/**
	 * Aborts the sharing
	 */
	public void abortSharing() {
		if (logger.isActivated()) {
			logger.info("Cancel session");
		}
		final VideoStreamingSession session = mRichcallService.getVideoSharingSession(mSharingId);
		if (session == null) {
			/*
			 * TODO: Throw correct exception as part of CR037 implementation
			 */
			throw new IllegalStateException("No session with sharing ID:".concat(mSharingId));
		}
		// Abort the session
        new Thread() {
    		public void run() {
    			session.abortSession(ImsServiceSession.TERMINATION_BY_USER);
    		}
    	}.start();	
	}
	
	/**
	 * Return the video encoding (eg. H.264)
	 * 
	 * @return Encoding
	 */
	public String getVideoEncoding() {
		final VideoStreamingSession session = mRichcallService.getVideoSharingSession(mSharingId);
		if (session == null) {
			return mPersistentStorage.getVideoEncoding();
		}
		try {
			IVideoPlayer player = session.getPlayer();
			try {
				return player.getCodec().getEncoding();
			} catch (RemoteException e) {
				/*
				 * Here we just log error and do not re-throw exception via AIDL because the problem
				 * occurred on the binding connection to the service.
				 */
				if (logger.isActivated()) {
					logger.error("Cannot get video codec from external player", e);
				}
				return null;
			}
		} catch (Exception e) {
			// TODO to be adjusted with CR037 on exceptions
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Returns the local timestamp of when the video sharing was initiated for outgoing
	 * video sharing or the local timestamp of when the video sharing invitation was received
	 * for incoming video sharings.
	 *  
	 * @return Timestamp in milliseconds
	 */
	public long getTimestamp() {
		final VideoStreamingSession session = mRichcallService.getVideoSharingSession(mSharingId);
		if (session == null) {
			return mPersistentStorage.getTimestamp();
		}
		return session.getTimestamp();
	}

	/**
	 * Returns the duration of the video sharing
	 * 
	 * @return Duration in milliseconds
	 */
	public long getDuration() {
		final VideoStreamingSession session = mRichcallService.getVideoSharingSession(mSharingId);
		if (session == null) {
			return mPersistentStorage.getDuration();
		}
		return System.currentTimeMillis() - session.getTimestamp();
	}	

	/** 		
	 * Returns the video descriptor 		
	 * 		
	 * @return Video descriptor 		
	 * @see VideoDescriptor 		
	 */ 		
	public VideoDescriptor getVideoDescriptor() {
		final VideoStreamingSession session = mRichcallService.getVideoSharingSession(mSharingId);
		if (session == null) {
			return mPersistentStorage.getVideoDescriptor();
		}
		try {
			IVideoPlayer player = session.getPlayer();
			try {
				VideoCodec codec = player.getCodec();
				return new VideoDescriptor(codec.getWidth(), codec.getHeight());
			} catch (RemoteException e) {
				/*
				 * Here we just log error and do not re-throw exception via AIDL because the problem
				 * occurred on the binding connection to the service.
				 */
				if (logger.isActivated()) {
					logger.error("Cannot get video codec from external player", e);
				}
				return null;
			}
		} catch (Exception e) {
			// TODO to be adjusted with CR037 on exceptions
			throw new IllegalStateException(e);
		}
	}
	
    /*------------------------------- SESSION EVENTS ----------------------------------*/

	/**
	 * Session is started
	 */
	public void handleSessionStarted() {
		if (logger.isActivated()) {
			logger.info("Session started");
		}
		synchronized (mLock) {
			mPersistentStorage.setStateReasonCodeAndDuration(VideoSharing.State.STARTED,
					ReasonCode.UNSPECIFIED, getCurrentDuration());

			mBroadcaster.broadcastStateChanged(getRemoteContact(),
					mSharingId, VideoSharing.State.STARTED, ReasonCode.UNSPECIFIED);
		}
	}

	/**
	 * Session has been aborted
	 *
	 * @param reason Termination reason
	 */
	public void handleSessionAborted(int reason) {
		if (logger.isActivated()) {
			logger.info("Session aborted, reason=".concat(String.valueOf(reason)));
		}
		long currentDuration = getCurrentDuration();
		synchronized (mLock) {
			mVideoSharingService.removeVideoSharing(mSharingId);
			VideoStreamingSession session = mRichcallService
					.getVideoSharingSession(mSharingId);
			if (session != null && session.getDialogPath().isSessionCancelled()) {
				mPersistentStorage.setStateReasonCodeAndDuration(VideoSharing.State.ABORTED,
						ReasonCode.ABORTED_BY_REMOTE, currentDuration);
				mBroadcaster.broadcastStateChanged(getRemoteContact(),
						mSharingId, VideoSharing.State.ABORTED, ReasonCode.ABORTED_BY_REMOTE);
			} else {
				int reasonCode = imsServiceSessionErrorToReasonCode(reason);
				mPersistentStorage.setStateReasonCodeAndDuration(VideoSharing.State.ABORTED,
						reasonCode, currentDuration);

				mBroadcaster.broadcastStateChanged(getRemoteContact(),
						mSharingId, VideoSharing.State.ABORTED, reasonCode);
			}
		}
	}

	/**
	 * Session has been terminated by remote
	 */
	public void handleSessionTerminatedByRemote() {
		if (logger.isActivated()) {
			logger.info("Session terminated by remote");
		}
		long currentDuration = getCurrentDuration();
		synchronized (mLock) {
			mVideoSharingService.removeVideoSharing(mSharingId);

			mPersistentStorage.setStateReasonCodeAndDuration(VideoSharing.State.ABORTED,
					ReasonCode.ABORTED_BY_REMOTE, currentDuration);

			mBroadcaster.broadcastStateChanged(getRemoteContact(),
					getSharingId(), VideoSharing.State.ABORTED, ReasonCode.ABORTED_BY_REMOTE);
		}
	}
	
	/**
	 * Content sharing error
	 *
	 * @param error Error
	 */
	public void handleSharingError(ContentSharingError error) {
		if (logger.isActivated()) {
			logger.info("Sharing error ".concat(String.valueOf(error.getErrorCode())));
		}
		VideoSharingStateAndReasonCode stateAndReasonCode = toStateAndReasonCode(error);
		int state = stateAndReasonCode.getState();
		int reasonCode = stateAndReasonCode.getReasonCode();
		long currentDuration = getCurrentDuration();
		synchronized (mLock) {
			mVideoSharingService.removeVideoSharing(mSharingId);

			mPersistentStorage.setStateReasonCodeAndDuration(state, reasonCode, currentDuration);

			mBroadcaster.broadcastStateChanged(getRemoteContact(),
					mSharingId, state, reasonCode);
		}
	}

	@Override
	public void handleSessionAccepted() {
		if (logger.isActivated()) {
			logger.info("Accepting sharing");
		}
		long currentDuration = getCurrentDuration();
		synchronized (mLock) {
			mPersistentStorage.setStateReasonCodeAndDuration(VideoSharing.State.ACCEPTING,
					ReasonCode.UNSPECIFIED,currentDuration);
			mBroadcaster.broadcastStateChanged(getRemoteContact(),
					mSharingId, VideoSharing.State.ACCEPTING, ReasonCode.UNSPECIFIED);
		}
	}
    
    /**
     * Video stream has been resized
     *
     * @param width Video width
     * @param height Video height
     */
	public void handleVideoResized(int width, int height) {
		// Not used
	}

	@Override
	public void handleSessionRejectedByUser() {
		handleSessionRejected(ReasonCode.REJECTED_BY_USER);
	}

	@Override
	public void handleSessionRejectedByTimeout() {
		handleSessionRejected(ReasonCode.REJECTED_TIME_OUT);
	}

	@Override
	public void handleSessionRejectedByRemote() {
		handleSessionRejected(ReasonCode.REJECTED_BY_REMOTE);
	}

	@Override
	public void handleSessionInvited() {
		if (logger.isActivated()) {
			logger.info("Invited to video sharing session");
		}
		VideoStreamingSession session = mRichcallService
				.getVideoSharingSession(mSharingId);
		VideoContent content = (VideoContent)session.getContent();
		synchronized (mLock) {
			mPersistentStorage.addVideoSharing(getRemoteContact(), Direction.INCOMING, content,
					VideoSharing.State.INVITED, ReasonCode.UNSPECIFIED);
		}
		mBroadcaster.broadcastInvitation(mSharingId);
	}

	@Override
	public void handle180Ringing() {
		long currentDuration = getCurrentDuration();
		synchronized (mLock) {
			mPersistentStorage.setStateReasonCodeAndDuration(VideoSharing.State.RINGING,
					ReasonCode.UNSPECIFIED, currentDuration);
			mBroadcaster.broadcastStateChanged(getRemoteContact(), mSharingId,
					VideoSharing.State.RINGING, ReasonCode.UNSPECIFIED);
		}
	}
}
