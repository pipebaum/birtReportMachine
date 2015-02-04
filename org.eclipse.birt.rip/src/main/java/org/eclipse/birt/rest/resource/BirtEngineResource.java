package org.eclipse.birt.rest.resource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import net.sf.json.JSONObject;

import org.eclipse.birt.engine.ReportEngine;
import org.eclipse.birt.report.engine.api.EngineException;
import org.eclipse.birt.report.engine.api.HTMLRenderOption;
import org.eclipse.birt.report.engine.api.IReportEngine;
import org.eclipse.birt.report.engine.api.IReportRunnable;
import org.eclipse.birt.report.engine.api.IRunAndRenderTask;
import org.eclipse.birt.report.engine.api.RenderOption;

@Path("run")
public class BirtEngineResource {
	private final File resourceDir;
	private final Map<UUID, Long> files = new HashMap<>();
	private final Thread terminator;
	private final long timeToLive; // ten minutes

	public BirtEngineResource() {
		final Properties properties = System.getProperties();
		// resource dir: where to put files, required
		final String resourceDirName = properties
				.getProperty("org.eclipse.birt.rip.resource.dir");
		if (resourceDirName == null)
			throw new NullPointerException(
					"property: org.eclipse.birt.rip.resource.dir");
		resourceDir = new File(resourceDirName);
		purgeResourceDir();
		// time-to-live: how long to keep files with no activity (in ms),
		// defaults to ten minutes
		final String ttlString = properties
				.getProperty("org.eclipse.birt.rip.file.ttl");
		timeToLive = ttlString == null ? 10 * 60 * 1000 : Long
				.valueOf(ttlString);
		// terminator thread deletes files when they get old enough
		final Runnable runnable = new TerminatorRunnable();
		terminator = new Thread(runnable, "terminator");
		terminator.start();
	}

	private final void purgeResourceDir() {
		final File[] files = resourceDir.listFiles();
		if (files == null)
			return;
		for (final File file : files) {
			if (file.isDirectory())
				continue;
			// there shouldn't be any directories, but if there are, ignore them
			file.delete();
		}
	}

	private static class FileInfo {
		public final UUID uuid;
		public final long endTime;

		public FileInfo(final UUID uuid, final long endTime) {
			if (uuid == null)
				throw new NullPointerException("uuid");
			this.uuid = uuid;
			this.endTime = endTime;
		}
	}

	private List<FileInfo> getFileInfoList() {
		final List<FileInfo> list = new ArrayList<>();
		for (final UUID uuid : files.keySet()) {
			Long endTime = files.get(uuid);
			if (endTime == null)
				endTime = Long.valueOf(0);
			list.add(new FileInfo(uuid, endTime.longValue()));
		}
		list.sort(new Comparator<FileInfo>() {

			@Override
			public int compare(final FileInfo o1, final FileInfo o2) {
				final String s1 = o1.toString();
				final String s2 = o2.toString();
				return s1.compareTo(s2);
			}
		});
		return list;
	}

	private void deleteFile(final UUID uuid) {
		System.out.println("deleting file " + uuid);
		files.remove(uuid);
		final File file = new File(resourceDir, uuid.toString());
		file.delete();
	}

	private class TerminatorRunnable implements Runnable {
		private long sleepTime = Long.MAX_VALUE;

		@Override
		public void run() {
			while (true) {
				try {
					Thread.sleep(sleepTime);
				} catch (final InterruptedException e) {
					// interrupted when a new file is added
				}
				final List<FileInfo> list = getFileInfoList();
				if (list.isEmpty()) {
					sleepTime = Long.MAX_VALUE;
					continue;
				}
				long nextEndTime = 0;
				for (final FileInfo fileInfo : list) {
					if (fileInfo.endTime < System.currentTimeMillis()) {
						deleteFile(fileInfo.uuid);
						continue;
					}
					nextEndTime = fileInfo.endTime;
					break;
				}
				sleepTime = nextEndTime - System.currentTimeMillis();
				System.out.println("setting sleep time to "
						+ (sleepTime / 1000.0) + " seconds");
			}
		}
	}

