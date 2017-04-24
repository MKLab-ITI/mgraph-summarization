package gr.iti.mklab.eval;

import gr.iti.mklab.utils.IOConstants;

import java.text.DecimalFormat;
import java.util.Set;


/**
 * There are two widely used performance metrics in the community detection
 * literature. 
 * 1. The more simple is the percentage of correctly identified nodes.
 * 2. The second more sophisticated is the so-called Normalized Mutual Information
 * 	For that, we need to first define a N: N1xN2 confusion matrix, where the N1 rows
 *  correspond to the 'real' communities and the N2 columns to the detected ones. 
 *  Then Nij is the number of nodes in real community i that appear in the found 
 *  community j. The formula used to calculate NMI is given in (Danon et al. 2005)
 *  
 * @author papadop
 *
 */
public class PerformanceMetric {

	
	private int numberOfObjects = 0;
	private int[][] confusionN = new int[1][1];
	private int[] groundTruthClusterSizes = new int[1];
	private int[] testClusterSizes = new int[1];
	private int Nreal = 1;
	private int Nfound = 1;
	
	public PerformanceMetric(int numberOfObjects, 
			int[] groundTruthClusterSizes, int[] testClusterSizes) {
		this.numberOfObjects = numberOfObjects;
		this.groundTruthClusterSizes = groundTruthClusterSizes;
		this.testClusterSizes = testClusterSizes;
		this.Nreal = groundTruthClusterSizes.length;
		this.Nfound = testClusterSizes.length;
		this.confusionN = new int[Nreal][Nfound];
	}
	
	public void setConfusionMatrixElement(int i, int j, int Nij){
		if ( (i<0) || (i>= Nreal) ){
			throw new IllegalArgumentException("Real community index negative or larger than number of communities!");
		}
		if ( (j<0) || (j>=Nfound) ){
			throw new IllegalArgumentException("Found community index negative or larger than number of communities!");
		}
		
		confusionN[i][j] = Nij;
	}
	
	public void printConfusionMatrix(){
		for (int i = 0; i < Nreal; i++){
			for (int j = 0; j < Nfound; j++){
				System.out.print(confusionN[i][j] + "\t");
			}
			System.out.println();
		}
	}
	
	
	public static class EvaluationMeasures {
		protected double NMI;
		protected IRMetric IrMetric;
		
		public EvaluationMeasures(double nMI, IRMetric irMetric) {
			super();
			NMI = nMI;
			IrMetric = irMetric;
		}

		public double getNMI() {
			return NMI;
		}

		public IRMetric getIrMetric() {
			return IrMetric;
		}
		
		@Override
		public String toString() {
			return "NMI = " + DF4.format(getNMI()) + IOConstants.TAB +
				"P = " + DF.format(100*getIrMetric().precision) + IOConstants.TAB +
				"R = " + DF.format(100*getIrMetric().recall) + IOConstants.TAB +
				"F = " + DF.format(100*getIrMetric().getFMeasure());
		}
		
		 private static final DecimalFormat DF = new DecimalFormat("#.##");
		 private static final DecimalFormat DF4 = new DecimalFormat("#.####");
		
	}
	
	public class IRMetric {
		public double precision;
		public double recall;
		
		public IRMetric(double precision, double recall) {
			super();
			this.precision = precision;
			this.recall = recall;
		}

		public double getFMeasure(){
			if (precision > 0 || recall > 0){
				return (2.0*precision*recall) / (precision + recall);
			} else {
				return Double.NaN;
				//throw new IllegalStateException("IRMetric has not been initialized!");
			}
		}
	}
	
	public IRMetric computeIRMetric(Set<Long> correctIds, Set<Long> foundIds){
		
		int matches = 0;
		for (Long id : foundIds){
			if (correctIds.contains(id)){
				matches++;
			}
		}
		
		double precision = (double)matches / (double)foundIds.size();
		double recall = (double)matches / (double)correctIds.size();

		IRMetric metric = new IRMetric(precision, recall);
		
		return metric;
	}
	
	
	public IRMetric computeIRMetric(){
		
		int totalNumberOfCorrectInstances = 0;
		for (int i = 0; i < groundTruthClusterSizes.length; i++){
			totalNumberOfCorrectInstances += groundTruthClusterSizes[i];
		}
		
		int totalNumberOfFoundInstances = 0;
		for (int i = 0; i < testClusterSizes.length; i++){
			totalNumberOfFoundInstances += testClusterSizes[i];
		}
		
//		int sumCorrect = 0;
//		for (int i = 0; i < Nreal; i++){
//			for (int j = i; j < Nfound; j++){
//				sumCorrect += confusionN[i][j];
//			}
//		}
		
		int allCorrect = 0;
		for (int i = 0; i < Nreal; i++){
			for (int j = 0; j < Nfound; j++){
				allCorrect += confusionN[i][j];
			}
		}
		
		//System.out.println(allCorrect + " out of " + totalNumberOfCorrectInstances + " / " + totalNumberOfFoundInstances);
	
		
		double precision = (double)allCorrect / (double)totalNumberOfFoundInstances;
		double recall = (double)allCorrect / (double)totalNumberOfCorrectInstances;

		IRMetric metric = new IRMetric(precision, recall);
		
		return metric;
	}
	
	/**
	 * Normalized Mutual Information
	 * @return
	 */
	public double calculateNMI(){
		
		double ln2 = Math.log(2);
		
		if ( (numberOfObjects < 1) || (Nreal < 1) || (Nfound < 1)){
			throw new IllegalStateException("The object PerformanceMetric has not been properly initialized!");
		}
		
		double numerator = 0.0;
		for (int i = 0; i < Nreal; i++){
			for (int j = 0; j < Nfound; j++){
				int nij = confusionN[i][j];
				if (nij == 0){
					continue;
				}
				int nia = groundTruthClusterSizes[i];
				int nib = testClusterSizes[j];
				numerator += nij * Math.log( (nij * numberOfObjects) / ((double)(nia * nib))) / ln2;
			}
		}
		
		double den1 = 0.0;
		for (int i = 0; i < Nreal; i++){
			int nia = groundTruthClusterSizes[i];
			den1 += nia * Math.log(nia/(double)numberOfObjects) / ln2;
		}
		double den2 = 0.0;
		for (int j = 0; j < Nfound; j++){
			int nib = testClusterSizes[j];
			den2 += nib * Math.log(nib/(double)numberOfObjects) / ln2;
		}
		
		return (-2.0*numerator)/(den1+den2);
	}

	
}
