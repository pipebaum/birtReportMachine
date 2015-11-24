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
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.InternalServerErrorException;
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

import org.eclipse.birt.core.exception.BirtException;
import org.eclipse.birt.engine.ReportEngine;
import org.eclipse.birt.report.engine.api.EngineException;
import org.eclipse.birt.report.engine.api.HTMLRenderOption;
import org.eclipse.birt.report.engine.api.IEngineTask;
import org.eclipse.birt.report.engine.api.IGetParameterDefinitionTask;
import org.eclipse.birt.report.engine.api.IParameterDefn;
import org.eclipse.birt.report.engine.api.IParameterDefnBase;
import org.eclipse.birt.report.engine.api.IParameterSelectionChoice;
import org.eclipse.birt.report.engine.api.IReportEngine;
import org.eclipse.birt.report.engine.api.IReportRunnable;
import org.eclipse.birt.report.engine.api.IRunAndRenderTask;
import org.eclipse.birt.report.engine.api.IRunTask;
import org.eclipse.birt.report.engine.api.IScalarParameterDefn;
import org.eclipse.birt.report.engine.api.RenderOption;
import org.eclipse.birt.report.engine.api.impl.ParameterValidationException;
import org.eclipse.birt.report.model.api.DesignElementHandle;
import org.eclipse.birt.report.model.api.elements.structures.IncludeScript;
import org.eclipse.birt.report.model.api.elements.structures.IncludedCssStyleSheet;
import org.eclipse.birt.report.model.api.elements.structures.IncludedLibrary;

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

	private static void deleteFile(final UUID uuid) {
		System.out.println("deleting file " + uuid);
		FILES.remove(uuid);
		final File file = new File(ReportEngine.RESOURCE_DIR, uuid.toString());
		file.delete();
	}

	private static class TerminatorRunnable implements Runnable {
		@Override
		public void run() {
			long sleepTime = getNextSleepTime();
			while (true) {
				try {
					Thread.sleep(sleepTime);
				}
				catch (final InterruptedException e) {
					// interrupted when a new file is added
				}
				sleepTime = getNextSleepTime();
			}
		}

		private long getNextSleepTime() {
			final List<FileInfo> list = new ArrayList<>();
			for (final UUID uuid : FILES.keySet()) {
				Long endTime = FILES.get(uuid);
				if (endTime == null)
					endTime = Long.valueOf(0);
				list.add(new FileInfo(uuid, endTime.longValue()));
			}
			if (list.isEmpty()) {
				System.out.println("Sleeping until interrupted");
				return Long.MAX_VALUE;
			}
			// sort by end time
			Collections.sort(list, new Comparator<FileInfo>() {
				@Override
				public int compare(final FileInfo o1, final FileInfo o2) {
					if (o1.endTime < o2.endTime)
						return -1;
					if (o1.endTime > o2.endTime)
						return 1;
					return 0;
				}
			});
			long nextEndTime = 0;
			for (final FileInfo fileInfo : list) {
				if (fileInfo.endTime <= System.currentTimeMillis()) {
					deleteFile(fileInfo.uuid);
					continue;
				}
				nextEndTime = fileInfo.endTime;
				// stop processing the list once we hit an end time that's in
				// the future
				break;
			}
			long sleepTime = nextEndTime - System.currentTimeMillis();
			if (sleepTime < 0)
				sleepTime = 0;
			System.out.println("sleeping " + (sleepTime / 1000.0) + " seconds");
			return sleepTime;
		}
	}

	@POST
	@Path("/upload{name : (/[^/]+?)+?}")
	@Consumes({ MediaType.APPLICATION_OCTET_STREAM })
	@Produces({ MediaType.APPLICATION_JSON })
	public String uploadResource(@PathParam("name") final String name,
			final java.io.Reader reader) throws IOException {
		System.out.println("POST /upload/" + name);
		final File resourceDir = ReportEngine.getResourceDir();
		final File file = new File(resourceDir, name);
		file.getParentFile().mkdirs();
		file.delete();
		file.createNewFile();
		final FileWriter writer = new FileWriter(file);
		try {
			final char[] buffer = new char[0x1000];
			int charsRead = reader.read(buffer);
			while (charsRead >= 0) {
				writer.write(buffer, 0, charsRead);
				charsRead = reader.read(buffer);
			}
		}
		finally {
			writer.close();
		}
		System.out.println("adding resource file " + file);
		final Map<String, Object> outputMap = new HashMap<>();
		final JSONObject jsonObject = JSONObject.fromObject(outputMap);
		return jsonObject.toString();
	}

	@POST
	@Path("/upload")
	@Consumes({ MediaType.APPLICATION_OCTET_STREAM })
	@Produces({ MediaType.APPLICATION_JSON })
	public String uploadReport(final java.io.Reader reader) throws IOException {
		System.out.println("POST upload");
		final UUID uuid = UUID.randomUUID();
		ReportEngine.RESOURCE_DIR.mkdirs();
		final File file = new File(ReportEngine.RESOURCE_DIR, uuid.toString());
		file.delete();
		file.createNewFile();
		final FileWriter writer = new FileWriter(file);
		try {
			final char[] buffer = new char[0x1000];
			int charsRead = reader.read(buffer);
			while (charsRead >= 0) {
				writer.write(buffer, 0, charsRead);
				charsRead = reader.read(buffer);
			}
		}
		finally {
			writer.close();
		}
		System.out.println("adding file " + file);
		final long endTime = System.currentTimeMillis()
				+ ReportEngine.TIME_TO_LIVE;
		FILES.put(uuid, Long.valueOf(endTime));
		TERMINATOR.interrupt();
		final Map<String, Object> outputMap = new HashMap<>();
		outputMap.put("fileId", uuid.toString());
		try {
			final List<String> resourceFiles = discoverResources(file);
			outputMap.put("resourceFiles", resourceFiles);
		}
		catch (final BirtException e) {
			// most likely, this is not a report design
			outputMap.put("error", e.toString());
		}
		final JSONObject jsonObject = JSONObject.fromObject(outputMap);
		return jsonObject.toString();
	}

	private List<String> discoverResources(final File file)
			throws FileNotFoundException, BirtException {
		final Set<String> set = new HashSet<String>();
		final FileInputStream fis = new FileInputStream(file);
		final IReportEngine reportEngine = ReportEngine.getReportEngine();
		final IReportRunnable design = reportEngine.openReportDesign(fis);
		// final IReportDesign designInstance = design.getDesignInstance();
		final DesignElementHandle deh = design.getDesignHandle();
		// final FactoryElementHandle feh = deh.getFactoryElementHandle();
		// final FactoryPropertyHandle fph = feh
		// .getFactoryPropertyHandle("includeResource");
		// final Object includeResource = deh.getProperty("includeResource");
		@SuppressWarnings("unchecked")
		final List<Object> scripts = deh.getListProperty("includeScripts");
		if (scripts != null) {
			for (final Object scriptObj : scripts) {
				if (scriptObj instanceof IncludeScript) {
					final IncludeScript includeScript = (IncludeScript) scriptObj;
					final String fileName = includeScript.getFileName();
					if (fileName != null)
						set.add(fileName);
				}
			}
		}
		@SuppressWarnings("unchecked")
		final List<Object> libraries = deh.getListProperty("libraries");
		if (libraries != null) {
			for (final Object libraryObj : libraries) {
				if (libraryObj instanceof IncludedLibrary) {
					final IncludedLibrary includeLibrary = (IncludedLibrary) libraryObj;
					final String fileName = includeLibrary.getFileName();
					if (fileName != null)
						set.add(fileName);
				}
			}
		}
		@SuppressWarnings("unchecked")
		final List<Object> cssStyleSheets = deh
				.getListProperty("cssStyleSheets");
		if (cssStyleSheets != null) {
			for (final Object cssStyleSheetObj : cssStyleSheets) {
				if (cssStyleSheetObj instanceof IncludedCssStyleSheet) {
					final IncludedCssStyleSheet includedCssStyleSheet = (IncludedCssStyleSheet) cssStyleSheetObj;
					final String fileName = includedCssStyleSheet.getFileName();
					if (fileName != null)
						set.add(fileName);
				}
			}
		}
		return new ArrayList<>(set);
	}

	// no practical use for this but it's handy for testing
	@GET
	@Path("/download/{fileId}")
	@Produces({ MediaType.APPLICATION_OCTET_STREAM })
	public StreamingOutput downloadReport(
			@PathParam("fileId") final String fileIdString) {
		System.out.println("GET /download/ " + fileIdString);
		final File file = new File(ReportEngine.RESOURCE_DIR, fileIdString);
		final FileInputStream fis;
		try {
			fis = new FileInputStream(file);
		}
		catch (final FileNotFoundException e) {
			throw new NotFoundException(e.getMessage(), e);
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
				}
				finally {
					fis.close();
				}
				// reset the timer
				FILES.put(
						UUID.fromString(fileIdString),
						Long.valueOf(System.currentTimeMillis()
								+ ReportEngine.TIME_TO_LIVE));
			}
		};
	}

	@GET
	@Path("/parameter/choices/{fileId}/{parameterName}")
	@Produces({ MediaType.APPLICATION_JSON })
	public String getParameterChoices(
			@PathParam("fileId") final String fileIdString,
			@PathParam("parameterName") final String parameterName)
			throws FileNotFoundException, BirtException {
		System.out.println("GET /parameter/choices/" + fileIdString + "/"
				+ parameterName);
		final File file = new File(ReportEngine.RESOURCE_DIR, fileIdString);
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
		}
		finally {
			task.close();
		}
	}

	@GET
	@Path("/parameters/{fileId}")
	@Produces({ MediaType.APPLICATION_JSON })
	public String getParameters(@PathParam("fileId") final String fileIdString)
			throws IOException, BirtException {
		System.out.println("GET /parameters/" + fileIdString);
		final File file = new File(ReportEngine.RESOURCE_DIR, fileIdString);
		System.out.println("file: " + file.getAbsolutePath());
		final FileInputStream fis = new FileInputStream(file);
		final IReportEngine reportEngine = ReportEngine.getReportEngine();
		System.out.println("reportEngine = " + reportEngine);
		final IReportRunnable design = reportEngine.openReportDesign(fis);
		System.out.println("reportDesign = " + design);
		final IGetParameterDefinitionTask task = reportEngine
				.createGetParameterDefinitionTask(design);
		System.out.println("task = " + task);
		try {
			@SuppressWarnings("unchecked")
			final Collection<Object> parameterDefns = task
					.getParameterDefns(true);
			final JSONArray jsonArray = new JSONArray();
			for (final Object object : parameterDefns) {
				System.out.println("parameterDefn = " + object);
				if (!(object instanceof IScalarParameterDefn)) {
					System.out.println("  unsupported");
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
				System.out.println("map = " + map);
				jsonArray.add(map);
			}
			// reset the timer
			FILES.put(
					UUID.fromString(fileIdString),
					Long.valueOf(System.currentTimeMillis()
							+ ReportEngine.TIME_TO_LIVE));
			System.out.println("returning " + jsonArray);
			return jsonArray.toString();
		}
		catch (final Exception e) {
			System.out.println("Exception: " + e);
			throw e;
		}
		finally {
			task.close();
		}
	}

	@SuppressWarnings("unchecked")
	@POST
	@Path("/run/rptdocument/{fileId}")
	@Consumes({ MediaType.APPLICATION_JSON })
	public Response runRptDoc(final String inputJsonString,
			@PathParam("fileId") final String fileIdString) {
		System.out.println("POST /run/rptdocument/" + fileIdString + ": "
				+ inputJsonString);
		final JSONObject paramsJsonObject = JSONObject
				.fromObject(inputJsonString);
		final File inputFile = new File(ReportEngine.RESOURCE_DIR, fileIdString);
		final File outputFile = new File(ReportEngine.RESOURCE_DIR,
				fileIdString + ".rptdocument");
		List<EngineException> errors = null;
		try {
			final IReportEngine reportEngine = ReportEngine.getReportEngine();
			final FileInputStream fis = new FileInputStream(inputFile);
			final IReportRunnable design = reportEngine.openReportDesign(fis);
			final IGetParameterDefinitionTask paramTask = reportEngine
					.createGetParameterDefinitionTask(design);
			try {
				final IRunTask runTask = reportEngine.createRunTask(design);
				final Map<String, Object> appContext = runTask.getAppContext();
				runTask.setAppContext(appContext);
				try {
					setParameterValues(runTask, paramsJsonObject, paramTask);
					// final boolean valid =
					// runTask.validateParameters();
					runTask.run(outputFile.getAbsolutePath());
					errors = runTask.getErrors();
				}
				finally {
					runTask.close();
				}
			}
			finally {
				paramTask.close();
			}
		}
		catch (final ParameterValidationException e) {
			System.out.println("Exception in /run/rptdocument/ " + fileIdString
					+ ": " + e);
			throw new NotAcceptableException(e.getMessage(), e);
		}
		catch (final FileNotFoundException e) {
			System.out.println("Exception in /run/rptdocument/ " + fileIdString
					+ ": " + e);
			throw new NotFoundException(e.getMessage(), e);
		}
		catch (final BirtException e) {
			System.out.println("Exception in /run/rptdocument/" + fileIdString
					+ ": " + e);
			throw new ServerErrorException(e.getMessage(), 500, e);
		}
		if (errors != null) {
			for (final EngineException engineException : errors) {
				System.out.println("ERROR in /run/rptdocument/" + fileIdString
						+ ": " + engineException.getMessage());
			}
			if (!errors.isEmpty())
				throw new ServerErrorException(errors.size()
						+ " error(s) encountered.  See log for details.", 500);
		}
		final FileInputStream fis;
		try {
			fis = new FileInputStream(outputFile);
		}
		catch (final FileNotFoundException e) {
			System.out.println("Exception in /run/rptdocument/" + fileIdString
					+ ": " + e);
			throw new InternalServerErrorException(
					"Unable to open generated rptdocument file", e);
		}
		final StreamingOutput entity = new StreamingOutput() {
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
				}
				finally {
					fis.close();
				}
				// reset the timer
				FILES.put(
						UUID.fromString(fileIdString),
						Long.valueOf(System.currentTimeMillis()
								+ ReportEngine.TIME_TO_LIVE));
			}
		};
		// reset the timer
		FILES.put(
				UUID.fromString(fileIdString),
				Long.valueOf(System.currentTimeMillis()
						+ ReportEngine.TIME_TO_LIVE));
		return Response.ok(entity, MediaType.APPLICATION_OCTET_STREAM_TYPE)
				.build();
	}

	@POST
	@Path("/run/{outputFormat}/{fileId}")
	@Consumes({ MediaType.APPLICATION_JSON })
	public Response runReport(final String inputJsonString,
			@PathParam("outputFormat") final String outputFormat,
			@PathParam("fileId") final String fileIdString) {
		System.out.println("POST /run/" + outputFormat + "/" + fileIdString
				+ ": " + inputJsonString);
		final JSONObject paramsJsonObject = JSONObject
				.fromObject(inputJsonString);
		final File file = new File(ReportEngine.RESOURCE_DIR, fileIdString);
		final StreamingOutput entity = new StreamingOutput() {
			@SuppressWarnings("unchecked")
			@Override
			public void write(final OutputStream output) throws IOException,
					WebApplicationException {
				List<EngineException> errors = null;
				try {
					final IReportEngine reportEngine = ReportEngine
							.getReportEngine();
					final FileInputStream fis = new FileInputStream(file);
					final IReportRunnable design = reportEngine
							.openReportDesign(fis);
					final IGetParameterDefinitionTask paramTask = reportEngine
							.createGetParameterDefinitionTask(design);
					try {
						final IRunAndRenderTask rrTask = reportEngine
								.createRunAndRenderTask(design);
						final Map<String, Object> appContext = rrTask
								.getAppContext();
						rrTask.setAppContext(appContext);
						try {
							setParameterValues(rrTask, paramsJsonObject,
									paramTask);
							// final boolean valid =
							// runTask.validateParameters();
							final RenderOption options = new HTMLRenderOption();
							options.setOutputFormat(outputFormat);
							options.setOutputStream(output);
							rrTask.setRenderOption(options);
							rrTask.run();
							errors = rrTask.getErrors();
						}
						finally {
							rrTask.close();
						}
					}
					finally {
						paramTask.close();
					}
				}
				catch (final ParameterValidationException e) {
					throw new NotAcceptableException(e.getMessage(), e);
				}
				catch (final FileNotFoundException e) {
					throw new NotFoundException(e.getMessage(), e);
				}
				catch (final BirtException e) {
					throw new ServerErrorException(e.getMessage(), 500, e);
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
				FILES.put(
						UUID.fromString(fileIdString),
						Long.valueOf(System.currentTimeMillis()
								+ ReportEngine.TIME_TO_LIVE));
				/*
				 * final FileInputStream fis = new FileInputStream(outputFile);
				 * try { final byte[] buffer = new byte[0x1000]; int bytesRead =
				 * fis.read(buffer); while (bytesRead >= 0) {
				 * output.write(buffer, 0, bytesRead); bytesRead =
				 * fis.read(buffer); } } finally { fis.close(); }
				 */
			}
		};
		return Response.ok(entity, ReportEngine.getMediaType(outputFormat))
				.build();
	}

	private void setParameterValues(final IEngineTask engineTask,
			final JSONObject paramsJsonObject,
			final IGetParameterDefinitionTask paramTask) {
		final Iterator<?> iterator = paramsJsonObject.keys();
		while (iterator.hasNext()) {
			final Object keyObj = iterator.next();
			if (!(keyObj instanceof String))
				continue;
			final String paramName = (String) keyObj;
			Object paramValue = paramsJsonObject.get(paramName);
			final IParameterDefnBase pdb = paramTask
					.getParameterDefn(paramName);
			if (!(pdb instanceof IScalarParameterDefn))
				continue;
			final IScalarParameterDefn parameterDefn = (IScalarParameterDefn) pdb;
			if (parameterDefn.getParameterType() != IScalarParameterDefn.SCALAR_PARAMETER)
				continue;
			final int birtDataType = parameterDefn.getDataType();
			try {
				switch (birtDataType) {
				case IParameterDefn.TYPE_STRING:
					paramValue = paramValue.toString();
					break;
				case IParameterDefn.TYPE_FLOAT:
					if (paramValue instanceof String) {
						paramValue = Float.valueOf((String) paramValue);
					}
					else if (paramValue instanceof Float) {
					}
					else if (paramValue instanceof Number) {
						paramValue = Float.valueOf(((Number) paramValue)
								.floatValue());
					}
					break;
				case IParameterDefn.TYPE_DECIMAL:
					if (paramValue instanceof String) {
						paramValue = BigDecimal.valueOf(Double.valueOf(
								(String) paramValue).doubleValue());
					}
					else if (paramValue instanceof BigDecimal) {
					}
					else if (paramValue instanceof Number) {
						paramValue = BigDecimal.valueOf(((Number) paramValue)
								.doubleValue());
					}
					break;
				case IParameterDefn.TYPE_DATE_TIME:
					if (paramValue instanceof String) {
						final DateFormat df = new SimpleDateFormat(
								"yyyy-MM-dd HH:mm:ss");
						paramValue = df.parse((String) paramValue);
					}
					else if (paramValue instanceof Date) {
					}
					else if (paramValue instanceof Long) {
						paramValue = new Date(((Long) paramValue).longValue());
					}
					break;
				case IParameterDefn.TYPE_BOOLEAN:
					if (paramValue instanceof String) {
						paramValue = Boolean.valueOf("true"
								.equalsIgnoreCase((String) paramValue));
					}
					else if (paramValue instanceof Boolean) {
					}
					else if (paramValue instanceof Number) {
						paramValue = Boolean.valueOf(((Number) paramValue)
								.doubleValue() != 0);
					}
					break;
				case IParameterDefn.TYPE_INTEGER:
					if (paramValue instanceof String) {
						paramValue = Integer.valueOf((String) paramValue);
					}
					else if (paramValue instanceof Integer) {
					}
					else if (paramValue instanceof Number) {
						paramValue = Integer.valueOf(((Number) paramValue)
								.intValue());
					}
					break;
				case IParameterDefn.TYPE_DATE:
					if (paramValue instanceof String) {
						final DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
						paramValue = new java.sql.Date(df.parse(
								(String) paramValue).getTime());
					}
					else if (paramValue instanceof java.sql.Date) {
					}
					else if (paramValue instanceof Date) {
						paramValue = new java.sql.Date(
								((Date) paramValue).getTime());
					}
					else if (paramValue instanceof Long) {
						paramValue = new java.sql.Date(
								((Long) paramValue).longValue());
					}
					break;
				case IParameterDefn.TYPE_TIME:
					if (paramValue instanceof String) {
						final DateFormat df = new SimpleDateFormat("HH:mm:ss");
						new java.sql.Time(df.parse((String) paramValue)
								.getTime());
					}
					else if (paramValue instanceof java.sql.Time) {
					}
					else if (paramValue instanceof Date) {
						paramValue = new java.sql.Time(
								((Date) paramValue).getTime());
					}
					else if (paramValue instanceof Long) {
						paramValue = new java.sql.Time(
								((Long) paramValue).longValue());
					}
					break;
				}
			}
			catch (final Exception e) {
				throw new NotAcceptableException(e.getMessage(), e);
			}
			engineTask.setParameterValue(paramName, paramValue);
		}
	}

	private static final Map<UUID, Long> FILES = new HashMap<>();
	private static final Thread TERMINATOR;
	static {
		// terminator thread deletes files when they get old enough
		final Runnable runnable = new TerminatorRunnable();
		TERMINATOR = new Thread(runnable, "terminator");
		TERMINATOR.start();
	}
}
