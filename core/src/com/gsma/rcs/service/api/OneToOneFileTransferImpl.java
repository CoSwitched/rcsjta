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

package com.gsma.rcs.service.api;

import com.gsma.rcs.core.content.MmContent;
import com.gsma.rcs.core.ims.protocol.sip.SipDialogPath;
import com.gsma.rcs.core.ims.service.ImsServiceSession.TerminationReason;
import com.gsma.rcs.core.ims.service.im.InstantMessagingService;
import com.gsma.rcs.core.ims.service.im.filetransfer.FileSharingError;
import com.gsma.rcs.core.ims.service.im.filetransfer.FileSharingSession;
import com.gsma.rcs.core.ims.service.im.filetransfer.FileSharingSessionListener;
import com.gsma.rcs.core.ims.service.im.filetransfer.FileTransferPersistedStorageAccessor;
import com.gsma.rcs.core.ims.service.im.filetransfer.FileTransferUtils;
import com.gsma.rcs.core.ims.service.im.filetransfer.ImsFileSharingSession;
import com.gsma.rcs.core.ims.service.im.filetransfer.http.HttpFileTransferSession;
import com.gsma.rcs.core.ims.service.im.filetransfer.http.HttpTransferState;
import com.gsma.rcs.core.ims.service.im.filetransfer.http.ResumeDownloadFileSharingSession;
import com.gsma.rcs.core.ims.service.im.filetransfer.http.ResumeUploadFileSharingSession;
import com.gsma.rcs.provider.fthttp.FtHttpResume;
import com.gsma.rcs.provider.fthttp.FtHttpResumeDownload;
import com.gsma.rcs.provider.fthttp.FtHttpResumeUpload;
import com.gsma.rcs.provider.messaging.FileTransferStateAndReasonCode;
import com.gsma.rcs.provider.settings.RcsSettings;
import com.gsma.rcs.service.broadcaster.IOneToOneFileTransferBroadcaster;
import com.gsma.rcs.utils.logger.Logger;
import com.gsma.services.rcs.RcsService.Direction;
import com.gsma.services.rcs.contact.ContactId;
import com.gsma.services.rcs.filetransfer.FileTransfer.ReasonCode;
import com.gsma.services.rcs.filetransfer.FileTransfer.State;
import com.gsma.services.rcs.filetransfer.IFileTransfer;

import android.database.SQLException;
import android.net.Uri;

import javax2.sip.message.Response;

/**
 * File transfer implementation
 * 
 * @author Jean-Marc AUFFRET
 */
