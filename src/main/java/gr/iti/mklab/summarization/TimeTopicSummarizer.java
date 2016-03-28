package gr.iti.mklab.summarization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import edu.uci.ics.jung.graph.Graph;
import gr.iti.mklab.models.ExtendedTimeline;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.Pair;
import gr.iti.mklab.models.Topic;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.models.WeightedEdge;
import gr.iti.mklab.ranking.GraphRanker;
import gr.iti.mklab.structures.TimeTopicGrid;
import gr.iti.mklab.utils.GraphUtils;


public class TimeTopicSummarizer implements Summarizer {
	
	private static double a = 0.5;
	private TimeTopicGrid grid;
	private Graph<String, WeightedEdge> graph;
	private Map<String, Integer> popularities;
	
	private static double w1 = 0.4, w2 = 0.5, w3 = 0.1;
	
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
		
		int lengthPerPeak = L/windows.size();
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
		
		Set<String> S = new HashSet<String>();
		
		ExtendedTimeline timeline = grid.getTotalTimeline();
		
		// Calculate global ranking
		List<String> itemIds = timeline.getItems(window);
		Graph<String, WeightedEdge> windowGraph = GraphUtils.filter(graph, itemIds);
		
		System.out.println(new Date(window.left) + " - " + new Date(window.right));
		System.out.println("#items: " + itemIds.size());
		System.out.println("Window Vertices: " + windowGraph.getVertexCount());
		System.out.println("Window Edges: " + windowGraph.getEdgeCount());
		
		Map<String, Double> priors = GraphRanker.getPriors(itemIds, popularities);
		Map<String, Double> divRankScores = GraphRanker.divrankScoring(windowGraph, priors);
		//Map<String, Double> divRankScores = GraphRanker.pagerankScoring(windowGraph, priors);
		
		//System.out.println("Max PR Score: " + Collections.max(divRankScores.values()));
		Collection<Integer>  Atotal = grid.getActiveTopics(window);
		System.out.println("|Atotal| = " +  Atotal.size());
		System.out.println("L = " + (int) (compression * itemIds.size()));
		
