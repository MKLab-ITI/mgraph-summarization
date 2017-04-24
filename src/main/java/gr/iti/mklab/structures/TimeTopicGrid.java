package gr.iti.mklab.structures;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import gr.iti.mklab.models.ExtendedTimeline;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.Pair;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.models.Topic;
import gr.iti.mklab.utils.Sorter;
import gr.iti.mklab.vocabulary.Vocabulary;

public class TimeTopicGrid {
		
	private Map<Long, Set<Integer>> activeTopicsGrid = new TreeMap<Long, Set<Integer>>();
	private Map<Long, Integer> maxOccurrences = new TreeMap<Long, Integer>();
	
	private Map<Integer, Topic> topics;
	private Map<String, Vector> vectors;
	private Map<String, Item> itemsMap;
	
	private ExtendedTimeline timeline, topicsTimeline;
	
	
	private int time;
	private TimeUnit tu;
	private long div;
	
	public TimeTopicGrid(int time, TimeUnit tu) {
		this.time = time;
		this.tu = tu;
		
		this.div = TimeUnit.MILLISECONDS.convert(time, tu);
		
		this.timeline = new ExtendedTimeline(time, tu);
		this.topicsTimeline = new ExtendedTimeline(time, tu);
	}
	
	private void addTopic(Topic topic) {
		
		ExtendedTimeline timeline = topic.getTimeline();
		List<Pair<Long, Long>> windows = timeline.getPeakWindows();
	
		for(Pair<Long, Long> window : windows) {
			for(long timeBin = window.left; timeBin<=window.right; timeBin += div) {
				Set<Integer> activeTopics = activeTopicsGrid.get(timeBin);
				if(activeTopics == null) {
					activeTopics = new HashSet<Integer>();
					activeTopicsGrid.put(timeBin, activeTopics);
				}
				activeTopics.add(topic.getId());
			}
		}
	}
	
	public void addTopics(List<Topic> topics, Map<Integer, Collection<String>> associations, 
			Map<String, Vector> vectors, Map<String, Item> itemsMap) {
		
		this.topics = new HashMap<Integer, Topic>();
		this.vectors = vectors;
		this.itemsMap = itemsMap;
		
		for(Topic topic : topics) {
			
			this.topics.put(topic.getId(), topic);

			Integer topicId = topic.getId();
			Collection<String> topicAssociations = associations.get(topicId);
			ExtendedTimeline topicTimeline = createTopicTimeline(topic, topicAssociations);
			topicsTimeline.merge(topicTimeline);
			
			addTopic(topic);
			
		}
		
		// Create generic timeline
		for(Item item : itemsMap.values()) {
			String id = item.getId();
			itemsMap.put(id, item);
			
			long publicationTime = item.getPublicationTime();
			Vector vector = vectors.get(id);
					
			if(vector != null) { 
				timeline.put(publicationTime, vector, id);
			}
		}
		timeline.detectPeakWindows();
		
		calculateSignificance();
	}
	
	private ExtendedTimeline createTopicTimeline(Topic topic, Collection<String> associations) {
		
		ExtendedTimeline timeline = new ExtendedTimeline(time, tu);
		if(associations != null) {
			for(String itemId : associations) {
				Vector vector = vectors.get(itemId);
				Item item = itemsMap.get(itemId);
			
				if(vector == null || item == null) {
					continue;
				}
			
				Long publicationTime = item.getPublicationTime();
				timeline.put(publicationTime, vector, itemId);
			}
		}
		topic.setTimeline(timeline);
		
		return timeline;
	}
	
	public Topic getTopic(Integer topicId) {
		return topics.get(topicId);
	}
	
	public Map<Integer, Integer> getTopicDistribution(Long timeBin) {
		Map<Integer, Integer> topicDistribution = new HashMap<Integer, Integer>();
	
		for(Integer index : topics.keySet()) {
			Topic topic = topics.get(index);
			
			ExtendedTimeline topicTimeline = topic.getTimeline();
			Integer numOfItems = topicTimeline.getFrequency(timeBin);
		
			topicDistribution.put(index, numOfItems);
		}
		
		return topicDistribution;
	}
	
	public ExtendedTimeline getTotalTimeline() {
		return this.timeline;
	}
	
	public ExtendedTimeline getTopicsTimeline() {
		return this.topicsTimeline;
	}
	
	public List<Entry<Integer, Double>> sortByPeakiness(Collection<Topic> topics) {
		TreeMap<Integer, Double> temp = new TreeMap<Integer, Double>();
		for(Topic topic : topics) {
			ExtendedTimeline timeline = topic.getTimeline();
			temp.put(topic.getId(), timeline.getPeakiness());
		}
		
		List<Entry<Integer, Double>> sortedTopics = Sorter.sort(temp);
		return sortedTopics;
	}
	
