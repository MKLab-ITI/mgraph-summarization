package gr.iti.mklab.summarization;

import edu.uci.ics.jung.graph.Graph;
import gr.iti.mklab.models.Pair;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.models.WeightedEdge;
import gr.iti.mklab.ranking.GraphRanker;
import gr.iti.mklab.utils.GraphUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DivRankSummarizer implements Summarizer {

	private HashMap<String, Integer> popularityMap;
	private Graph<String, WeightedEdge> graph;

	public DivRankSummarizer(Graph<String, WeightedEdge> graph, Map<String, Integer> map) {
		this.graph = graph;
		this.popularityMap = new HashMap<String, Integer>();
		this.popularityMap.putAll(map);
	}
	
	public Set<String> summarize(Collection<String> ids, Map<String, Vector> vectors, int L) {
		
		Set<String> summary = new HashSet<String>();
		
		Graph<String, WeightedEdge> subGraph = GraphUtils.filter(graph, ids);
		Map<String, Double> priors = GraphRanker.getPriors(ids, popularityMap);
		Map<String, Double> divRankScores = GraphRanker.divrankScoring(subGraph, priors);
		
		Set<String> candidates = new HashSet<String>(ids);
		while(summary.size() < L && !candidates.isEmpty()) {
			double maxScore = -Double.MAX_VALUE;
			String selected = null;
			for(String id : candidates) {
				Double divRankScore = divRankScores.get(id);
				double score = divRankScore - getRedandancy(id, summary, vectors);
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
	public Set<String> summarize(Map<String, Vector> vectors) {
		return null;
	}

	@Override
	public Set<String> summarize(Map<String, Vector> vectors, Pair<Long, Long> window) {
		return null;
	}

	@Override
	public Set<String> summarize(Map<String, Vector> messages, int L) {
		return null;
	}

	@Override
	public Set<String> summarize(Map<String, Vector> vectors, int L, Pair<Long, Long> window) {
		return null;
	}

	@Override
	public Set<String> summarize(Map<String, Vector> vectors, double compression, Pair<Long, Long> window) {
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
