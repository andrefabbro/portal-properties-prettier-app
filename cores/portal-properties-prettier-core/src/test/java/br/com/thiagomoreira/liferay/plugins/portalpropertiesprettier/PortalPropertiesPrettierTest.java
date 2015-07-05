/**
 * Copyright (C) 2015 Thiago Moreira (tmoreira2020@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package br.com.thiagomoreira.liferay.plugins.portalpropertiesprettier;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

import com.liferay.portal.kernel.util.PropertiesUtil;

public class PortalPropertiesPrettierTest {

	@Test
	public void testFixLineBreakNull() {
		PortalPropertiesPrettier prettier = new PortalPropertiesPrettier();

		String actual = prettier.fixLineBreak(null);

		Assert.assertNull(actual);
	}

	@Test
	public void testFixLineBreakNotNull() {
		PortalPropertiesPrettier prettier = new PortalPropertiesPrettier();

		String expected = "\\n";

		String actual = prettier.fixLineBreak("\n");

		Assert.assertEquals(expected, actual);

		actual = prettier.fixLineBreak("\r");

		Assert.assertEquals(expected, actual);

		actual = prettier.fixLineBreak("\r\n");

		Assert.assertEquals(expected, actual);
	}

	@Test
	public void testIssue10() throws Exception {
		PortalPropertiesPrettier prettier = new PortalPropertiesPrettier();
		String expected = getContent("/portal-issue-10-expected.properties");
		String actual = getContent("/portal-issue-10.properties");
		Properties customProperties = PropertiesUtil.load(actual);

		actual = prettier.prettify(customProperties, "6.2.3-ga4");

		Assert.assertEquals(expected, actual);
	}

	@Test
	public void testIssue12() throws Exception {
		PortalPropertiesPrettier prettier = new PortalPropertiesPrettier();
		String expected = getContent("/portal-issue-12-expected.properties");
		String actual = getContent("/portal-issue-12.properties");
		Properties customProperties = PropertiesUtil.load(actual);

		actual = prettier.prettify(customProperties, "6.2.3-ga4");

		Assert.assertEquals(expected, actual);
	}

	@Test
	public void testIssue13() throws Exception {
		PortalPropertiesPrettier prettier = new PortalPropertiesPrettier();
		String expected = getContent("/portal-issue-13-expected.properties");
		String actual = getContent("/portal-issue-13.properties");
		Properties customProperties = PropertiesUtil.load(actual);

		actual = prettier.prettify(customProperties, "6.2.3-ga4", true);

		Assert.assertEquals(expected, actual);
	}

	protected String getContent(String path) throws IOException {
		InputStream in = getClass().getResourceAsStream(path);

		try {
			return IOUtils.toString(in);
		} catch (IOException ioex) {
			throw ioex;
		} finally {
			IOUtils.closeQuietly(in);
		}
	}

}