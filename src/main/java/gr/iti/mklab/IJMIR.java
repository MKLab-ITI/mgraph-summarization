package gr.iti.mklab;

import edu.uci.ics.jung.graph.DirectedGraph;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.util.EdgeType;
import gr.iti.mklab.JudgementsApi.ImageJudgement;
import gr.iti.mklab.clustering.GraphClusterer;
import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.index.TextIndex;
import gr.iti.mklab.models.Cluster;
import gr.iti.mklab.models.ClusterVector;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.MediaItem;
import gr.iti.mklab.models.Pair;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.models.WeightedEdge;
import gr.iti.mklab.summarization.RandomSummarizer;
import gr.iti.mklab.summarization.itcr.ITSummarizer;
import gr.iti.mklab.utils.CollectionsUtils;
import gr.iti.mklab.utils.GraphUtils;
import gr.iti.mklab.utils.IOUtil;
import gr.iti.mklab.utils.ItemsUtils;
import gr.iti.mklab.utils.Sorter;
import gr.iti.mklab.vocabulary.Vocabulary;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.commons.io.IOUtils;
import org.mongodb.morphia.query.Query;


public class IJMIR {

	private static String dataset = "SNOW";
	
	public static void main(String...args) throws Exception {
		
		//MorphiaDAO<Cluster> clusterDAO = new MorphiaDAO<Cluster>("160.40.50.207", dataset, Cluster.class);
		
		Set<String> idsToRemove = getNoiseItem(new MorphiaDAO<MediaItem>("160.40.50.207", dataset, MediaItem.class));
		System.out.println(idsToRemove.size() + " ids to remove");
		
		MorphiaDAO<Item> dao = new MorphiaDAO<Item>("160.40.50.207", dataset, Item.class);
		Iterator<Item> iterator = dao.iterator();
		Map<String, Item> itemsMap = ItemsUtils.loadUniqueItems(iterator);
		System.out.println(itemsMap.size() + " items");
		
		itemsMap = CollectionsUtils.mapFilter(itemsMap, idsToRemove);
		System.out.println(itemsMap.size() + " items after filtering");
		
		//String textualGraphFile = "/disk1_data/Datasets/" + dataset + "/graphs/textual_items_graph_pruned.graphml";
		//String socialGraphFile = "/disk1_data/Datasets/" + dataset + "/graphs/social_items_graph.graphml";
		String visualGraphFile = "/disk1_data/Datasets/" + dataset + "/graphs/visual_items_graph_pruned.graphml";
		//String unifiedGraphFile = "/disk1_data/Datasets/" + dataset + "/graphs/unified_items_graph.graphml";
		//String foldedUnifiedGraphFile = "/disk1_data/Datasets/" + dataset + "/graphs/unified_items_graph_folded.graphml";
		
		//String clustersFile = "/disk1_data/Datasets/" + dataset + "/scan-clusters-folded.txt";
		
		TextIndex tIndex = new TextIndex("/disk1_data/Datasets/" + dataset + "/TextIndex");
		tIndex.open();
		System.out.println(tIndex.count() + " documents in text index!");
		
		Map<String, Vector> vectorsMap = tIndex.createVocabulary();
		System.out.println(vectorsMap.size() + " vectors");
		vectorsMap = CollectionsUtils.mapFilter(vectorsMap, idsToRemove);
		System.out.println(vectorsMap.size() + " vectors after filtering");
		
		Graph<String, WeightedEdge> visualGraph = GraphUtils.loadGraph(visualGraphFile);
		System.out.println("Visual Graph: #Vertices " + visualGraph.getVertexCount() + ", #edges: " + visualGraph.getEdgeCount()
				 + ", Density: " + GraphUtils.getGraphDensity(visualGraph) + ", MinMax: " + GraphUtils.getMinMaxWeight(visualGraph));
		
		visualGraph = GraphUtils.discardNodes(visualGraph, idsToRemove);
		System.out.println("Visual Graph: #Vertices " + visualGraph.getVertexCount() + ", #edges: " + visualGraph.getEdgeCount()
				 + ", Density: " + GraphUtils.getGraphDensity(visualGraph) + ", MinMax: " + GraphUtils.getMinMaxWeight(visualGraph));
		
		Collection<Collection<String>> cliques = GraphClusterer.cluster(GraphUtils.filter(visualGraph, 0.55), false);
		System.out.println("Cliques: " + cliques.size());
		
		GraphUtils.fold(visualGraph, cliques);
		System.out.println("Visual Graph: #Vertices " + visualGraph.getVertexCount() + ", #edges: " + visualGraph.getEdgeCount()
				 + ", Density: " + GraphUtils.getGraphDensity(visualGraph) + ", MinMax: " + GraphUtils.getMinMaxWeight(visualGraph));
		
		//Graph<String, WeightedEdge> unifiedGraph = GraphUtils.loadGraph(foldedUnifiedGraphFile);
		//System.out.println("Unified Graph: #Vertices " + unifiedGraph.getVertexCount() + ", #edges: " + unifiedGraph.getEdgeCount()
		//		 + ", Density: " + GraphUtils.getGraphDensity(unifiedGraph) + ", MinMax: " + GraphUtils.getMinMaxWeight(unifiedGraph));
		
		Vector.fold(vectorsMap, cliques);
		Item.fold(itemsMap, cliques);
		//List<String> missing = new ArrayList<String>();
		//for(String vertex : unifiedGraph.getVertices()) {
		//	if(!vectorsMap.containsKey(vertex) || !itemsMap.containsKey(vertex)) {
		//		missing.add(vertex);
		//		System.out.println(vertex);
		//	}
		//}
		//System.out.println(missing.size() + " missing items.");
		
		//unifiedGraph = GraphUtils.discardNodes(unifiedGraph, missing);
		
		//Collection<Collection<String>> clusters = GraphClusterer.cluster(unifiedGraph, true);
		//IOUtil.saveClusters(clusters, clustersFile);
		
		//clustering(unifiedGraph, clusterDAO);
		
		//List<Set<String>> clusters = IOUtil.loadClusters(clustersFile);
		//List<Cluster> clusters = createClusters(IOUtil.loadClusters(clustersFile), unifiedGraph);
		//FileOutputStream fileOut = new FileOutputStream("/disk1_data/Datasets/" + dataset + "/graphs/clusters_folded.bin");
		//ObjectOutputStream out = new ObjectOutputStream(fileOut);
		//out.writeObject(clusters);
		//out.close();
		//fileOut.close();
	
		FileInputStream fileIn = new FileInputStream("/disk1_data/Datasets/" + dataset + "/graphs/clusters_folded.bin");
		ObjectInputStream in = new ObjectInputStream(fileIn);
		@SuppressWarnings("unchecked")
		List<Cluster> clusters = (List<Cluster>) in.readObject();
		in.close();
		fileIn.close();
         
		//List<Cluster> clusters = clusterDAO.get();
		Map<String, Cluster> clustersMap = new HashMap<String, Cluster>();
		for(Cluster cluster : clusters) {
			clustersMap.put(cluster.getId(), cluster);
		}
		Map<String, ClusterVector> clusterVectors = getClusterVectors(clustersMap, vectorsMap, itemsMap);
			 
		System.out.println(clusters.size() + " clusters");
		System.out.println(clusterVectors.size() + " cluster Vectors");
		
		ITSummarizer summarizer = new ITSummarizer(itemsMap, vectorsMap, clusterVectors);
		
		Map<String, Double> ranks = summarizer.summarize(visualGraph);
		List<Entry<String, Double>> sortedRanks = Sorter.sort(ranks);
		
		writeResults(sortedRanks, "/disk1_data/Datasets/" + dataset + "/summary.tsv");
	
		Set<String> s = new HashSet<String>();
		s.addAll(visualGraph.getVertices());
		
		Map<String, Double> popRanks = summarizer.getPopularityScores(s);
		Map<String, Double> topicRanks = summarizer.getTopicScores(s);	
		Map<String, Double> lexRanks = summarizer.lexrank(visualGraph);
		
		List<Entry<String, Double>> sortedLexRanks = Sorter.sort(lexRanks);
		List<Entry<String, Double>> sortedTopicRanks = Sorter.sort(topicRanks);
		List<Entry<String, Double>> sortedPopRanks = Sorter.sort(popRanks);

		writeResults(sortedLexRanks, "/disk1_data/Datasets/" + dataset + "/lexrank_summary.tsv");
		writeResults(sortedTopicRanks, "/disk1_data/Datasets/" + dataset + "/topics_summary.tsv");
		writeResults(sortedPopRanks, "/disk1_data/Datasets/" + dataset + "/pop_summary.tsv");
		
		//Graph<String, WeightedEdge> textualGraph = GraphUtils.loadGraph(textualGraphFile);
		//System.out.println("Textual Graph: #Vertices " + textualGraph.getVertexCount() + ", #edges: " + textualGraph.getEdgeCount()
		//		+ ", Density: " + GraphUtils.getGraphDensity(textualGraph) + ", MinMax: " + GraphUtils.getMinMaxWeight(textualGraph));
		
		//DirectedGraph<String, WeightedEdge> socialGraph = GraphUtils.generateSocialGraph(itemsMap);
		//GraphUtils.saveGraph(socialGraph, socialGraphFile);
		//Graph<String, WeightedEdge> socialGraph = GraphUtils.loadGraph(socialGraphFile);
		//System.out.println("Social Graph: #Vertices " + socialGraph.getVertexCount() + ", #edges: " + socialGraph.getEdgeCount()
		//		 + ", Density: " + GraphUtils.getGraphDensity(socialGraph) + ", MinMax: " + GraphUtils.getMinMaxWeight(socialGraph));	
	
		//Graph<String, WeightedEdge> visualGraph = GraphUtils.loadGraph(visualGraphFile);
		//System.out.println("Visual Graph: #Vertices " + visualGraph.getVertexCount() + ", #edges: " + visualGraph.getEdgeCount()
		//		 + ", Density: " + GraphUtils.getGraphDensity(visualGraph) + ", MinMax: " + GraphUtils.getMinMaxWeight(visualGraph));
		
//		visualGraph = GraphUtils.filter(visualGraph, 0.33);
//		System.out.println("Visual Graph: #Vertices " + visualGraph.getVertexCount() + ", #edges: " + visualGraph.getEdgeCount()
//				 + ", Density: " + GraphUtils.getGraphDensity(visualGraph) + ", MinMax: " + GraphUtils.getMinMaxWeight(visualGraph));
//		GraphUtils.saveGraph(visualGraph, "/disk1_data/Datasets/" + dataset + "/graphs/visual_items_graph_pruned.graphml");
		
		//textualGraph = GraphUtils.createUnifiedGraph(textualGraph, visualGraph, socialGraph);
		//GraphUtils.saveGraph(textualGraph, unifiedGraphFile);
		//Graph<String, WeightedEdge> unifiedGraph = textualGraph;
		//Graph<String, WeightedEdge> unifiedGraph = GraphUtils.loadGraph(unifiedGraphFile);
		//System.out.println("Unified Graph: #Vertices " + unifiedGraph.getVertexCount() + ", #edges: " + unifiedGraph.getEdgeCount()
		//		 + ", Density: " + GraphUtils.getGraphDensity(unifiedGraph) + ", MinMax: " + GraphUtils.getMinMaxWeight(unifiedGraph));
		
		//unifiedGraph = GraphUtils.discardNodes(unifiedGraph, idsToRemove);
		//GraphUtils.fold(unifiedGraph, cliques);
		//System.out.println("Visual Graph: #Vertices " + unifiedGraph.getVertexCount() + ", #edges: " + unifiedGraph.getEdgeCount()
		//		 + ", Density: " + GraphUtils.getGraphDensity(unifiedGraph) + ", MinMax: " + GraphUtils.getMinMaxWeight(unifiedGraph));
		//GraphUtils.saveGraph(unifiedGraph, foldedUnifiedGraphFile);
		
		
		//MorphiaDAO<Cluster> dao = new MorphiaDAO<Cluster>("160.40.50.207", dataset, Cluster.class);
		//clustering(unifiedGraph, dao);
		
		/*
		Query<Cluster> query = dao.getQuery();
		query.filter("size >", 1);
		List<Cluster> clusters = dao.get(query);
		System.out.println(clusters.size() + " clusters");
		
		visualGraph = GraphUtils.filter(visualGraph, 0.6);
		GraphClusterer.scanEpsilon = 0.6;
		GraphClusterer.scanMu = 2;
		List<Set<String>> cliques = GraphClusterer.cluster(visualGraph, false);
		for(Set<String> clique : cliques) {
			int k = 0;
			List<Cluster> temp = new ArrayList<Cluster>();
			for(String m : clique) {
				for(Cluster cluster : clusters) {
					if(!temp.contains(cluster) && cluster.hasMember(m)) {
						k++;
						temp.add(cluster);
					}
				}
			}
			if(k > 1) {
				System.out.println(k + " sub-topics for clique " + cliques.indexOf(clique) 
						+ " of size " + clique.size());
			}
		}
		*/
		
	}
	
