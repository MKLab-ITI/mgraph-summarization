package gr.iti.mklab.topicmodels;

import edu.ucla.sspace.text.Document;
import edu.ucla.sspace.text.TemporalStringDocument;
import edu.ucla.sspace.tri.FixedDurationTemporalRandomIndexing;
import edu.ucla.sspace.vector.DenseVector;
import edu.ucla.sspace.vector.DoubleVector;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.models.Topic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang.StringUtils;

public class TRI extends TopicDetector {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4818104526932455781L;

	private static double minProbability = 0.2;
	
	private TreeMap<Integer, Collection<String>> associations = new TreeMap<Integer, Collection<String>>();
	private Map<Integer, Topic> topicsMap = new TreeMap<Integer, Topic>();
	
	@Override
	public void run(Map<String, Vector> vectors, Map<String, Item> items) throws IOException {

		List<String> itemIds = new ArrayList<String>(vectors.keySet());
		List<Document> documents = new ArrayList<Document>(itemIds.size());
		for(String itemId : itemIds) {
			Vector vector = vectors.get(itemId);
			String text = StringUtils.join(vector.getTerms(), " ");
			
			Item item = items.get(itemId);
			TemporalStringDocument document;
			if(item != null)
				document = new TemporalStringDocument(text, item.getPublicationTime());
			else
				document = new TemporalStringDocument(text, 0l);
			
			documents.add(document);
		}
		
		FixedDurationTemporalRandomIndexing fdTRI = new FixedDurationTemporalRandomIndexing();
		for(Document doc : documents) {
			fdTRI.processDocument(doc.reader());
		}
		fdTRI.processSpace(System.getProperties());
		
		for(Integer topicId=0; topicId<fdTRI.getVectorLength(); topicId++) {
			Topic topic = new Topic(topicId, null);
			topicsMap.put(topicId, topic);
		}

		for(int index=0; index<itemIds.size(); index++) {
			String itemId = itemIds.get(index);
			DoubleVector topicDistribution = new DenseVector(new double[0]);
			
			double maxProb = 0;
			for(int i=0; i<topicDistribution.length(); i++) {
				if(topicDistribution.get(i) > maxProb) {
					index = i;
					maxProb = topicDistribution.get(i);
				}
			}
			if(maxProb > minProbability) {
				Collection<String> list = associations.get(index);
				if(list == null) {
					list = new ArrayList<String>();
					associations.put(index, list);
				}
				list.add(itemId);
			}
		}
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
	public Map<Integer, Collection<String>> getTopicAssociations() {
		return associations;
	}

}
