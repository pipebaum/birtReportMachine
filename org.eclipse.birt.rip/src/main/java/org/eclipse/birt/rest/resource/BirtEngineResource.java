package org.eclipse.birt.rest.resource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
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

	public BirtEngineResource() {
		final Properties properties = System.getProperties();
		final String resourceDirName = properties
				.getProperty("org.eclipse.birt.rip.resource.dir");
		resourceDir = new File(resourceDirName);
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
		final Map<String, Object> outputMap = new HashMap<>();
		outputMap.put("fileId", uuid.toString());
		final JSONObject jsonObject = JSONObject.fromObject(outputMap);
		return jsonObject.toString();
	}

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
	@Produces({ MediaType.TEXT_HTML })
	public StreamingOutput runReport(final String inputJsonString,
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
		final UUID outputFileId = UUID.randomUUID();
		final File outputFile = new File(resourceDir, outputFileId.toString()
				+ "." + outputFormat);
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(outputFile);
		} catch (final FileNotFoundException e) {
			throw new ServerErrorException(500, e);
		}
		IReportEngine reportEngine;
		reportEngine = ReportEngine.getReportEngine();
		IRunAndRenderTask runTask;
		try {
			final FileInputStream fis = new FileInputStream(file);
			final IReportRunnable design = reportEngine.openReportDesign(fis);
			runTask = reportEngine.createRunAndRenderTask(design);
			runTask.validateParameters();
			final RenderOption options = new HTMLRenderOption();
			options.setOutputFormat(outputFormat);
			options.setOutputStream(fos);
			runTask.setRenderOption(options);
			runTask.run();
			fos.close();
		} catch (final FileNotFoundException e) {
			throw new NotFoundException(e);
		} catch (final IOException e) {
			throw new ServerErrorException(500, e);
		} catch (final EngineException e) {
			throw new ServerErrorException(500, e);
		}
		@SuppressWarnings("unchecked")
		final List<EngineException> errors = runTask.getErrors();
		for (final EngineException engineException : errors) {
			System.out.println("ERROR:\t" + engineException.getMessage());
		}
		if (!errors.isEmpty())
			throw new ServerErrorException(errors.size()
					+ " error(s) encountered.  See log for details.", 500);
		return new StreamingOutput() {

			@Override
			public void write(final OutputStream output) throws IOException,
					WebApplicationException {
				final FileInputStream fis = new FileInputStream(outputFile);
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
}