	public static Map<String, ClusterVector> getClusterVectors(Map<String, Cluster> clustersMap, 
			Map<String, Vector> vectorsMap, Map<String, Item> itemsMap) {
		Map<String, ClusterVector> CVs = new HashMap<String, ClusterVector>();
		for(Entry<String, Cluster> clusterEntry : clustersMap.entrySet()) {
			ClusterVector cv = new ClusterVector();
			String cId = clusterEntry.getKey();
			Cluster cluster = clusterEntry.getValue();
			for(String clusterMember : cluster.getMembers()) {
				Vector vector = vectorsMap.get(clusterMember);
				Item item = itemsMap.get(clusterMember);
				if(item!= null && vector != null) {
					cv.addVector(item.getId(), vector, item.getPublicationTime());
				}
			}
			if(cv.getNumOfVectors() > 0) {
				cv.updateLength();
				CVs.put(cId, cv);
			}
		}
		return CVs;
	}
	
	public static Map<String, ClusterVector> getClusterVectors(List<Set<String>> clusters, 
			Map<String, Vector> vectorsMap, Map<String, Item> itemsMap) {
		Map<String, ClusterVector> CVs = new HashMap<String, ClusterVector>();
		for(Set<String> cluster : clusters) {
			ClusterVector cv = new ClusterVector();
			Integer cId = clusters.indexOf(cluster);
			for(String clusterMember : cluster) {
				Vector vector = vectorsMap.get(clusterMember);
				Item item = itemsMap.get(clusterMember);
				if(item!= null && vector != null) {
					cv.addVector(item.getId(), vector, item.getPublicationTime());
				}
			}
			if(cv.getNumOfVectors() > 0) {
				cv.updateLength();
				CVs.put(cId.toString(), cv);
			}
		}
		return CVs;
	}
	
