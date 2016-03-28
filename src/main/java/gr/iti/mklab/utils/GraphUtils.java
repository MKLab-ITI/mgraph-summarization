package gr.iti.mklab.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections15.Predicate;
import org.apache.commons.collections15.Transformer;
import org.mongodb.morphia.query.Query;

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
import gr.iti.mklab.Config;
import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.index.LuceneTextIndex;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.NamedEntity;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.models.WeightedEdge;
import gr.iti.mklab.vocabulary.Vocabulary;

public class GraphUtils {

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
			
			if(firstItem.getPublicationTime() > secondItem.getPublicationTime()) {
				directedGraph.addEdge(new WeightedEdge(edge.getWeight()), firstId, secondId);
			}
			else if(firstItem.getPublicationTime() < secondItem.getPublicationTime()) {
				directedGraph.addEdge(new WeightedEdge(edge.getWeight()), secondId, firstId);
			}
			else {
				// If items have the same publication date then do not add any edge
				// TODO: Check whether adding a bidirectional edge gives better results
			}
		}
		
		return directedGraph;
	}
	
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
	
	public static  <V> Graph<V, WeightedEdge> filter(Graph<V, WeightedEdge> graph, final double weight) {
		
		Predicate<WeightedEdge> edgePredicate = new Predicate<WeightedEdge>() {
			@Override
			public boolean evaluate(WeightedEdge edge) {
				if(edge.getWeight() > weight)
					return true;
			
				return false;
			}
		};
	
		//Filter graph
		Filter<V, WeightedEdge> edgeFiler = new EdgePredicateFilter<V, WeightedEdge>(edgePredicate);
		graph = edgeFiler.transform(graph);

		return graph;
	}
	
	public static void saveGraph(Graph<String, WeightedEdge> graph, String filename) throws IOException {
		GraphMLWriter<String, WeightedEdge> graphWriter = new GraphMLWriter<String, WeightedEdge> ();
		PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(filename)));
		
		graphWriter.addEdgeData("weight", null, "0", new Transformer<WeightedEdge, String>() {
				@Override
				public String transform(WeightedEdge e) {
					return Double.toString(e.getWeight());
				}
			}	
		);
		graphWriter.save(graph, out);
	}
	
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
			public WeightedEdge transform(EdgeMetadata metadata) {
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
					 
		GraphMLReader2<Graph<String, WeightedEdge>, String, WeightedEdge> graphReader = new GraphMLReader2<Graph<String, WeightedEdge>, String, WeightedEdge>(
				fileReader, graphTransformer, vertexTransformer, edgeTransformer, hyperEdgeTransformer);
			
		try {
			/* Get the new graph object from the GraphML file */
			Graph<String, WeightedEdge> graph = graphReader.readGraph();
			return graph;
		} catch (GraphIOException ex) {
			return null;
		}
	}

	public static Graph<String, WeightedEdge> generateGraph(Map<String, Vector> vectorsMap, double similarityThreshold) {

		Graph<String, WeightedEdge> graph = new UndirectedSparseGraph<String, WeightedEdge>();
		
		for(String node : vectorsMap.keySet()) {
			graph.addVertex(node);
		}
		
		String[] ids = vectorsMap.keySet().toArray(new String[vectorsMap.size()]);
		Vector[] vectors = vectorsMap.values().toArray(new Vector[vectorsMap.size()]);

		for(int i=0; i<ids.length-1; i++) {
			
			if(i%(0.01*vectorsMap.size())==0) {
				System.out.println(i + " / " + ids.length + " vectors processed!");
			}
			
			String id1 = ids[i];
			Vector vector1 = vectors[i];
			for(int j=i+1; j<ids.length; j++) {
				
				String id2 = ids[j];
				Vector vector2 = vectors[j];
				
				Double similarity = vector1.cosine(vector2);
				if(similarity > similarityThreshold) {
					WeightedEdge link = new WeightedEdge(similarity);
					graph.addEdge(link , id1, id2);
				}
			}
		}		
		return graph;
	}
	
	public static Graph<String, WeightedEdge> generateApproximateGraph(Map<String, Vector> vectorsMap, 
			double similarityThreshold, LuceneTextIndex indexer) {

		Graph<String, WeightedEdge> graph = new UndirectedSparseGraph<String, WeightedEdge>();
		
		for(String node : vectorsMap.keySet()) {
			graph.addVertex(node);
		}
		
		String[] ids = vectorsMap.keySet().toArray(new String[vectorsMap.size()]);
		for(int i=0; i<ids.length-1; i++) {
			
			if(i%((int)(0.01*vectorsMap.size()))==0) {
				System.out.println(i + " / " + ids.length + " vectors processed!");
			}
			
			String id1 = ids[i];
			Vector vector1 = vectorsMap.get(id1);
			if(vector1 == null)
				continue;
			
			String query = StringUtils.join(vector1.getWords(), " ");
			Map<String, Double> similar = indexer.search(query);
			
			for(String id2 : similar.keySet()) {
				
				Vector vector2 = vectorsMap.get(id2);
				if(vector2 == null)
					continue;
				
				Double similarity = vector1.cosine(vector2);
				if(similarity > similarityThreshold) {
					WeightedEdge link = new WeightedEdge(similarity);
					graph.addEdge(link , id1, id2);
				}
				
			}
		}		
		return graph;
	}
	
	public static void main(String...args) throws Exception {
		
		MorphiaDAO<Item> dao = new MorphiaDAO<Item>(Config.hostname , Config.dbname, Item.class);
		Query<Item> query = dao.getQuery().filter("accepted =", Boolean.TRUE);
		System.out.println(dao.count(query) + " items");
		
		LuceneTextIndex indexer = new LuceneTextIndex(Config.luceneIndex);
		indexer.open(false);
		
		Map<String, Item> itemsMap = new HashMap<String, Item>();
		Iterator<Item> it = dao.iterator(query);
		while(it.hasNext()) {
			Item item = it.next();
			itemsMap.put(item.getId(), item);
		}
		
		Set<String> boostedTerms = new HashSet<String>();
		for(Item item : itemsMap.values()) {
			for(NamedEntity ne : item.getNamedEntities()) {
				boostedTerms.add(ne.getName());
			}
			for(String pn : item.getProperNouns()) {
				boostedTerms.add(pn);
			}
			
		}
		
		Map<String, Vector> vectorsMap = Vocabulary.createVocabulary(dao.iterator(query), 2);
		Vocabulary.addBoostedTerms(boostedTerms, 2);
		
		Graph<String, WeightedEdge> graph = GraphUtils.generateApproximateGraph(vectorsMap, 0.1, indexer);
		GraphUtils.saveGraph(graph, Config.graphFile);
		
		
	}
}
