package gr.iti.mklab.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.collections15.Predicate;
import org.apache.commons.collections15.Transformer;
import org.apache.commons.lang.StringUtils;

import edu.uci.ics.jung.algorithms.filters.EdgePredicateFilter;
import edu.uci.ics.jung.algorithms.filters.Filter;
import edu.uci.ics.jung.algorithms.filters.VertexPredicateFilter;
import edu.uci.ics.jung.graph.DirectedGraph;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import edu.uci.ics.jung.graph.util.Pair;
import edu.uci.ics.jung.io.GraphIOException;
import edu.uci.ics.jung.io.GraphMLWriter;
import edu.uci.ics.jung.io.graphml.EdgeMetadata;
import edu.uci.ics.jung.io.graphml.GraphMLReader2;
import edu.uci.ics.jung.io.graphml.GraphMetadata;
import edu.uci.ics.jung.io.graphml.HyperEdgeMetadata;
import edu.uci.ics.jung.io.graphml.NodeMetadata;
import edu.uci.ics.jung.io.graphml.GraphMetadata.EdgeDefault;
import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.index.TextIndex;
import gr.iti.mklab.index.VisualIndex;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.models.WeightedEdge;

public class GraphUtils {

	
	public static  <V> gr.iti.mklab.models.Pair<Double, Double> getMinMaxWeight(Graph<V, WeightedEdge> graph) {
		Double min = Double.MAX_VALUE, max = .0;
		for(WeightedEdge e : graph.getEdges()) {
			Double weight = e.getWeight();
			if(weight > max) {
				max = weight;
			}
			if(weight < min) {
				min = weight;
			}
		}
		return gr.iti.mklab.models.Pair.of(min, max);
	}
	
	public static  <V> Double getAvgWeight(Graph<V, WeightedEdge> graph) {
		Double avg = .0;
		if(graph.getEdgeCount() > 0) {
			for(WeightedEdge e : graph.getEdges()) {
				Double weight = e.getWeight();
				avg += weight;
			}
			avg = avg / graph.getEdgeCount();
		}
		return avg;
	}
	
	public static <V, E> Double getGraphDensity(Graph<V, E> graph) {
		double v = (double) graph.getVertexCount();
		double e = 2. * (double) graph.getEdgeCount();
		
		if(v == 1) {
			return 1.;
		}
		
		return e / (v*(v-1));
	}
	
	public static <V> Map<Double, Double> getWeightDitribution(Graph<V, WeightedEdge> graph) {
		DecimalFormat df = new DecimalFormat("#.#");      
		Map<Double, Double> map = new TreeMap<Double, Double>();
		for(WeightedEdge e : graph.getEdges()) {
			Double weight = e.getWeight();
			weight = Double.valueOf(df.format(weight));
			
			Double freq = map.get(weight);
			if(freq == null) {
				freq = .0;
			}
			map.put(weight, ++freq);
		}
		Double sum = .0;
		for(Double freq : map.values()) {
			sum += freq;
		}
		for(Double bin : map.keySet()) {
			Double freq = map.get(bin);
			if(freq == null) {
				freq = .0;
			}
			else {
				freq = freq / sum;
			}
			map.put(bin, freq);
		}
		
		return map;
	}
	
	/*
	 * Get a directed graph of items from an undirected graph. 
	 * For each undirected edges two directed edges are added to the new graph.
	 */
	public static <V> DirectedGraph<V, WeightedEdge> toDirected(Graph<V, WeightedEdge> graph) {	
		DirectedGraph<V, WeightedEdge> directedGraph = new DirectedSparseGraph<V, WeightedEdge>();
	
		// Add all vertices first
		Collection<V> vertices = graph.getVertices();
		for(V vertex : vertices) {
			directedGraph.addVertex(vertex);
		}
		
		// Add directed edges
		for(WeightedEdge edge : graph.getEdges()) {	
			Pair<V> endpoints = graph.getEndpoints(edge);
			directedGraph.addEdge(new WeightedEdge(edge.getWeight()), endpoints.getFirst(), endpoints.getSecond());
			directedGraph.addEdge(new WeightedEdge(edge.getWeight()), endpoints.getSecond(), endpoints.getFirst());
		}
		return directedGraph;
	}

