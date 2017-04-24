package gr.iti.mklab.summarization;

import edu.uci.ics.jung.graph.Graph;

import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.models.ExtendedTimeline;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.Pair;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.models.WeightedEdge;
import gr.iti.mklab.ranking.GraphRanker;
import gr.iti.mklab.topicmodels.SCAN;
import gr.iti.mklab.utils.CollectionsUtils;
import gr.iti.mklab.utils.GraphUtils;
import gr.iti.mklab.vocabulary.Vocabulary;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.mongodb.morphia.query.Query;

public class DivRankSummarizer implements Summarizer {

	private HashMap<String, Integer> popularityMap;
	private Graph<String, WeightedEdge> graph;

	public DivRankSummarizer(Graph<String, WeightedEdge> graph, Map<String, Integer> popularityMap) {
		this.graph = graph;
		this.popularityMap = new HashMap<String, Integer>();
		this.popularityMap.putAll(popularityMap);
	}
	
	@Override
	public Set<String> summarize(Map<String, Vector> vectors, int L) {
		
		Set<String> ids = new HashSet<String>(vectors.keySet());
		
		Graph<String, WeightedEdge> subGraph = GraphUtils.filter(graph, ids);
		Map<String, Double> priors = GraphRanker.getPriors(ids, popularityMap);
		Map<String, Double> divRankScores = GraphRanker.divrankScoring(subGraph, priors);
		
		int S = 0;
		Set<String> summary = new HashSet<String>();
		while(summary.size() < L && !ids.isEmpty()) {
			double maxScore = -Double.MAX_VALUE;
			String selected = null;
			for(String id : ids) {
				Double divRankScore = divRankScores.get(id);
				
				Vector itemVector = vectors.get(id);
				double redundancy = getRedundancy(itemVector, CollectionsUtils.mapSlice(vectors, summary));
				
				double score = divRankScore - redundancy;
				if(maxScore < score) {
					maxScore = score;
					selected = id;
				}
			}
			
			if(selected != null) {
				summary.add(selected);
				ids.remove(selected);
			}
			
			if(summary.size() == S) {
				break;
			}
			S = summary.size();
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
	public Set<String> summarize(Map<String, Vector> vectors, int L, Pair<Long, Long> window) {
		return null;
	}

	@Override
	public Set<String> summarize(Map<String, Vector> vectors, double compression, Pair<Long, Long> window) {
		return null;
	}

	private static double getRedundancy(Vector itemVector, Map<String, Vector> vectors) {
		double maxRedundancy = 0;
		for(Vector vector : vectors.values()) {
			double redundancy = itemVector.tfIdfSimilarity(vector);
			if(redundancy > maxRedundancy) {
				maxRedundancy = redundancy;
			}
		}
		return maxRedundancy;
	}
	
	public static void main(String...args) throws Exception {
		
		MorphiaDAO<Item> dao = new MorphiaDAO<Item>("", "", Item.class);
		Query<Item> query = dao.getQuery().filter("accepted =", Boolean.TRUE);
		System.out.println(dao.count(query) + " items to be proccessed");
		
		Map<String, Item> itemsMap = new HashMap<String, Item>();
		Iterator<Item> it = dao.iterator(query);
		while(it.hasNext()) {
			Item item = it.next();
			itemsMap.put(item.getId(), item);
		}
		System.out.println("Loaded.");
		
		Map<String, Vector> vectorsMap = Vocabulary.createVocabulary(itemsMap.values(), 2);
		
		SCAN scan = (SCAN) SCAN.loadModel("");
		
		Graph<String, WeightedEdge> graph = scan.getGraph();
		
		Map<String, Integer> popularities = new HashMap<String, Integer>();
		for(Item item : itemsMap.values()) {
			popularities.put(item.getId(), item.getReposts());
		}
		
		ExtendedTimeline tml = ExtendedTimeline.createTimeline(1, TimeUnit.HOURS, vectorsMap, itemsMap.values());
		List<Pair<Long, Long>> peakWindows = tml.detectPeakWindows();
		
		DivRankSummarizer summarizer = new DivRankSummarizer(graph, popularities);
		double compression = 0.01;
		for(Pair<Long, Long> window : peakWindows) {
			List<String> items = tml.getItems(window);
			if(items.size() > 1000) {
				System.out.println(new Date(window.left) + " - " + new Date(window.right));
				System.out.println("#items: " + items.size());
				
				int L = (int) Math.round(compression * items.size());
				System.out.println("target: " + L);
				
				Map<String, Vector> windowVector = CollectionsUtils.mapSlice(vectorsMap, items);
				Set<String> summary = summarizer.summarize(windowVector, L);

				System.out.println("|S|=" + summary.size());
				System.out.println("==========================================");
			}
		}
		
	}
}
