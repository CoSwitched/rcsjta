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

package com.gsma.rcs.core.ims.service.im.chat.event;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import javax2.sip.header.ExpiresHeader;
import javax2.sip.header.SubscriptionStateHeader;

import org.xml.sax.InputSource;

import com.gsma.rcs.core.Core;
import com.gsma.rcs.core.ims.ImsModule;
import com.gsma.rcs.core.ims.network.sip.SipMessageFactory;
import com.gsma.rcs.core.ims.network.sip.SipUtils;
import com.gsma.rcs.core.ims.protocol.sip.SipDialogPath;
import com.gsma.rcs.core.ims.protocol.sip.SipRequest;
import com.gsma.rcs.core.ims.protocol.sip.SipResponse;
import com.gsma.rcs.core.ims.protocol.sip.SipTransactionContext;
import com.gsma.rcs.core.ims.service.SessionAuthenticationAgent;
import com.gsma.rcs.core.ims.service.im.InstantMessagingService;
import com.gsma.rcs.core.ims.service.im.chat.ChatError;
import com.gsma.rcs.core.ims.service.im.chat.ChatSessionListener;
import com.gsma.rcs.core.ims.service.im.chat.GroupChatSession;
import com.gsma.rcs.core.ims.service.im.chat.ParticipantInfoUtils;
import com.gsma.rcs.platform.registry.RegistryFactory;
import com.gsma.rcs.provider.messaging.MessagingLog;
import com.gsma.rcs.provider.settings.RcsSettings;
import com.gsma.rcs.utils.ContactUtils;
import com.gsma.rcs.utils.PeriodicRefresher;
import com.gsma.rcs.utils.logger.Logger;
import com.gsma.services.rcs.RcsContactFormatException;
import com.gsma.services.rcs.chat.ParticipantInfo;
import com.gsma.services.rcs.chat.ParticipantInfo.Status;
import com.gsma.services.rcs.contacts.ContactId;

/**
 * Conference event subscribe manager
 * 
 * @author jexa7410
 */
public class ConferenceEventSubscribeManager extends PeriodicRefresher {
    /**
     * Last min expire period (in seconds)
     */
    private static final String REGISTRY_MIN_EXPIRE_PERIOD = "MinSubscribeConferenceEventExpirePeriod";

    /**
     * IMS module
     */
    private ImsModule mImsModule;

    /**
     * Group chat session
     */
    private GroupChatSession mSession;

    /**
     * Dialog path
     */
    private SipDialogPath mDialogPath;

    /**
     * Expire period
     */
    private int mExpirePeriod;

    /**
     * Subscription flag
     */
    private boolean mSubscribed = false;

    /**
     * Authentication agent
     */
    private SessionAuthenticationAgent mAuthenticationAgent;

    /**
     * List of connected participants
     */
    private Set<ParticipantInfo> mParticipants;

    private final RcsSettings mRcsSettings;

    /**
     * The logger
     */
    private final static Logger sLogger = Logger.getLogger(ConferenceEventSubscribeManager.class
            .getSimpleName());

    /**
     * Constructor
     * 
     * @param session Group chat session
     * @param rcsSettings
     */
    public ConferenceEventSubscribeManager(GroupChatSession session, RcsSettings rcsSettings) {
        mSession = session;
        mImsModule = session.getImsService().getImsModule();
        mAuthenticationAgent = new SessionAuthenticationAgent(mImsModule);
        // Initiate list of participants with list of invited with status UNKNOWN
        mParticipants = new HashSet<ParticipantInfo>(session.getParticipants());
        mRcsSettings = rcsSettings;

        int defaultExpirePeriod = mRcsSettings.getSubscribeExpirePeriod();
        int minExpireValue = RegistryFactory.getFactory().readInteger(REGISTRY_MIN_EXPIRE_PERIOD,
                -1);
        if ((minExpireValue != -1) && (defaultExpirePeriod < minExpireValue)) {
            mExpirePeriod = minExpireValue;
        } else {
            mExpirePeriod = defaultExpirePeriod;
        }
    }

    /**
     * Is subscribed
     * 
     * @return Boolean
     */
    public boolean isSubscribed() {
        return mSubscribed;
    }

    /**
     * Returns the presentity
     * 
     * @return Presentity
     */
    public String getPresentity() {
        return mSession.getImSessionIdentity();
    }

    /**
     * Returns the list of connected participants
     * 
     * @return List of participants
     */
    public Set<ParticipantInfo> getParticipants() {
        return mParticipants;
    }

