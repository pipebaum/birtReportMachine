package org.eclipse.birt.rest.resource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.eclipse.birt.core.exception.BirtException;
import org.eclipse.birt.engine.ReportEngine;
import org.eclipse.birt.report.engine.api.EngineException;
import org.eclipse.birt.report.engine.api.HTMLRenderOption;
import org.eclipse.birt.report.engine.api.IReportEngine;
import org.eclipse.birt.report.engine.api.IReportRunnable;
import org.eclipse.birt.report.engine.api.IRunAndRenderTask;
import org.eclipse.birt.report.engine.api.RenderOption;

@Path("run")
public class BirtEngineResource {

	@GET
	@Produces( { MediaType.TEXT_HTML } )
	public String runReport () {
		System.out.println("run a report");
		IReportEngine reportEngine;
		try {
			reportEngine = ReportEngine.getReportEngine();
			File testFile = new File("fud.xml");
			
			File designFile = new File("script.RPTDESIGN");
			System.out.println("design file: " + designFile.getAbsolutePath());

			final FileInputStream fis = new FileInputStream(designFile);
			final IReportRunnable design = reportEngine.openReportDesign(fis);

			System.out.println("Start run");

			final IRunAndRenderTask runTask = reportEngine.createRunAndRenderTask(design);

			runTask.validateParameters();

			runTask.setRenderOption(getRenderOption());

			System.out.println("Building " );
			runTask.run();
			@SuppressWarnings("unchecked")
			List<EngineException> errors = (List<EngineException>)runTask.getErrors();
			for (EngineException engineException : errors) {
				System.out.println("ERROR:\t" + engineException.getMessage());
			}
			
			return runTask.getRenderOption().getOutputStream().toString();
		} catch (IOException e) {
			System.out.println("IOException" + e.getMessage());
			e.printStackTrace();
		} catch (BirtException e) {
			System.out.println("BirtException" + e.getMessage());
			e.printStackTrace();
		}
		
		
		return "<html><body>Something went wrongs</body></html>";
	}
	
	private RenderOption getRenderOption(){
		RenderOption options = new HTMLRenderOption();
		options.setOutputFormat(RenderOption.OUTPUT_FORMAT_HTML);
		options.setOutputStream(new ByteArrayOutputStream());
		
		return options;
		
	}
	
}