	/*
	 * Get a directed graph of items from an undirected graph based on a time constraint on the corresponding items. 
	 */
	public static <V> DirectedGraph<V, WeightedEdge> toDirected(Graph<V, WeightedEdge> graph, Map<String, Item> itemsMap) {	
		DirectedGraph<V, WeightedEdge> directedGraph = new DirectedSparseGraph<V, WeightedEdge>();
	
		// Add all vertices first
		Collection<V> vertices = graph.getVertices();
		for(V vertex : vertices) {
			directedGraph.addVertex(vertex);
		}
		
		// Add directed edges
		for(WeightedEdge edge : graph.getEdges()) {	
			Pair<V> endpoints = graph.getEndpoints(edge);
			
			V firstId = endpoints.getFirst();
			V secondId = endpoints.getSecond();
			
			Item firstItem = itemsMap.get(firstId);
			Item secondItem = itemsMap.get(secondId);
			if(firstItem == null || secondItem == null) {
				continue;
			}
			
			long dt = Math.abs(firstItem.getPublicationTime() - secondItem.getPublicationTime());
			Double timeProximity = 1.; //Kernel.gaussian(dt, 72*3600*1000);
			
			if(firstItem.getPublicationTime() > secondItem.getPublicationTime()) {
				directedGraph.addEdge(new WeightedEdge(timeProximity.floatValue()*edge.getWeight()), firstId, secondId);
			}
			else if(firstItem.getPublicationTime() < secondItem.getPublicationTime()) {
				directedGraph.addEdge(new WeightedEdge(timeProximity.floatValue()*edge.getWeight()), secondId, firstId);
			}
			else {
				// If items have the same publication date then do not add any edge
				directedGraph.addEdge(new WeightedEdge(timeProximity.floatValue()*edge.getWeight()), secondId, firstId);
				directedGraph.addEdge(new WeightedEdge(timeProximity.floatValue()*edge.getWeight()), firstId, secondId);
				
				// TODO: Check whether adding a bidirectional edge gives better results
			}
		}
		
		return directedGraph;
	}
	
	/*
	 * Normalize tha weights of edges in an undirected graph.
	 * The sum of weights of out-edges of a vertex has to be equal to 1.  
	 */
	public static <V> Graph<V, WeightedEdge> normalize(Graph<V, WeightedEdge> graph) {
		Graph<V, WeightedEdge> normalizedGraph = new DirectedSparseGraph<V, WeightedEdge>();
		
		Collection<V> vertices = graph.getVertices();
		for(V vertex : vertices) {
			normalizedGraph.addVertex(vertex);
		}
		
		for(V vertex : vertices) {		
			Collection<V> successors = graph.getSuccessors(vertex);
			
			double totalWeight = 0;	
			for(V successor : successors) {
				WeightedEdge edge = graph.findEdge(vertex, successor);
				if(edge != null) {
					totalWeight += edge.getWeight();
				}
			}
			
			if(totalWeight == 0)
				continue;
	
			for(V successor : successors) {
				WeightedEdge edge = graph.findEdge(vertex, successor);
				if(edge == null)
					continue;
				
				Double normalizedWeight = edge.getWeight() / totalWeight;
				
				WeightedEdge normalizedEdge = new WeightedEdge(normalizedWeight);
				normalizedGraph.addEdge(normalizedEdge, vertex, successor);
			}
		}
		
		return normalizedGraph;
	}
	
	public static  <V, E> Graph<V, E> clone(Graph<V, E> graph) {
		graph = filter(graph, graph.getVertices());
		return graph;
	}
	
	/*
	 * Filter graph vertices based on a set of desired vertices
	 */
	public static  <V, E> Graph<V, E> filter(Graph<V, E> graph, final Collection<V> vertices) {	
		Predicate<V> predicate = new Predicate<V>() {
			@Override
			public boolean evaluate(V vertex) {
				if(vertices.contains(vertex))
					return true;
			
				return false;
			}
		};
	
		//Filter graph
		Filter<V, E> verticesFilter = new VertexPredicateFilter<V, E>(predicate);
		graph = verticesFilter.transform(graph);

		return graph;
	}
	
