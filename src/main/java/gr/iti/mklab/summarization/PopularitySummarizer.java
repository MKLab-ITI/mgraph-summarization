package gr.iti.mklab.summarization;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import gr.iti.mklab.models.Pair;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.utils.Sorter;

public class PopularitySummarizer implements Summarizer {

	private Map<String, Double> popularityMap;

	public PopularitySummarizer(Map<String, Integer> map) {
		this.popularityMap = new HashMap<String, Double>();
		
		int maxPopularity = Collections.max(map.values());
		for(String id : map.keySet()) {
			Integer popularity = map.get(id);
			
			this.popularityMap.put(id, (double)popularity / ( double)maxPopularity);
		}
	}
	
	@Override
	public Set<String> summarize(Map<String, Vector> vectors) {
		Set<String> set = new HashSet<String>();
		TreeMap<String, Double> weights = new TreeMap<String, Double>();
		for(Entry<String, Vector> m : vectors.entrySet()) {
			String id = m.getKey();
			
			Double popularity = popularityMap.get(id);
			if(popularity == null)
				popularity = 0d;
			weights.put(id, popularity);
		}

		// Sort by weight
		List<Entry<String, Double>> sortedWeights = Sorter.sort(weights);
		
		while(!sortedWeights.isEmpty()) {
			double maxScore = 0;
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
	
	@Override
	public Set<String> summarize(Map<String, Vector> vectors, double compression, Pair<Long, Long> window) {
		return null;
	}
	
	public Set<String> summarize(Map<String, Vector> messages, int L) {
		Set<String> set = new HashSet<String>();
		TreeMap<String, Double> weights = new TreeMap<String, Double>();
		for(Entry<String, Vector> m : messages.entrySet()) {
			String id = m.getKey();
			
			Double reposts = popularityMap.get(id);
			if(reposts == null)
				reposts = 0d;
			weights.put(id, reposts);
		}

		// Sort by weight
		List<Entry<String, Double>> sortedWeights = Sorter.sort(weights);
		
		while(set.size() < L && !sortedWeights.isEmpty()) {
			double maxScore = 0;
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

	public Set<String> summarize(Collection<String> ids, Map<String, Vector> vectors, int L) {
		Set<String> summary = new HashSet<String>();
		
		Set<String> candidates = new HashSet<String>(ids);
		while(summary.size() < L && !candidates.isEmpty()) {
			double maxScore = -Double.MAX_VALUE;
			String selected = null;
			for(String id : candidates) {
				Double popularity = popularityMap.get(id);
				double score = popularity - getRedandancy(id, summary, vectors);
				if(maxScore < score) {
					maxScore = score;
					selected = id;
				}
			}
			if(selected != null) {
				summary.add(selected);
				candidates.remove(selected);
			}
		}

		return summary;
	}
	
	@Override
	public Set<String> summarize(Map<String, Vector> vectors, int L, Pair<Long, Long> window) {
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
	
}