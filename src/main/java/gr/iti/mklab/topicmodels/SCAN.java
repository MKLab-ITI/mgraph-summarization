package gr.iti.mklab.topicmodels;

import edu.uci.ics.jung.graph.Graph;
import gr.iti.mklab.clustering.GraphClusterer;
import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.index.TextIndex;
import gr.iti.mklab.models.ClusterVector;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.models.Topic;
import gr.iti.mklab.models.WeightedEdge;
import gr.iti.mklab.utils.GraphUtils;
import gr.iti.mklab.utils.IOUtil;
import gr.iti.mklab.utils.ItemsUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.mongodb.morphia.query.Query;

public class SCAN extends TopicDetector {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5636674893001275360L;
	
	private int minFSet = 1;
	
	private Graph<String, WeightedEdge> graph;
	
	private HashMap<Integer, Collection<String>> associations = new HashMap<Integer, Collection<String>>();
	private Map<Integer, Topic> topicsMap = new HashMap<Integer, Topic>();

	public SCAN(Graph<String, WeightedEdge> graph) {
		this.graph = graph;
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
	public Map<Integer, Collection<String>> getTopicAssociations() {
		return associations;
	}

	@Override
	public void run(Map<String, Vector> vectors, Map<String, Item> items) throws IOException {

		List<ClusterVector> clusters = GraphClusterer.cluster(graph, vectors, items);
		Integer clusterId = 0;
		for(ClusterVector clusterVector : clusters) {
			
			Set<String> fSet = clusterVector.getFocusSet();
			if(fSet.size() < minFSet) {
				continue;
			}
			
			List<String> itemsList = new ArrayList<String>();
			itemsList.addAll(fSet);
			
			Map<String, Double> wordsMap = new TreeMap<String, Double>();
			Set<String> words = clusterVector.getTerms();
			double tfSum = 0;
			for(String word : words) {
				Double tf = clusterVector.getTf(word);
				
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
	
	private static String dataset = "SNOW";
	
	public static void main(String...args) throws Exception {
		
		String visualGraphFile = "/disk1_data/Datasets/" + dataset + "/graphs/visual_items_graph_pruned.graphml";
		String unifiedGraphFile = "/disk1_data/Datasets/" + dataset + "/graphs/unified_items_graph.graphml";
		
		/*
		MorphiaDAO<Item> dao = new MorphiaDAO<Item>("160.40.50.207" , dataset, Item.class);
		Query<Item> query = dao.getQuery().filter("original =", Boolean.TRUE);
		Map<String, Item> itemsMap = ItemsUtils.loadItems(dao.iterator(query));
		System.out.println(itemsMap.size() + " items");
		
		TextIndex tIndex = new TextIndex("/disk1_data/Datasets/" + dataset + "/TextIndex");
		tIndex.open();
		Map<String, Vector> vectorsMap = tIndex.createVocabulary("text");
		System.out.println(vectorsMap.size() + " vectors");
		*/
		
		Graph<String, WeightedEdge> graph = GraphUtils.loadGraph(unifiedGraphFile);
		System.out.println("Unified Graph");
		System.out.println(graph.getVertexCount() + " vertices");
		System.out.println(graph.getEdgeCount() + " edges");
		
		Graph<String, WeightedEdge> visualGraph = GraphUtils.loadGraph(visualGraphFile);
		//graph = GraphUtils.filter(graph, 0.5);
		System.out.println("Visual Graph");
		System.out.println(visualGraph.getVertexCount() + " vertices");
		System.out.println(visualGraph.getEdgeCount() + " edges");
		
		Collection<Collection<String>> cliques = GraphClusterer.cluster(GraphUtils.filter(visualGraph, 0.5), false);
		System.out.println(cliques.size() + " cliques");
		
		GraphUtils.fold(graph, cliques);
		System.out.println("Unified Graph after folding");
		System.out.println(graph.getVertexCount() + " vertices");
		System.out.println(graph.getEdgeCount() + " edges");
		
		Collection<Collection<String>> clusters = GraphClusterer.cluster(graph, true);
		IOUtil.saveClusters(clusters, "/disk1_data/Datasets/" + dataset + "/scan-clusters-folded.txt");
		
		/*
		SCAN scan = new SCAN(graph);
		scan.run(vectorsMap, itemsMap);

		List<Topic> topics = scan.getTopics();
		System.out.println(topics.size() + " topics!");
		
		Map<Integer, Collection<String>> associations = scan.getTopicAssociations();
		
		Integer topicId = Collections.max(associations.keySet());
		Set<String> associated = new HashSet<String>();
		for(Collection<String> c : associations.values()) {
			associated.addAll(c);
		}
		Set<String> unclustered = new HashSet<String>(vectorsMap.keySet());
		unclustered.removeAll(associated);
		for(String id : unclustered) {
			Set<String> set = new HashSet<String>();
			set.add(id);
			associations.put(++topicId, set);
		}
		
		IOUtil.saveClusters(associations.values(), "/disk1_data/Datasets/" + dataset + "/scan-clusters.txt");
		*/
	}
}
