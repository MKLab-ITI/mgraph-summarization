package gr.iti.mklab.summarization;

import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import gr.iti.mklab.models.Pair;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.models.WeightedEdge;
import gr.iti.mklab.ranking.GraphRanker;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;

public class LexRankSummarizer implements Summarizer {

	private TreeMap<String, Double> sortedVertices = null;
	
	public Set<String> summarize(Map<String, Vector> messages, int L) {
		
		if(sortedVertices == null) {
		
			Graph<String, WeightedEdge>  graph = new UndirectedSparseGraph<String, WeightedEdge>();
			String[] ids = messages.keySet().toArray(new String[messages.size()]);
			for(String node : ids)
				graph.addVertex(node);
		
			for(int i=0; i<ids.length; i++) {
				for(int j=i; j<ids.length; j++) {
				
					if(i == j)
						continue;
				
					Vector v1 = messages.get(ids[i]);
					Vector v2 = messages.get(ids[j]);
				
					Double similarity = v1.tfIdfSimilarity(v2);
				
					if(similarity < 7)
						continue;
				
					WeightedEdge e = new WeightedEdge(similarity);
					graph.addEdge(e, ids[i], ids[j]);
				}
			}
			
			sortedVertices = GraphRanker.eigenvectorScoring(graph);
		}
		
		Set<String> set = new HashSet<String>();
		while(set.size() < L && !sortedVertices.isEmpty()) {
			double maxScore = -Double.MAX_VALUE;
			String selected = null;
			for(Entry<String, Double> entry : sortedVertices.entrySet()) {
				double score = entry.getValue() - getRedandancy(entry.getKey(), set, messages);
				if(maxScore < score) {
					maxScore = score;
					selected = entry.getKey();
				}
			}
			if(selected != null) {
				set.add(selected);
				sortedVertices.remove(selected);
			}
		}
		return set;
	}

	@Override
	public Set<String> summarize(Map<String, Vector> vectors, int L, Pair<Long, Long> window) {

		return null;
	}

	@Override
	public Set<String> summarize(Map<String, Vector> vectors, double compression, Pair<Long, Long> window) {
		
		return null;
	}
	
	@Override
	public Set<String> summarize(Map<String, Vector> vectors) {

		if(sortedVertices == null) {
		
			Graph<String, WeightedEdge>  graph = new UndirectedSparseGraph<String, WeightedEdge>();
			String[] ids = vectors.keySet().toArray(new String[vectors.size()]);
			for(String node : ids)
				graph.addVertex(node);
		
			for(int i=0; i<ids.length; i++) {
				for(int j=i; j<ids.length; j++) {
				
					if(i == j)
						continue;
				
					Vector v1 = vectors.get(ids[i]);
					Vector v2 = vectors.get(ids[j]);
				
					Double similarity = v1.tfIdfSimilarity(v2);
				
					if(similarity < 7)
						continue;
				
					WeightedEdge e = new WeightedEdge(similarity);
					graph.addEdge(e, ids[i], ids[j]);
				}
			}
			
			sortedVertices = GraphRanker.eigenvectorScoring(graph);
			
		}
		
		Set<String> set = new HashSet<String>();
		while(!sortedVertices.isEmpty()) {
			double maxScore = -Double.MAX_VALUE;
			String selected = null;
			for(Entry<String, Double> entry : sortedVertices.entrySet()) {
				double score = entry.getValue() - getRedandancy(entry.getKey(), set, vectors);
				if(maxScore < score) {
					maxScore = score;
					selected = entry.getKey();
				}
			}
			if(selected != null) {
				set.add(selected);
				sortedVertices.remove(selected);
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
	
}
