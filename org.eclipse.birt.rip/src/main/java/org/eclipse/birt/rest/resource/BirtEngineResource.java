package org.eclipse.birt.rest.resource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.NotAcceptableException;
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

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.eclipse.birt.engine.ReportEngine;
import org.eclipse.birt.report.engine.api.EngineException;
import org.eclipse.birt.report.engine.api.HTMLRenderOption;
import org.eclipse.birt.report.engine.api.IGetParameterDefinitionTask;
import org.eclipse.birt.report.engine.api.IParameterDefn;
import org.eclipse.birt.report.engine.api.IParameterDefnBase;
import org.eclipse.birt.report.engine.api.IParameterSelectionChoice;
import org.eclipse.birt.report.engine.api.IReportEngine;
import org.eclipse.birt.report.engine.api.IReportRunnable;
import org.eclipse.birt.report.engine.api.IRunAndRenderTask;
import org.eclipse.birt.report.engine.api.IScalarParameterDefn;
import org.eclipse.birt.report.engine.api.RenderOption;
import org.eclipse.birt.report.engine.api.impl.ParameterValidationException;

@Path("report")
public class BirtEngineResource {

	public BirtEngineResource() {
		System.out.println("constructor");
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

	private static List<FileInfo> getFileInfoList() {
		final List<FileInfo> list = new ArrayList<>();
		for (final UUID uuid : FILES.keySet()) {
			Long endTime = FILES.get(uuid);
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

	private static void deleteFile(final UUID uuid) {
		System.out.println("deleting file " + uuid);
		FILES.remove(uuid);
		final File file = new File(RESOURCE_DIR, uuid.toString());
		file.delete();
	}

	private static class TerminatorRunnable implements Runnable {

		@Override
		public void run() {
			long sleepTime = Long.MAX_VALUE;
			System.out.println("sleeping until interrupted");
			while (true) {
				if (sleepTime > 0)
					try {
						Thread.sleep(sleepTime);
					} catch (final InterruptedException e) {
						// interrupted when a new file is added
					}
				final List<FileInfo> list = getFileInfoList();
				if (list.isEmpty()) {
					sleepTime = Long.MAX_VALUE;
					System.out.println("sleeping until interrupted");
					continue;
				}
				long nextEndTime = 0;
				for (final FileInfo fileInfo : list) {
					if (fileInfo.endTime <= System.currentTimeMillis()) {
						deleteFile(fileInfo.uuid);
						continue;
					}
					nextEndTime = fileInfo.endTime;
					break;
				}
				sleepTime = nextEndTime - System.currentTimeMillis();
				System.out.println("sleeping " + (sleepTime / 1000.0)
						+ " seconds");
			}
		}
	}

	@POST
	@Path("/upload")
	@Consumes({ MediaType.APPLICATION_OCTET_STREAM })
	@Produces({ MediaType.APPLICATION_JSON })
	public String uploadReport(final java.io.Reader reader) throws IOException {

		final UUID uuid = UUID.randomUUID();
		RESOURCE_DIR.mkdirs();
		final File file = new File(RESOURCE_DIR, uuid.toString());
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
		final long endTime = System.currentTimeMillis() + TIME_TO_LIVE;
		FILES.put(uuid, Long.valueOf(endTime));
		TERMINATOR.interrupt();
		final Map<String, Object> outputMap = new HashMap<>();
		outputMap.put("fileId", uuid.toString());
		final JSONObject jsonObject = JSONObject.fromObject(outputMap);
		return jsonObject.toString();
	}

	// no practical use for this but it's handy for testing
	@GET
	@Path("/download/{fileId}")
	@Produces({ MediaType.APPLICATION_OCTET_STREAM })
	public StreamingOutput downloadReport(
			@PathParam("fileId") final String fileIdString) {
		final File file = new File(RESOURCE_DIR, fileIdString);
		final FileInputStream fis;
		try {
			fis = new FileInputStream(file);
		} catch (final FileNotFoundException e) {
			throw new NotFoundException(e);
		}
		return new StreamingOutput() {

			@Override
			public void write(final OutputStream output) throws IOException,
					WebApplicationException {
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
				// reset the timer
				FILES.put(UUID.fromString(fileIdString),
						Long.valueOf(System.currentTimeMillis() + TIME_TO_LIVE));
			}
		};
	}

	@GET
	@Path("/parameter/choices/{fileId}/{parameterName}")
	@Produces({ MediaType.APPLICATION_JSON })
	public String getParameterChoices(
			@PathParam("fileId") final String fileIdString,
			@PathParam("parameterName") final String parameterName)
			throws FileNotFoundException, EngineException {
		final File file = new File(RESOURCE_DIR, fileIdString);
		final FileInputStream fis = new FileInputStream(file);
		final IReportEngine reportEngine = ReportEngine.getReportEngine();
		final IReportRunnable design = reportEngine.openReportDesign(fis);
		final IGetParameterDefinitionTask task = reportEngine
				.createGetParameterDefinitionTask(design);
		try {
			@SuppressWarnings("unchecked")
			final Collection<Object> birtChoices = task
					.getSelectionList(parameterName);
			final JSONArray choices = new JSONArray();
			for (final Object object : birtChoices) {
				if (!(object instanceof IParameterSelectionChoice))
					continue;
				final IParameterSelectionChoice birtChoice = (IParameterSelectionChoice) object;
				final String label = birtChoice.getLabel();
				final Object value = birtChoice.getValue();
				final Map<String, Object> map = new HashMap<>();
				map.put("label", label);
				map.put("value", value);
				choices.add(map);
			}
			return choices.toString();
		} finally {
			task.close();
		}
	}

	@GET
	@Path("/parameters/{fileId}")
	@Produces({ MediaType.APPLICATION_JSON })
	public String getParameters(@PathParam("fileId") final String fileIdString)
			throws IOException, EngineException {
		final File file = new File(RESOURCE_DIR, fileIdString);
		final FileInputStream fis = new FileInputStream(file);
		final IReportEngine reportEngine = ReportEngine.getReportEngine();
		final IReportRunnable design = reportEngine.openReportDesign(fis);
		final IGetParameterDefinitionTask task = reportEngine
				.createGetParameterDefinitionTask(design);
		try {
			@SuppressWarnings("unchecked")
			final Collection<Object> parameterDefns = task
					.getParameterDefns(true);
			final JSONArray jsonArray = new JSONArray();
			for (final Object object : parameterDefns) {
				if (!(object instanceof IScalarParameterDefn)) {
					// only support scalar parameters for now
					continue;
				}
				final IScalarParameterDefn parameterDefn = (IScalarParameterDefn) object;
				final Map<String, Object> map = new HashMap<>();
				map.put("name", parameterDefn.getName());
				map.put("displayName", parameterDefn.getDisplayName());
				map.put("helpText", parameterDefn.getHelpText());
				final int birtParamType = parameterDefn.getParameterType();
				final String type;
				switch (birtParamType) {
				case IParameterDefnBase.FILTER_PARAMETER:
					type = "filter";
					break;
				case IParameterDefnBase.LIST_PARAMETER:
					type = "list";
					break;
				case IParameterDefnBase.TABLE_PARAMETER:
					type = "table";
					break;
				case IParameterDefnBase.PARAMETER_GROUP:
					type = "parameter-group";
					break;
				case IParameterDefnBase.CASCADING_PARAMETER_GROUP:
					type = "cascading-parameter-group";
					break;
				case IParameterDefnBase.SCALAR_PARAMETER:
				default:
					type = "scalar";
				}
				map.put("type", type);
				// type should be SCALAR
				map.put("promptText", parameterDefn.getPromptText());
				final int birtDataType = parameterDefn.getDataType();
				final String dataType;
				switch (birtDataType) {
				case IParameterDefn.TYPE_STRING:
					dataType = "string";
					break;
				case IParameterDefn.TYPE_FLOAT:
					dataType = "float";
					break;
				case IParameterDefn.TYPE_DECIMAL:
					dataType = "decimal";
					break;
				case IParameterDefn.TYPE_DATE_TIME:
					dataType = "date-time";
					break;
				case IParameterDefn.TYPE_BOOLEAN:
					dataType = "boolean";
					break;
				case IParameterDefn.TYPE_INTEGER:
					dataType = "integer";
					break;
				case IParameterDefn.TYPE_DATE:
					dataType = "date";
					break;
				case IParameterDefn.TYPE_TIME:
					dataType = "time";
					break;
				case IParameterDefn.TYPE_ANY:
				default:
					dataType = "any";
				}
				map.put("dataType", dataType);
				map.put("hidden", parameterDefn.isHidden());
				map.put("required", parameterDefn.isRequired());
				map.put("allowNewValues", parameterDefn.allowNewValues());
				map.put("displayInFixedOrder",
						parameterDefn.displayInFixedOrder());
				map.put("valueConcealed", parameterDefn.isValueConcealed());
				map.put("displayFormat", parameterDefn.getDisplayFormat());
				final String controlType;
				switch (parameterDefn.getControlType()) {
				case IScalarParameterDefn.LIST_BOX:
					controlType = "list-box";
					break;
				case IScalarParameterDefn.RADIO_BUTTON:
					controlType = "radio-button";
					break;
				case IScalarParameterDefn.CHECK_BOX:
					controlType = "check-box";
					break;
				case IScalarParameterDefn.AUTO_SUGGEST:
					controlType = "auto-suggest";
					break;
				case IScalarParameterDefn.TEXT_BOX:
				default:
					controlType = "text-box";
				}
				map.put("controlType", controlType);
				final String alignment;
				switch (parameterDefn.getAlignment()) {
				case IScalarParameterDefn.LEFT:
					alignment = "left";
					break;
				case IScalarParameterDefn.CENTER:
					alignment = "center";
					break;
				case IScalarParameterDefn.RIGHT:
					alignment = "right";
					break;
				case IScalarParameterDefn.AUTO:
				default:
					alignment = "auto";
				}
				map.put("alignment", alignment);
				map.put("defaultValue", parameterDefn.getDefaultValue());
				map.put("scalarParameterType",
						parameterDefn.getScalarParameterType());
				map.put("autoSuggestThreshold",
						parameterDefn.getAutoSuggestThreshold());
				jsonArray.add(map);
			}
			// reset the timer
			FILES.put(UUID.fromString(fileIdString),
					Long.valueOf(System.currentTimeMillis() + TIME_TO_LIVE));
			return jsonArray.toString();
		} finally {
			task.close();
		}
	}

	@POST
	@Path("/run/{outputFormat}/{fileId}")
	@Consumes({ MediaType.APPLICATION_JSON })
	public Response runReport(final String inputJsonString,
			@PathParam("outputFormat") final String outputFormat,
			@PathParam("fileId") final String fileIdString) {
		final JSONObject paramsJsonObject = JSONObject
				.fromObject(inputJsonString);
		final File file = new File(RESOURCE_DIR, fileIdString);
		final StreamingOutput entity = new StreamingOutput() {

			@SuppressWarnings("unchecked")
			@Override
			public void write(final OutputStream output) throws IOException,
					WebApplicationException {
				final IReportEngine reportEngine = ReportEngine
						.getReportEngine();
				List<EngineException> errors = null;
				IRunAndRenderTask runTask;
				try {
					final FileInputStream fis = new FileInputStream(file);
					final IReportRunnable design = reportEngine
							.openReportDesign(fis);
					final IGetParameterDefinitionTask paramTask = reportEngine
							.createGetParameterDefinitionTask(design);
					try {
						runTask = reportEngine.createRunAndRenderTask(design);
						try {
							final Iterator<?> iterator = paramsJsonObject
									.keys();
							while (iterator.hasNext()) {
								final Object keyObj = iterator.next();
								if (!(keyObj instanceof String))
									continue;
								final String paramName = (String) keyObj;
								Object paramValue = paramsJsonObject
										.get(paramName);
								final IParameterDefnBase pdb = paramTask
										.getParameterDefn(paramName);
								if (!(pdb instanceof IScalarParameterDefn))
									continue;
								final IScalarParameterDefn parameterDefn = (IScalarParameterDefn) pdb;
								if (parameterDefn.getParameterType() != IScalarParameterDefn.SCALAR_PARAMETER)
									continue;
								final int birtDataType = parameterDefn
										.getDataType();
								try {
									switch (birtDataType) {
									case IParameterDefn.TYPE_STRING:
										paramValue = paramValue.toString();
										break;
									case IParameterDefn.TYPE_FLOAT:
										if (paramValue instanceof String) {
											paramValue = Float
													.valueOf((String) paramValue);

										} else if (paramValue instanceof Float) {
										} else if (paramValue instanceof Number) {
											paramValue = Float
													.valueOf(((Number) paramValue)
															.floatValue());
										}
										break;
									case IParameterDefn.TYPE_DECIMAL:
										if (paramValue instanceof String) {
											paramValue = BigDecimal
													.valueOf(Double
															.valueOf(
																	(String) paramValue)
															.doubleValue());
										} else if (paramValue instanceof BigDecimal) {

										} else if (paramValue instanceof Number) {
											paramValue = BigDecimal
													.valueOf(((Number) paramValue)
															.doubleValue());
										}
										break;
									case IParameterDefn.TYPE_DATE_TIME:
										if (paramValue instanceof String) {
											final DateFormat df = new SimpleDateFormat(
													"yyyy-MM-dd HH:mm:ss");
											paramValue = df
													.parse((String) paramValue);
										} else if (paramValue instanceof Date) {
										} else if (paramValue instanceof Long) {
											paramValue = new Date(
													((Long) paramValue)
															.longValue());
										}
										break;
									case IParameterDefn.TYPE_BOOLEAN:
										if (paramValue instanceof String) {
											paramValue = Boolean
													.valueOf("true"
															.equalsIgnoreCase((String) paramValue));
										} else if (paramValue instanceof Boolean) {
										} else if (paramValue instanceof Number) {
											paramValue = Boolean
													.valueOf(((Number) paramValue)
															.doubleValue() != 0);
										}
										break;
									case IParameterDefn.TYPE_INTEGER:
										if (paramValue instanceof String) {
											paramValue = Integer
													.valueOf((String) paramValue);

										} else if (paramValue instanceof Integer) {
										} else if (paramValue instanceof Number) {
											paramValue = Integer
													.valueOf(((Number) paramValue)
															.intValue());
										}
										break;
									case IParameterDefn.TYPE_DATE:
										if (paramValue instanceof String) {
											final DateFormat df = new SimpleDateFormat(
													"yyyy-MM-dd");
											paramValue = df
													.parse((String) paramValue);
										} else if (paramValue instanceof Date) {
										} else if (paramValue instanceof Long) {
											paramValue = new Date(
													((Long) paramValue)
															.longValue());
										}
										break;
									case IParameterDefn.TYPE_TIME:
										if (paramValue instanceof String) {
											final DateFormat df = new SimpleDateFormat(
													"HH:mm:ss");
											paramValue = df
													.parse((String) paramValue);
										} else if (paramValue instanceof Date) {
										} else if (paramValue instanceof Long) {
											paramValue = new Date(
													((Long) paramValue)
															.longValue());
										}
										break;
									}
								} catch (final Exception e) {
									throw new NotAcceptableException(e);
								}
								runTask.setParameterValue(paramName, paramValue);
							}
							// final boolean valid =
							// runTask.validateParameters();
							final RenderOption options = new HTMLRenderOption();
							options.setOutputFormat(outputFormat);
							options.setOutputStream(output);
							runTask.setRenderOption(options);
							runTask.run();
							errors = runTask.getErrors();
						} finally {
							runTask.close();
						}
					} finally {
						paramTask.close();
					}
				} catch (final ParameterValidationException e) {
					throw new NotAcceptableException(e);
				} catch (final FileNotFoundException e) {
					throw new NotFoundException(e);
				} catch (final EngineException e) {
					throw new ServerErrorException(500, e);
				}
				if (errors != null) {
					for (final EngineException engineException : errors) {
						System.out.println("ERROR:\t"
								+ engineException.getMessage());
					}
					if (!errors.isEmpty())
						throw new ServerErrorException(
								errors.size()
										+ " error(s) encountered.  See log for details.",
								500);
				}
				// reset the timer
				FILES.put(UUID.fromString(fileIdString),
						Long.valueOf(System.currentTimeMillis() + TIME_TO_LIVE));
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

	private static void purgeResourceDir() {
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

	private static final Map<String, String> MIME_TYPES = new HashMap<>();
	private static final File RESOURCE_DIR;
	private static final Map<UUID, Long> FILES = new HashMap<>();
	private static final Thread TERMINATOR;
	private static final long TIME_TO_LIVE; // ten minutes
	static {
		initializeMediaTypes();
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
		// terminator thread deletes files when they get old enough
		final Runnable runnable = new TerminatorRunnable();
		TERMINATOR = new Thread(runnable, "terminator");
		TERMINATOR.start();
	}
}