    /**
     * Returns the dialog path of the conference subscriber
     * 
     * @return SipDialogPath
     */
    public SipDialogPath getDialogPath() {
        return mDialogPath;
    }

    /**
     * Receive a notification
     * 
     * @param notify Received notify
     */
    public void receiveNotification(SipRequest notify) {
        if (sLogger.isActivated()) {
            sLogger.debug("New conference event notification received");
        }

        // Parse XML part
        byte[] content = notify.getContentBytes();
        if (content != null) {
            try {
                InputSource pidfInput = new InputSource(new ByteArrayInputStream(content));
                ConferenceInfoParser confParser = new ConferenceInfoParser(pidfInput);
                ConferenceInfoDocument conference = confParser.getConferenceInfo();
                if (conference != null) {
                    int maxParticipants = conference.getMaxUserCount();
                    if (maxParticipants > 0) {
                        if (sLogger.isActivated()) {
                            sLogger.debug("Set max number of participants to " + maxParticipants);
                        }
                        mSession.setMaxParticipants(maxParticipants);
                    }
                    Set<ParticipantInfo> newSet = new HashSet<ParticipantInfo>();
                    if (!ConferenceInfoDocument.STATE_FULL.equalsIgnoreCase(conference.getState())) {
                        newSet = new HashSet<ParticipantInfo>(mParticipants);
                    }
                    Vector<User> users = conference.getUsers();
                    for (User user : users) {
                        ContactId contact;
                        try {
                            contact = ContactUtils.createContactId(user.getEntity());
                        } catch (RcsContactFormatException e) {
                            // Invalid entity
                            continue;
                        }

                        if (sLogger.isActivated()) {
                            sLogger.debug("Conference info notification for " + contact);
                        }

                        if (user.isMe() || contact.equals(ImsModule.IMS_USER_PROFILE.getUsername())) {
                            // By-pass me
                            continue;
                        }

                        // Get state
                        String state = user.getState();
                        String method = user.getDisconnectionMethod();
                        if (sLogger.isActivated()) {
                            sLogger.debug("User conference info: " + user);
                        }
                        if (method != null) {
                            // If there is a method then use it as a specific state
                            state = method;

                            // If session failed because declined by remote then use it as a
                            // specific state
                            if (method.equals("failed")) {
                                String reason = user.getFailureReason();
                                if ((reason != null) && reason.contains("603")) {
                                    state = User.STATE_DECLINED;
                                }
                            }
                        }

                        // Manage "pending-out" and "pending-in" status like "pending" status. See
                        // RFC 4575 dialing-in: Endpoint is
                        // dialing into the conference, not yet in the roster (probably being
                        // authenticated). dialing-out: Focus has
                        // dialed out to connect the endpoint to the conference, but the endpoint is
                        // not yet in the roster (probably
                        // being authenticated).
                        if ((state.equalsIgnoreCase("dialing-out"))
                                || (state.equalsIgnoreCase("dialing-in"))) {
                            state = User.STATE_PENDING;
                        }
                        ParticipantInfo item2add = new ParticipantInfo(contact, getStatus(state));
                        // Update the set of participants
                        ParticipantInfoUtils.addParticipant(newSet, item2add);
                        // Check if original set has changed
                        if (mParticipants.contains(item2add) == false) {
                            // Notify session listeners
                            for (int j = 0; j < mSession.getListeners().size(); j++) {
                                ((ChatSessionListener) mSession.getListeners().get(j))
                                        .handleConferenceEvent(contact, user.getDisplayName(),
                                                state);
                            }
                        }
                    }
                    if (mSession instanceof GroupChatSession
                            && newSet.equals(mParticipants) == false) {
                        // Update the set of participants of the terminating group chat session
                        UpdateSessionParticipantSet(newSet);
                    }
                }
            } catch (Exception e) {
                if (sLogger.isActivated()) {
                    sLogger.error("Can't parse XML notification", e);
                }
            }
        }

        // Check subscription state
        SubscriptionStateHeader stateHeader = (SubscriptionStateHeader) notify
                .getHeader(SubscriptionStateHeader.NAME);
        if ((stateHeader != null) && stateHeader.getState().equalsIgnoreCase("terminated")) {
            if (sLogger.isActivated()) {
                sLogger.info("Conference event subscription has been terminated by server");
            }
            terminatedByServer();
        }
    }

