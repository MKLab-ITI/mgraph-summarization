package gr.iti.mklab.topicmodelling;

import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.Topic;
import gr.iti.mklab.models.Vector;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface TopicDetector {
	
	public void run(Map<String, Vector> vectors, Map<String, Item> items) throws IOException;
	
	public void saveModel(String serializedModelFile) throws IOException ;
	
	public void loadModel(String serializedModelFile) throws Exception;
		
	public List<Topic> getTopics();
	
	public Map<Integer, Topic> getTopicsMap();

	public int getNumOfTopics();

	public Map<Integer, List<String>> getTopicAssociations();
	
}
