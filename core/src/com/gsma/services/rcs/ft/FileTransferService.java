/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
 * Copyright (C) 2014 Sony Mobile Communications AB.
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
 * NOTE: This file has been modified by Sony Mobile Communications AB.
 * Modifications are licensed under the License.
 ******************************************************************************/
package com.gsma.services.rcs.ft;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.IBinder;
import android.os.IInterface;

import com.gsma.services.rcs.JoynContactFormatException;
import com.gsma.services.rcs.JoynService;
import com.gsma.services.rcs.JoynServiceException;
import com.gsma.services.rcs.JoynServiceListener;
import com.gsma.services.rcs.JoynServiceNotAvailableException;

/**
 * This class offers the main entry point to transfer files and to
 * receive files. Several applications may connect/disconnect to the API.
 * 
 * The parameter contact in the API supports the following formats:
 * MSISDN in national or international format, SIP address, SIP-URI
 * or Tel-URI.
 * 
 * @author Jean-Marc AUFFRET 
 */
public class FileTransferService extends JoynService {
	/**
	 * API
	 */
	private IFileTransferService api = null;
	
    /**
     * Constructor
     * 
     * @param ctx Application context
     * @param listener Service listener
     */
    public FileTransferService(Context ctx, JoynServiceListener listener) {
    	super(ctx, listener);
    }

    /**
     * Connects to the API
     */
    public void connect() {
    	ctx.bindService(new Intent(IFileTransferService.class.getName()), apiConnection, 0);
    }
    
    /**
     * Disconnects from the API
     */
    public void disconnect() {
    	try {
    		ctx.unbindService(apiConnection);
        } catch(IllegalArgumentException e) {
        	// Nothing to do
        }
    }

	/**
	 * Set API interface
	 * 
	 * @param api API interface
	 */
    protected void setApi(IInterface api) {
    	super.setApi(api);
    	
        this.api = (IFileTransferService)api;
    }
    