public class OneToOneFileTransferImpl extends IFileTransfer.Stub implements
        FileSharingSessionListener {

    private final String mFileTransferId;

    private final IOneToOneFileTransferBroadcaster mBroadcaster;

    private final InstantMessagingService mImService;

    private final FileTransferPersistedStorageAccessor mPersistentStorage;

    private final FileTransferServiceImpl mFileTransferService;

    private final RcsSettings mRcsSettings;

    /**
     * Lock used for synchronization
     */
    private final Object lock = new Object();

    /**
     * The logger
     */
    private final Logger logger = Logger.getLogger(getClass().getName());

    /**
     * Constructor
     * 
     * @param transferId Transfer ID
     * @param broadcaster IOneToOneFileTransferBroadcaster
     * @param imService InstantMessagingService
     * @param persistentStorage FileTransferPersistedStorageAccessor
     * @param fileTransferService FileTransferServiceImpl
     * @param rcsSettings RcsSettings
     */
    public OneToOneFileTransferImpl(String transferId,
            IOneToOneFileTransferBroadcaster broadcaster, InstantMessagingService imService,
            FileTransferPersistedStorageAccessor persistentStorage,
            FileTransferServiceImpl fileTransferService, RcsSettings rcsSettings) {
        mFileTransferId = transferId;
        mBroadcaster = broadcaster;
        mImService = imService;
        mPersistentStorage = persistentStorage;
        mFileTransferService = fileTransferService;
        mRcsSettings = rcsSettings;
    }

    private State getRcsState(FileSharingSession session) {
        if (session instanceof HttpFileTransferSession) {
            int state = ((HttpFileTransferSession)session).getSessionState();
            if (state == HttpTransferState.ESTABLISHED) {
                if (isSessionPaused()) {
                    return State.PAUSED;
                }

                return State.STARTED;
            }
        } else if (session instanceof ImsFileSharingSession) {
            SipDialogPath dialogPath = session.getDialogPath();
            if (dialogPath != null && dialogPath.isSessionEstablished()) {
                return State.STARTED;
            }
        } else {
            throw new IllegalArgumentException("Unsupported Filetransfer session type.");
        }
        if (session.isInitiatedByRemote()) {
            if (session.isSessionAccepted()) {
                return State.ACCEPTING;
            }
            return State.INVITED;
        }
        return State.INITIATING;
    }

    private ReasonCode getRcsReasonCode(FileSharingSession session) {
        if (isSessionPaused()) {
            /*
             * If session is paused and still established it must have been
             * paused by user
             */
            return ReasonCode.PAUSED_BY_USER;
        }
        return ReasonCode.UNSPECIFIED;
    }

    /**
     * Returns the chat ID of the file transfer
     * 
     * @return Transfer ID
     */
    public String getChatId() {
        // For 1-1 file transfer, chat ID corresponds to the formatted contact number
        return getRemoteContact().toString();
    }

    /**
     * Returns the file transfer ID of the file transfer
     * 
     * @return Transfer ID
     */
    public String getTransferId() {
        return mFileTransferId;
    }

    /**
     * Returns the remote contact identifier
     * 
     * @return ContactId
     */
    public ContactId getRemoteContact() {
        FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
        if (session == null) {
            return mPersistentStorage.getRemoteContact();
        }
        return session.getRemoteContact();
    }

    /**
     * Returns the complete filename including the path of the file to be transferred
     * 
     * @return Filename
     */
    public String getFileName() {
        FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
        if (session == null) {
            return mPersistentStorage.getFileName();
        }
        return session.getContent().getName();
    }

    /**
     * Returns the Uri of the file to be transferred
     * 
     * @return Filename
     */
    public Uri getFile() {
        FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
        if (session == null) {
            return mPersistentStorage.getFile();
        }
        return session.getContent().getUri();
    }

    /**
     * Returns the size of the file to be transferred
     * 
     * @return Size in bytes
     */
    public long getFileSize() {
        FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
        if (session == null) {
            return mPersistentStorage.getFileSize();
        }
        return session.getContent().getSize();
    }

    /**
     * Returns the MIME type of the file to be transferred
     * 
     * @return Type
     */
    public String getMimeType() {
        FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
        if (session == null) {
            return mPersistentStorage.getMimeType();
        }
        return session.getContent().getEncoding();
    }

    /**
     * Returns the Uri of the file icon
     * 
     * @return Uri
     */
    public Uri getFileIcon() {
        FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
        if (session == null) {
            return mPersistentStorage.getFileIcon();
        }
        MmContent fileIcon = session.getContent();
        return fileIcon != null ? fileIcon.getUri() : null;
    }

    /**
     * Returns the Mime type of file icon
     * 
     * @return Mime type
     */
    public String getFileIconMimeType() {
        FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
        if (session == null) {
            return mPersistentStorage.getFileIconMimeType();
        }
        MmContent fileIconMimeType = session.getContent();
        return fileIconMimeType != null ? fileIconMimeType.getEncoding() : null;
    }

    /**
     * Returns the state of the file transfer
     * 
     * @return State
     */
    public int getState() {
        FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
        if (session == null) {
            return mPersistentStorage.getState().toInt();
        }
        return getRcsState(session).toInt();
    }

    /**
     * Returns the reason code of the state of the file transfer
     * 
     * @return ReasonCode
     */
    public int getReasonCode() {
        FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
        if (session == null) {
            return mPersistentStorage.getReasonCode().toInt();
        }
        return getRcsReasonCode(session).toInt();
    }

    /**
     * Returns the direction of the transfer (incoming or outgoing)
     * 
     * @return Direction
     * @see Direction
     */
    public int getDirection() {
        FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
        if (session == null) {
            return mPersistentStorage.getDirection().toInt();
        }
        if (session.isInitiatedByRemote()) {
            return Direction.INCOMING.toInt();
        }
        return Direction.OUTGOING.toInt();
    }

    public long getTimestamp() {
        return mPersistentStorage.getTimestamp();
    }

    public long getTimestampSent() {
        return mPersistentStorage.getTimestampSent();
    }

    public long getTimestampDelivered() {
        return mPersistentStorage.getTimestampDelivered();
    }

    public long getTimestampDisplayed() {
        return mPersistentStorage.getTimestampDisplayed();
    }

    /**
     * Accepts file transfer invitation
     */
    public void acceptInvitation() {
        if (logger.isActivated()) {
            logger.info("Accept session invitation");
        }
        final FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
        if (session == null) {
            /*
             * TODO: Throw correct exception as part of CR037 implementation
             */
            throw new IllegalStateException("Session with file transfer ID '" + mFileTransferId
                    + "' not available.");
        }

        // Accept invitation
        new Thread() {
            public void run() {
                session.acceptSession();
            }
        }.start();
    }

    /**
     * Rejects file transfer invitation
     */
    public void rejectInvitation() {
        if (logger.isActivated()) {
            logger.info("Reject session invitation");
        }
        final FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
        if (session == null) {
            /*
             * TODO: Throw correct exception as part of CR037 implementation
             */
            throw new IllegalStateException("Session with file transfer ID '" + mFileTransferId
                    + "' not available.");
        }

        // Reject invitation
        new Thread() {
            public void run() {
                session.rejectSession(Response.DECLINE);
            }
        }.start();
    }

    /**
     * Aborts the file transfer
     */
    public void abortTransfer() {
        if (logger.isActivated()) {
            logger.info("Cancel session");
        }
        final FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
        if (session == null) {
            /*
             * TODO: Throw correct exception as part of CR037 implementation
             */
            throw new IllegalStateException("Session with file transfer ID '" + mFileTransferId
                    + "' not available.");
        }
        if (session.isFileTransfered()) {
            // File already transferred and session automatically closed after transfer
            return;
        }
        // Abort the session
        new Thread() {
            public void run() {
                session.abortSession(TerminationReason.TERMINATION_BY_USER);
            }
        }.start();
    }

    /**
     * Is HTTP transfer
     * 
     * @return Boolean
     */
    public boolean isHttpTransfer() {
        FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
        if (session == null) {
            /*
             * TODO: Throw correct exception as part of CR037 implementation
             */
            throw new IllegalStateException(
                    "Unable to check if it is HTTP transfer since session with file transfer ID '"
                            + mFileTransferId + "' not available.");
        }

        return (session instanceof HttpFileTransferSession);
    }

    /**
     * Returns true if it is possible to pause this file transfer right now, else returns false. If
     * this filetransfer corresponds to a file transfer that is no longer present in the persistent
     * storage false will be returned (this is no error)
     * 
     * @return boolean
     */
    public boolean isAllowedToPauseTransfer() {
        FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
        if (session == null) {
            if (logger.isActivated()) {
                logger.debug(new StringBuilder("Cannot pause transfer with file transfer Id '")
                .append(mFileTransferId)
                .append("' as there is no ongoing session corresponding to the fileTransferId.")
                .toString());
    }
            return false;
        }
        if (!session.isHttpTransfer()) {
            if (logger.isActivated()) {
                logger.debug(new StringBuilder("Cannot pause transfer with file transfer Id '")
                        .append(mFileTransferId).append("' as it is not a HTTP File transfer.")
                        .toString());
            }
            return false;
        }
        State state = getRcsState(session);
        if (State.STARTED != state) {
            if (logger.isActivated()) {
                logger.debug(new StringBuilder("Cannot pause transfer with file transfer Id '")
                        .append(mFileTransferId).append("' as it is in state ").append(state)
                        .toString());
            }
            return false;
        }
        return true;
    }

    /**
     * Pauses the file transfer (only for HTTP transfer)
     */
    public void pauseTransfer() {
        if (logger.isActivated()) {
            logger.info("Pause session");
        }
        FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
        if (session == null) {
            /*
             * TODO: Throw correct exception as part of CR037 implementation
             */
            throw new IllegalStateException(
                    "Unable to pause transfer since session with file transfer ID '"
                            + mFileTransferId + "' not available.");
        }
        if (!isHttpTransfer()) {
            if (logger.isActivated()) {
                logger.info("Pause available only for HTTP transfer");
            }
            return;
        }
        State state = getRcsState(session);
        if (State.STARTED != state) {
            if (logger.isActivated()) {
                logger.debug(new StringBuilder("Cannot pause transfer with file transfer Id '")
                        .append(mFileTransferId).append("' as it is in state ").append(state)
                        .toString());
            }
            /*
             * TODO: Throw correct exception as part of CR037 implementation
             */
            throw new IllegalStateException("Session not in STARTED state.");
        }
        ((HttpFileTransferSession) session).pauseFileTransfer();
    }

    /**
     * Checks if transfer is paused (only for HTTP transfer)
     * 
     * @return True if transfer is paused
     */
    public boolean isSessionPaused() {
        FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
        if (session == null) {
            /*
             * TODO: Throw correct exception as part of CR037 implementation
             */
            throw new IllegalStateException(
                    "Unable to check if transfer is paused since session with file transfer ID '"
                            + mFileTransferId + "' not available.");
        }
        if (!isHttpTransfer()) {
            if (logger.isActivated()) {
                logger.info("Pause available only for HTTP transfer");
            }
            return false;
        }
        return ((HttpFileTransferSession) session).isFileTransferPaused();
    }

    /**
     * Returns true if it is possible to resume this file transfer right now, else return false. If
     * this filetransfer corresponds to a file transfer that is no longer present in the persistent
     * storage false will be returned.
     * 
     * @return boolean
     */
    public boolean isAllowedToResumeTransfer() {
        ReasonCode reasonCode;
        FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
        if (session != null) {
            reasonCode = getRcsReasonCode(session);
        } else {
            try {
                reasonCode = mPersistentStorage.getReasonCode();
            } catch (SQLException e) {
                if (logger.isActivated()) {
                    logger.debug(new StringBuilder("Cannot resume transfer with file transfer Id '")
                            .append(mFileTransferId).append("' as it does not exist in DB.")
                            .toString());
                }
                return false;
            }
        }
        if (ReasonCode.PAUSED_BY_USER != reasonCode) {
            if (logger.isActivated()) {
                logger.debug(new StringBuilder("Cannot resume transfer with file transfer Id '")
                        .append(mFileTransferId).append("' as it is ").append(reasonCode)
                        .toString());
            }
            return false;
        }
        return true;
    }

    /**
     * Resume the session (only for HTTP transfer)
     */
    public void resumeTransfer() {
        FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
        if (session == null) {
            if (ReasonCode.PAUSED_BY_USER != mPersistentStorage.getReasonCode()) {
                /*
                 * TODO: Throw correct exception as part of CR037 implementation
                 */
                throw new IllegalStateException(
                        "Unable to resume transfer with file transfer ID '"
                                + mFileTransferId + "' as it is not in PAUSED state.");
            }
            if (!ServerApiUtils.isImsConnected()) {
                /*
                 * TODO: Throw correct exception as part of CR037 implementation
                 */
                throw new IllegalStateException(
                        "Unable to resume transfer with file transfer ID '"
                                + mFileTransferId + "' as there is no IMS connection.");
            }
            if (!mImService.isFileTransferSessionAvailable()) {
                /*
                 * TODO: Throw correct exception as part of CR037 implementation
                 */
                throw new IllegalStateException(
                        "Unable to resume transfer with file transfer ID '"
                                + mFileTransferId
                                + "' as there is no available file transfer session.");
            }
            FtHttpResume resume = mPersistentStorage.getFileTransferResumeInfo();
            if (Direction.OUTGOING == mPersistentStorage.getDirection()) {
                if (mImService.isMaxConcurrentOutgoingFileTransfersReached()) {
                    /*
                     * TODO: Throw correct exception as part of CR037 implementation
                     */
                    throw new IllegalStateException(
                            "Unable to resume transfer with file transfer ID '"
                                    + mFileTransferId
                                    + "' as the limit of maximum concurrent outgoing file transfers is reached.");
                }
                session = new ResumeUploadFileSharingSession(
                        mImService, FileTransferUtils.createMmContent(resume.getFile()),
                        (FtHttpResumeUpload) resume, mRcsSettings);
            } else {
                session = new ResumeDownloadFileSharingSession(
                        mImService, FileTransferUtils.createMmContent(resume.getFile()),
                        (FtHttpResumeDownload) resume, mRcsSettings);
            }
            session.addListener(this);
            session.startSession();
            return;
        }
        boolean fileSharingSessionPaused = isSessionPaused();
        boolean fileTransferOverHttp = isHttpTransfer();
        if (logger.isActivated()) {
            logger.info("Resuming session paused=" + fileSharingSessionPaused + " http="
                    + fileTransferOverHttp);
        }

        if (!(fileTransferOverHttp && fileSharingSessionPaused)) {
            if (logger.isActivated()) {
                logger.info("Resuming can only be used on a paused HTTP transfer");
            }
            return;
        }

        ((HttpFileTransferSession) session).resumeFileTransfer();
    }

    /**
     * Returns whether you can resend the transfer.
     * 
     * @return boolean
     */
    public boolean isAllowedToResendTransfer() {
        FileSharingSession session = mImService.getFileSharingSession(mFileTransferId);
        if (session != null) {
            if (logger.isActivated()) {
                logger.debug(new StringBuilder("Cannot resend transfer with fileTransferId ")
                        .append(mFileTransferId)
                        .append(" as there is already an ongoing session corresponding to this fileTransferId")
                        .toString());
            }
            return false;
        }
        State rcsState = mPersistentStorage.getState();
        /*
         * According to Blackbird PDD v3.0, "When a File Transfer is interrupted by sender
         * interaction (or fails), then ‘resend button’ shall be offered to allow the user to
         * re-send the file without selecting a new receiver or selecting the file again."
         */
        switch (rcsState) {
            case FAILED:
                return true;
            case ABORTED:
                ReasonCode rcsReasonCode = mPersistentStorage.getReasonCode();
                switch (rcsReasonCode) {
                    case ABORTED_BY_SYSTEM:
                    case ABORTED_BY_USER:
                        return true;
                    default:
                        if (logger.isActivated()) {
                            logger.debug(new StringBuilder(
                                    "Cannot resend transfer with fileTransferId ")
                                    .append(mFileTransferId).append(" as reasonCode=")
                                    .append(rcsReasonCode).toString());
                        }
                        return false;
                }
            default:
                if (logger.isActivated()) {
                    logger.debug(new StringBuilder("Cannot resend transfer with fileTransferId ")
                            .append(mFileTransferId).append(" as state=").append(rcsState)
                            .toString());
                }
                return false;
        }
    }

    /**
     * Resend a file transfer which was previously failed. This only for 1-1 file transfer, an
     * exception is thrown in case of a file transfer to group.
     */
    public void resendTransfer() {
        if (!isAllowedToResendTransfer()) {
            // TODO Temporarily illegal access exception
            throw new IllegalStateException(new StringBuilder(
                    "Unable to resend file with fileTransferId ").append(mFileTransferId)
                    .toString());
        }
        MmContent file = FileTransferUtils.createMmContent(getFile());
        Uri fileIcon = getFileIcon();
        MmContent fileIconContent = fileIcon != null ? FileTransferUtils.createMmContent(fileIcon)
                : null;

        mFileTransferService.resendOneToOneFile(getRemoteContact(), file, fileIconContent,
                mFileTransferId);
    }

    /**
     * Returns true if file transfer has been marked as read
     * 
     * @return boolean
     */
    public boolean isRead() {
        return mPersistentStorage.isRead();
    }

    /*------------------------------- SESSION EVENTS ----------------------------------*/
    /*
     * TODO : Fix reasoncode mapping in the switch.
     */
    private FileTransferStateAndReasonCode toStateAndReasonCode(FileSharingError error) {
        int fileSharingError = error.getErrorCode();
        switch (fileSharingError) {
            case FileSharingError.SESSION_INITIATION_DECLINED:
            case FileSharingError.SESSION_INITIATION_CANCELLED:
                return new FileTransferStateAndReasonCode(State.REJECTED,
                        ReasonCode.REJECTED_BY_REMOTE);
            case FileSharingError.MEDIA_SAVING_FAILED:
                return new FileTransferStateAndReasonCode(State.FAILED, ReasonCode.FAILED_SAVING);
            case FileSharingError.MEDIA_SIZE_TOO_BIG:
                return new FileTransferStateAndReasonCode(State.REJECTED,
                        ReasonCode.REJECTED_MAX_SIZE);
            case FileSharingError.MEDIA_TRANSFER_FAILED:
            case FileSharingError.MEDIA_UPLOAD_FAILED:
            case FileSharingError.MEDIA_DOWNLOAD_FAILED:
                return new FileTransferStateAndReasonCode(State.FAILED,
                        ReasonCode.FAILED_DATA_TRANSFER);
            case FileSharingError.NO_CHAT_SESSION:
            case FileSharingError.SESSION_INITIATION_FAILED:
                return new FileTransferStateAndReasonCode(State.FAILED,
                        ReasonCode.FAILED_INITIATION);
            case FileSharingError.NOT_ENOUGH_STORAGE_SPACE:
                return new FileTransferStateAndReasonCode(State.REJECTED,
                        ReasonCode.REJECTED_LOW_SPACE);
            default:
                throw new IllegalArgumentException(
                        new StringBuilder(
                                "Unknown reason in OneToOneFileTransferImpl.toStateAndReasonCode; fileSharingError=")
                                .append(fileSharingError).append("!").toString());
        }
    }

    private void handleSessionRejected(ReasonCode reasonCode, ContactId contact) {
        if (logger.isActivated()) {
            logger.info("Session rejected; reasonCode=" + reasonCode + ".");
        }
        synchronized (lock) {
            mFileTransferService.removeFileTransfer(mFileTransferId);

            mPersistentStorage.setStateAndReasonCode(State.REJECTED, reasonCode);

            mBroadcaster
                    .broadcastStateChanged(contact, mFileTransferId, State.REJECTED, reasonCode);
        }
    }

    /**
     * Session is started
     */
    public void handleSessionStarted(ContactId contact) {
        if (logger.isActivated()) {
            logger.info("Session started");
        }
        synchronized (lock) {
            mPersistentStorage.setStateAndReasonCode(State.STARTED, ReasonCode.UNSPECIFIED);

            mBroadcaster.broadcastStateChanged(contact, mFileTransferId, State.STARTED,
                    ReasonCode.UNSPECIFIED);
        }
    }

    /**
     * Session has been aborted
     * 
     * @param reason Termination reason
     */
    public void handleSessionAborted(ContactId contact, TerminationReason reason) {
        if (logger.isActivated()) {
            logger.info(new StringBuilder("Session aborted (reason ").append(reason).append(")")
                    .toString());
        }
        /*
         * TODO : Fix reasoncode mapping in the switch.
         */
        ReasonCode reasonCode;
        switch (reason) {
            case TERMINATION_BY_TIMEOUT:
            case TERMINATION_BY_SYSTEM:
                reasonCode = ReasonCode.ABORTED_BY_SYSTEM;
                break;
            case TERMINATION_BY_USER:
                reasonCode = ReasonCode.ABORTED_BY_USER;
                break;
            default:
                throw new IllegalArgumentException(
                        "Unknown reason in OneToOneFileTransferImpl.handleSessionAborted; terminationReason="
                                + reason + "!");
        }
        synchronized (lock) {
            mFileTransferService.removeFileTransfer(mFileTransferId);

            mPersistentStorage.setStateAndReasonCode(State.ABORTED, reasonCode);

            mBroadcaster.broadcastStateChanged(contact, mFileTransferId, State.ABORTED, reasonCode);
        }
    }

    /**
     * Session has been terminated by remote
     */
    public void handleSessionTerminatedByRemote(ContactId contact) {
        if (logger.isActivated()) {
            logger.info("Session terminated by remote");
        }
        synchronized (lock) {
            mFileTransferService.removeFileTransfer(mFileTransferId);
            /*
             * TODO : Fix sending of SIP BYE by sender once transfer is completed and media session
             * is closed. Then this check of state can be removed.
             */
            if (State.TRANSFERRED != mPersistentStorage.getState()) {
                mPersistentStorage.setStateAndReasonCode(State.ABORTED,
                        ReasonCode.ABORTED_BY_REMOTE);
                mBroadcaster.broadcastStateChanged(contact, mFileTransferId, State.ABORTED,
                        ReasonCode.ABORTED_BY_REMOTE);
            }
        }
    }

    /**
     * File transfer error
     * 
     * @param error Error
     */
    public void handleTransferError(FileSharingError error, ContactId contact) {
        if (logger.isActivated()) {
            logger.info("Sharing error " + error.getErrorCode());
        }

        FileTransferStateAndReasonCode stateAndReasonCode = toStateAndReasonCode(error);
        State state = stateAndReasonCode.getState();
        ReasonCode reasonCode = stateAndReasonCode.getReasonCode();
        synchronized (lock) {
            mFileTransferService.removeFileTransfer(mFileTransferId);

            mPersistentStorage.setStateAndReasonCode(state, reasonCode);

            mBroadcaster.broadcastStateChanged(getRemoteContact(), mFileTransferId, state,
                    reasonCode);
        }
    }

    /**
     * File transfer progress
     * 
     * @param currentSize Data size transferred
     * @param totalSize Total size to be transferred
     */
    public void handleTransferProgress(ContactId contact, long currentSize, long totalSize) {
        synchronized (lock) {
            mPersistentStorage.setProgress(currentSize);

            mBroadcaster.broadcastProgressUpdate(contact, mFileTransferId, currentSize, totalSize);
        }
    }

    /**
     * File transfer not allowed to send
     */
    @Override
    public void handleTransferNotAllowedToSend(ContactId contact) {
        synchronized (lock) {
            mFileTransferService.removeFileTransfer(mFileTransferId);

            mPersistentStorage.setStateAndReasonCode(State.FAILED,
                    ReasonCode.FAILED_NOT_ALLOWED_TO_SEND);

            mBroadcaster.broadcastStateChanged(contact, mFileTransferId, State.FAILED,
                    ReasonCode.FAILED_NOT_ALLOWED_TO_SEND);
        }
    }

    /**
     * File has been transfered
     * 
     * @param content MmContent associated to the received file
     */
    public void handleFileTransfered(MmContent content, ContactId contact) {
        if (logger.isActivated()) {
            logger.info("Content transferred");
        }

        synchronized (lock) {
            mFileTransferService.removeFileTransfer(mFileTransferId);

            mPersistentStorage.setTransferred(content);

            mBroadcaster.broadcastStateChanged(contact, mFileTransferId, State.TRANSFERRED,
                    ReasonCode.UNSPECIFIED);
        }
    }

    /**
     * File transfer has been paused by user
     */
    public void handleFileTransferPausedByUser(ContactId contact) {
        if (logger.isActivated()) {
            logger.info("Transfer paused by user");
        }
        synchronized (lock) {
            mPersistentStorage.setStateAndReasonCode(State.PAUSED, ReasonCode.PAUSED_BY_USER);

            mBroadcaster.broadcastStateChanged(contact, mFileTransferId, State.PAUSED,
                    ReasonCode.PAUSED_BY_USER);
        }
    }

    /**
     * File transfer has been paused by system
     */
    public void handleFileTransferPausedBySystem(ContactId contact) {
        if (logger.isActivated()) {
            logger.info("Transfer paused by system");
        }
        synchronized (lock) {
            mFileTransferService.removeFileTransfer(mFileTransferId);

            mPersistentStorage.setStateAndReasonCode(State.PAUSED, ReasonCode.PAUSED_BY_SYSTEM);

            mBroadcaster.broadcastStateChanged(contact, mFileTransferId, State.PAUSED,
                    ReasonCode.PAUSED_BY_SYSTEM);
        }
    }

    /**
     * File transfer has been resumed
     */
    public void handleFileTransferResumed(ContactId contact) {
        if (logger.isActivated()) {
            logger.info("Transfer resumed");
        }
        synchronized (lock) {
            mPersistentStorage.setStateAndReasonCode(State.STARTED, ReasonCode.UNSPECIFIED);

            mBroadcaster.broadcastStateChanged(contact, mFileTransferId, State.STARTED,
                    ReasonCode.UNSPECIFIED);
        }
    }

    @Override
    public void handleSessionAccepted(ContactId contact) {
        if (logger.isActivated()) {
            logger.info("Accepting transfer");
        }
        synchronized (lock) {
            mPersistentStorage.setStateAndReasonCode(State.ACCEPTING, ReasonCode.UNSPECIFIED);

            mBroadcaster.broadcastStateChanged(contact, mFileTransferId, State.ACCEPTING,
                    ReasonCode.UNSPECIFIED);
        }
    }

    @Override
    public void handleSessionRejectedByUser(ContactId contact) {
        handleSessionRejected(ReasonCode.REJECTED_BY_USER, contact);
    }

    /*
     * TODO: Fix reason code mapping between rejected_by_timeout and rejected_by_inactivity.
     */
    @Override
    public void handleSessionRejectedByTimeout(ContactId contact) {
        handleSessionRejected(ReasonCode.REJECTED_BY_INACTIVITY, contact);
    }

    @Override
    public void handleSessionRejectedByRemote(ContactId contact) {
        handleSessionRejected(ReasonCode.REJECTED_BY_REMOTE, contact);
    }

    @Override
    public void handleSessionInvited(ContactId contact, MmContent file, MmContent fileIcon) {
        if (logger.isActivated()) {
            logger.info("Invited to one-to-one file transfer session");
        }
        synchronized (lock) {
            mPersistentStorage.addFileTransfer(contact, Direction.INCOMING, file, fileIcon,
                    State.INVITED, ReasonCode.UNSPECIFIED);
        }

        mBroadcaster.broadcastInvitation(mFileTransferId);
    }

    @Override
    public void handleSessionAutoAccepted(ContactId contact, MmContent file, MmContent fileIcon) {
        if (logger.isActivated()) {
            logger.info("Session auto accepted");
        }
        synchronized (lock) {
            mPersistentStorage.addFileTransfer(contact, Direction.INCOMING, file, fileIcon,
                    State.ACCEPTING, ReasonCode.UNSPECIFIED);
        }

        mBroadcaster.broadcastInvitation(mFileTransferId);
    }
}