	public List<Entry<Integer, Double>> sortByPersistence(Collection<Topic> topics) {
		
		TreeMap<Integer, Double> temp = new TreeMap<Integer, Double>();
		for(Topic topic : topics) {
			ExtendedTimeline timeline = topic.getTimeline();
			temp.put(topic.getId(), timeline.getSustainedInterest());
		}
		
		List<Entry<Integer, Double>> sortedTopics = Sorter.sort(temp);
		return sortedTopics;
	}

	public List<Entry<Integer, Double>> sortByBursts(Collection<Topic> topics) {
		
		TreeMap<Integer, Double> temp = new TreeMap<Integer, Double>();
		for(Topic topic : topics) {
			ExtendedTimeline timeline = topic.getTimeline();
			List<Pair<Long, Long>> peakWindows = timeline.getPeakWindows();
			temp.put(topic.getId(), new Double(peakWindows.size()));
		}
		
		List<Entry<Integer, Double>> sortedTopics = Sorter.sort(temp);
		return sortedTopics;
	}
	
	public List<Entry<Integer, Double>> sortBySize(Collection<Topic> topics) {
		
		TreeMap<Integer, Double> temp = new TreeMap<Integer, Double>();
		for(Topic topic : topics) {
			ExtendedTimeline timeline = topic.getTimeline();
			temp.put(topic.getId(), new Double(timeline.getTotal()));
		}
		
		List<Entry<Integer, Double>> sortedTopics = Sorter.sort(temp);
		return sortedTopics;
	}

	public void calculateSignificance() {
		for(Entry<Long, Set<Integer>> entry : activeTopicsGrid.entrySet()) {
			
			Long timeBin = entry.getKey();
			Set<Integer> activeTopics = entry.getValue();
			
			int Tmax = 0;
			for(Integer topicId : activeTopics) {
				Topic topic = topics.get(topicId);
				ExtendedTimeline timeline = topic.getTimeline();
				
				Integer T = timeline.getFrequency(timeBin);
				if(Tmax < T)
					Tmax = T;
			}
			maxOccurrences.put(timeBin, Tmax);
		}
	}
	
	public double getTopicSigificance(int topicId, long timeBin) {
		
		Collection<Integer> A = getActiveTopics(timeBin);
		
		if(!A.contains(topicId)) {
			return .0;
		}
		
		Topic topic = topics.get(topicId);
		ExtendedTimeline timeline = topic.getTimeline();
		Integer T = timeline.getFrequency(timeBin);
		
		Integer Tmax = 0;
		for(Integer aTopicId : A) { 
			Topic aTopic = topics.get(aTopicId);
			ExtendedTimeline aTimeline = aTopic.getTimeline();
			Integer Ta = aTimeline.getFrequency(timeBin);
			if(Tmax < Ta) {
				Tmax = Ta;
			}
		}

		if(Tmax == null || Tmax == 0)
			return 0;
		
		return T.doubleValue() / Tmax.doubleValue();
	}
	
	public double getTopicSigificance(int topicId, Pair<Long, Long> window) {
		double maxSignificance = 0;
		for(long timeBin = window.left; timeBin <= window.right; timeBin += div) {
			double significance = getTopicSigificance(topicId, timeBin);
			if(significance > maxSignificance)
				maxSignificance = significance;
		}
		return maxSignificance;
	}
	
	public double getTermWeight(String term, int topicId, long timeBin) {
		Topic topic = topics.get(topicId);
		ExtendedTimeline timeline = topic.getTimeline();
		Vector vector = timeline.getVector(timeBin);
		
		return vector.getTf(term) * Vocabulary.getIdf(term);
	}
	
	public double getWeight(Vector messageVector, int topicId, long publicationTime) {
		
		long timeBin = (publicationTime/div)*div;
		
		Set<String> messageTerms = messageVector.getTerms();
		Topic topic = topics.get(topicId);
		ExtendedTimeline timeline = topic.getTimeline();
		Vector binVector = timeline.getVector(timeBin);
		double weight = 0;
		for(String term : messageTerms) {
			weight += binVector.getTf(term) * Vocabulary.getIdf(term);
		}
		return weight;
	}
	
	public double getWeight(Vector messageVector, int topicId, Pair<Long, Long> window) {

		Topic topic = topics.get(topicId);
		ExtendedTimeline timeline = topic.getTimeline();
		
		Vector windowVector = new Vector();
		for(long timeBin = window.left; timeBin <= window.right; timeBin += div) {
			Vector vector = timeline.getVector(timeBin);
			if(vector != null)
				windowVector.mergeVector(vector);
		}
		return messageVector.getTfIdfWeight();
	}

	public Item getItem(String itemId) {
		return itemsMap.get(itemId);
	}
	
	public List<String> getItems(Integer topicId, Long time) {
		Topic topic = topics.get(topicId);
		ExtendedTimeline timeline = topic.getTimeline();
		List<String> messages = timeline.getItems(time);
		return messages;
	}
	
