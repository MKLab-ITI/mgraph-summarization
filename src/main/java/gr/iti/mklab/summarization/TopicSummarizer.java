package gr.iti.mklab.summarization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import gr.iti.mklab.models.ExtendedTimeline;
import gr.iti.mklab.models.Pair;
import gr.iti.mklab.models.Topic;
import gr.iti.mklab.models.Vector;

public class TopicSummarizer implements Summarizer {

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
		
		//Collections.sort(_topics, comp);
	}
	
	
	@Override
	public Set<String> summarize(Map<String, Vector> messages, int L) {
		return null;
	}

	
	@Override
	public Set<String> summarize(Map<String, Vector> vectors, int L, Pair<Long, Long> window) {
		
		Set<String> summary = new HashSet<String>();
			
		for(Topic topic : _topics) {
			
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
				
				Double score = topicCentroid.cosine(itemVector) - getRedundancy(id, summary, vectors);
				if(score > maxScore) {
					maxScore = score;
					selected = id;
				}
				
			}
			
			if(selected != null) {
				summary.add(selected);
			}
			
			if(summary.size() >= L)
				break;
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
	
}
