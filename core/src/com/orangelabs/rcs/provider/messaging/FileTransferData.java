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
package com.orangelabs.rcs.provider.messaging;

import android.net.Uri;

import com.gsma.services.rcs.ft.FileTransferLog;

/**
 * File transfer data constants
 * 
 * @author Jean-Marc AUFFRET
 */
public class FileTransferData {
	/**
	 * Database URI
	 */
	protected static final Uri CONTENT_URI = Uri.parse("content://com.orangelabs.rcs.ft/ft");
	
	/**
	 * Column name
	 */
	static final String KEY_ID = FileTransferLog.ID;

	/**
	 * Column name
	 */
	static final String KEY_SESSION_ID = FileTransferLog.FT_ID;
	
	/**
	 * Column name
	 */
	static final String KEY_CHAT_ID = FileTransferLog.CHAT_ID;

	/**
	 * Column name
	 */
	static final String KEY_TIMESTAMP = FileTransferLog.TIMESTAMP;
	
	/**
	 * Column name
	 */
    static final String KEY_TIMESTAMP_SENT = FileTransferLog.TIMESTAMP_SENT;
    
	/**
	 * Column name
	 */
    static final String KEY_TIMESTAMP_DELIVERED = FileTransferLog.TIMESTAMP_DELIVERED;
    
	/**
	 * Column name
	 */
    static final String KEY_TIMESTAMP_DISPLAYED = FileTransferLog.TIMESTAMP_DISPLAYED;	

	/**
	 * Column name
	 */
	static final String KEY_CONTACT = FileTransferLog.CONTACT_NUMBER;
	
	/**
	 * Column name
	 */
	static final String KEY_STATUS = FileTransferLog.STATE;

	/**
	 * Column name
	 */
	static final String KEY_MIME_TYPE = FileTransferLog.MIME_TYPE;
	
	/**
	 * Column name
	 */
	static final String KEY_NAME = FileTransferLog.FILENAME;
	
	/**
	 * Column name
	 */
	static final String KEY_SIZE = FileTransferLog.TRANSFERRED;
	
	/**
	 * Column name
	 */
	static final String KEY_TOTAL_SIZE = FileTransferLog.FILESIZE;	

	/**
	 * Column name
	 */
	static final String KEY_DIRECTION = FileTransferLog.DIRECTION;

	/**
	 * Column name KEY_MSG_ID : the reference to the Chat message
	 */
	static final String KEY_MSG_ID =  FileTransferLog.MESSAGE_ID;
	
	/**
	 * Column name KEY_FILEICON : the URI of the file icon
	 */
	static final String KEY_FILEICON =  FileTransferLog.FILEICON;
}