		int remainingItems = itemIds.size();
		long targetLength = Math.round(compression * itemIds.size());
		for(Long timeBin = window.left; timeBin<=window.right; timeBin += grid.getTimeslotLength()) {
			
			Integer numOfItems = timeline.getFrequency(timeBin);
			long L = Math.round(compression * numOfItems);
			
			if(numOfItems == 0)
				continue;
			
			remainingItems -= numOfItems;
			
			Collection<Integer> A = grid.getActiveTopics(timeBin);
			Map<String, Integer> M = grid.getM(timeBin);
			
			//System.out.print(new Date(timeBin) + " => #items: " + numOfItems + ", target: " + L + ", Max|C_ij|=" + 
			//		Collections.max(topicDistribution.values()) + ",  |A|=" + A.size() + ",  |Mc| = " + Mc.size() + ", |M|=" + M.size());
			
			int localSummaryLength = 0;
			while(localSummaryLength < L && !M.isEmpty()) {
				
				double maxScore = 0;
				String selected = null;
				for(Entry<String, Integer> entry : M.entrySet()) {
					
					String itemId = entry.getKey();
					Vector itemVector = vectors.get(itemId);
					
					Integer topicId = entry.getValue();
					
					double coverage = 0d;
					double topicSignificance = grid.getTopicSigificance(topicId, timeBin);
					if(topicSignificance > 0) {
						//coverage = topicSignificance * getCoverage(itemVector, timeBin, topicId, grid);
						//coverage = topicSignificance * getCoverage(itemVector, timeBin, A, grid);	
						coverage = getCoverage(itemVector, timeBin, A, grid);	
					}
					
					Double significance = divRankScores.get(itemId);
					if(significance == null)
						significance = 0d;
					
					if(coverage + significance == 0)
						continue;
					
					double redundancy = getRedundancy(itemId, S, vectors);
					
					double score = w1*coverage + w2*significance - w3*redundancy;
					
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
			
			if(localSummaryLength < L) {
				//Re-calculate compression
				long remainingTarget = targetLength - S.size();
				compression = ((double) remainingTarget) / ((double) remainingItems);
				
			}
			//System.out.println(",  |S| = " + S.size() + ", localSummary: " + localSummaryLength);
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
				
				Double redundancy = getRedundancy(id, S, vectors);
					
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
			Map<String, Integer> M = getM(window, A);
			for(String s : S)
				M.remove(s);
			
			System.out.println("|M|=" + M.size());
			
			while(S.size() < L && !M.isEmpty()) {
				//System.out.println("|S|=" + S.size());
				
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

	private double getCoverage(Vector messageVector, Long timeBin, Collection<Integer> A, TimeTopicGrid grid) {
		
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
				//if(topicVector != null)
				//	timeBinVector.mergeVector(topicVector);
			}
			//coverage = timeBinVector.cosine(messageVector);
			//coverage = timeBinVector.cosine(messageVector) / (double)A.size();
			
			if(coveredTopics == 0)
				return 0;
			
			coverage = coverage / (double) coveredTopics;
		}
		return coverage;
	}
	
	private double getCoverage(Vector messageVector, Long timeBin, Integer topicId, TimeTopicGrid grid) {

		Topic topic = grid.getTopic(topicId);
		if(topic == null)
			return 0;
				
		ExtendedTimeline topicTimeline = topic.getTimeline();
		Vector topicVector = topicTimeline.getVector(timeBin);
				
		Double coverage = topicVector.cosine(messageVector);
		return coverage;
	}
	
	private double getRedundancy(String itemId, Set<String> S, Map<String, Vector> vectors) {
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
			if(size == 0)
				return 0d;
			
			redundancy = redundancy / size;
		}
		return redundancy;
	}
	
	private double getScore(Integer topicId, String msgId, Set<String> S, Map<String, Vector> vectors, Pair<Long, Long> window) {
		
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
		
		//System.out.println("significance: " + significance + ", weight: " + weight + 
		//		", redundancy: " + redundancy);
		return a*importance - (1-a)*redundancy;
	}
	
	private Map<String, Integer> getMc(Map<String, Vector> vectors, Long timeBin, Collection<Integer> activeTopics) {
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
	
	private Map<String, Integer> getMc(Map<String, Vector> vectors, Pair<Long, Long> window, Collection<Integer> A) {
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
	
	
	private Map<String, Integer> getM(Pair<Long, Long> window, Collection<Integer> A) {
		System.out.println("* |A| = " + A.size());
		Map<String, Integer> totalItems = new HashMap<String, Integer>();
		for(Integer topicId : A) {
			System.out.println("-------------------");
			System.out.println("* topicId = " + topicId);
			List<String> windowItems = grid.getItems(topicId, window);
			
			Topic topic = grid.getTopic(topicId);
			ExtendedTimeline timeline = topic.getTimeline();
			
			System.out.println("* |ActiveBins(" + topicId + ")| = " + timeline.getActiveBins().size());
			int totalItem = 0, currentWindow = 0;
			for(Long ab : timeline.getActiveBins()) {
				totalItem += timeline.getFrequency(ab);
				if(ab < window.right && ab > window.left)
					currentWindow += timeline.getFrequency(ab);
				
			}
			System.out.println("* Items in Active Bins = " + totalItem);
			System.out.println("* Items in Current Active Bins = " + currentWindow);
			
			System.out.println("* |Peaks(" + topicId + ")| = " + timeline.getPeakWindows().size());
			System.out.println("* |T(" + topicId + ")| = " + timeline.getTotal());
			System.out.println("* |length(" + topicId + ")| = " + timeline.size());
			System.out.println("* |Ti(" + topicId + ")| = " + windowItems.size());
			
			for(String itemId : windowItems) {
				totalItems.put(itemId, topicId);
			}
		}
		return totalItems;
	}
	
}
