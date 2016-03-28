package gr.iti.mklab.summarization.etts;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.Set;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import gr.iti.mklab.analysis.ItemFilter;
import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.models.ExtendedTimeline;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.Pair;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.models.WeightedEdge;
import gr.iti.mklab.ranking.GraphRanker;
import gr.iti.mklab.summarization.Summarizer;
import gr.iti.mklab.utils.GraphUtils;
import gr.iti.mklab.utils.Sorter;
import gr.iti.mklab.vocabulary.Vocabulary;

public class EttsSummarizer implements Summarizer {

	
	private static String hostname = "160.40.50.207";
	private static String dbname = "Sundance2013";
	
	private double a = 0.5, b = 0.5;
	
	private long kernelSpread;
	private int s = 2;
	
	private ExtendedTimeline timeline;
	private Map<String, Item> itemsMap;
	
	public static void main(String[] args) throws Exception {
		
		System.out.println("Load items: ");
		
		MorphiaDAO<Item> dao = new MorphiaDAO<Item>(hostname ,dbname, Item.class);
	
		System.out.println(dao.count() + " items");
		
		// Aggressive Filtering 
		ItemFilter filter = new ItemFilter();
		List<Item> items = filter.filter(dao.iterator());
		System.out.println(items.size() + " items after filtering");
				
		Map<String, Vector> vectors = Vocabulary.createVocabulary(items);
		System.out.println(vectors.size() + " vectors");
		
		System.out.println(Vocabulary.getTerms().size() + " terms in vocabulary");
		
		ExtendedTimeline tml = ExtendedTimeline.createTimeline(1, TimeUnit.HOURS, vectors, items);
		EttsSummarizer etts = new EttsSummarizer(tml, items);
		
		for(long t = tml.getMinTime(); t <= tml.getMaxTime(); t += tml.getTimeslotLength()) {
			System.out.println("========================================");
			System.out.println(new Date(t) + " => " + tml.getFrequency(t) + " items.");
			Set<String> summary = etts.summarize(vectors, 0.1, t);
			System.out.println("|S("+t+")| = " + summary.size());
			
		}
	}

	public EttsSummarizer(ExtendedTimeline timeline, List<Item> items) {
		this.timeline = timeline;
		this.kernelSpread = s*timeline.getTimeslotLength();
		
		this.itemsMap = new HashMap<String, Item>();
		for(Item item : items) {
			itemsMap.put(item.getId(), item);
		}
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
		
		Set<String> summary = new HashSet<String>();
		
		for(long t = window.left; t<=window.right; t += timeline.getTimeslotLength()) {
			Set<String> s = summarize(vectors, compression, t, a, b);
			summary.addAll(s);
		}
		
		return summary;
	}

	public Set<String> summarize(Map<String, Vector> vectors, double compression, Long t) {
		return summarize(vectors, compression, t, 0.5, 0.5);
	}
	
