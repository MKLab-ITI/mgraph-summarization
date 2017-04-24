package gr.iti.mklab.summarization;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import gr.iti.mklab.models.ExtendedTimeline;
import gr.iti.mklab.models.Pair;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.models.WeightedEdge;
import gr.iti.mklab.ranking.GraphRanker;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;

public class TextRankSummarizer implements Summarizer {

	private ExtendedTimeline timeline;

	TextRankSummarizer(ExtendedTimeline timeline) {
		this.timeline = timeline;
	}

	public Set<String> summarize(Map<String, Vector> vectors, int L) {
		
		Graph<String, WeightedEdge> graph = generateGraph(vectors);		
		Map<String, Double> scoredVertices = GraphRanker.pagerankScoring(graph);
		
		return summarize(scoredVertices, L, vectors);
	}

	@Override
	public Set<String> summarize(Map<String, Vector> vectors, int L, Pair<Long, Long> window) {
		
		List<String> windowItems = timeline.getItems(window);
		Map<String, Vector> windowVectors = new HashMap<String, Vector>();
		for(String itemId : windowItems) {
			Vector vector = vectors.get(itemId);
			
			if(vector == null)
				continue;
			
			windowVectors.put(itemId, vector);
		}
		Graph<String, WeightedEdge> graph = generateGraph(windowVectors);
		Map<String, Double> scoredVertices = GraphRanker.pagerankScoring(graph);
		
		return summarize(scoredVertices, L, vectors);
	}

	@Override
	public Set<String> summarize(Map<String, Vector> vectors) {
		Graph<String, WeightedEdge> graph = generateGraph(vectors);	
		Map<String, Double> scoredVertices = GraphRanker.pagerankScoring(graph);
		
		return summarize(scoredVertices, scoredVertices.size(), vectors);
	}

	@Override
	public Set<String> summarize(Map<String, Vector> vectors, Pair<Long, Long> window) {
		List<String> windowItems = timeline.getItems(window);
		Map<String, Vector> windowVectors = new HashMap<String, Vector>();
		for(String itemId : windowItems) {
			Vector vector = vectors.get(itemId);
			
			if(vector == null)
				continue;
			
			windowVectors.put(itemId, vector);
		}
		Graph<String, WeightedEdge> graph = generateGraph(windowVectors);
		Map<String, Double> scoredVertices = GraphRanker.pagerankScoring(graph);
		
		return summarize(scoredVertices, scoredVertices.size(), vectors);
	}

	@Override
	public Set<String> summarize(Map<String, Vector> vectors, double compression, Pair<Long, Long> window) {
		Integer size = timeline.getFrequency(window);
		int L = (int) compression * size;
		
		return summarize(vectors, L, window);
	}
	
	private static Set<String> summarize(Map<String, Double> scoredVertices, int L, Map<String, Vector> vectors) {
		Set<String> set = new HashSet<String>();
		while(set.size() < L && !scoredVertices.isEmpty()) {
			double maxScore = -Double.MAX_VALUE;
			String selected = null;
			for(Entry<String, Double> entry : scoredVertices.entrySet()) {
				double score = entry.getValue() - getRedundancy(entry.getKey(), set, vectors);
				if(maxScore < score) {
					maxScore = score;
					selected = entry.getKey();
				}
			}
			if(selected != null) {
				set.add(selected);
				scoredVertices.remove(selected);
			}
		}
		return set;
	}
	
	private static Graph<String, WeightedEdge> generateGraph(Map<String, Vector> vectors) {
		Graph<String, WeightedEdge>  graph = new UndirectedSparseGraph<String, WeightedEdge>();
		String[] ids = vectors.keySet().toArray(new String[vectors.size()]);
		
		// Add nodes
		for(String node : ids)
			graph.addVertex(node);
		
		// Add edges
		for(int i=0; i<ids.length-1; i++) {
			for(int j=i+1; j<ids.length; j++) {
				Vector v1 = vectors.get(ids[i]);
				Vector v2 = vectors.get(ids[j]);
				Double similarity = v1.cosine(v2);
				
				// Similarity Threshold to keep graph sparse
				if(similarity < 0.5)
					continue;
				
				WeightedEdge e = new WeightedEdge(similarity);
				graph.addEdge(e, ids[i], ids[j]);
			}
		}
		
		return graph;
	}
	
	private static double getRedundancy(String itemId, Set<String> S, Map<String, Vector> vectors) {
		Vector messageVector = vectors.get(itemId);
		double redundancy = 0;
		if(S.size()>0) {
			for(String selectedId : S) {
				Vector vector = vectors.get(selectedId);
				redundancy += messageVector.tfIdfSimilarity(vector);
			}
			redundancy = redundancy/S.size();
		}
		return redundancy;
	}

}
