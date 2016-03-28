package gr.iti.mklab.models;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class Topic implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -8503122984355381771L;
	
	private Integer topicId;
	private Map<String, Double> words;
	
	private ExtendedTimeline timeline;
	
	public Topic(int id, Map<String, Double> words) {
		this.topicId = id;
		this.words = words;
	}
	
	public Integer getId() {
		return topicId;
	}
	
	public Map<String, Double> getWords() {
		return words;
	}
	
	public void setTimeline(ExtendedTimeline timeline) {
		this.timeline = timeline;
	}
	
	public ExtendedTimeline getTimeline() {
		return timeline;
	}
	
	public double similarity(Topic other) {
		double similarity = 0;
		Map<String, Double> V1 = this.getWords();
		Map<String, Double> V2 = other.getWords();
		
		for(Entry<String, Double> e1 : V1.entrySet()) {
			String word = e1.getKey();
			if(V2.containsKey(word)) {
				Double w1 = e1.getValue();
				Double w2 = V2.get(word);
				
				similarity += w1*w2;
			}
		}
		
		double L1 = 0;
		for(Double w1 : V1.values()) {
			L1 += w1*w1;
		}
		L1 = Math.sqrt(L1);
		
		double L2 = 0;
		for(Double w2 : V2.values()) {
			L2 += w2*w2;
		}
		L2 = Math.sqrt(L2);
		
		if(L1==0 || L2==0)
			return 0;
		
		return similarity / (L1*L2);
	}
	
	public double distance(Topic other) {
		
		Set<String> K1 = this.getWords().keySet();
		Set<String> K2 = other.getWords().keySet();
		
		Set<String> words = new HashSet<String>();
		words.addAll(K1);
		words.addAll(K2);
		
		//words.retainAll(K2);
		//if(words.isEmpty())
		//	return Math.sqrt(2);
		
		Double[] v1 = new Double[words.size()];
		Double[] v2 = new Double[words.size()];
		
		int i = 0;
		for(String word : words) {
			
			v1[i] = K1.contains(word) ? this.getWords().get(word) : 0;
			v2[i] = K2.contains(word) ? other.getWords().get(word) : 0;
			i++;
		}
		
		double L1 = 0, L2 = 0;
		for(i=0; i<v1.length; i++) {
			L1 += v1[i]*v1[i];
			L2 += v2[i]*v2[i];
		}
		L1 = Math.sqrt(L1);
		L2 = Math.sqrt(L2);
		
		for(i=0; i<v1.length; i++) {
			v1[i] = v1[i]/L1;
			v2[i] = v2[i]/L2;
		}
		
		double distance = 0;
		for(i=0; i<v1.length; i++) {
			distance += Math.pow(v1[i]-v2[i], 2);
		}
			
		return Math.sqrt(distance);
	}
	
	public String toString() {
		return "{ id :" + topicId + ", " +
				"peaks : " + timeline.getPeakWindows().size() + ", " +
				"items : " + timeline.getTotal() + ", " +
				"peakiness : " + timeline.getPeakiness() + ", " +
				"words : " + words +  "}";
	}
	
	public static double getTopicsAvgSimilarity(Map<Integer, Topic> topics) {
		Integer total = 0;
		Double similarity = 0D;
		for(int i=0; i<topics.size()-1; i++) {
			for(int j=i+1; j<topics.size(); j++) {
				Topic topic1 = topics.get(i);
				Topic topic2 = topics.get(j);				
				similarity += topic1.similarity(topic2);
				total++;
			}
		}
		return similarity/total.doubleValue();
	}
}
