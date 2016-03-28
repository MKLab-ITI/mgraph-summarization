package gr.iti.mklab.topicmodelling;

import gr.iti.mklab.clustering.ΝΝClusterer;
import gr.iti.mklab.models.ClusterVector;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.Topic;
import gr.iti.mklab.models.Vector;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;

public class NNTopicModel implements TopicDetector, Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -970477424655932554L;
	
	private double similarityThreshold;

	private HashMap<Integer, List<String>> associations = new HashMap<Integer, List<String>>();
	private Map<Integer, Topic> topicsMap = new HashMap<Integer, Topic>();
	
	public NNTopicModel(double similarityThreshold) {
		this.similarityThreshold = similarityThreshold;
	}
	
	@Override
	public void run(Map<String, Vector> vectors, Map<String, Item> items)
			throws IOException {
		
		List<ClusterVector> clusters = ΝΝClusterer.cluster(vectors, items, similarityThreshold);
		
		Integer clusterId = 0;
		for(ClusterVector clusterVector : clusters) {
			
			Set<String> fSet = clusterVector.getFocusSet();
			List<String> itemsList = new ArrayList<String>();
			itemsList.addAll(fSet);
			
			if(fSet.size()<10)
				continue;
			
			Map<String, Double> wordsMap = new TreeMap<String, Double>();
			Set<String> words = clusterVector.getWords();
			double tfSum = 0;
			for(String word : words) {
				Double tf = clusterVector.getTf(word);
				//Double idf = Vocabulary.getIdf(word);
				tfSum += tf;
				wordsMap.put(word, tf);
			}
			for(Entry<String, Double> e : wordsMap.entrySet()) {
				wordsMap.put(e.getKey(), e.getValue()/tfSum);
			}
			
			Topic topic = new Topic(clusterId, wordsMap);
			topicsMap.put(clusterId, topic);
			
			associations.put(clusterId++, itemsList);
		}
	}

	@Override
	public void saveModel(String serializedModelFile) throws IOException {
		
		
	}

	@Override
	public void loadModel(String serializedModelFile) throws Exception {
		
	}

	@Override
	public List<Topic> getTopics() {
		List<Topic> topics = new ArrayList<Topic>();
		topics.addAll(topicsMap.values());
		return topics;
	}

	@Override
	public Map<Integer, Topic> getTopicsMap() {
		return topicsMap;
	}

	@Override
	public int getNumOfTopics() {
		return topicsMap.size();
	}

	@Override
	public Map<Integer, List<String>> getTopicAssociations() {
		return associations;
	}
	
}
