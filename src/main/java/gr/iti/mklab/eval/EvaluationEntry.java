package gr.iti.mklab.eval;

import gr.iti.mklab.eval.PerformanceMetric.EvaluationMeasures;
import gr.iti.mklab.utils.IOConstants;

import java.text.DecimalFormat;


public class EvaluationEntry {

	protected EvaluationMeasures performance;
	
	// entry
	public EvaluationEntry(String submissionFilename, EvaluationMeasures performance) {
		super();
		if (!submissionFilename.endsWith("csv")){
			throw new IllegalArgumentException("String: " + submissionFilename + " is not a proper CSV filename.");
		}
		
		String[] parts = submissionFilename.substring(0, submissionFilename.length()-4).split(IOConstants.UNDERSCORE);
		
		if (parts.length != 4){
			throw new IllegalArgumentException("String: " + submissionFilename + " is not a proper mediaeval submission filename.");
		}
		
		this.performance = performance;
	}

	public EvaluationMeasures getPerformance() {
		return performance;
	}
	
	@Override
	public String toString() {
		
		return  
			DF4.format(Math.abs(getPerformance().getNMI())) + IOConstants.TAB + 
			DF.format(100*getPerformance().getIrMetric().precision) + IOConstants.TAB +
			DF.format(100*getPerformance().getIrMetric().recall) + IOConstants.TAB + 
			DF.format(100*getPerformance().getIrMetric().getFMeasure());
	}
	
	private static final DecimalFormat DF = new DecimalFormat("#.##");
	private static final DecimalFormat DF4 = new DecimalFormat("#.####");
	
	
}
