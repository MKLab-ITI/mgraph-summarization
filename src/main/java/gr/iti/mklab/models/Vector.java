package gr.iti.mklab.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import gr.iti.mklab.vocabulary.Vocabulary;

public class Vector implements Serializable {
	
	private static final long serialVersionUID = -6569373740959054635L;
	
	protected Map<String, Double> TFs = new HashMap<String, Double>();
	protected Double V = null;
	
	public Vector() {	
		
	}
	
	public Vector(Collection<String> tokens) {
		for(String token : tokens) {
			Double tf = TFs.get(token);
			if(tf == null) {
				tf = .0;
			}
			TFs.put(token, ++tf);
		}
	}
	
	public Vector(Map<String, Double> v) {
		this.TFs.putAll(v);
	}

	public void mergeVector(Vector vector) {
		for(Entry<String, Double> w : vector.TFs.entrySet()) {
			String word = w.getKey();
			
			Double tf = TFs.get(word);
			if(tf == null)
				tf = .0;
			
			Double k = w.getValue();
			tf = tf + k;
			TFs.put(word, tf);
			
			updateLength(word, tf, tf-k);
		}
	}
	
	public void subtrackVector(Vector vector) {
		for(Entry<String, Double> w : vector.TFs.entrySet()) {
			String word = w.getKey();
			
			Double tf = TFs.get(word);
			if(tf == null)
				tf = .0;
			
			Double k = w.getValue();
			tf = tf - k;
			if(tf == 0)
				TFs.remove(word);
			else
				TFs.put(word, tf);
			
			updateLength(word, tf, tf+k);
		}
	}
	
	public Set<String> getTerms() {
		Set<String> terms = new HashSet<String>(TFs.keySet());
		return terms;
	}
	
	public Double getTf(String word) {
		Double tf = TFs.get(word);
		if(tf == null)
			return 0d;
		
		return tf;
	}
	
	public Double getIdf(String term) {
		return Vocabulary.getIdf(term);	
	}
	
	public Double getTfIdf(String term) {
		Double tf = TFs.get(term);
		if(tf == null)
			return 0d;
		
		return tf * Vocabulary.getIdf(term);	
	}
	
	public Map<String, Double> getWordsMap() {
		return TFs;
	}
	
	public Double getLength() {
		if(V == null) {
			updateLength();
		}
		return V;
	}
	
	public Double updateLength() {
		V = 0d;
		for(String word : getTerms()) {
			double tf = getTf(word);
			double idf = getIdf(word);		
			double boost = Vocabulary.getBoost(word);		
				
			V += Math.pow(boost * tf * idf, 2);
		}
		return V;
	}
	
	
	public void updateLength(String word, Double newTf, Double oldTf) {
		if(V == null) {
			V = 0d;
		}
		Double idf = Vocabulary.getIdf(word);
		//V += (Math.pow((k/maxTf) * idf, 2) + (2 * (k/maxTf) * (tf/maxTf) * idf * idf));
		V += (Math.pow((newTf) * idf, 2) - Math.pow((oldTf) * idf, 2));
	}
	
	public Double cosine(Vector other) {
		
		Double similarity = 0d;
		
		Set<String> set1 = getTerms();
		Set<String> set2 = other.getTerms();

		for(String word : set1) {
			if(!set2.contains(word)) {
				continue;
			}
			
			double tf1 = this.getTf(word);
			double tf2 = other.getTf(word);
			double idf = Vocabulary.getIdf(word);
			double boost = Vocabulary.getBoost(word);
			
			similarity += (tf1 * tf2 * Math.pow(boost * idf, 2));
		}
		Double A = this.getLength();
		Double B = other.getLength();
		
		if(A == 0 || B == 0) {
			return 0d;
		}
		
		return similarity / (Math.sqrt(A) * Math.sqrt(B));
	}
	
	public Double tfIdfSimilarity(Vector other) {
		
		Double similarity = 0d;
		
		Set<String> set1 = getTerms();
		Set<String> set2 = other.getTerms();
		for(String word : set1) {
			if(!set2.contains(word))
				continue;
			
			double tf1 = this.getTf(word);
			double tf2 = other.getTf(word);
			double idf = Vocabulary.getIdf(word);
			double boost = Vocabulary.getBoost(word);
			
			similarity += (tf1 * tf2 * Math.pow(boost * idf, 2));
		}
	
		return similarity;
	}

	public Double getTfIdfWeight() {
		Double weight = 0d;

		for(String word : this.getTerms()) {	
			double tf1 = this.getTf(word);
			double idf = Vocabulary.getIdf(word);
			double boost = Vocabulary.getBoost(word);
			
			weight += (boost * tf1 * idf);
		}
		return weight;
	}
	
	@Override
	public String toString() {
		StringBuffer strBuff = new StringBuffer("{ words : [");
		
		for(Entry<String, Double> e : TFs.entrySet()) {
			String word = e.getKey();
			strBuff.append("{ word : " + word + ", tf : ");
			strBuff.append(e.getValue() + ", idf : " + Vocabulary.getIdf(word) + " }, ");
		}
		strBuff.append("], Length : " + getLength() + " }");
		
		return strBuff.toString();
	}	
	
	public static void fold(Map<String, Vector> vectorsMap, Collection<Collection<String>> clusters) {
		for(Collection<String> cluster : clusters) {
			List<String> list = new ArrayList<String>(cluster);
			Collections.sort(list);
			String clusterId = StringUtils.join(list, "-");
			
			Vector mergedVector = new Vector();
			for(String id : cluster) {
				Vector vector = vectorsMap.remove(id);
				if(vector != null) {
					mergedVector.mergeVector(vector);
				}
			}
			mergedVector.updateLength();
			vectorsMap.put(clusterId, mergedVector);
		}
	}
}
