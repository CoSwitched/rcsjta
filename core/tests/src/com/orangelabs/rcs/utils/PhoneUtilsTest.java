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

import android.content.Context;
import android.test.AndroidTestCase;

public class PhoneUtilsTest extends AndroidTestCase {

	private Context mContext;

	protected void setUp() throws Exception {
		super.setUp();
		mContext = getContext();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}

	public void testFranceNumber() {
		PhoneUtils.initialize(mContext);

		assertEquals(PhoneUtils.formatNumberToInternational("0033121345678"), "+33121345678");
		assertEquals(PhoneUtils.formatNumberToInternational("0121345678"), "+33121345678");
		assertEquals(PhoneUtils.formatNumberToInternational("+33121345678"), "+33121345678");
		assertEquals(PhoneUtils.formatNumberToInternational("33121345678"), "+3333121345678");
		assertEquals(PhoneUtils.formatNumberToInternational("123"), "+33123");
		assertEquals(PhoneUtils.formatNumberToInternational("+33004010001"), "+33004010001");
	}

	public void testSpainNumber() {
		PhoneUtils.initialize(getContext());

		assertEquals(PhoneUtils.formatNumberToInternational("0034121345678"), "+34121345678");
		assertEquals(PhoneUtils.formatNumberToInternational("121345678"), "+34121345678");
		assertEquals(PhoneUtils.formatNumberToInternational("+34121345678"), "+34121345678");
		assertEquals(PhoneUtils.formatNumberToInternational("34121345678"), "+3434121345678");
		assertEquals(PhoneUtils.formatNumberToInternational("123"), "+34123");
	}

}
