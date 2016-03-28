package gr.iti.mklab.topicmodelling;

import edu.ucla.sspace.lsa.LatentSemanticAnalysis;
import edu.ucla.sspace.text.Document;
import edu.ucla.sspace.text.StringDocument;
import edu.ucla.sspace.vector.DoubleVector;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.Topic;
import gr.iti.mklab.models.Vector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang.StringUtils;

public class LSA implements TopicDetector {

	private static double minProbability = 0.2;
	
	private TreeMap<Integer, List<String>> associations = new TreeMap<Integer, List<String>>();
	private Map<Integer, Topic> topicsMap = new TreeMap<Integer, Topic>();
	
	@Override
	public void run(Map<String, Vector> vectors, Map<String, Item> items) throws IOException {
		
		int n = vectors.size();
		int K = (int) Math.sqrt(n);
		
		List<String> itemIds = new ArrayList<String>(vectors.keySet());
		List<Document> documents = new ArrayList<Document>(itemIds.size());
		for(String itemId : itemIds) {
			Vector vector = vectors.get(itemId);
			String text = StringUtils.join(vector.getWords(), " ");
			
			StringDocument document = new StringDocument(text);
			documents.add(document);
		}

		LatentSemanticAnalysis lsa = new LatentSemanticAnalysis(K, true);
		for(Document doc : documents) {
			lsa.processDocument(doc.reader());
		}
		lsa.processSpace(System.getProperties());
		
		for(Integer topicId=0; topicId<lsa.getVectorLength(); topicId++) {
			Topic topic = new Topic(topicId, null);
			topicsMap.put(topicId, topic);
		}
		
		for(int index=0; index<itemIds.size(); index++) {
			String itemId = itemIds.get(index);
			DoubleVector topicDistribution = lsa.getDocumentVector(index);
			
			double maxProb = 0;
			for(int i=0; i<topicDistribution.length(); i++) {
				if(topicDistribution.get(i) > maxProb) {
					index = i;
					maxProb = topicDistribution.get(i);
				}
			}
			if(maxProb > minProbability) {
				List<String> list = associations.get(index);
				if(list == null) {
					list = new ArrayList<String>();
					associations.put(index, list);
				}
				list.add(itemId);
			}
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
		return new ArrayList<Topic>(topicsMap.values());
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
