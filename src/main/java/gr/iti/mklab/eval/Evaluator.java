package gr.iti.mklab.eval;

import gr.iti.mklab.eval.PerformanceMetric.EvaluationMeasures;
import gr.iti.mklab.eval.PerformanceMetric.IRMetric;
import gr.iti.mklab.utils.IOConstants;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;

public class Evaluator {

	
	public static void main(String[] args) throws IOException {
		
		String groundTruthFile = "/disk1_data/workspace/git/social-media-summarization/gt-events.txt";
		String testFile = "/disk1_data/workspace/git/social-media-summarization/detected-events.txt";
		
		EvaluationMeasures measures = evaluate(groundTruthFile, testFile);
		 System.out.println(measures);
			
	}
	
	public static void runComparativeEvaluation(String sumbission, String gt, String outputFile) {
		try {
			BufferedWriter writer = null;
			if(outputFile != null){
				writer = new BufferedWriter(
					new OutputStreamWriter(new FileOutputStream(outputFile), "UTF8"));
			}
			File csvFile = new File(sumbission);
			List<EvaluationEntry> measures = new ArrayList<EvaluationEntry>();
			measures.add(new EvaluationEntry(csvFile.getName(), evaluate(gt, csvFile.getAbsolutePath())));
			for(EvaluationEntry entry : measures){
				try {
					if(writer!=null){
						writer.write(entry.toString());
						writer.newLine();
					}
					System.out.println(entry);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if(writer!=null)
				writer.close();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void runComparativeEvaluation(String root, String gt1, String gt2, String gt3, String outputFile) {
		try {
			BufferedWriter writer = new BufferedWriter(
					new OutputStreamWriter(new FileOutputStream(outputFile), "UTF8"));
			File f = new File(root);
			for(File submissionsDirectory : f.listFiles()) {
				writer.write(submissionsDirectory.getName());
				writer.newLine();
				List<EvaluationEntry> results = printMediaevalResults(gt1, gt2, gt3,submissionsDirectory.toString());
				for(EvaluationEntry entry : results){
					try {
						writer.write(entry.toString());
						writer.newLine();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}	
			writer.close();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
	}
	
	public static List<EvaluationEntry> printMediaevalResults(String challenge1groundTruthFile, 
			String challenge2groundTruthFile, String challenge3groundTruthFile, String submissionsDirectory) throws IOException{
		

		File directory = new File(submissionsDirectory);
		File[] csvFiles = directory.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				if (pathname.getAbsolutePath().endsWith("csv")){
					return true;
				} else {
					return false;
				}
			}
		});
		
		List<EvaluationEntry> measures = new ArrayList<EvaluationEntry>();
		
		for (int i = 0; i < csvFiles.length; i++){
			String evalFilename = csvFiles[i].getName(); 
			if (evalFilename.contains(CHALLENGE1_MAGIC_TOKEN1) ||
					evalFilename.contains(CHALLENGE1_MAGIC_TOKEN2)) {
				// challenge 1
				measures.add(new EvaluationEntry(
						evalFilename, evaluate(challenge1groundTruthFile, csvFiles[i].getAbsolutePath())));
			} else if(evalFilename.contains(CHALLENGE2_MAGIC_TOKEN1) ||
					evalFilename.contains(CHALLENGE2_MAGIC_TOKEN2)){
				// challenge 2
				measures.add(new EvaluationEntry(
						evalFilename, evaluate(challenge2groundTruthFile, csvFiles[i].getAbsolutePath())));
			}else {
				// challenge 3
				measures.add(new EvaluationEntry(
						evalFilename, evaluate(challenge3groundTruthFile, csvFiles[i].getAbsolutePath())));
			}
		}

		return measures;
	}
	
	private static final String CHALLENGE1_MAGIC_TOKEN1 = "_C1_";
	private static final String CHALLENGE1_MAGIC_TOKEN2 = "_1_";
	
	private static final String CHALLENGE2_MAGIC_TOKEN1 = "_C2_";
	private static final String CHALLENGE2_MAGIC_TOKEN2 = "_2_";
	
	
	public static EvaluationMeasures evaluate(String groundTruthFile, String testFile) throws IOException {
		List<String> groundTruthLines = IOUtils.readLines(new FileInputStream(groundTruthFile));
		List<String> testLines = IOUtils.readLines(new FileInputStream(testFile));
		
		Set<Long> uniquePhotoIds = new HashSet<Long>();
		Set<Long> correctPhotoIds = new HashSet<Long>();
		int[] groundTruthClusterSizes = new int[groundTruthLines.size()];
		for (int i = 0; i < groundTruthLines.size(); i++){
			String line = groundTruthLines.get(i);
			long[] ids = getPhotoIds(line);
			groundTruthClusterSizes[i] = ids.length;
			
			for (int x = 0; x < ids.length; x++) {
				uniquePhotoIds.add(ids[x]);
				correctPhotoIds.add(ids[x]);
			}
		}
		
		Set<Long> foundPhotoIds = new HashSet<Long>();
		int[] testClusterSizes = new int[testLines.size()];
		for (int i = 0; i < testLines.size(); i++){
			String line = testLines.get(i);
			long[] ids = getPhotoIds(line);
			testClusterSizes[i] = ids.length;
			
			for (int x = 0; x < ids.length; x++){
				uniquePhotoIds.add(ids[x]);
				foundPhotoIds.add(ids[x]);
			}
		}
		
		// set N to be the number of all photos in the result set
		PerformanceMetric perf = new PerformanceMetric(
				uniquePhotoIds.size(), groundTruthClusterSizes, testClusterSizes);
		
		for (int i = 0; i < groundTruthLines.size(); i++){
			
			long[] groundTruthIds = getPhotoIds(groundTruthLines.get(i));
			Set<Long> groundTruthSet = new HashSet<Long>();
			for (int x = 0; x < groundTruthIds.length; x++){
				groundTruthSet.add(groundTruthIds[x]);
			}
			
			for (int j = 0; j < testLines.size(); j++) {
				int Nij = 0;
				long[] testIds = getPhotoIds(testLines.get(j));
				for (int y = 0; y < testIds.length; y++) {
					if (groundTruthSet.contains(testIds[y])){
						Nij++;
					}
				}
				perf.setConfusionMatrixElement(i, j, Nij);
			}
		}
		
		
		
		double nmi = perf.calculateNMI();
		IRMetric ir = perf.computeIRMetric(correctPhotoIds, foundPhotoIds);
		
		EvaluationMeasures measures = new EvaluationMeasures(nmi, ir);
		
//		System.out.println("NMI:\t" + nmi);
//		System.out.println("P:\t" + ir.precision);
//		System.out.println("R:\t" + ir.recall);
//		System.out.println("F-m:\t" + ir.getFMeasure());
		//perf.printConfusionMatrix();
		
		return measures;
	}
	
	public static void listIncorrectPhotoIds(String challenge1groundTruthFile, 
			String challenge2groundTruthFile, String submissionsDirectory, 
			String outputFileChallenge1, String outputFileChallenge2) throws FileNotFoundException, IOException{
		File directory = new File(submissionsDirectory);
		File[] csvFiles = directory.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				if (pathname.getAbsolutePath().endsWith("csv")){
					return true;
				} else {
					return false;
				}
			}
		});
		
		Set<String> incorrectIdsChallenge1 = new HashSet<String>();
		Set<String> incorrectIdsChallenge2 = new HashSet<String>();
		
		for (int i = 0; i < csvFiles.length; i++){
			String evalFilename = csvFiles[i].getName(); 
			String groundTruthFilename = challenge2groundTruthFile;
			boolean isChallenge1 = false;
			if (evalFilename.contains(CHALLENGE1_MAGIC_TOKEN1) ||
					evalFilename.contains(CHALLENGE1_MAGIC_TOKEN2)) {
				// challenge 1
				groundTruthFilename = challenge1groundTruthFile;
				isChallenge1 = true;
			} 
			
			List<String> groundTruthLines = IOUtils.readLines(new FileInputStream(groundTruthFilename));
			Set<Long> correctPhotoIds = new HashSet<Long>();
			for (int x = 0; x < groundTruthLines.size(); x++){
				long[] ids = getPhotoIds(groundTruthLines.get(x));
				for (int y = 0; y < ids.length; y++) {
					correctPhotoIds.add(ids[y]);
				}
			}
			List<String> testLines = IOUtils.readLines(new FileInputStream(csvFiles[i].getAbsolutePath()));
			Set<Long> foundPhotoIds = new HashSet<Long>();
			for (int x = 0; x < testLines.size(); x++) {
				long[] ids = getPhotoIds(testLines.get(x));
				for (int y = 0; y < ids.length; y++) {
					foundPhotoIds.add(ids[y]);
				}
			}
			
			for (Long foundId : foundPhotoIds) {
				if (!correctPhotoIds.contains(foundId)){
					if (isChallenge1){
						incorrectIdsChallenge1.add(String.valueOf(foundId));
					} else {
						incorrectIdsChallenge2.add(String.valueOf(foundId));
					}
				}
			}
		}
		//IOUtils.writeStringCollectionToFile(incorrectIdsChallenge1, outputFileChallenge1);
		//IOUtils.writeStringCollectionToFile(incorrectIdsChallenge2, outputFileChallenge2);
	}
	
	
	public static long[] getPhotoIds(String line) {
		String[] parts = line.split(IOConstants.COMMA);
		long[] ids = new long[parts.length];
		for (int i = 0; i < parts.length; i++) {
			if (parts[i].trim().length() < 1) {
				continue;
			}
			ids[i] = Long.parseLong(parts[i].trim());
		}
		return ids;
	}
}