	public Set<String> summarize(Map<String, Vector> vectors, double compression, Long t, double a, double b) {
			
		// Items of the current window
		List<String> currentTimeItems = timeline.getItems(t);
		System.out.println("Current Time Items: " + currentTimeItems.size());
		
		int L = (int) (compression * currentTimeItems.size());
		System.out.println("Local Summary Length " + L);
		
		if(L < 1) {
			return new HashSet<String>();
		}
		
		// Projected Items
		List<String> items = new ArrayList<String>();
		Set<String> set = new HashSet<String>();
		for(int i=-(3*s); i<=(3*s); i++) {
			long ti = t + i*timeline.getTimeslotLength();
			set.addAll(timeline.getItems(ti));
		}
		items.addAll(set);
		System.out.println("Projected Items: " + items.size());
		
		// Global Summarization 
		Graph<String, WeightedEdge> globalGraph = createTransitionGraph(t, items, vectors);
		
		System.out.println("Global Graph |V|=" + globalGraph.getVertexCount() + ", |E|=" + globalGraph.getEdgeCount());
		globalGraph = GraphUtils.toDirected(globalGraph);
		globalGraph = GraphUtils.normalize(globalGraph);
		Map<String, Double> globalScores = GraphRanker.divrankScoring(globalGraph);
		
		// Local Summarization 
		Map<String, Vector> timeslotVectors = new HashMap<String, Vector>();
		for(String itemId : currentTimeItems) {
			Vector vector = vectors.get(itemId);
			if(vector != null) {
				timeslotVectors.put(itemId, vector);
			}
		}
		Graph<String, WeightedEdge> localGraph = GraphUtils.generateGraph(timeslotVectors, 0);
		Map<String, Double> localScores = GraphRanker.divrankScoring(localGraph);
		
		double sum = 0d;
		for(Double score : localScores.values())
			sum += score;
		System.out.println("Local Scores Sum: " + sum);
		
		sum = 0d;
		for(Double score : globalScores.values())
			sum += score;
		System.out.println("Global Scores Sum: " + sum);
		
		// Get  Global/Local Combination 
		Map<String, Double> scores = new HashMap<String, Double>();
		for(String itemId : currentTimeItems) {
			Double gRank = globalScores.get(itemId);
			if(gRank == null)
				gRank = 0.;
			
			Double lRank = localScores.get(itemId);
			if(lRank == null)
				lRank = 0.;
			
			Double rank = (a*gRank + b*lRank)/(a+b);
			scores.put(itemId, rank);
		}
		
		// Sort by weight
		List<Entry<String, Double>> sortedScores = Sorter.sort(scores);				
		// Keep top L items to preserve compression rate
		Set<String> summary = new HashSet<String>();
		for(int i = 0; i < Math.min(L, sortedScores.size()); i++) {
			Entry<String, Double> entry = sortedScores.get(i);
			summary.add(entry.getKey());
		}
		
		return summary;
	}
	
	private Graph<String, WeightedEdge> createTransitionGraph(long t, List<String> items, Map<String, Vector> vectors) {
		Graph<String, WeightedEdge> graph = new UndirectedSparseGraph<String, WeightedEdge>();
		for(String id : items) {
			graph.addVertex(id);
		}
		
		for(int i = 0; i<items.size(); i++) {
			for(int j = i+1; j<items.size(); j++) {
				
				String id_i = items.get(i);
				String id_j = items.get(j);
				
				if(id_i.equals(id_j)) {
					continue;
				}
				
				Vector v_i = vectors.get(id_i);
				Item item_i = itemsMap.get(id_i);
				
				Vector v_j = vectors.get(id_j);
				Item item_j = itemsMap.get(id_j);
				
				if(v_i == null || v_j == null || item_i == null || item_j == null)
					continue;
				
				double w = transitionWeight(t, v_i, item_i.getPublicationTime(), v_j, item_j.getPublicationTime());
				
				if(w > 0.01) {
					WeightedEdge link = new WeightedEdge(w);
					graph.addEdge(link , id_i, id_j);
				}
			}
		}
		
		return graph;
	}
	
	private double transitionWeight(long t, Vector v1, long t1, Vector v2, long t2) {
		double f = 0;
		
		if(t1 == t2) {
			return f;
		}
		
		double D1 = 0., D2 = 0.;
		for(String word : v1.getWords()) {
			D1 += Math.pow(v1.getTf(word) * (1 + Vocabulary.getIdf(word)), 2);
		}
		for(String word : v2.getWords()) {
			D2 += Math.pow(v2.getTf(word) * (1 + Vocabulary.getIdf(word)), 2);
		}
		
		D1 = Math.sqrt(D1);
		D2 = Math.sqrt(D2);
		
		Set<String> words = new HashSet<String>();
		words.addAll(v1.getWords());
		words.retainAll(v2.getWords());
		
		for(String word : words) {
			
			Double idf = Vocabulary.getIdf(word);
			
			double tf1 = v1.getTf(word);
			double tf2 = v2.getTf(word);
			
			double pw1 = TemporalKernel.gaussian(t - t1, kernelSpread)*tf1*(1+idf);
			double pw2 = TemporalKernel.gaussian(t - t2, kernelSpread)*tf2*(1+idf);
			
			f += (pw1/D1)*(pw2/D2);
			
		}
		
		return f;
	}
}
