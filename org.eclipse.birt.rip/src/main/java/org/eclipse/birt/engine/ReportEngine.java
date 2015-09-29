package org.eclipse.birt.engine;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;

import org.eclipse.birt.core.exception.BirtException;
import org.eclipse.birt.core.framework.Platform;
import org.eclipse.birt.report.engine.api.EngineConfig;
import org.eclipse.birt.report.engine.api.IReportEngine;
import org.eclipse.birt.report.engine.api.IReportEngineFactory;

public class ReportEngine {
	private static IReportEngine reportEngine;
	public static final File RESOURCE_DIR;
	public static final long TIME_TO_LIVE; // ten minutes
	private static final Map<String, String> MIME_TYPES = new HashMap<>();
	static {
		final Properties properties = System.getProperties();
		// resource dir: where to put files, required
		final String resourceDirName = properties
				.getProperty("org.eclipse.birt.rip.resource.dir");
		if (resourceDirName == null)
			throw new NullPointerException(
					"property: org.eclipse.birt.rip.resource.dir");
		RESOURCE_DIR = new File(resourceDirName);
		purgeResourceDir();
		// time-to-live: how long to keep files with no activity (in ms),
		// defaults to ten minutes
		final String ttlString = properties
				.getProperty("org.eclipse.birt.rip.file.ttl");
		TIME_TO_LIVE = ttlString == null ? 10 * 60 * 1000 : Long
				.valueOf(ttlString);
	}

	private static void purgeResourceDir() {
		initializeMediaTypes();
		final File[] files = RESOURCE_DIR.listFiles();
		if (files == null)
			return;
		for (final File file : files) {
			if (file.isDirectory())
				continue;
			// there shouldn't be any directories, but if there are, ignore them
			file.delete();
		}
	}

	private static void initializeMediaTypes() {
		MIME_TYPES.put("html", "text/html");
		MIME_TYPES.put("pdf", "application/pdf");
		// see http://filext.com/faq/office_mime_types.php
		MIME_TYPES.put("doc", "application/msword");
		MIME_TYPES.put("dot", "application/msword");
		MIME_TYPES
				.put("docx",
						"application/vnd.openxmlformats-officedocument.wordprocessingml.document");
		MIME_TYPES
				.put("dotx",
						"application/vnd.openxmlformats-officedocument.wordprocessingml.template");
		MIME_TYPES.put("docm",
				"application/vnd.ms-word.document.macroEnabled.12");
		MIME_TYPES.put("dotm",
				"application/vnd.ms-word.template.macroEnabled.12");
		MIME_TYPES.put("xls", "application/vnd.ms-excel");
		MIME_TYPES.put("xlt", "application/vnd.ms-excel");
		MIME_TYPES.put("xla", "application/vnd.ms-excel");
		MIME_TYPES
				.put("xlsx",
						"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		MIME_TYPES
				.put("xltx",
						"application/vnd.openxmlformats-officedocument.spreadsheetml.template");
		MIME_TYPES
				.put("xlsm", "application/vnd.ms-excel.sheet.macroEnabled.12");
		MIME_TYPES.put("xltm",
				"application/vnd.ms-excel.template.macroEnabled.12");
		MIME_TYPES
				.put("xlam", "application/vnd.ms-excel.addin.macroEnabled.12");
		MIME_TYPES.put("xlsb",
				"application/vnd.ms-excel.sheet.binary.macroEnabled.12");
		MIME_TYPES.put("ppt", "application/vnd.ms-powerpoint");
		MIME_TYPES.put("pot", "application/vnd.ms-powerpoint");
		MIME_TYPES.put("pps", "application/vnd.ms-powerpoint");
		MIME_TYPES.put("ppa", "application/vnd.ms-powerpoint");
		MIME_TYPES
				.put("pptx",
						"application/vnd.openxmlformats-officedocument.presentationml.presentation");
		MIME_TYPES
				.put("potx",
						"application/vnd.openxmlformats-officedocument.presentationml.template");
		MIME_TYPES
				.put("ppsx",
						"application/vnd.openxmlformats-officedocument.presentationml.slideshow");
		MIME_TYPES.put("ppam",
				"application/vnd.ms-powerpoint.addin.macroEnabled.12");
		MIME_TYPES.put("pptm",
				"application/vnd.ms-powerpoint.presentation.macroEnabled.12");
		MIME_TYPES.put("potm",
				"application/vnd.ms-powerpoint.template.macroEnabled.12");
		MIME_TYPES.put("ppsm",
				"application/vnd.ms-powerpoint.slideshow.macroEnabled.12");
	}

	public static String getMediaType(final String outputFormat) {
		final String mediaType = MIME_TYPES.get(outputFormat.toLowerCase());
		if (mediaType != null)
			return mediaType;
		return "application/octet-stream";
	}

	public static IReportEngine getReportEngine() throws BirtException {
		if (reportEngine != null) {
			return reportEngine;
		}
		final EngineConfig config = new EngineConfig();
		config.setLogConfig("/log", Level.WARNING);
		config.setResourcePath(getResourceDir().getAbsolutePath());
		Platform.startup(config);
		final IReportEngineFactory factory = (IReportEngineFactory) Platform
				.createFactoryObject(IReportEngineFactory.EXTENSION_REPORT_ENGINE_FACTORY);
		reportEngine = factory.createReportEngine(config);
		return reportEngine;
	}

	public static File getResourceDir() {
		return new File(RESOURCE_DIR, "resources");
	}
}
