package gr.iti.mklab.summarization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.mongodb.morphia.query.Query;

import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.models.ExtendedTimeline;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.Pair;
import gr.iti.mklab.models.Topic;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.structures.TimeTopicGrid;
import gr.iti.mklab.topicmodels.SCAN;
import gr.iti.mklab.utils.CollectionsUtils;
import gr.iti.mklab.vocabulary.Vocabulary;

public class TopicSummarizer implements Summarizer {

	private double a = 0.6, b = 0.4;
	private List<Topic> _topics;

	public TopicSummarizer(List<Topic> topics) {
		_topics = new ArrayList<Topic>(topics);
		
		Comparator<Topic> comp = new Comparator<Topic>() {
			@Override
			public int compare(Topic t1, Topic t2) {
				if(t1.getTimeline().getTotal() < t2.getTimeline().getTotal())
					return 1;
				return -1;
			}
		};
		
		Collections.sort(_topics, comp);
	}
		
	@Override
	public Set<String> summarize(Map<String, Vector> messages, int L) {
		return null;
	}

	@Override
	public Set<String> summarize(Map<String, Vector> vectors, int L, Pair<Long, Long> window) {
		
		Set<String> summary = new HashSet<String>();
		int S = summary.size();
		while(summary.size() < L) {
			for(Topic topic : _topics) {
			
				ExtendedTimeline topicTimeline = topic.getTimeline();
				Vector topicCentroid = topicTimeline.getVector(window);
			
				if(topicCentroid == null) {
					continue;
				}
			
				String selected = null;
				double maxScore = 0;
				for(String id : topicTimeline.getItems(window)) {
					if(summary.contains(id))
						continue;
					
					Vector itemVector = vectors.get(id);
					if(itemVector == null) {
						continue;
					}
					
					double redundancy = getRedundancy(itemVector, CollectionsUtils.mapSlice(vectors, summary));
					Double score = a * topicCentroid.cosine(itemVector) - b * redundancy;
					
					if(score > maxScore) {
						maxScore = score;
						selected = id;
					}
				}
			
				if(selected != null) {
					summary.add(selected);
					if(summary.size() >= L) {
						break;
					}
				}
			}
			
			if(S == summary.size()) {
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
	public Set<String> summarize(Map<String, Vector> vectors, double compression, Pair<Long, Long> window) {
		return null;
	}

	private double getRedundancy(Vector itemVvector,  Map<String, Vector> vectors) {
		double maxRedundancy = 0;
		
		for(Vector vector : vectors.values()) {
			if(vector == null) {
				continue;
			}
			
			double redundancy = itemVvector.cosine(vector);
			if(redundancy > maxRedundancy) {
				maxRedundancy = redundancy;
			}
		}
		return maxRedundancy;
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
		
		TimeTopicGrid ttGrid = new TimeTopicGrid(1, TimeUnit.HOURS);
		ttGrid.addTopics(scan.getTopics(), scan.getTopicAssociations(), vectorsMap, itemsMap);
		
		ExtendedTimeline tml = ttGrid.getTotalTimeline();
		List<Pair<Long, Long>> peakWindows = tml.detectPeakWindows();
		
		ExtendedTimeline topicsTimeline = ttGrid.getTopicsTimeline();
		
		TopicSummarizer summarizer = new TopicSummarizer(scan.getTopics());
		double compression = 0.01;
		for(Pair<Long, Long> window : peakWindows) {
			List<String> items = tml.getItems(window);
			if(items.size() > 1000) {
				System.out.println(new Date(window.left) + " - " + new Date(window.right));
				System.out.println("#items: " + items.size());
				System.out.println("#clustered items: " + topicsTimeline.getFrequency(window));
				
				int L = (int) Math.round(compression * items.size());
				System.out.println("target: " + L);
				
				Set<String> summary = summarizer.summarize(vectorsMap, L, window);

				System.out.println("|S|=" + summary.size());
			}
		}
		
	}
	
}