	public List<String> getItems(Integer topicId, Pair<Long, Long> window) {
		Topic topic = topics.get(topicId);
		ExtendedTimeline timeline = topic.getTimeline();
		List<String> messages = timeline.getItems(window);
		return messages;
	}
	
	public Collection<Integer> getActiveTopics(Long timeBin) {
		Set<Integer> topics = new HashSet<Integer>(); 
		Set<Integer> activeTopics = activeTopicsGrid.get(timeBin);	
		if(activeTopics != null) {
			topics.addAll(activeTopics);	
		}
		
		return new ArrayList<Integer>(topics);
	}
	
	public Collection<Integer> getActiveTopics(Pair<Long, Long> window) {
		Set<Integer> activeTopics = new HashSet<Integer>(); 
		for(long timeBin = window.left; timeBin<=window.right; timeBin += div) {
			Set<Integer> binActiveTopics = activeTopicsGrid.get(timeBin);
			if(binActiveTopics == null)
				continue;
			else
				activeTopics.addAll(binActiveTopics);	
		}
		return new ArrayList<Integer>(activeTopics);
	}
	
	public Collection<Integer> getActiveTopics() {
		Set<Integer> topics = new HashSet<Integer>(); 
		for(Long timeBin : activeTopicsGrid.keySet()) {
			Set<Integer> activeTopics = activeTopicsGrid.get(timeBin);
			if(activeTopics == null)
				continue;
			else
				topics.addAll(activeTopics);	
		}
		return new ArrayList<Integer>(topics);
	}
	
	public Collection<Integer> getTopics() {
		return topics.keySet();
	}
	
	public Long getTimeslotLength() {
		return div;
	}
	
	public Map<String, Integer> getTopicRelatedItems(Long timeBin) {
		Map<String, Integer> totalItems = new HashMap<String, Integer>();
		for(Integer topicId : topics.keySet()) {
			List<String> binItems = getItems(topicId, timeBin);	
			for(String itemId : binItems) {
				totalItems.put(itemId, topicId);
			}
		}
		
		return totalItems;
	}
	
	public Map<String, Integer> getTopicRelatedItems(Pair<Long, Long> window) {
		Map<String, Integer> totalItems = new HashMap<String, Integer>();
		for(Integer topicId : topics.keySet()) {
			List<String> windowItems = getItems(topicId, window);	
			for(String itemId : windowItems) {
				totalItems.put(itemId, topicId);
			}
		}
		return totalItems;
	}
	
	public Map<String, Integer> getItems(Long timeBin) {
		Map<String, Integer> totalItems = new HashMap<String, Integer>();
		for(Integer topicId : topics.keySet()) {
			List<String> windowItems = getItems(topicId, timeBin);	
			for(String itemId : windowItems) {
				totalItems.put(itemId, topicId);
			}
		}
		
		List<String> allItems = timeline.getItems(timeBin);
		for(String itemId : allItems) {
			if(!totalItems.containsKey(itemId)) {
				totalItems.put(itemId, null);
			}
		}
		
		return totalItems;
	}
	
	public Map<String, Integer> getItems(Pair<Long, Long> window) {
		Map<String, Integer> totalItems = new HashMap<String, Integer>();
		for(Integer topicId : topics.keySet()) {
			List<String> windowItems = getItems(topicId, window);	
			for(String itemId : windowItems) {
				if(!totalItems.containsKey(itemId)) {
					totalItems.put(itemId, topicId);
				}
			}
		}
		
		List<String> allItems = timeline.getItems(window);
		for(String itemId : allItems) {
			if(!totalItems.containsKey(itemId)) {
				totalItems.put(itemId, null);
			}
		}
		
		return totalItems;
	}
	
	public void writeToFile(String filename) throws FileNotFoundException, IOException {
		
		int t = 0;
		for(Topic topic : topics.values()) {
			if(topic.getTimeline().getPeakWindows().size()!=0) {
				t++;
			}
		}
		Double[][] map = new Double[topics.size()][activeTopicsGrid.size()];
		int timeIndex = 0;
		for(Entry<Long, Set<Integer>> e : activeTopicsGrid.entrySet()) {
			Long timeBin = e.getKey();
			Set<Integer> activeTopics = e.getValue();
			t = 0;
			for(int topic=0; topic<topics.size(); topic++) {
				if(topics.get(topic).getTimeline().getPeakWindows().size()==0) {
					continue;
				}
				
				if(!activeTopics.contains(topic)) {
					map[t++][timeIndex] = 0D;
				}
				else {
					map[t++][timeIndex] = getTopicSigificance(topic, timeBin);
				}
			}
			timeIndex++;
		}
		List<String> lines = new ArrayList<String>();
		for(Double[] row : map) {
			String line = StringUtils.join(row, "\t");
			lines.add(line);
		}
		IOUtils.writeLines(lines, "\n", new FileOutputStream(filename));
		
	}
	
}
