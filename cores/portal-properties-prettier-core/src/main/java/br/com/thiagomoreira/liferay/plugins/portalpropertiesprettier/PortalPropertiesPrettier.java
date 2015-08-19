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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.simmetrics.StringMetric;
import org.simmetrics.StringMetricBuilder;
import org.simmetrics.metrics.CosineSimilarity;
import org.simmetrics.simplifiers.Case;
import org.simmetrics.simplifiers.WordCharacters;
import org.simmetrics.tokenizers.QGram;
import org.simmetrics.tokenizers.Whitespace;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.PropertiesUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.util.ContentUtil;

public class PortalPropertiesPrettier {

	protected String[] portalFileNames = {"6.1.0-ga1", "6.1.1-ga2",
			"6.1.2-ga3", "6.2.0-ga1", "6.2.1-ga2", "6.2.2-ga3", "6.2.3-ga4"};
	protected Map<String, String> defaultPortalProperties = new HashMap<>();
	private static Log log = LogFactoryUtil
			.getLog(PortalPropertiesPrettier.class);

	public String prettify(Properties customProperties, String liferayVersion)
			throws Exception {
		return prettify(customProperties, liferayVersion, false);
	}

	public String prettify(Properties customProperties, String liferayVersion,
			boolean printDefaultValue) throws Exception {

		log.info("Processing " + customProperties.size() + " custom properties");

		String defaultPortalProperties = getDefaultPortalProperties(liferayVersion);
		String currentContext = null;
		StringBuilder currentComment = new StringBuilder();
		Set<String> processedContexts = new HashSet<String>();
		Properties removedProperties = new Properties();
		StringBuilder pretty = new StringBuilder();
		BufferedReader reader = new BufferedReader(new StringReader(
				defaultPortalProperties));
		String line = reader.readLine();
		int oldCommentLength = 0;
		Pattern keyDigitPattern = Pattern.compile("([a-z]|\\.|\\[|\\])+(\\d)$");
		boolean hasCommentAfterContext = false;

		while (line != null) {
			if (line.startsWith("## ")) {
				currentContext = line;
			}
			if (line.startsWith("    #")) {
				oldCommentLength = currentComment.length();
				currentComment.append(line);
				currentComment.append("\n");
				hasCommentAfterContext = true;
			}
			if (line.length() == 0) {
				currentComment.setLength(0);
			}

			Enumeration<Object> keys = (Enumeration<Object>) customProperties
					.keys();
			while (keys.hasMoreElements()) {
				String key = (String) keys.nextElement();
				String value = fixLineBreak(customProperties.getProperty(key));

				if (isLineProperty(line, key)) {
					if (!line.startsWith("    " + key + "=" + value)
							|| !line.equals("    " + key + "=" + value)) {
						if (!processedContexts.contains(currentContext)) {
							pretty.append("\n");
							pretty.append("##");
							pretty.append("\n");
							pretty.append(currentContext);
							pretty.append("\n");
							pretty.append("##");
							pretty.append("\n");
							processedContexts.add(currentContext);
							hasCommentAfterContext = false;
						}
						if (line.startsWith("    #" + key + "=")) {
							currentComment.setLength(oldCommentLength);
						}
						if (currentComment.length() != 0) {
							pretty.append("\n");
							pretty.append(currentComment);
							currentComment.setLength(0);
							hasCommentAfterContext = true;
						}
						if (!hasCommentAfterContext) {
							pretty.append("\n");
							hasCommentAfterContext = true;
						}

						if (printDefaultValue) {
							if (!line.startsWith("    #")) {
								pretty.append(line.replace("    ", "    #"));
							} else {
								pretty.append(line);
							}
							pretty.append("\n");
						}

						pretty.append("    " + key + "=" + value);
						pretty.append("\n");

						Matcher matcher = keyDigitPattern.matcher(key);
						if (matcher.matches()) {

							String digitFound = matcher.group(2);
							for (int i = 1; i < 10; i++) {
								String tempKey = key.replace(digitFound,
										String.valueOf(i));
								value = fixLineBreak(customProperties
										.getProperty(tempKey));

								if (value != null) {
									pretty.append("    " + tempKey + "="
											+ value);
									pretty.append("\n");

									customProperties.remove(tempKey);
								}
							}
						}
					} else {
						removedProperties.put(key, value);
					}
					customProperties.remove(key);
				}

			}
			line = reader.readLine();
		}

		String obsoleteProperties = processObsoleteCustomProperties(
				liferayVersion, customProperties);

		String typoProperties = processTypoCustomProperties(liferayVersion,
				customProperties);

		pretty.insert(0, processRemainingCustomProperties(customProperties));

		pretty.insert(0, typoProperties);

		pretty.insert(0, obsoleteProperties);

		pretty.insert(0, processRemovedProperties(removedProperties));

		return pretty.toString();
	}