	/*
	 * Filter graph vertices based on a set of desired vertices
	 */
	public static  <V, E> Graph<V, E> discardNodes(Graph<V, E> graph, final Collection<V> vertices) {	
		Predicate<V> predicate = new Predicate<V>() {
			@Override
			public boolean evaluate(V vertex) {
				if(vertices.contains(vertex)) {
					return false;
				}
				return true;
			}
		};
	
		//Filter graph
		Filter<V, E> verticesFilter = new VertexPredicateFilter<V, E>(predicate);
		graph = verticesFilter.transform(graph);

		return graph;
	}
	
	/*
	 * Filter graph vertices based on their degree 
	 */
	public static  <V> Graph<V, WeightedEdge> filter(final Graph<V, WeightedEdge> graph, final int degree) {
		Predicate<V> predicate = new Predicate<V>() {
			@Override
			public boolean evaluate(V vertex) {
				Collection<WeightedEdge> incidentEdges = graph.getIncidentEdges(vertex);
				if(incidentEdges.size() > degree) {
					return true;
				}
				return false;
			}
		};
	
		//Filter graph
		Filter<V, WeightedEdge> verticesFilter = new VertexPredicateFilter<V, WeightedEdge>(predicate);
		return verticesFilter.transform(graph);
		
	}
	
	/*
	 * Filter graph edges based on their weights 
	 */
	public static  <V> Graph<V, WeightedEdge> filter(Graph<V, WeightedEdge> graph, final double weightThreshold) {
		
		Predicate<WeightedEdge> edgePredicate = new Predicate<WeightedEdge>() {
			@Override
			public boolean evaluate(WeightedEdge edge) {
				if(edge.getWeight() > weightThreshold)
					return true;
			
				return false;
			}
		};
	
		//Filter graph
		Filter<V, WeightedEdge> edgeFiler = new EdgePredicateFilter<V, WeightedEdge>(edgePredicate);
		graph = edgeFiler.transform(graph);

		return graph;
	}
	