    /**
     * Update the set of participants of the group chat session to be aligned with the provider
     * content
     * 
     * @param newSet the new set of participants
     */
    private void UpdateSessionParticipantSet(final Set<ParticipantInfo> newSet) {
        // Save old set of participants
        Set<ParticipantInfo> oldSet = mParticipants;
        mParticipants = newSet;
        // Update provider
        MessagingLog.getInstance().updateGroupChatParticipant(mSession.getContributionID(),
                mParticipants);
        // Notify participant status change. Make a copy of new set.
        Set<ParticipantInfo> workSet = new HashSet<ParticipantInfo>(newSet);
        // Notify status change for the new set
        workSet.removeAll(oldSet);
        for (ParticipantInfo item : workSet) {
            for (int i = 0; i < mSession.getListeners().size(); i++) {
                ((ChatSessionListener) mSession.getListeners().get(i))
                        .handleParticipantStatusChanged(item);
            }
        }
    }

    /**
     * Check if the received notification if for this subscriber
     * 
     * @param notify
     * @return Boolean
     */
    public boolean isNotifyForThisSubscriber(SipRequest notify) {
        boolean result = false;
        if ((mDialogPath != null) && notify.getCallId().equals(mDialogPath.getCallId())) {
            result = true;
        }
        return result;
    }

    /**
     * Subscription has been terminated by server
     */
    public synchronized void terminatedByServer() {
        if (!mSubscribed) {
            // Already unsubscribed
            return;
        }

        if (sLogger.isActivated()) {
            sLogger.info("Subscription has been terminated by server");
        }

        // Stop periodic subscription
        stopTimer();

        // Reset dialog path attributes
        resetDialogPath();

        // Force subscription flag to false
        mSubscribed = false;
    }

    /**
     * Terminate manager
     */
    public void terminate() {
        if (sLogger.isActivated()) {
            sLogger.info("Terminate the subscribe manager");
        }

        // Stop periodic subscription
        stopTimer();

        // Unsubscribe before to quit
        if ((mImsModule.getCurrentNetworkInterface() != null)
                && mImsModule.getCurrentNetworkInterface().isRegistered() && mSubscribed) {
            unSubscribe();
        }

        if (sLogger.isActivated()) {
            sLogger.info("Subscribe manager is terminated");
        }
    }

    /**
     * Create a SUBSCRIBE request
     * 
     * @param dialog SIP dialog path
     * @param expirePeriod Expiration period
     * @return SIP request
     * @throws Exception
     */
    private SipRequest createSubscribe(SipDialogPath dialog, int expirePeriod) throws Exception {
        // Create SUBSCRIBE message
        SipRequest subscribe = SipMessageFactory.createSubscribe(dialog, expirePeriod);

        // Set feature tags
        SipUtils.setFeatureTags(subscribe, InstantMessagingService.CHAT_FEATURE_TAGS);

        // Set the Event header
        subscribe.addHeader("Event", "conference");

        // Set the Accept header
        subscribe.addHeader("Accept", "application/conference-info+xml");

        return subscribe;
    }

    /**
     * Subscription refresh processing
     */
    public void periodicProcessing() {
        // Make a subscribe
        if (sLogger.isActivated()) {
            sLogger.info("Execute re-subscribe");
        }

        // Send SUBSCRIBE request
        subscribe();
    }

    /**
     * Subscribe
     */
    public synchronized void subscribe() {
        new Thread() {
            public void run() {
                if (sLogger.isActivated()) {
                    sLogger.info("Subscribe to " + getPresentity());
                }

                try {
                    // Create a dialog path if necessary
                    if (mDialogPath == null) {
                        // Set Call-Id
                        String callId = mImsModule.getSipManager().getSipStack().generateCallId();

                        // Set target
                        String target = getPresentity();

                        // Set local party
                        String localParty = ImsModule.IMS_USER_PROFILE.getPublicUri();

                        // Set remote party
                        String remoteParty = getPresentity();

                        // Set the route path
                        Vector<String> route = mImsModule.getSipManager().getSipStack()
                                .getServiceRoutePath();

                        // Create a dialog path
                        mDialogPath = new SipDialogPath(mImsModule.getSipManager().getSipStack(),
                                callId, 1, target, localParty, remoteParty, route, mRcsSettings);
                    } else {
                        // Increment the Cseq number of the dialog path
                        mDialogPath.incrementCseq();
                    }

                    // Create a SUBSCRIBE request
                    SipRequest subscribe = createSubscribe(mDialogPath, mExpirePeriod);

                    // Send SUBSCRIBE request
                    sendSubscribe(subscribe);

                } catch (Exception e) {
                    if (sLogger.isActivated()) {
                        sLogger.error("Subscribe has failed", e);
                    }
                    handleError(new ChatError(ChatError.UNEXPECTED_EXCEPTION, e.getMessage()));
                }
            }
        }.start();
    }

