package org.eclipse.birt.engine;

import java.util.logging.Level;

import org.eclipse.birt.core.exception.BirtException;
import org.eclipse.birt.core.framework.Platform;
import org.eclipse.birt.report.engine.api.EngineConfig;
import org.eclipse.birt.report.engine.api.IReportEngine;
import org.eclipse.birt.report.engine.api.IReportEngineFactory;

public class ReportEngine {
	private static IReportEngine reportEngine;

	public static IReportEngine getReportEngine() {
	
		if (reportEngine != null){
			System.out.println("Return Engine");
			return reportEngine;
		}
		System.out.println("Start Engine");
		EngineConfig config = new EngineConfig();
		config.setLogConfig("/log", Level.WARNING);
		try {
			Platform.startup(config);
			IReportEngineFactory factory = (IReportEngineFactory) Platform
					.createFactoryObject(IReportEngineFactory.EXTENSION_REPORT_ENGINE_FACTORY);
			reportEngine = factory.createReportEngine(config);
			
			return reportEngine;
		} catch (BirtException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		
		return null;

	}

}
