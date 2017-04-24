package gr.iti.mklab.summarization.ttgrid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.Set;

import org.mongodb.morphia.query.Query;

import edu.uci.ics.jung.graph.Graph;
import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.models.ExtendedTimeline;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.Pair;
import gr.iti.mklab.models.Topic;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.models.WeightedEdge;
import gr.iti.mklab.ranking.GraphRanker;
import gr.iti.mklab.structures.TimeTopicGrid;
import gr.iti.mklab.summarization.Summarizer;
import gr.iti.mklab.topicmodels.SCAN;
import gr.iti.mklab.utils.CollectionsUtils;
import gr.iti.mklab.utils.GraphUtils;
import gr.iti.mklab.vocabulary.Vocabulary;

public class TimeTopicSummarizer implements Summarizer {
	
	private static double a = 0.5;
	private TimeTopicGrid grid;
	private Graph<String, WeightedEdge> graph;
	private Map<String, Integer> popularities;
	
	private static double w1 = 0.4, w2 = 0.4, w3 = 0.2;
	
	public TimeTopicSummarizer(TimeTopicGrid grid, Graph<String, WeightedEdge> graph, Map<String, Integer> popularities) {
		this.grid = grid;
		this.graph = graph;
		this.popularities = popularities;
	}
	
	@Override
	public Set<String> summarize(Map<String, Vector> vectors, int L) {
		Set<String> summary = new HashSet<String>();
		
		ExtendedTimeline timeline = grid.getTotalTimeline();
		List<Pair<Long, Long>> windows = timeline.getPeakWindows();
		System.out.println(windows.size() + " detected peaks");
		
		int lengthPerPeak = L / windows.size();
		System.out.println(lengthPerPeak + " items per peak should be included in the summary");
		for(Pair<Long, Long> window : windows) {		
			System.out.println("Peak [" + new Date(window.left) + " - " + new Date(window.right) + "] => " + timeline.getFrequency(window));
			
			Set<String> s = summarize(vectors, lengthPerPeak, window);
			System.out.println("Summary: " + s.size());
			
			summary.addAll(s);
		}
		return summary;
	}
	
	@Override
	public Set<String> summarize(Map<String, Vector> vectors) {
		Set<String> summary = new HashSet<String>();
		
		ExtendedTimeline timeline = grid.getTotalTimeline();
		List<Pair<Long, Long>> windows = timeline.getPeakWindows();
		System.out.println(windows.size() + " detected peaks");
		
		for(Pair<Long, Long> window : windows) {		
			System.out.println("Peak [" + new Date(window.left) + " - " + new Date(window.right) + "] => " + timeline.getFrequency(window));
			
			Set<String> s = summarize(vectors, window);
			System.out.println("Summary: " + s.size());
			
			summary.addAll(s);
		}
		return summary;
	}
	