	public static void writeResults(List<Entry<String, Double>> sortedRanks, String file) throws IOException {
		List<String> lines = new ArrayList<String>();
		for(Entry<String, Double> entry : sortedRanks) {
			String line =  entry.getKey() + "\t" + entry.getValue();
			lines.add(line);
		}
		Writer writer = new FileWriter(file);
		IOUtils.writeLines(lines, "\n", writer);
		writer.close();
	}
	
	public static void clustering(Graph<String, WeightedEdge> unifiedGraph, MorphiaDAO<Cluster> dao) throws Exception {
		List<Cluster> clusters = GraphClusterer.clusterGraph(unifiedGraph, true);
		System.out.println(clusters.size() + " clusters");

		for(Cluster cluster : clusters) {	
			dao.save(cluster);
		}
	}
	
	public static List<Cluster> createClusters(List<Set<String>> clusters, 
			Graph<String, WeightedEdge> graph) {
		
		List<Cluster> clusterObjects = new ArrayList<Cluster>();
		List<String> clustered = new ArrayList<String>();
		
		int i = 0;
		for(Set<String> cMembers : clusters) {
			i++;
			if(i%100==0) {
				System.out.println(i);
			}
			else {
				System.out.print(".");
			}
			
			clustered.addAll(cMembers);
			Integer index = clusters.indexOf(cMembers);
			Cluster cluster = new Cluster(index.toString());
			cluster.setMembers(cMembers);
			
			if(cMembers.size() > 1) {
				Graph<String, WeightedEdge> clusterGraph = GraphUtils.filter(graph, cMembers);
				double avgWeight = GraphUtils.getAvgWeight(clusterGraph);
				double density = GraphUtils.getGraphDensity(clusterGraph);
				Pair<Double, Double> minMax = GraphUtils.getMinMaxWeight(clusterGraph);
				cluster.setAvgWeight(avgWeight);
				cluster.setMinWeight(minMax.left);
				cluster.setMaxWeight(minMax.right);
				cluster.setDensity(density);
				cluster.setType("community");
			}
			else {
				cluster.setType("outlier");
			}
			clusterObjects.add(cluster);
		}
		return clusterObjects;
	}
	
	public static Set<String> getNoiseItem(MorphiaDAO<MediaItem> dao) throws Exception {
		Set<String> ids = new HashSet<String>();
		Query<MediaItem> query = dao.getQuery();
		query.or(
				//query.criteria("numOfJudgements").lessThan(1),
				query.criteria("width").lessThan(250),
				query.criteria("height").lessThan(250),
				query.criteria("concept").in(Arrays.asList(new String[] {"memes", "porn", "keepcalm", "messages"}))
				);
		
		List<MediaItem> mItems = dao.get(query);
		for(MediaItem mItem : mItems) {	
			List<String> refs = mItem.getReferences();
			if(refs != null && !refs.isEmpty()) {
				ids.addAll(refs);
			}
		}
		
		return ids;
	}
	
}
