package gr.iti.mklab.models;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import gr.iti.mklab.vocabulary.Vocabulary;

public class Vector implements Serializable {
	
	private static final long serialVersionUID = -6569373740959054635L;
	
	private Double maxTf = 0d;
	private Map<String, Integer> v = new HashMap<String, Integer>();
	private Set<String> wordSet = new HashSet<String>();
	
	private Double V = null;
	
	public Vector() {	
		
	}
	
	public Vector(List<String> tokens) {
		
		for(String token : tokens) {
			Integer tf = v.get(token);
			if(tf == null)
				tf = 0;
			v.put(token, ++tf);
			wordSet.add(token);
			
			if(maxTf < tf) {
				maxTf = tf.doubleValue();
			}
		}
	}
	
	public Vector(Map<String, Integer> v) {
		this.v.putAll(v);
		wordSet.addAll(v.keySet());
		
		maxTf = Collections.max(v.values()).doubleValue();			
	}

	public void mergeVector(Vector vector) {
		for(Entry<String, Integer> w : vector.v.entrySet()) {
			String word = w.getKey();
			
			Integer tf = v.get(word);
			if(tf == null)
				tf = 0;
			
			Integer k = w.getValue();
			tf = tf + k;
			v.put(word, tf);
			
			if(maxTf < tf) {
				maxTf = tf.doubleValue();
			}
			
			updateLength(word, tf, tf-k);
		}
		wordSet.addAll(vector.wordSet);
	}
	
	public void subtrackVector(Vector vector) {
		for(Entry<String, Integer> w : vector.v.entrySet()) {
			String word = w.getKey();
			
			Integer tf = v.get(word);
			if(tf == null)
				tf = 0;
			
			Integer k = w.getValue();
			tf = tf - k;
			if(tf == 0)
				v.remove(word);
			else
				v.put(word, tf);
			
			if(tf + k == maxTf && !v.isEmpty()) {
				maxTf = Collections.min(v.values()).doubleValue();
			}
			
			updateLength(word, tf, tf+k);
		}
		wordSet.addAll(vector.wordSet);
	}
	
	public Set<String> getWords() {
		return wordSet;
	}
	
	public Double getTf(String word) {
		Integer tf = v.get(word);
		if(tf == null || maxTf == 0)
			return 0d;
		
		return tf.doubleValue() / maxTf.doubleValue();
	}
	
	public Map<String, Integer> getWordsMap() {
		return v;
	}
	
	public Double getLength() {
		if(V == null) {
			V = 0d;
			for(String word : getWords()) {
				double tf = getTf(word);
				double idf = Vocabulary.getIdf(word);		
				double boost = Vocabulary.getBoost(word);		
				
				V += Math.pow(boost * tf * idf, 2);
			}
		}
		return V;
	}
	
	public void updateLength(String word, Integer newTf, Integer oldTf) {
		if(V == null)
			V = 0d;
		
		Double idf = Vocabulary.getIdf(word);
		//V += (Math.pow((k/maxTf) * idf, 2) + (2 * (k/maxTf) * (tf/maxTf) * idf * idf));
		V += (Math.pow((newTf/maxTf) * idf, 2) - Math.pow((oldTf/maxTf) * idf, 2));
	}
	
	public Double cosine(Vector other) {
		
		Double similarity = 0d;
		
		Set<String> set1 = getWords();
		Set<String> set2 = other.getWords();

		for(String word : set1) {
			if(!set2.contains(word))
				continue;
			
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
		
		Set<String> set1 = getWords();
		Set<String> set2 = other.getWords();
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

		for(String word : this.getWords()) {	
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
		
		for(Entry<String, Integer> e : v.entrySet()) {
			String word = e.getKey();
			strBuff.append("{ word : " + word + ", tf : ");
			strBuff.append(e.getValue() + ", idf : " + Vocabulary.getIdf(word) + " }, ");
		}
		strBuff.append("] , maxTf : " + maxTf + ", Length : " + getLength() + " }");
		
		return strBuff.toString();
	}	
}