	/**
	 * Save graph in a .graphml file.
	 * 
	 * @param grap : the graph  to be saved
	 * @param filename : the name of the file
	 * @throws IOException
	 */
	public static void saveGraph(Graph<String, WeightedEdge> graph, String filename) throws IOException {
		
		File file =new File(filename);
		File dir = file.getParentFile();
		if(!dir.exists()) {
			dir.mkdirs();
		}
		
		GraphMLWriter<String, WeightedEdge> graphWriter = new GraphMLWriter<String, WeightedEdge> ();
		PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(file)));
		
		graphWriter.addEdgeData("weight", null, "0", new Transformer<WeightedEdge, String>() {
				@Override
				public String transform(WeightedEdge e) {
					return Double.toString(e.getWeight());
				}
			}	
		);
		graphWriter.save(graph, out);
		
	}
	

	/**
	 * Load a graph from a .graphml file
	 * @param filename : the name of the file that the graph is stored
	 * @return graph
	 * @throws IOException
	 */
	public static Graph<String, WeightedEdge> loadGraph(String filename) throws IOException {
		
		BufferedReader fileReader = new BufferedReader(new FileReader(filename));	
		
		Transformer<GraphMetadata, Graph<String, WeightedEdge>> graphTransformer = new Transformer<GraphMetadata, Graph<String, WeightedEdge>>() {
			public Graph<String, WeightedEdge> transform(GraphMetadata metadata) {
				if (metadata.getEdgeDefault().equals(EdgeDefault.DIRECTED)) {
					return new DirectedSparseGraph<String, WeightedEdge>();
				} else {
					return new UndirectedSparseGraph<String, WeightedEdge>();
				}
			}
		};
		
		Transformer<NodeMetadata, String> vertexTransformer = new Transformer<NodeMetadata, String>() {
			public String transform(NodeMetadata metadata) {
				String vertex = metadata.getId();
				return vertex;
			}
		};
			
		Transformer<EdgeMetadata, WeightedEdge> edgeTransformer = new Transformer<EdgeMetadata, WeightedEdge>() {
			int e = 0;
			public WeightedEdge transform(EdgeMetadata metadata) {
				if(++e%500000 == 0) {
					System.out.print(".");
				}
				
				Double weight = Double.parseDouble(metadata.getProperty("weight"));
				WeightedEdge edge = new WeightedEdge(weight);
				return edge;
			}
		};
		
		Transformer<HyperEdgeMetadata, WeightedEdge> hyperEdgeTransformer = new Transformer<HyperEdgeMetadata, WeightedEdge>() {
			public WeightedEdge transform(HyperEdgeMetadata metadata) {
				Double weight = Double.parseDouble(metadata.getProperty("weight"));
				
				WeightedEdge edge = new WeightedEdge(weight);
				return edge;
			}
		};
					 
		GraphMLReader2<Graph<String, WeightedEdge>, String, WeightedEdge> graphReader 
			= new GraphMLReader2<Graph<String, WeightedEdge>, String, WeightedEdge>(
				fileReader, graphTransformer, vertexTransformer, edgeTransformer, hyperEdgeTransformer);

		try {
			/* 
			 * Get the new graph object from the GraphML file 
			 * */
			Graph<String, WeightedEdge> graph = graphReader.readGraph();
			System.out.println(".");
			return graph;
		} catch (GraphIOException ex) {
			return null;
		}
	}

	/**
	 * Generate a graph of items, where connections between nodes represent textual similarity of the corresponding items. 
	 * @param vectorsMap : a map of <itemId, vector> pairs
	 * @param similarityThreshold : if textual similarity between two nodes exceeds this values add an edges between the nodes
	 * @return
	 */
	public static Graph<String, WeightedEdge> generateTextualItemsGraph(Map<String, Vector> vectorsMap, double similarityThreshold) {

		Graph<String, WeightedEdge> graph = new UndirectedSparseGraph<String, WeightedEdge>();
		for(String node : vectorsMap.keySet()) {
			graph.addVertex(node);
		}
		
		String[] ids = vectorsMap.keySet().toArray(new String[vectorsMap.size()]);
		for(int i=0; i<ids.length-1; i++) {
			
			if(i%((int)(0.05*vectorsMap.size()))==0) {
				System.out.println(i + " / " + ids.length + " vectors processed => " + 
					graph.getVertexCount() + " vertices, " + graph.getEdgeCount() + " edges.");
			}
			
			String id1 = ids[i];
			Vector vector1 = vectorsMap.get(id1);
			if(vector1 == null)
				continue;
			
			for(int j=i+1; j<ids.length; j++) {
				
				String id2 = ids[j];
				Vector vector2 = vectorsMap.get(id2);
				if(vector2 == null) {
					continue;
				}
				
				Double similarity = vector1.cosine(vector2);
				
				if(similarity > similarityThreshold) {
					WeightedEdge link = new WeightedEdge(similarity);
					graph.addEdge(link , id1, id2);
				}
			}
		}		
		return graph;
	}
	
	/**
	 * Generate an approximate graph of items, where connections between nodes represent textual similarity of the corresponding items. 
	 * Approximation introduced as the method does not calculate textual similarity between all the pairs of items, 
	 * but for each item gets a candidate set of nearest items and calculate similarity only to the items of that set. 
	 * 
	 * @param itemsMap : a map of <itemId, item> pairs
	 * @param vectorsMap : a map of <itemId, vector> pairs
	 * @param similarityThreshold : if textual similarity between two nodes exceeds this values add an edges between the nodes
	 * @param tIndex : instance of Lucene Text Index
	 * @return
	 */
	public static Graph<String, WeightedEdge> generateTextualItemsGraph(Map<String, Item> itemsMap, 
			Map<String, Vector> vectorsMap, double similarityThreshold, TextIndex tIndex) {

		Graph<String, WeightedEdge> graph = new UndirectedSparseGraph<String, WeightedEdge>();
		for(String node : itemsMap.keySet()) {
			if(vectorsMap.containsKey(node)) {
				graph.addVertex(node);
			}
		}
		
		String[] ids = itemsMap.keySet().toArray(new String[itemsMap.size()]);
		int n = (int) (0.01 * ids.length);
		for(int i=0; i<ids.length-1; i++) {
			
			if(i%((int)(0.05*itemsMap.size()))==0) {
				System.out.println(i + " / " + ids.length + " vectors processed  => " +
						graph.getVertexCount() + " vertices " + graph.getEdgeCount() + ", edges, density: " +
						(2.*graph.getEdgeCount()) / ((double)graph.getVertexCount()*((double)graph.getVertexCount()-1)));
			}
			
			String id1 = ids[i];
			
			Item item1 = itemsMap.get(id1);
			Vector vector1 = vectorsMap.get(id1);
			if(vector1 == null) {
				continue;
			}		
			
			try {
				// Experimental setting. Does not work properly.
				//String query = StringUtils.join(vector1.getTerms(), " ");
				//Map<String, Double> similarItems = tIndex.search(query);
				
				Map<String, Double> similarItems = tIndex.searchIndex(item1, n);
				for(String id2 : similarItems.keySet()) {
					if(id1.equals(id2)) {
						continue;
					}
					
					Vector vector2 = vectorsMap.get(id2);
					if(vector2 == null) {
						continue;
					}
					
					Double similarity = vector1.cosine(vector2);
					if(similarity > similarityThreshold) {
						WeightedEdge link = new WeightedEdge(similarity);
						graph.addEdge(link , id1, id2);
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}		
		return graph;
	}
	
	public static DirectedGraph<String, WeightedEdge> generateSocialGraph(Map<String, Item> itemsMap) {

		DirectedGraph<String, WeightedEdge> graph = new DirectedSparseGraph<String, WeightedEdge>();

		for(Item item : itemsMap.values()) {
			String itemId = item.getId();
			String inReplyId = item.getInReplyId();
			
			if(inReplyId != null && !graph.containsVertex(itemId)){
				graph.addVertex(itemId);
			}
			
			if(!itemsMap.containsKey(inReplyId)) {
				continue;
			}
			
			if(!graph.containsVertex(inReplyId)) {
				graph.addVertex(inReplyId);
			}
			
			WeightedEdge link = new WeightedEdge(1d);
			graph.addEdge(link , itemId, inReplyId);
		}		
		
		return graph;
	}
	
	/**
	 * Generate a graph of items that connections between them represent the visual similarity between the corresponding embedded media
	 * 
	 * 	@param mediaItems: a map of pairs <itemId, mediaId>
	 *  @param similarityThreshold: if visual similarity between two nodes exceeds this values add an edges between the nodes
	 *  @param vIndex: the visual index that holds the VLAD+SURF vectors of each media item 
	 *  
	 */
	public static Graph<String, WeightedEdge> generateVisualGraph(Map<String, String> mediaItems, double similarityThreshold, VisualIndex vIndex) {

		Graph<String, WeightedEdge> graph = new UndirectedSparseGraph<String, WeightedEdge>();
		for(Entry<String, String> entry : mediaItems.entrySet()) {
			if(vIndex.isIndexed(entry.getValue())) {
				graph.addVertex(entry.getKey());
			}
		}
		
		String[] itemIds = mediaItems.keySet().toArray(new String[mediaItems.size()]);
		for(int i=0; i<itemIds.length-1; i++) {
			
			if(i%((int)(0.1*mediaItems.size()))==0) {
				System.out.println(i + " / " + itemIds.length + " items processed => " +
						graph.getVertexCount() + " vertices " + graph.getEdgeCount() + " edges!");
			}
			
			String id1 = itemIds[i];
			String imgId1 = mediaItems.get(id1);
			double[] vector1 = vIndex.getVector(imgId1);
			
			if(vector1 == null) {
				continue;
			}
			
			for(int j=i+1; j<itemIds.length; j++) {
				String id2 = itemIds[j];
				String imgId2 = mediaItems.get(id2);
				
				double[] vector2 = vIndex.getVector(imgId2);
				if(vector2 == null)
					continue;
				
				Double similarity = L2.similarity(vector1, vector2);
				if(similarity > similarityThreshold) {
					WeightedEdge link = new WeightedEdge(similarity);
					graph.addEdge(link , id1, id2);
				}
			}
		}		
		return graph;
	}
	
	/**
	 * Generate a graph of items that connections between them represent the visual similarity between the corresponding embedded media
	 * 
	 * 	@param mediaItems: a map of pairs <mediaId, itemId[]>. For each media id the map keeps the list of item ids that contain that media item 
	 *  @param similarityThreshold: if visual similarity between two nodes exceeds this values add an edges between the nodes
	 *  @param vIndex: the visual index that holds the VLAD+SURF vectors of each media item 
	 */
	public static Graph<String, WeightedEdge> generateVisualItemGraph(Map<String, List<String>> mediaItems, double similarityThreshold, VisualIndex vIndex) {

		Graph<String, WeightedEdge> graph = new UndirectedSparseGraph<String, WeightedEdge>();
		for(Entry<String, List<String>> entry : mediaItems.entrySet()) {
			String mediaId = entry.getKey(); 
			if(vIndex.isIndexed(mediaId)) {
				for(String itemId :  entry.getValue()) {
					graph.addVertex(itemId);
				}
			}
		}
		
		String[] mediaIds = mediaItems.keySet().toArray(new String[mediaItems.size()]);
		for(int i=0; i<mediaIds.length-1; i++) {
			
			if(i%((int)(0.1*mediaItems.size()))==0) {
				System.out.println(i + " / " + mediaIds.length + " media items processed => " +
						graph.getVertexCount() + " vertices " + graph.getEdgeCount() + " edges!");
			}
			
			String mediaId1 = mediaIds[i];
			List<String> itemIds1 = mediaItems.get(mediaId1);
			double[] vector1 = vIndex.getVector(mediaId1);
			if(vector1 == null) {
				continue;
			}
			
			for(int j=i+1; j<mediaIds.length; j++) {
			
				String mediaId2 = mediaIds[j];
				List<String> itemIds2 = mediaItems.get(mediaId2);
				double[] vector2 = vIndex.getVector(mediaId2);
				if(vector2 == null) {
					continue;
				}
				
				Double similarity = L2.similarity(vector1, vector2);
				if(similarity > similarityThreshold) {
					for(String itemId1 : itemIds1) {
						for(String itemId2 : itemIds2) {
							if(itemId1.equals(itemId2)) {
								continue;
							}
							
							WeightedEdge link = new WeightedEdge(similarity);
							graph.addEdge(link , itemId1, itemId2);
						}
					}
					
				}
			}
		}		
		return graph;
	}
	
	/**
	 * Generate a graph of media items
	 * Nodes: 	media ids
	 * Edges:	visual similarity between media items
	 * 
	 * 	@param mediaItems: a collection of media ids
	 * 	@param similarityThreshold: if visual similarity between two nodes exceeds this values add an edges between the nodes
	 *  @param vIndex: the visual index that holds the VLAD+SURF vectors of each media item 
	 */
	public static Graph<String, WeightedEdge> generateVisualGraph(Collection<String> mediaItems, double similarityThreshold, VisualIndex index) {

		Graph<String, WeightedEdge> graph = new UndirectedSparseGraph<String, WeightedEdge>();
		for(String mediaId : mediaItems) {
			if(index.isIndexed(mediaId)) {
				graph.addVertex(mediaId);
				
			}
		}
		
		String[] mediaIds = mediaItems.toArray(new String[mediaItems.size()]);
		for(int i=0; i<mediaIds.length-1; i++) {
			
			if(i%((int)(0.1*mediaItems.size()))==0) {
				System.out.println(i + " / " + mediaIds.length + " media items processed => " +
						graph.getVertexCount() + " vertices " + graph.getEdgeCount() + " edges!");
			}
			
			String mediaId1 = mediaIds[i];
			double[] vector1 = index.getVector(mediaId1);
			if(vector1 == null) {
				continue;
			}
			
			for(int j=i+1; j<mediaIds.length; j++) {
			
				String mediaId2 = mediaIds[j];
				double[] vector2 = index.getVector(mediaId2);
				if(vector2 == null) {
					continue;
				}
				
				Double similarity = L2.similarity(vector1, vector2);
				if(similarity > similarityThreshold) {		
					WeightedEdge link = new WeightedEdge(similarity);
					graph.addEdge(link , mediaId1, mediaId2);
				}
			}
		}		
		return graph;
	}
	
	/**
	 * Folds sub-graphs of the graph into single nodes based on a clustering of nodes.
	 * Does not return a new graph but changes the initial graph passed as a parameter
	 * 
	 * @param graph: a graph of items
	 * @param clusters: a collections of clustered items 
	 */
	public static void fold(Graph<String, WeightedEdge> graph, Collection<Collection<String>> clusters) {
		
		int clustered = 0, removedEdges=0;
		for(Collection<String> cluster : clusters) {
			
			List<String> list = new ArrayList<String>(cluster);
			Collections.sort(list);
			String newVertex = StringUtils.join(list, "-");
			
			//System.out.println("Cluster " + clusters.indexOf(cluster) + " size " + cluster.size());
			clustered += cluster.size();
			
			for(String v1 : cluster) {
				for(String v2 : cluster) {
					WeightedEdge edge = graph.findEdge(v1, v2);
					if(edge != null) {
						removedEdges++;
						graph.removeEdge(edge);
					}
				}
			}
			//System.out.println("Between edges to remove:  " + edgesToRemove);

			Map<String, Set<WeightedEdge>> map = new HashMap<String, Set<WeightedEdge>>();
			
			for(String vertex : cluster) {
				Collection<String> neighbors = new ArrayList<String>(graph.getNeighbors(vertex));
				//System.out.println(vertex + " => " + neighbors.size());
				for(String neighbor : neighbors) {
					WeightedEdge edge = graph.findEdge(vertex, neighbor);
					if(edge != null) {
						removedEdges++;
						graph.removeEdge(edge);
						Set<WeightedEdge> edges = map.get(neighbor);
						if(edges == null) {
							edges = new HashSet<WeightedEdge>();
							map.put(neighbor, edges);
						}
						edges.add(edge);
					}
				}
				graph.removeVertex(vertex);
			}
			
			
			graph.addVertex(newVertex);
			
			for(String neighbor : map.keySet()) {
				Set<WeightedEdge> edges = map.get(neighbor);
				WeightedEdge maxEdge = Collections.max(edges);
				graph.addEdge(maxEdge, neighbor, newVertex);
			}
		}
		
		System.out.println("Clustered Vertices: " + clustered + ", Removed Edges: " + removedEdges);
	}
	
	public static Graph<String, WeightedEdge> createUnifiedGraph(Graph<String, WeightedEdge> textualGraph, 
			Graph<String, WeightedEdge> visualGraph, Graph<String, WeightedEdge> socialGraph) {
				
		Graph<String, WeightedEdge> graph = GraphUtils.clone(textualGraph);	
		
		// Integrate visual graph
		for(String vertex : visualGraph.getVertices()) {
			if(!graph.containsVertex(vertex)) {
				graph.addVertex(vertex);
			}
		}
		for(WeightedEdge edge : visualGraph.getEdges()) {
			Collection<String> vertices = visualGraph.getIncidentVertices(edge);
			if(vertices.size() == 2) {
				String[] vArray = vertices.toArray(new String[2]);
				if(!graph.isNeighbor(vArray[0], vArray[1])) {	
					graph.addEdge(edge, vArray[0], vArray[1]);
				}
			}
		}
		
		// Integrate social graph
		for(String vertex : socialGraph.getVertices()) {
			if(!graph.containsVertex(vertex)) {
				graph.addVertex(vertex);
			}
		}
		for(WeightedEdge edge : socialGraph.getEdges()) {
			Collection<String> vertices = socialGraph.getIncidentVertices(edge);
			if(vertices.size() == 2) {
				String[] vArray = vertices.toArray(new String[2]);
				if(!graph.isNeighbor(vArray[0], vArray[1])) {	
					graph.addEdge(edge,  vArray[0], vArray[1]);
				}
			}
		}
		return graph;
	}
	
	public static Graph<String, WeightedEdge> createUnifiedGraph(Graph<String, WeightedEdge> textualGraph, double THtext, 
			Graph<String, WeightedEdge> visualGraph, double THvisual, Graph<String, WeightedEdge> socialGraph) {
				
		Graph<String, WeightedEdge> graph = GraphUtils.filter(textualGraph, THtext);	
		
		for(String vertex : visualGraph.getVertices()) {
			if(!graph.containsVertex(vertex)) {
				graph.addVertex(vertex);
			}
		}
		for(WeightedEdge edge : GraphUtils.filter(visualGraph, THvisual).getEdges()) {
			Collection<String> vertices = visualGraph.getIncidentVertices(edge);
			if(vertices.size() == 2) {
				String[] vArray = vertices.toArray(new String[2]);
				if(!graph.isNeighbor(vArray[0], vArray[1])) {	
					graph.addEdge(edge, vArray[0], vArray[1]);
				}
			}
		}
		
		for(String vertex : socialGraph.getVertices()) {
			if(!graph.containsVertex(vertex)) {
				graph.addVertex(vertex);
			}
		}
		for(WeightedEdge edge : socialGraph.getEdges()) {
			Collection<String> vertices = socialGraph.getIncidentVertices(edge);
			if(vertices.size() == 2) {
				String[] vArray = vertices.toArray(new String[2]);
				if(!graph.isNeighbor(vArray[0], vArray[1])) {	
					graph.addEdge(edge,  vArray[0], vArray[1]);
				}
			}
		}
		return graph;
	}
	
	private static String dataset = "Sundance2013";
	
	public static void main(String...args) throws Exception {
		
		MorphiaDAO<Item> dao = new MorphiaDAO<Item>("160.40.50.207" , dataset, Item.class);
		System.out.println(dao.count() + " items");
		
		Map<String, List<String>> media = new HashMap<String, List<String>>();
		Iterator<Item> it = dao.iterator();
		while(it.hasNext()) {
			Item item = it.next();
			String itemId = item.getId();		
			Map<String, String> mediaItems = item.getMediaItems();
			if(mediaItems != null) {
				for(String mediaId : mediaItems.keySet()) {
					List<String> itemIds = media.get(mediaId);
					if(itemIds == null) {
						itemIds = new ArrayList<String>();
						media.put(mediaId, itemIds);
					}
					itemIds.add(itemId);
				}
			}
		}
		System.out.println(media.size() + " media items");
		
		//Graph<String, WeightedEdge> graph = GraphUtils.generateApproximateTFIDFGraph(itemMap, vectorsMap, 0.33, indexer);
		//GraphUtils.saveGraph(graph, "/disk1_data/Datasets/WWDC14/graphs/textual_graph.graphml");
		
		VisualIndex index = new VisualIndex("/disk2_data/VisualIndex/learning_files", "/disk1_data/Datasets/" + dataset);
		Graph<String, WeightedEdge> vGraph = GraphUtils.generateVisualGraph(media.keySet(), 0.2, index);
		
		GraphUtils.saveGraph(vGraph, "/disk1_data/Datasets/" + dataset + "/graphs/visual_graph.graphml");
		
	}
	
	{
		//TextIndex indexer = new TextIndex("/disk1_data/Datasets/" + dataset + "/TextIndex");
		//indexer.open();
		//Map<String, Item> itemMap = new HashMap<String, Item>();
		//Map<String, TFIDFVector> vectorsMap = new HashMap<String, TFIDFVector>();
//		while(it.hasNext()) {
//			Item item = it.next();
//			String itemId = item.getId();
//			
			//TFIDFVector vector = indexer.getTFIDF(itemId);
			//vectorsMap.put(itemId, vector);
			//itemMap.put(itemId, item);
			
//			Map<String, String> mediaItems = item.getMediaItems();
//			if(mediaItems != null) {
//				for(String mediaId : mediaItems.keySet()) {
//					List<String> itemIds = media.get(mediaId);
//					if(itemIds == null) {
//						itemIds = new ArrayList<String>();
//						media.put(mediaId, itemIds);
//					}
//					itemIds.add(itemId);
//				}
//			}
//		}
		
	}
}