	@Override
	public Set<String> summarize(Map<String, Vector> vectors, double compression, Pair<Long, Long> window) {
		
		// Summary
		Set<String> S = new HashSet<String>();
		
		ExtendedTimeline timeline = grid.getTotalTimeline();		
		List<String> itemIds = timeline.getItems(window);
		
		// Get subgraph that contains only nodes of this window
		Graph<String, WeightedEdge> windowGraph = GraphUtils.filter(graph, itemIds);
		
		System.out.println(new Date(window.left) + " - " + new Date(window.right));
		System.out.println("#items: " + itemIds.size());
		System.out.println("Window Vertices: " + windowGraph.getVertexCount());
		System.out.println("Window Edges: " + windowGraph.getEdgeCount());
		
		// Calculate global ranking based on DivRank and popularity priors 
		Map<String, Double> priors = GraphRanker.getPriors(itemIds, popularities);
		Map<String, Double> divRankScores = GraphRanker.divrankScoring(windowGraph, priors);
		
		Collection<Integer>  Atotal = grid.getActiveTopics(window);
		System.out.println("|Atotal| = " +  Atotal.size());
		System.out.println("L = " + (int) (compression * itemIds.size()));
		
		int remainingItems = itemIds.size();
		long targetLength = Math.round(compression * itemIds.size());
		for(Long timeBin = window.left; timeBin<=window.right; timeBin += grid.getTimeslotLength()) {
			
			Integer numOfItems = timeline.getFrequency(timeBin);
			if(numOfItems == 0) {
				continue;
			}
			
			long L = Math.max(Math.round(compression * numOfItems), 1);
			remainingItems -= numOfItems;
			
			Collection<Integer> A = grid.getActiveTopics(timeBin);
			
			Map<String, Integer> M = grid.getTopicRelatedItems(timeBin);
			if(M.size() < L) {
				M = grid.getItems(timeBin);	
			}
			
			
			System.out.println(new Date(timeBin) + " => #items: " + numOfItems + ", target: " + L + ",  |A|=" + A.size()
					+ ", |M|=" + M.size());
			
			int localSummaryLength = 0;
			while(localSummaryLength < L && !M.isEmpty()) {
				
				double maxScore = 0;
				String selected = null;
				for(Entry<String, Integer> entry : M.entrySet()) {
					
					String itemId = entry.getKey();
					Vector itemVector = vectors.get(itemId);
					
					Integer topicId = entry.getValue();
					
					double coverage = .0, topicSignificance = .0;
					if(topicId != null) {
						topicSignificance = grid.getTopicSigificance(topicId, timeBin);
						if(topicSignificance > 0) {
							coverage = topicSignificance * getTopicCoverage(itemVector, timeBin, topicId, grid);
							//coverage = getTopicCoverage(itemVector, timeBin, A, grid);	
						}
					}
					
					Double popularity = divRankScores.get(itemId);
					if(popularity == null) {
						popularity = 0d;
					}
					
					// If the combination of coverage and popularity 
					// is not positive continue to next item
					if(coverage + popularity <= 0) {
						continue;
					}
					
					double redundancy = getMaximumRedundancy(itemId, S, vectors);
					
					// Combine to a total score, based on coverage, popularity and redundancy 
					double score = w1 * coverage + w2 * popularity - w3 * redundancy;
					
					if(maxScore < score) {
						maxScore = score;
						selected = itemId;
					}
				}
				
				if(selected != null) {
					localSummaryLength++;
					S.add(selected);
					M.remove(selected);
				}
				else {
					break;
				}
			}
			
			//Re-calculate compression rate if local target hasn't been met 
			if(localSummaryLength < L) {
				long remainingTarget = targetLength - S.size();
				compression = ((double) remainingTarget) / ((double) remainingItems);
			}
			
		}
		return S;
	}
	
	public Set<String> summarizeTest(Map<String, Vector> vectors, double compression, Pair<Long, Long> window, 
			double w1, double w2, double w3) {
		
		Set<String> S = new HashSet<String>();
		
		ExtendedTimeline timeline = grid.getTotalTimeline();
		
		// Calculate global ranking
		List<String> itemIds = timeline.getItems(window);
		Graph<String, WeightedEdge> windowGraph = GraphUtils.filter(graph, itemIds);
		
		long targetLength = Math.round(compression * itemIds.size());
		
		Map<String, Double> priors = GraphRanker.getPriors(itemIds, popularities);
		Map<String, Double> divRankScores = GraphRanker.divrankScoring(windowGraph, priors);
		
		Collection<Integer>  topicIds = grid.getTopics();
		List<Topic> topics = new ArrayList<Topic>();
		for(Integer topicId : topicIds) {
			Topic topic = grid.getTopic(topicId);
			if(topic != null)
				topics.add(topic);
		}
		Collections.sort(topics, new Comparator<Topic>() {
				@Override
				public int compare(Topic t1, Topic t2) {
					if(t1.getTimeline().getTotal() < t2.getTimeline().getTotal())
						return 1;
					return -1;
				}
			});
		
		for(Topic topic : topics) {
			
			ExtendedTimeline topicTimeline = topic.getTimeline();
			Vector topicCentroid = topicTimeline.getVector(window);
			
			if(topicCentroid == null)
				continue;
			
			String selected = null;
			double maxScore = 0;
			for(String id : topicTimeline.getItems(window)) {
				Vector itemVector = vectors.get(id);
				if(itemVector == null)
					continue;
				
				Double contentScore = topicCentroid.cosine(itemVector);
				Double significanceScore = divRankScores.get(id);
				
				//Double redundancy = getAverageRedundancy(id, S, vectors);
				Double redundancy = getMaximumRedundancy(id, S, vectors);
				
				double score = w1*contentScore + w2*significanceScore - w3*redundancy;
				if(score > maxScore) {
					maxScore = score;
					selected = id;
				}
				
			}
			
			if(selected != null) {
				S.add(selected);
			}
			
			if(S.size() >= targetLength)
				break;
		}	

		return S;
	}
	
