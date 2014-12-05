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

package com.orangelabs.rcs.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import com.gsma.services.rcs.contacts.ContactId;
import com.gsma.services.rcs.contacts.ContactUtils;
import com.orangelabs.rcs.core.ims.ImsModule;
import com.orangelabs.rcs.provider.settings.RcsSettings;

/**
 * Phone utility functions
 * 
 * @author jexa7410
 */
public class PhoneUtils {
	/**
	 * Tel-URI format
	 */
	private static boolean TEL_URI_SUPPORTED = true;

	/**
	 * Country code
	 */
	private static String COUNTRY_CODE = "+33";
	
	/**
	 * Country area code
	 */
	private static String COUNTRY_AREA_CODE = "0";
	
    /**
     * Regular expression of the SIP header
     *
     */
    private final static String REGEXP_EXTRACT_URI = "<(.*)>";
    
    /**
     * Pattern to extract Uri from SIP header
     */
    private final static Pattern PATTERN_EXTRACT_URI = Pattern.compile(REGEXP_EXTRACT_URI);

	/**
	 * Set the country code
	 * 
	 * @param context Context
	 */
	public static synchronized void initialize(Context context) {
		RcsSettings.createInstance(context);
		ContactUtils contactUtils = ContactUtils.getInstance(context);
		TEL_URI_SUPPORTED = RcsSettings.getInstance().isTelUriFormatUsed();
		COUNTRY_CODE = contactUtils.getMyCountryCode();
		COUNTRY_AREA_CODE = contactUtils.getMyCountryAreaCode();
	}

	/**
	 * Returns the country code
	 * 
	 * @return Country code
	 */
	public static String getCountryCode() {
		return COUNTRY_CODE;
	}
	
	/**
	 * Format a phone number to international format
	 * 
	 * @param number Phone number
	 * @return International number
	 */
	public static String formatNumberToInternational(String number) {
		if (number == null) {
			return null;
		}
		
		// Remove spaces TODO check if necessary
		number = number.trim();

		// Strip all non digits
		String phoneNumber = PhoneNumberUtils.stripSeparators(number);

		// Format into international
		if (phoneNumber.startsWith("00" + COUNTRY_CODE.substring(1))) {
			// International format
			phoneNumber = COUNTRY_CODE + phoneNumber.substring(1 + COUNTRY_CODE.length());
		} else if (!TextUtils.isEmpty(COUNTRY_AREA_CODE) && phoneNumber.startsWith(COUNTRY_AREA_CODE)) {
			// National number with area code
			phoneNumber = COUNTRY_CODE + phoneNumber.substring(COUNTRY_AREA_CODE.length());
		} else if (!phoneNumber.startsWith("+")) {
			// National number
			phoneNumber = COUNTRY_CODE + phoneNumber;
		}
		return phoneNumber;
	}
	
	/**
	 * Format a phone number to a SIP URI
	 * 
	 * @param number Phone number
	 * @return SIP URI
	 */
	public static String formatNumberToSipUri(String number) {
		if (number == null) {
			return null;
		}

		// Remove spaces
		number = number.trim();
		
		// Extract username part
		if (number.startsWith("tel:")) {
			number = number.substring(4);
		} else if (number.startsWith("sip:")) {
			number = number.substring(4, number.indexOf("@"));
		}
		
		if (TEL_URI_SUPPORTED) {
			// Tel-URI format
			return "tel:" + formatNumberToInternational(number);
		} else {
			// SIP-URI format
			return "sip:" + formatNumberToInternational(number) + "@" +
				ImsModule.IMS_USER_PROFILE.getHomeDomain() + ";user=phone";	 
		}
	}
	
	/**
	 * Format ContactId to tel or sip Uri
	 * 
	 * @param contactId
	 *            the contact identifier
	 * @return the Uri
	 */
	public static String formatContactIdToUri(ContactId contactId) {
		if (contactId == null) {
			throw new IllegalArgumentException("ContactId is null");
		}
		if (TEL_URI_SUPPORTED) {
			// Tel-URI format
			return new StringBuilder("tel:").append(contactId).toString();
		} else {
			// SIP-URI format
			return new StringBuilder("sip:").append(contactId).append("@").append(ImsModule.IMS_USER_PROFILE.getHomeDomain())
					.append(";user=phone").toString();
		}
	}

	/**
	 * Extract user part phone number from a SIP-URI or Tel-URI or SIP address
	 * 
	 * @param uri SIP or Tel URI
	 * @return Unformatted Number or null in case of error
	 */
	public static String extractNumberFromUriWithoutFormatting(String uri) {
		if (uri == null) {
			return null;
		}

		try {
			// Extract URI from address
			int index0 = uri.indexOf("<");
			if (index0 != -1) {
				uri = uri.substring(index0 + 1, uri.indexOf(">", index0));
			}

			// Extract a Tel-URI
			int index1 = uri.indexOf("tel:");
			if (index1 != -1) {
				uri = uri.substring(index1 + 4);
			}

			// Extract a SIP-URI
			index1 = uri.indexOf("sip:");
			if (index1 != -1) {
				int index2 = uri.indexOf("@", index1);
				uri = uri.substring(index1 + 4, index2);
			}

			// Remove URI parameters
			int index2 = uri.indexOf(";");
			if (index2 != -1) {
				uri = uri.substring(0, index2);
			}

			// Returns the extracted number (username part of the URI)
			return uri;
		} catch (Exception e) {
			return null;
		}
	}
	
	/**
	 * Extract user part phone number from a SIP-URI or Tel-URI or SIP address
	 * 
	 * @param uri SIP or Tel URI
	 * @return Number or null in case of error
	 */
	public static String extractNumberFromUri(String uri) {
		// Format the extracted number (username part of the URI)
		return formatNumberToInternational(extractNumberFromUriWithoutFormatting(uri));
	}
		
	/**
	 * get URI from SIP identity header
	 * 
	 * @param header
	 *            the SIP header
	 * @return the Uri
	 */
	public static String extractUriFromSipHeader(String header) {
		if (header != null) {
			Matcher matcher = PATTERN_EXTRACT_URI.matcher(header);
			if (matcher.find()) {
				return matcher.group(1);
			}
		}
		return header;
	}

}
