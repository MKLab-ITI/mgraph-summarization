package gr.iti.mklab.topicmodelling;

import edu.uci.ics.jung.graph.Graph;
import gr.iti.mklab.Config;
import gr.iti.mklab.clustering.GraphClusterer;
import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.models.ClusterVector;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.Topic;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.models.WeightedEdge;
import gr.iti.mklab.utils.GraphUtils;
import gr.iti.mklab.vocabulary.Vocabulary;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.mongodb.morphia.query.Query;

public class SCAN implements TopicDetector, Serializable {

	private static final long serialVersionUID = 7857170033810246813L;

	private double similarityCutoff = 0.7;

	private Graph<String, WeightedEdge> graph;
	
	private HashMap<Integer, List<String>> associations = new HashMap<Integer, List<String>>();
	private Map<Integer, Topic> topicsMap = new HashMap<Integer, Topic>();
	
	public SCAN() {

	}
	
	public SCAN(double similarityCutoff) throws IOException {
		this.similarityCutoff = similarityCutoff;
	}

	public SCAN(Graph<String, WeightedEdge> graph) {
		this.graph = graph;
	}
	
	@Override
	public void saveModel(String serializedModelFile) throws IOException {
		FileOutputStream fos = new FileOutputStream(serializedModelFile);
		ObjectOutputStream out = new ObjectOutputStream(fos);
		out.writeObject(this);
		out.close();
	}

	@Override
	public void loadModel(String serializedModelFile) throws Exception {
		FileInputStream fis = new FileInputStream(serializedModelFile);
		ObjectInputStream in = new ObjectInputStream(fis);
		SCAN model = (SCAN) in.readObject();
		
		this.similarityCutoff = model.similarityCutoff;
		this.topicsMap = model.topicsMap;
		this.associations = model.associations;
		this.graph = model.graph;
		
		in.close();
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

	@Override
	public void run(Map<String, Vector> vectors, Map<String, Item> items) throws IOException {
		List<ClusterVector> clusters;
		if(graph == null) {
			graph = GraphUtils.generateGraph(vectors, similarityCutoff);
		}
		 
		clusters = GraphClusterer.cluster(graph, vectors, items);
		Integer clusterId = 0;
		for(ClusterVector clusterVector : clusters) {
			
			Set<String> fSet = clusterVector.getFocusSet();
			if(fSet.size()<8)
				continue;
			
			List<String> itemsList = new ArrayList<String>();
			itemsList.addAll(fSet);
			
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

	public Graph<String, WeightedEdge> getGraph() {
		return this.graph;
	}
	
	public static void main(String...args) throws Exception {
		
		MorphiaDAO<Item> dao = new MorphiaDAO<Item>(Config.hostname , Config.dbname, Item.class);
		Query<Item> query = dao.getQuery().filter("accepted =", Boolean.TRUE);
		System.out.println(dao.count(query) + " items");
		
		Map<String, Item> itemsMap = new HashMap<String, Item>();
		Iterator<Item> it = dao.iterator(query);
		while(it.hasNext()) {
			Item item = it.next();
			itemsMap.put(item.getId(), item);
		}
		
		Map<String, Vector> vectorsMap = Vocabulary.createVocabulary(itemsMap.values(), 2);
		
		Graph<String, WeightedEdge> graph = GraphUtils.loadGraph(Config.graphFile);
		System.out.println(graph.getVertexCount() + " vertices");
		System.out.println(graph.getEdgeCount() + " edges");
		
		//graph = GraphUtils.filter(graph, 0.5);
		//System.out.println(graph.getVertexCount() + " vertices");
		//System.out.println(graph.getEdgeCount() + " edges");
		
		SCAN scan = new SCAN(graph);
		scan.run(vectorsMap, itemsMap);
		
		List<Topic> topics = scan.getTopics();
		System.out.println(topics.size() + " topics!");
		
		Set<String> total = new HashSet<String>();
		Map<Integer, List<String>> associations = scan.getTopicAssociations();
		for(Topic topic : topics) {
			//System.out.println(topic.getId() + " => " + associations.get(topic.getId()).size());
			total.addAll(associations.get(topic.getId()));
			//System.out.println("===========================================");
		}
		System.out.println(total.size());
		
		scan.saveModel(Config.modelFile);
		
	}
}