	@Override
	public Set<String> summarize(Map<String, Vector> vectors, int L, Pair<Long, Long> window) {
		System.out.println("K=" + grid.getTopics().size());
		Set<String> S = new HashSet<String>();
		Collection<Integer> A = grid.getActiveTopics(window);
		System.out.println("|A|=" + A.size());
		
		Map<String, Integer> Mc = getMc(vectors, window, A);
		System.out.println("|Mc|=" + Mc.size());
		
		while(S.size() < L && !Mc.isEmpty()) {
			double maxScore = 0;
			String selected = null;
			for(Entry<String, Integer> entry : Mc.entrySet()) {
				double score = getScore(entry.getValue(), entry.getKey(), S, vectors, window);
				if(maxScore < score) {
					maxScore = score;
					selected = entry.getKey();
				}
			}
			if(selected != null) {
				S.add(selected);
				Mc.remove(selected);
			}
			else {
				break;
			}
		}
		
		if(S.size() < L) {
			Map<String, Integer> M = grid.getTopicRelatedItems(window);
			for(String s : S) {
				M.remove(s);
			}
			
			System.out.println("|M|=" + M.size());
			
			while(S.size() < L && !M.isEmpty()) {
				
				double maxScore = 0;
				String selected = null;
				for(Entry<String, Integer> entry : M.entrySet()) {
					double score = getScore(entry.getValue(), entry.getKey(), S, vectors, window);
					if(maxScore < score) {
						maxScore = score;
						selected = entry.getKey();
					}
				}
				if(selected != null) {
					S.add(selected);
					M.remove(selected);
				}
				else {
					break;
				}
			}
		}
		return S;
	}
	
	@Override
	public Set<String> summarize(Map<String, Vector> vectors, Pair<Long, Long> window) {
		System.out.println("K=" + grid.getTopics().size());
		Set<String> S = new HashSet<String>();
		Collection<Integer> A = grid.getActiveTopics(window);
		System.out.println("|A|=" + A.size());
		
		Map<String, Integer> Mc = getMc(vectors, window, A);
		System.out.println("|Mc|=" + Mc.size());
		
		while(!Mc.isEmpty()) {
			double maxScore = 0;
			String selected = null;
			for(Entry<String, Integer> entry : Mc.entrySet()) {
				double score = getScore(entry.getValue(), entry.getKey(), S, vectors, window);
				if(maxScore < score) {
					maxScore = score;
					selected = entry.getKey();
				}
			}
			if(selected != null) {
				S.add(selected);
				Mc.remove(selected);
			}
			else {
				break;
			}
		}
		return S;
	}

	/*
	 * Get Coverage of a message in t_j as the average cosine between the message vector and each active topic in t_j
	 */
	public double getTopicCoverage(Vector messageVector, Long timeBin, Collection<Integer> A, TimeTopicGrid grid) {
		
		int coveredTopics = 0;
		double coverage = 0;
		if(A.size() > 0) {
			//Vector timeBinVector = new Vector();
			for(Integer topicId : A) {
				Topic topic = grid.getTopic(topicId);
				if(topic == null)
					continue;
				
				ExtendedTimeline topicTimeline = topic.getTimeline();
				Vector topicVector = topicTimeline.getVector(timeBin);
				
				if(topicVector != null) {
					coveredTopics++;
					coverage += topicVector.cosine(messageVector);
				}
			}
			
			if(coveredTopics == 0) {
				return 0;
			}
			
			coverage = coverage / (double) coveredTopics;
		}
		return coverage;
	}
	
	/*
	 * Get Coverage of a message in t_j as the cosine between the message vector and each corresponding topic in t_j
	 */
	public double getTopicCoverage(Vector messageVector, Long timeBin, Integer topicId, TimeTopicGrid grid) {

		Topic topic = grid.getTopic(topicId);
		if(topic == null) {
			return 0;
		}
		
		ExtendedTimeline topicTimeline = topic.getTimeline();
		Vector topicVector = topicTimeline.getVector(timeBin);
				
		Double coverage = topicVector.cosine(messageVector);
		return coverage;
	}
	
	public double getAverageRedundancy(String itemId, Set<String> S, Map<String, Vector> vectors) {
		double redundancy = 0;
		Vector messageVector = vectors.get(itemId);
		if(S.size() > 0) {
			int size = 0;
			for(String selectedId : S) {
				Vector vector = vectors.get(selectedId);
				if(vector == null)
					continue;

				size++;
				redundancy += messageVector.cosine(vector);
			}
			// get average redundancy
			if(size == 0) {
				return 0d;
			}
			
			redundancy = redundancy / size;
		}
		return redundancy;
	}
	
	public double getMaximumRedundancy(String itemId, Set<String> S, Map<String, Vector> vectors) {
		double maxRedundancy = 0;
		Vector messageVector = vectors.get(itemId);
		for(String selectedId : S) {
			Vector vector = vectors.get(selectedId);
			if(vector == null) {
				continue;
			}
				
			Double redundancy = messageVector.cosine(vector);
			if(maxRedundancy < redundancy) {
				maxRedundancy = redundancy;
			}
		}
		
		return maxRedundancy;
	}
	