	protected String fixLineBreak(String text) {
		if (text == null) {
			return null;
		}

		return text.replaceAll("(\\r|\\n|\\r\\n)+", "\\\\n");
	}

	protected String getDefaultPortalProperties(String liferayVersion)
			throws IOException {
		String defaultPortalPropertiesPath = "portal-" + liferayVersion
				+ ".properties";
		String portalProperties = (String) defaultPortalProperties
				.get(defaultPortalPropertiesPath);

		if (Validator.isNull(portalProperties)) {
			log.info("Missing cache for portal.properties version "
					+ liferayVersion);

			portalProperties = ContentUtil.get(defaultPortalPropertiesPath);

			defaultPortalProperties.put(defaultPortalPropertiesPath,
					portalProperties);
		}

		return portalProperties;
	}

	protected Set<String> getProperyKeys(String liferayVersion)
			throws IOException {
		String properties = getDefaultPortalProperties(liferayVersion);
		Set<String> keys = new HashSet<String>();
		Pattern pattern = Pattern
				.compile("    ((\\w|\\.)+)=|    #((\\w|\\.)+)=");
		Matcher matcher = pattern.matcher(properties);

		while (matcher.find()) {
			String property = matcher.group(1);
			if (Validator.isNull(property)) {
				property = matcher.group(3);
			}
			keys.add(property);
		}

		return keys;
	}

	protected boolean isLineProperty(String line, String key) {
		return line.startsWith("    " + key + "=")
				|| line.startsWith("    #" + key + "=");
	}

	protected String processObsoleteCustomProperties(String liferayVersion,
			Properties customPortalProperties) throws IOException {
		if (customPortalProperties.isEmpty()) {
			return StringPool.BLANK;
		}

		StringBuilder obsoleteProperties = new StringBuilder();
		int index = Arrays.binarySearch(portalFileNames, liferayVersion);

		for (int i = index; i >= 0; i--) {
			boolean processedContext = false;
			Properties portalProperties = PropertiesUtil
					.load(getDefaultPortalProperties(portalFileNames[i]));
			SortedSet<String> keys = new TreeSet<String>(
					customPortalProperties.stringPropertyNames());
			Iterator<String> iterator = keys.iterator();

			while (iterator.hasNext()) {
				String key = iterator.next();

				if (portalProperties.containsKey(key)) {
					if (!processedContext) {
						obsoleteProperties
								.append("##\n## Obsolete properties of ");
						obsoleteProperties.append(portalFileNames[i]);
						obsoleteProperties.append("\n##\n\n");
						obsoleteProperties.append("    #\n");
						obsoleteProperties
								.append("    # The properties listed below are obsolete for version ");
						obsoleteProperties.append(liferayVersion);
						obsoleteProperties.append(" which\n");
						obsoleteProperties
								.append("    # means that they don't have any influence in how Liferay is configured\n");
						obsoleteProperties
								.append("    # and are safe be to removed.\n");
						obsoleteProperties.append("    #");

						processedContext = true;
					}

					String value = fixLineBreak(customPortalProperties
							.getProperty(key));

					obsoleteProperties.append("\n");
					obsoleteProperties.append("    #" + key + "=" + value);
					customPortalProperties.remove(key);
				}
			}
			if (processedContext) {
				obsoleteProperties.append("\n");
				obsoleteProperties.append("\n");
			}
		}

		return obsoleteProperties.toString();
	}

