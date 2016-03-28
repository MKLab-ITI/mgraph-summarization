package gr.iti.mklab.summarization;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import gr.iti.mklab.models.Pair;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.utils.Sorter;

public class CentroidSummarizer implements Summarizer {

	public Set<String> summarize(Map<String, Vector> messages, int L) {
		
		TreeMap<String, Double> weights = new TreeMap<String, Double>();
		for(Entry<String, Vector> m : messages.entrySet()) {
			String id = m.getKey();
			Vector vector = m.getValue();
			
			Double weight = vector.getTfIdfWeight();
			weights.put(id, weight);
		}

		// Sort by weight
		List<Entry<String, Double>> sortedWeights = Sorter.sort(weights);
		
		Set<String> set = new HashSet<String>();
		while(set.size() < L && !sortedWeights.isEmpty()) {
			double maxScore = -Double.MAX_VALUE;
			String selected = null;
			for(Entry<String, Double> entry : sortedWeights) {
				double score = entry.getValue() - getRedandancy(entry.getKey(), set, messages);
				if(maxScore < score) {
					maxScore = score;
					selected = entry.getKey();
				}
			}
			if(selected != null) {
				set.add(selected);
				sortedWeights.remove(selected);
			}
		}
		
		return set;
	}


	@Override
	public Set<String> summarize(Map<String, Vector> vectors, int L, Pair<Long, Long> window) {
		return null;
	}


	@Override
	public Set<String> summarize(Map<String, Vector> vectors) {
		TreeMap<String, Double> weights = new TreeMap<String, Double>();
		for(Entry<String, Vector> m : vectors.entrySet()) {
			String id = m.getKey();
			Vector vector = m.getValue();
			
			Double weight = vector.getTfIdfWeight();
			weights.put(id, weight);
		}

		// Sort by weight
		List<Entry<String, Double>> sortedWeights = Sorter.sort(weights);
		
		Set<String> set = new HashSet<String>();
		while(!sortedWeights.isEmpty()) {
			double maxScore = -Double.MAX_VALUE;
			String selected = null;
			for(Entry<String, Double> entry : sortedWeights) {
				double score = entry.getValue() - getRedandancy(entry.getKey(), set, vectors);
				if(maxScore < score) {
					maxScore = score;
					selected = entry.getKey();
				}
			}
			if(selected != null) {
				set.add(selected);
				sortedWeights.remove(selected);
			}
		}
		
		return set;
	}


	@Override
	public Set<String> summarize(Map<String, Vector> vectors, Pair<Long, Long> window) {
		return null;
	}

	private static double getRedandancy(String msgId, Set<String> S, Map<String, Vector> messages) {
		
		Vector messageVector = messages.get(msgId);
		double redundancy = 0;
		if(S.size()>0) {
			for(String selectedId : S) {
				Vector vector = messages.get(selectedId);
				redundancy += messageVector.tfIdfSimilarity(vector);
			}
			redundancy = redundancy/S.size();
		}
		return redundancy;
	}


	@Override
	public Set<String> summarize(Map<String, Vector> vectors, double compression, Pair<Long, Long> window) {
		return null;
	}


	public Set<String> summarize(List<String> itemIds, Map<String, Vector> vectors, int target) {
		
		Set<String> ids = new HashSet<String>(itemIds);
		
		Vector centroid = new Vector();
		for(String id : ids) {
			Vector vector = vectors.get(id);
			if(vector != null) {
				centroid.mergeVector(vector);
			}
		}
		
		Set<String> summary = new HashSet<String>();
		while(summary.size() < target) {
			
			String selected = null;
			double maxScore = 0;
			for(String id : ids) {
				Vector vector = vectors.get(id);
				if(vector == null) 
					continue;
			
				double coverage = centroid.cosine(vector);
				double redundancy = getRedandancy(id, summary, vectors);
				
				double score = coverage - redundancy;
				if(maxScore < score) {
					selected = id;	
				}
			}
			
			if(selected != null) {
				summary.add(selected);
				ids.remove(selected);
			}
			else {
				break;
			}
		}
		
		return summary;
	}
}