    /**
	 * Service connection
	 */
	private ServiceConnection apiConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
        	setApi(IFileTransferService.Stub.asInterface(service));
        	if (serviceListener != null) {
        		serviceListener.onServiceConnected();
        	}
        }

        public void onServiceDisconnected(ComponentName className) {
        	setApi(null);
        	if (serviceListener != null) {
        		serviceListener.onServiceDisconnected(JoynService.Error.CONNECTION_LOST);
        	}
        }
    };

    /**
     * Returns the configuration of the file transfer service
     * 
     * @return Configuration
     * @throws JoynServiceException
     */
    public FileTransferServiceConfiguration getConfiguration() throws JoynServiceException {
		if (api != null) {
			try {
				return api.getConfiguration();
			} catch(Exception e) {
				throw new JoynServiceException(e.getMessage());
			}
		} else {
			throw new JoynServiceNotAvailableException();
		}
	}

	private void grantUriPermissionToStackServices(Uri file) {
		Intent fileTransferServiceIntent = new Intent(IFileTransferService.class.getName());
		List<ResolveInfo> stackServices = ctx.getPackageManager().queryIntentServices(
				fileTransferServiceIntent, 0);
		for (ResolveInfo stackService : stackServices) {
			ctx.grantUriPermission(stackService.serviceInfo.packageName, file,
					Intent.FLAG_GRANT_READ_URI_PERMISSION);
		}
	}

	private void persistUriPermissionForClient(Uri file) {
//		 ctx.getContentResolver().takePersistableUriPermission(file,
//				Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
	}

    /**
     * Transfers a file to a contact. The parameter file contains the URI of the
     * file to be transferred (for a local or a remote file). The parameter 
     * contact supports the following formats: MSISDN in national or 
     * international format, SIP address, SIP-URI or Tel-URI. If the format of 
     * the contact is not supported an exception is thrown.
     * 
     * @param contact 
     * @param file URI of file to transfer
     * @param listener File transfer event listener
     * @return File transfer
     * @throws JoynServiceException
	 * @throws JoynContactFormatException
	 */
    public FileTransfer transferFile(String contact, Uri file, FileTransferListener listener) throws JoynServiceException, JoynContactFormatException {
    	return transferFile(contact, file, false, listener);
    }
    
	/**
	 * Grant permission to the stack and persist access permission
	 * @param file the file URI
	 */
	private void grantAndPersistUriPermission(Uri file) {
		if (android.os.Build.VERSION.SDK_INT < 19) {
			return;
		}
		if (ContentResolver.SCHEME_CONTENT.equals(file.getScheme())) {
			// Granting temporary read Uri permission from client to
			// stack service if it is a content URI
			grantUriPermissionToStackServices(file);
			// Persist Uri access permission for the client
			// to be able to read the contents from this Uri even
			// after the client is restarted after device reboot.
			persistUriPermissionForClient(file);
		}
	}
    
	/**
     * Transfers a file to a contact. The parameter file contains the URI of the
     * file to be transferred (for a local or a remote file). The parameter
     * contact supports the following formats: MSISDN in national or
     * international format, SIP address, SIP-URI or Tel-URI. If the format of
     * the contact is not supported an exception is thrown.
	 * 
	 * @param contact
	 * @param file
	 *            Uri of file to transfer
	 * @param fileicon
	 *            File icon option. If true, the stack tries to attach fileicon. Fileicon may not be attached if file is not an
	 *            image or if local or remote contact does not support fileicon.
	 * @param listener
	 *            File transfer event listener
	 * @return File transfer
	 * @throws JoynServiceException
	 * @throws JoynContactFormatException
	 */
	/**
	 * @param contact
	 * @param file
	 * @param fileicon
	 * @param listener
	 * @return
	 * @throws JoynServiceException
	 * @throws JoynContactFormatException
	 */
	public FileTransfer transferFile(String contact, Uri file, boolean fileicon, FileTransferListener listener) throws JoynServiceException, JoynContactFormatException {
    	if (api != null) {
			try {
				grantAndPersistUriPermission(file);

				IFileTransfer ftIntf = api.transferFile(contact, file, fileicon, listener);
				if (ftIntf != null) {
					return new FileTransfer(ftIntf);
				} else {
					return null;
				}
			} catch(Exception e) {
				throw new JoynServiceException(e.getMessage());
			}
		} else {
			throw new JoynServiceNotAvailableException();
		}
	}

	/**
	 * Transfers a file to a group chat with an optional file icon.
	 *
	 * @param chatId
	 * @param file Uri of file to transfer
	 * @param fileicon File icon option. If true, the stack tries to attach
	 *            fileicon. Fileicon may not be attached if file is not an
	 *            image or if local or remote contact does not support
	 *            fileicon.
	 * @param listener File transfer event listener
	 * @return File transfer
	 * @throws JoynServiceException
	 * @throws JoynContactFormatException
	 */
	public FileTransfer transferFileToGroupChat(String chatId, Uri file, boolean fileicon,
			FileTransferListener listener) throws JoynServiceException, JoynContactFormatException {
		if (api != null) {
			try {
				grantAndPersistUriPermission(file);
				
				IFileTransfer ftIntf = api.transferFileToGroupChat(chatId, file, fileicon, listener);
				if (ftIntf != null) {
					return new FileTransfer(ftIntf);
				} else {
					return null;
				}
			} catch (Exception e) {
				throw new JoynServiceException(e.getMessage());
			}
		} else {
			throw new JoynServiceNotAvailableException();
		}
	}

    /**
     * Mark a received file transfer as read (i.e. the invitation or the file has been displayed in the UI).
     *
     * @param transferId
     * @throws JoynServiceException
     * @throws JoynContactFormatException
     */
    public void markFileTransferAsRead(String transferId) throws JoynServiceException {
        if (api != null) {
            try {
                api.markFileTransferAsRead(transferId);
            } catch(Exception e) {
                throw new JoynServiceException(e.getMessage());
            }
        } else {
            throw new JoynServiceNotAvailableException();
        }
    }
    
    /**
     * Returns the list of file transfers in progress
     * 
     * @return List of file transfers
     * @throws JoynServiceException
     */
    public Set<FileTransfer> getFileTransfers() throws JoynServiceException {
		if (api != null) {
			try {
	    		Set<FileTransfer> result = new HashSet<FileTransfer>();
				List<IBinder> ftList = api.getFileTransfers();
				for (IBinder binder : ftList) {
					FileTransfer ft = new FileTransfer(IFileTransfer.Stub.asInterface(binder));
					result.add(ft);
				}
				return result;
			} catch(Exception e) {
				throw new JoynServiceException(e.getMessage());
			}
		} else {
			throw new JoynServiceNotAvailableException();
		}
    }    

    /**
     * Returns a current file transfer from its unique ID
     * 
     * @return File transfer or null if not found
     * @throws JoynServiceException
     */
    public FileTransfer getFileTransfer(String transferId) throws JoynServiceException {
		if (api != null) {
			try {
				IFileTransfer ftIntf = api.getFileTransfer(transferId);
				if (ftIntf != null) {
					return new FileTransfer(ftIntf);
				} else {
					return null;
				}
			} catch(Exception e) {
				throw new JoynServiceException(e.getMessage());
			}
		} else {
			throw new JoynServiceNotAvailableException();
		}
    }    
    
    /**
     * Returns a current file transfer from its invitation Intent
     * 
     * @param intent Invitation intent
     * @return File transfer or null if not found
     * @throws JoynServiceException
     */
    public FileTransfer getFileTransferFor(Intent intent) throws JoynServiceException {
		if (api != null) {
			try {
				String transferId = intent.getStringExtra(FileTransferIntent.EXTRA_TRANSFER_ID);
				if (transferId != null) {
					return getFileTransfer(transferId);
				} else {
					return null;
				}
			} catch(Exception e) {
				throw new JoynServiceException(e.getMessage());
			}
		} else {
			throw new JoynServiceNotAvailableException();
		}
    }     
    
    /**
	 * Registers a file transfer invitation listener
	 * 
	 * @param listener New file transfer listener
	 * @throws JoynServiceException
	 */
	public void addNewFileTransferListener(NewFileTransferListener listener) throws JoynServiceException {
		if (api != null) {
			try {
				api.addNewFileTransferListener(listener);
			} catch(Exception e) {
				throw new JoynServiceException(e.getMessage());
			}
		} else {
			throw new JoynServiceNotAvailableException();
		}
	}

	/**
	 * Unregisters a file transfer invitation listener
	 * 
	 * @param listener New file transfer listener
	 * @throws JoynServiceException
	 */
	public void removeNewFileTransferListener(NewFileTransferListener listener) throws JoynServiceException {
		if (api != null) {
			try {
				api.removeNewFileTransferListener(listener);
			} catch(Exception e) {
				throw new JoynServiceException(e.getMessage());
			}
		} else {
			throw new JoynServiceNotAvailableException();
		}
	}
	
	/**
	 * set the Auto Accept Mode of a File Transfer configuration.
	 * <p>
	 * The Auto Accept Mode can only be modified by client application if isAutoAcceptModeChangeable (see
	 * FileTransferServiceConfiguration class) is true
	 * 
	 * @param enable
	 *            true to enable else false
	 * @throws JoynServiceException
	 */
    public void setAutoAccept(boolean enable) throws JoynServiceException {
		if (api != null) {
			try {
				api.setAutoAccept(enable);
			} catch(Exception e) {
				throw new JoynServiceException(e.getMessage());
			}
		} else {
			throw new JoynServiceNotAvailableException();
		}
	}
	
	/**
	 * set the Auto Accept Mode of a File Transfer configuration while roaming.
	 * <p>
	 * The AutoAcceptModeInRoaming can only be modified by client application if isAutoAcceptModeChangeable (@see
	 * FileTransferServiceConfiguration class) is true and if the Auto Accept Mode in normal conditions is true
	 * 
	 * @param enable
	 *            true to enable else false
	 * @throws JoynServiceException
	 */
	public void setAutoAcceptInRoaming(boolean enable) throws JoynServiceException {
		if (api != null) {
			try {
				api.setAutoAcceptInRoaming(enable);
			} catch (Exception e) {
				throw new JoynServiceException(e.getMessage());
			}
		} else {
			throw new JoynServiceNotAvailableException();
		}
	}
    
	/**
	 * set the image resize option for file transfer. 
	 * 
	 * @param option
	 *            the image resize option (0: ALWAYS_PERFORM, 1: ONLY_ABOVE_MAX_SIZE, 2: ASK)
	 */
    public void setImageResizeOption(int option) throws JoynServiceException {
		if (api != null) {
			try {
				api.setImageResizeOption(option);
			} catch(Exception e) {
				throw new JoynServiceException(e.getMessage());
			}
		} else {
			throw new JoynServiceNotAvailableException();
		}
	}
}