	@POST
	@Path("/report/upload")
	@Consumes({ MediaType.APPLICATION_OCTET_STREAM })
	@Produces({ MediaType.APPLICATION_JSON })
	public String uploadReport(final java.io.Reader reader) throws IOException {
		final UUID uuid = UUID.randomUUID();
		resourceDir.mkdirs();
		final File file = new File(resourceDir, uuid.toString());
		final FileWriter writer = new FileWriter(file);
		try {
			final char[] buffer = new char[0x1000];
			int charsRead = reader.read(buffer);
			while (charsRead >= 0) {
				writer.write(buffer, 0, charsRead);
				charsRead = reader.read(buffer);
			}
		} finally {
			writer.close();
		}
		System.out.println("adding file " + uuid);
		final long endTime = System.currentTimeMillis() + timeToLive;
		files.put(uuid, Long.valueOf(endTime));
		terminator.interrupt();
		final Map<String, Object> outputMap = new HashMap<>();
		outputMap.put("fileId", uuid.toString());
		final JSONObject jsonObject = JSONObject.fromObject(outputMap);
		return jsonObject.toString();
	}

	// no practical use for this but it's handy for testing
	@GET
	@Path("/report/download/{fileId}")
	@Produces({ MediaType.APPLICATION_OCTET_STREAM })
	public StreamingOutput downloadReport(
			@PathParam("fileId") final String fileIdString) {
		final File file = new File(resourceDir, fileIdString);
		return new StreamingOutput() {

			@Override
			public void write(final OutputStream output) throws IOException,
					WebApplicationException {
				final FileInputStream fis = new FileInputStream(file);
				try {
					final byte[] buffer = new byte[0x1000];
					int bytesRead = fis.read(buffer);
					while (bytesRead >= 0) {
						output.write(buffer, 0, bytesRead);
						bytesRead = fis.read(buffer);
					}
				} finally {
					fis.close();
				}
			}
		};
	}

	@POST
	@Path("/report/run/{outputFormat}/{fileId}")
	@Consumes({ MediaType.APPLICATION_JSON })
	public Response runReport(final String inputJsonString,
			@PathParam("outputFormat") final String outputFormat,
			@PathParam("fileId") final String fileIdString) {
		final JSONObject jsonObject = JSONObject.fromString(inputJsonString);
		final Iterator<?> iterator = jsonObject.keys();
		while (iterator.hasNext()) {
			final Object object = iterator.next();
			System.out.println(object);
		}
		// jsonObject holds the report parameters, if any
		final File file = new File(resourceDir, fileIdString);
		final StreamingOutput entity = new StreamingOutput() {

			@Override
			public void write(final OutputStream output) throws IOException,
					WebApplicationException {
				IReportEngine reportEngine;
				reportEngine = ReportEngine.getReportEngine();
				IRunAndRenderTask runTask;
				try {
					final FileInputStream fis = new FileInputStream(file);
					final IReportRunnable design = reportEngine
							.openReportDesign(fis);
					runTask = reportEngine.createRunAndRenderTask(design);
					runTask.validateParameters();
					final RenderOption options = new HTMLRenderOption();
					options.setOutputFormat(outputFormat);
					options.setOutputStream(output);
					runTask.setRenderOption(options);
					runTask.run();
				} catch (final FileNotFoundException e) {
					throw new NotFoundException(e);
				} catch (final EngineException e) {
					throw new ServerErrorException(500, e);
				}
				@SuppressWarnings("unchecked")
				final List<EngineException> errors = runTask.getErrors();
				for (final EngineException engineException : errors) {
					System.out.println("ERROR:\t"
							+ engineException.getMessage());
				}
				if (!errors.isEmpty())
					throw new ServerErrorException(errors.size()
							+ " error(s) encountered.  See log for details.",
							500);
				/*
				 * final FileInputStream fis = new FileInputStream(outputFile);
				 * try { final byte[] buffer = new byte[0x1000]; int bytesRead =
				 * fis.read(buffer); while (bytesRead >= 0) {
				 * output.write(buffer, 0, bytesRead); bytesRead =
				 * fis.read(buffer); } } finally { fis.close(); }
				 */
			}
		};
		return Response.ok(entity, getMediaType(outputFormat)).build();
	}

	private static String getMediaType(final String outputFormat) {
		final String mediaType = MIME_TYPES.get(outputFormat.toLowerCase());
		if (mediaType != null)
			return mediaType;
		return "application/octet-stream";
	}

	private static final Map<String, String> MIME_TYPES = new HashMap<>();
	static {
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
}