    /**
     * Unsubscribe
     */
    public synchronized void unSubscribe() {
        if (!mSubscribed) {
            // Already unsubscribed
            return;
        }

        if (sLogger.isActivated()) {
            sLogger.info("Unsubscribe to " + getPresentity());
        }

        try {
            // Stop periodic subscription
            stopTimer();

            // Increment the Cseq number of the dialog path

            mDialogPath.incrementCseq();

            // Create a SUBSCRIBE with expire 0
            SipRequest subscribe = createSubscribe(mDialogPath, 0);

            // Send SUBSCRIBE request
            sendSubscribe(subscribe);

        } catch (Exception e) {
            if (sLogger.isActivated()) {
                sLogger.error("UnSubscribe has failed", e);
            }
        }

        // Force subscription flag to false
        mSubscribed = false;

        // Reset dialog path attributes
        resetDialogPath();
    }

    /**
     * Reset the dialog path
     */
    private void resetDialogPath() {
        if (mDialogPath != null) {
            Core.getInstance().getImService()
                    .removeGroupChatConferenceSubscriber(mDialogPath.getCallId());
            mDialogPath = null;
        }
    }

    /**
     * Retrieve the expire period
     * 
     * @param resp SIP response
     */
    private void retrieveExpirePeriod(SipResponse response) {
        // Extract expire value from Expires header
        ExpiresHeader expiresHeader = (ExpiresHeader) response.getHeader(ExpiresHeader.NAME);
        if (expiresHeader != null) {
            int expires = expiresHeader.getExpires();
            if (expires != -1) {
                mExpirePeriod = expires;
            }
        }
    }

    /**
     * Send SUBSCRIBE message
     * 
     * @param subscribe SIP SUBSCRIBE
     * @throws Exception
     */
    private void sendSubscribe(SipRequest subscribe) throws Exception {
        if (sLogger.isActivated()) {
            sLogger.info("Send SUBSCRIBE, expire=" + subscribe.getExpires());
        }

        if (mSubscribed) {
            // Set the Authorization header
            mAuthenticationAgent.setProxyAuthorizationHeader(subscribe);
        }

        // Send SUBSCRIBE request
        SipTransactionContext ctx = mImsModule.getSipManager().sendSipMessageAndWait(subscribe);

        // Analyze the received response
        if (ctx.isSipResponse()) {
            // A response has been received
            if (ctx.getStatusCode() == 200) {
                if (subscribe.getExpires() != 0) {
                    handle200OK(ctx);
                } else {
                    handle200OkUnsubscribe(ctx);
                }
            } else if (ctx.getStatusCode() == 202) {
                // 202 Accepted
                handle200OK(ctx);
            } else if (ctx.getStatusCode() == 407) {
                // 407 Proxy Authentication Required
                handle407Authentication(ctx);
            } else if (ctx.getStatusCode() == 423) {
                // 423 Interval Too Brief
                handle423IntervalTooBrief(ctx);
            } else {
                // Other error response
                handleError(new ChatError(ChatError.SUBSCRIBE_CONFERENCE_FAILED,
                        ctx.getStatusCode() + " " + ctx.getReasonPhrase()));
            }
        } else {
            if (sLogger.isActivated()) {
                sLogger.debug("No response received for SUBSCRIBE");
            }

            // No response received: timeout
            handleError(new ChatError(ChatError.SUBSCRIBE_CONFERENCE_FAILED));
        }
    }

    /**
     * Handle 200 0K response
     * 
     * @param ctx SIP transaction context
     */
    private void handle200OK(SipTransactionContext ctx) {
        // 200 OK response received
        if (sLogger.isActivated()) {
            sLogger.info("200 OK response received");
        }
        mSubscribed = true;

        SipResponse resp = ctx.getSipResponse();

        // Set the route path with the Record-Route header
        Vector<String> newRoute = SipUtils.routeProcessing(resp, true);
        mDialogPath.setRoute(newRoute);

        // Set the remote tag
        mDialogPath.setRemoteTag(resp.getToTag());

        // Set the target
        mDialogPath.setTarget(resp.getContactURI());

        // Set the Proxy-Authorization header
        mAuthenticationAgent.readProxyAuthenticateHeader(resp);

        // Retrieve the expire value in the response
        retrieveExpirePeriod(resp);

        // Start the periodic subscribe
        startTimer(mExpirePeriod, 0.5);

        Core.getInstance().getImService()
                .addGroupChatConferenceSubscriber(mDialogPath.getCallId(), mSession);
    }