	public double getScore(Integer topicId, String msgId, Set<String> S, Map<String, Vector> vectors, Pair<Long, Long> window) {
		Vector messageVector = vectors.get(msgId);
		double significance = grid.getTopicSigificance(topicId, window);
		double weight = grid.getWeight(messageVector, topicId, window);
		
		double importance = significance*weight;
		
		double redundancy = 0;
		if(S.size()>0) {
			for(String selectedId : S) {
				Vector vector = vectors.get(selectedId);
				if(vector == null) {
					System.out.println(selectedId + " is null!");
					continue;
				}

				redundancy += messageVector.tfIdfSimilarity(vector);
				//redundancy += messageVector.cosine(vector);
			}
			redundancy = redundancy/S.size();
		}
		return a*importance - (1-a)*redundancy;
	}
	
	public Map<String, Integer> getMc(Map<String, Vector> vectors, Long timeBin, Collection<Integer> activeTopics) {
		Map<String, Integer> mc = new HashMap<String, Integer>();
		for(Integer topicId : activeTopics) {
			List<String> items = grid.getItems(topicId, timeBin);
			double maxWeight = 0;
			String maxId = null;
			for(String itemId : items) {
				Vector messageVector = vectors.get(itemId);
				if(messageVector == null)
					continue;
				
				Item item = grid.getItem(itemId);
				if(item == null)
					continue;
				
				long publicationTime = item.getPublicationTime();
				double weight = grid.getWeight(messageVector, topicId, publicationTime);
				if(weight > maxWeight) {
					maxWeight = weight;
					maxId = itemId;
				}
			}
			if(maxId != null) {
				mc.put(maxId, topicId);
			}
		}
		return mc;
	}
	
	public Map<String, Integer> getMc(Map<String, Vector> vectors, Pair<Long, Long> window, Collection<Integer> A) {
		Map<String, Integer> mc = new HashMap<String, Integer>();
		for(Integer topicId : A) {
			List<String> items = grid.getItems(topicId, window);
			
			double maxWeight = 0;
			String maxId = null;
			for(String itemId : items) {
				Vector messageVector = vectors.get(itemId);
				if(messageVector == null)
					continue;
				
				Item item = grid.getItem(itemId);
				if(item == null)
					continue;
				
				long publicationTime = item.getPublicationTime();
				double weight = grid.getWeight(messageVector, topicId, publicationTime);
				if(weight > maxWeight) {
					maxWeight = weight;
					maxId = itemId;
				}
	
			}
			if(maxId != null) {
				mc.put(maxId, topicId);
			}
		}
		return mc;
	}
	
	public static void main(String...args) throws Exception {
		
		MorphiaDAO<Item> dao = new MorphiaDAO<Item>("" , "", Item.class);
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
		System.out.println(scan.getNumOfTopics() + " topics detected");
		
		// Get & process graph
		Graph<String, WeightedEdge> graph = scan.getGraph();
		Graph<String, WeightedEdge> normalizedDirectedGraph = GraphUtils.toDirected(graph, itemsMap);	// Convert to directed
		normalizedDirectedGraph = GraphUtils.normalize(normalizedDirectedGraph);		// normalize
		System.out.println(graph.getVertexCount() + " vertices to normalized Directed Graph");
		System.out.println(graph.getEdgeCount() + " edges to normalized Directed Graph");
		
		// Get popularities
		Map<String, Integer> popularities = new HashMap<String, Integer>();
		for(Item item : itemsMap.values()) {
			popularities.put(item.getId(), item.getReposts());
		}
		
		// Create Time-Topic Grid. Time resolution: 1 hour 
		TimeTopicGrid ttGrid = new TimeTopicGrid(1, TimeUnit.HOURS);
		ttGrid.addTopics(scan.getTopics(), scan.getTopicAssociations(), vectorsMap, itemsMap);
		
		TimeTopicSummarizer ttSummarizer = new TimeTopicSummarizer(ttGrid, normalizedDirectedGraph, popularities);
		System.out.println("Summarizer Initialized");
		
		ExtendedTimeline totalTimeline = ttGrid.getTotalTimeline();
		List<Pair<Long, Long>> peakWindows = totalTimeline.detectPeakWindows();
		System.out.println(peakWindows.size() + " peaks detected");
		
		double compression = 0.01;
		for(Pair<Long, Long> window : peakWindows) {
			List<String> items = totalTimeline.getItems(window);
			
			if(items.size() > 1000) {
				Map<String, Vector> windowVectors = CollectionsUtils.mapSlice(vectorsMap, items);
				
				Set<String> summary = ttSummarizer.summarize(windowVectors, compression, window);
				System.out.println(summary.size() + " items in summary!");
				System.out.println("=====================================================");
			}
		}
		
	}
	
}
