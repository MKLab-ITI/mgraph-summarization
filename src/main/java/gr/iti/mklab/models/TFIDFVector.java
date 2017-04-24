package gr.iti.mklab.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

public class TFIDFVector extends Vector {
	
	private static final long serialVersionUID = -6569373740959054635L;
	
	private Map<String, Double> IDFs = new HashMap<String, Double>();
	
	public TFIDFVector() {	
		
	}
	
	public TFIDFVector(Map<String, Pair<Double, Double>> terms) {
		for(Entry<String, Pair<Double, Double>> e : terms.entrySet()) {
			String term = e.getKey();
			Pair<Double, Double> tfidf = e.getValue();

			TFs.put(term, tfidf.left);
			IDFs.put(term, tfidf.right);
		}
		updateLength();
	}
	
	public void addTerms(Map<String, Pair<Double, Double>> terms) {
		for(Entry<String, Pair<Double, Double>> e : terms.entrySet()) {
			String term = e.getKey();
			Pair<Double, Double> tfidf = e.getValue();
			
			Double tf = TFs.get(term);
			if(tf == null) {
				tf = .0;
			}
			TFs.put(term, tf + tfidf.left);
			IDFs.put(term, tfidf.right);
		}
		updateLength();
	}
	
	public void mergeVector(TFIDFVector vector) {
		for(String term : vector.getTerms()) {
			
			Double tf = vector.getTf(term);
			Double idf = vector.getIdf(term);
			
			Double currentTF = this.getTf(term);
			if(currentTF == null || currentTF == 0) {
				currentTF = tf;
			}
			else {
				currentTF = Math.sqrt(Math.pow(tf, 2) + Math.pow(currentTF, 2));
			}
			TFs.put(term, currentTF);
			
			Double currentIDF = IDFs.get(term);
			if(currentIDF == null) {
				currentIDF = idf;
			}
			else {
				currentIDF = Math.min(idf, currentIDF);
			}
			IDFs.put(term, currentIDF);
		}
	}
	
	public void subtrackVector(TFIDFVector vector) {
		for(String term : vector.getTerms()) {
			Double tf = vector.getTf(term);
			Double currentTf = getTf(term);
			if(currentTf == null) {
				currentTf = tf;
			}
			else {
				if(currentTf > tf) {
					currentTf = Math.sqrt(Math.pow(currentTf, 2) - Math.pow(tf, 2));
				}
				else {
					currentTf = .0;
				}
			}
			TFs.put(term, currentTf);
		}
	}

	public Double getIdf(String term) {
		Double idf = IDFs.get(term);
		if(idf == null) {
			return 0d;
		}
		
		return idf;
	}
	
	public Double getTfIdf(String term) {
		Double tf = getTf(term);
		if(tf == null) {
			return 0d;
		}
	
		Double idf = getIdf(term);
		if(idf == null) {
			return 0d;
		}
		
		return tf * idf;
	}
	
	public Map<String, Pair<Double, Double>> getTfIdfMap() {
		Map<String, Pair<Double, Double>> map = new HashMap<String, Pair<Double, Double>>();
		for(String term : this.getTerms()) {
			Double tf = this.getTf(term);
			Double idf = this.getIdf(term);
			
			map.put(term, Pair.of(tf,  idf));
		}
		return map;
	}
	
	public Double getLength() {
		if(V == null) {
			V = 0d;
			for(String term : getTerms()) {
				double tfidf = getTfIdf(term);
				V += Math.pow(tfidf, 2);
			}
		}
		return V;
	}
	
	@Override
	public Double cosine(Vector other) {
		Double similarity = 0d;
		
		Set<String> set1 = getTerms();
		Set<String> set2 = other.getTerms();

		for(String term : set1) {
			if(!set2.contains(term)) {
				continue;
			}
			
			double tfidf1 = this.getTfIdf(term);
			double tfidf2 = other.getTfIdf(term);
			
			similarity += (tfidf1 * tfidf2);
		}
		Double A = this.getLength();
		Double B = other.getLength();
		
		if(A == 0 || B == 0) {
			return 0d;
		}
		
		return similarity / (Math.sqrt(A) * Math.sqrt(B));
	}
	
	public Double tfIdfSimilarity(TFIDFVector other) {
		
		Double similarity = 0d;
		
		Set<String> set1 = getTerms();
		Set<String> set2 = other.getTerms();
		for(String term : set1) {
			if(!set2.contains(term))
				continue;
			
			double tfidf1 = this.getTfIdf(term);
			double tfidf2 = other.getTfIdf(term);

			
			similarity += (tfidf1 * tfidf2);
		}
	
		return similarity;
	}

	public Double getTfIdfWeight() {
		Double weight = 0d;

		for(String term : this.getTerms()) {	
			double tfidf1 = this.getTfIdf(term);
			
			weight += (tfidf1);
		}
		return weight;
	}
	
	public static void fold(Map<String, TFIDFVector> vectorsMap, List<Set<String>> clusters) {
		for(Set<String> cluster : clusters) {
			List<String> list = new ArrayList<String>(cluster);
			Collections.sort(list);
			String clusterId = StringUtils.join(list, "-");
			
			TFIDFVector mergedVector = new TFIDFVector();
			for(String id : cluster) {
				TFIDFVector vector = vectorsMap.remove(id);
				if(vector != null) {
					mergedVector.mergeVector(vector);
				}
			}
			vectorsMap.put(clusterId, mergedVector);
		}
	}
}