    /**
     * Handle 200 0K response of UNSUBSCRIBE
     * 
     * @param ctx SIP transaction context
     */
    private void handle200OkUnsubscribe(SipTransactionContext ctx) {
        // 200 OK response received
        if (sLogger.isActivated()) {
            sLogger.info("200 OK response received");
        }
    }

    /**
     * Handle 407 response
     * 
     * @param ctx SIP transaction context
     * @throws Exception
     */
    private void handle407Authentication(SipTransactionContext ctx) throws Exception {
        // 407 response received
        if (sLogger.isActivated()) {
            sLogger.info("407 response received");
        }

        SipResponse resp = ctx.getSipResponse();

        // Set the Proxy-Authorization header
        mAuthenticationAgent.readProxyAuthenticateHeader(resp);

        // Increment the Cseq number of the dialog path
        mDialogPath.incrementCseq();

        // Create a second SUBSCRIBE request with the right token
        if (sLogger.isActivated()) {
            sLogger.info("Send second SUBSCRIBE");
        }
        SipRequest subscribe = createSubscribe(mDialogPath, ctx.getTransaction().getRequest()
                .getExpires().getExpires());

        // Set the Authorization header
        mAuthenticationAgent.setProxyAuthorizationHeader(subscribe);

        // Send SUBSCRIBE request
        sendSubscribe(subscribe);
    }

    /**
     * Handle 423 response
     * 
     * @param ctx SIP transaction context
     * @throws Exception
     */
    private void handle423IntervalTooBrief(SipTransactionContext ctx) throws Exception {
        // 423 response received
        if (sLogger.isActivated()) {
            sLogger.info("423 interval too brief response received");
        }

        SipResponse resp = ctx.getSipResponse();

        // Increment the Cseq number of the dialog path
        mDialogPath.incrementCseq();

        // Extract the Min-Expire value
        int minExpire = SipUtils.getMinExpiresPeriod(resp);
        if (minExpire == -1) {
            if (sLogger.isActivated()) {
                sLogger.error("Can't read the Min-Expires value");
            }
            handleError(new ChatError(ChatError.SUBSCRIBE_CONFERENCE_FAILED,
                    "No Min-Expires value found"));
            return;
        }

        // Save the min expire value in the terminal registry
        RegistryFactory.getFactory().writeInteger(REGISTRY_MIN_EXPIRE_PERIOD, minExpire);

        // Set the default expire value
        mExpirePeriod = minExpire;

        // Create a new SUBSCRIBE request with the right expire period
        SipRequest subscribe = createSubscribe(mDialogPath, mExpirePeriod);

        // Set the Authorization header
        mAuthenticationAgent.setProxyAuthorizationHeader(subscribe);

        // Send SUBSCRIBE request
        sendSubscribe(subscribe);
    }

    /**
     * Handle error response
     * 
     * @param error Error
     */
    private void handleError(ChatError error) {
        // Error
        if (sLogger.isActivated()) {
            sLogger.info("Subscribe has failed: " + error.getErrorCode() + ", reason="
                    + error.getMessage());
        }
        mSubscribed = false;

        // Subscribe has failed, stop the periodic subscribe
        stopTimer();

        // Reset dialog path attributes
        resetDialogPath();
    }

    /**
     * Convert the status into integer
     * 
     * @param status the string status
     * @return the integer status
     */
    private static int getStatus(final String status) {
        if (status == null || status.equals(User.STATE_UNKNOWN)) {
            return Status.UNKNOWN;
        }
        if (status.equals(User.STATE_CONNECTED)) {
            return Status.CONNECTED;
        }
        if (status.equals(User.STATE_DISCONNECTED)) {
            return Status.DISCONNECTED;
        }
        if (status.equals(User.STATE_DEPARTED)) {
            return Status.DEPARTED;
        }
        if (status.equals(User.STATE_BOOTED)) {
            return Status.BOOTED;
        }
        if (status.equals(User.STATE_FAILED)) {
            return Status.FAILED;
        }
        if (status.equals(User.STATE_BUSY)) {
            return Status.BUSY;
        }
        if (status.equals(User.STATE_DECLINED)) {
            return Status.DECLINED;
        }
        if (status.equals(User.STATE_PENDING)) {
            return Status.PENDING;
        }
        return Status.UNKNOWN;
    }
}
