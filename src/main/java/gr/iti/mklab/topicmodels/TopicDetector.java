package gr.iti.mklab.topicmodels;

import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.Topic;
import gr.iti.mklab.models.Vector;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public abstract class TopicDetector implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 4418982468912213406L;

	public abstract void run(Map<String, Vector> vectors, Map<String, Item> items) throws IOException;
	
	public void saveModel(String serializedModelFile) throws IOException {
		FileOutputStream fos = new FileOutputStream(serializedModelFile);
		ObjectOutputStream out = new ObjectOutputStream(fos);
		out.writeObject(this);
		out.close();
	}
	
	public static TopicDetector loadModel(String serializedModelFile) throws Exception {
		FileInputStream fis = new FileInputStream(serializedModelFile);
		ObjectInputStream in = new ObjectInputStream(fis);
		TopicDetector model = (TopicDetector) in.readObject();

		in.close();
		
		return model;
	}
		
	public abstract List<Topic> getTopics();
	
	public abstract Map<Integer, Topic> getTopicsMap();

	public abstract int getNumOfTopics();

	public abstract Map<Integer, Collection<String>> getTopicAssociations();
	
}