	protected String processRemainingCustomProperties(
			Properties customPortalProperties) {
		if (customPortalProperties.isEmpty()) {
			return StringPool.BLANK;
		}

		SortedSet<String> keys = new TreeSet<String>(
				customPortalProperties.stringPropertyNames());

		StringBuilder customProperties = new StringBuilder();

		customProperties.append("##\n## Custom properties\n##");
		customProperties.append("\n");

		Iterator<String> iterator = keys.iterator();
		while (iterator.hasNext()) {
			String key = iterator.next();
			String value = fixLineBreak(customPortalProperties.getProperty(key));
			customProperties.append("\n");
			customProperties.append("    " + key + "=" + value);
		}
		customProperties.append("\n");

		return customProperties.toString();
	}

	protected String processRemovedProperties(Properties removedProperties) {
		if (removedProperties.isEmpty()) {
			return StringPool.BLANK;
		}

		log.info("Removing " + removedProperties.size() + " properties");

		SortedSet<String> keys = new TreeSet<String>(
				removedProperties.stringPropertyNames());

		StringBuilder stringBuilder = new StringBuilder();

		stringBuilder.append("##\n## Removed properties\n##\n");
		stringBuilder.append("\n");
		stringBuilder.append("    #\n");
		stringBuilder
				.append("    # The properties listed below has the exactly same value as in the original\n");
		stringBuilder
				.append("    # portal.properties which means that they are safe to be removed.\n");
		stringBuilder.append("    #");

		Iterator<String> iterator = keys.iterator();
		while (iterator.hasNext()) {
			String key = iterator.next();
			String value = fixLineBreak(removedProperties.getProperty(key));
			stringBuilder.append("\n");
			stringBuilder.append("    #" + key + "=" + value);
		}
		stringBuilder.append("\n\n");

		return stringBuilder.toString();
	}

	protected String processTypoCustomProperties(String liferayVersion,
			Properties customPortalProperties) throws IOException {
		if (customPortalProperties.isEmpty()) {
			return StringPool.BLANK;
		}

		SortedSet<String> customKeys = new TreeSet<String>(
				customPortalProperties.stringPropertyNames());
		StringBuilder customProperties = new StringBuilder();

		boolean processedContext = false;
		StringMetric metric = StringMetricBuilder
				.with(new CosineSimilarity<String>())
				.simplify(new Case.Lower(Locale.ENGLISH))
				.simplify(new WordCharacters()).tokenize(new Whitespace())
				.tokenize(new QGram(2)).build();
		Set<String> defaultKeys = getProperyKeys(liferayVersion);

		for (String customKey : customKeys) {
			float distance = 0;
			String key = null;

			for (String defaultKey : defaultKeys) {
				float temp = metric.compare(defaultKey, customKey);

				if (temp > distance) {
					distance = temp;
					key = defaultKey;
				}
			}

			if (distance > 0.9) {
				if (!processedContext) {
					customProperties.append("##\n## Typo properties\n##");
					customProperties.append("\n\n");
					customProperties.append("    #\n");
					customProperties
							.append("    # The properties listed below looks like that has a typo in its declaration\n");
					customProperties
							.append("    # which means that they don't have any influence in how Liferay is configured.\n");
					customProperties
							.append("    # The system suggested the correct property name in the comments.\n");
					customProperties.append("    #");

					processedContext = true;
				}
				String value = fixLineBreak(customPortalProperties
						.getProperty(customKey));

				customProperties.append("\n");
				customProperties.append("    #" + key + "=" + value);
				customProperties.append("\n");
				customProperties.append("    " + customKey + "=" + value);
				customPortalProperties.remove(customKey);
			}
		}

		if (processedContext) {
			customProperties.append("\n");
			customProperties.append("\n");
		}

		return customProperties.toString();
	}
}
