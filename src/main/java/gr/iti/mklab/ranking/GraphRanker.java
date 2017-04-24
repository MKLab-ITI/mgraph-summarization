package gr.iti.mklab.ranking;

import edu.uci.ics.jung.algorithms.matrix.GraphMatrixOperations;
import edu.uci.ics.jung.algorithms.scoring.EigenvectorCentrality;
import edu.uci.ics.jung.algorithms.scoring.HITS.Scores;
import edu.uci.ics.jung.algorithms.scoring.PageRank;
import edu.uci.ics.jung.algorithms.scoring.HITS;
import edu.uci.ics.jung.algorithms.scoring.PageRankWithPriors;
import edu.uci.ics.jung.graph.Graph;
import edu.ucla.sspace.matrix.DivRank;
import edu.ucla.sspace.matrix.SparseHashMatrix;
import edu.ucla.sspace.matrix.SparseMatrix;
import edu.ucla.sspace.vector.DenseVector;
import edu.ucla.sspace.vector.DoubleVector;
import gr.iti.mklab.models.WeightedEdge;
import gr.iti.mklab.utils.GraphUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.commons.collections15.Transformer;

import cern.colt.list.DoubleArrayList;
import cern.colt.list.IntArrayList;
import cern.colt.matrix.impl.SparseDoubleMatrix2D;

public class GraphRanker {
	
	//public static Double d = 0.75; //damping factor in PageRank
	public static Double d = 0.7; //damping factor in PageRank
	
	// Stoping criteria
	private static double tolerance = 0.000001;
	private static int maxIterations = 300;
	
	public static Map<String, Double> pagerankScoring(Graph<String, WeightedEdge>  graph) {
		
		Transformer<WeightedEdge, Double> edgeTransformer = WeightedEdge.getEdgeTransformer();
		PageRank<String, WeightedEdge> ranker = new PageRank<String, WeightedEdge>(graph, edgeTransformer , d);
		
		ranker.setTolerance(tolerance) ;
		ranker.setMaxIterations(maxIterations);
		ranker.evaluate();
	 
		System.out.println("Iterations: " + ranker.getIterations());
		System.out.println("Tolerance: " + ranker.getTolerance());
		
		double maxScore = 0;
		Collection<String> vertices = graph.getVertices();
		Map<String, Double> verticesMap = new TreeMap<String, Double>();
		for(String vertex : vertices) {
			Double score = ranker.getVertexScore(vertex);
			
			if(score > maxScore)
				maxScore = score;
			
			verticesMap.put(vertex, score);
		}
		
		if(maxScore > 0) {
			for(Entry<String, Double> ve : verticesMap.entrySet()) {
				verticesMap.put(ve.getKey(), ve.getValue()/maxScore);
			}
		}
		
		return verticesMap;
	}
	
	public static Map<String, Double> pagerankScoring(Graph<String, WeightedEdge>  graph, final Map<String, Double> priors) {
		
		Transformer<WeightedEdge, Double> edgeTransformer = WeightedEdge.getEdgeTransformer();
		Transformer<String, Double> priorsTransformer = new Transformer<String, Double>() {
			@Override
			public Double transform(String vertex) {
				Double vertexPrior = priors.get(vertex);
				if(vertexPrior == null)
					return 0d;
				
				return vertexPrior;
			}
		};
		
		PageRankWithPriors<String, WeightedEdge> ranker = 
				new PageRankWithPriors<String, WeightedEdge>(graph, edgeTransformer, priorsTransformer, d);
		
		ranker.setTolerance(tolerance) ;
		ranker.setMaxIterations(maxIterations);
		ranker.evaluate();
	
		double maxScore = 0;
		Collection<String> vertices = graph.getVertices();
		Map<String, Double> verticesMap = new TreeMap<String, Double>();
		for(String vertex : vertices) {
			Double score = ranker.getVertexScore(vertex);
			
			if(score > maxScore)
				maxScore = score;
			
			verticesMap.put(vertex, score);
		}
	
		if(maxScore > 0) {
			for(Entry<String, Double> ve : verticesMap.entrySet()) {
				verticesMap.put(ve.getKey(), ve.getValue()/maxScore);
			}
		}
		
		return verticesMap;
	}
	
	public static Map<String, Double> hitsScoring(Graph<String, WeightedEdge>  graph) {
		
		//Transformer<WeightedEdge, Float> edgeTransformer = WeightedEdge.getEdgeTransformer();
		HITS<String, WeightedEdge> ranker = new HITS<String, WeightedEdge>(graph, d);
		//HITS<String, WeightedEdge> ranker = new HITS<String, WeightedEdge>(graph, edgeTransformer , d);
		ranker.setTolerance(tolerance) ;
		ranker.setMaxIterations(maxIterations);
		ranker.evaluate();
	
		Collection<String> vertices = graph.getVertices();
		Map<String, Double> verticesMap = new TreeMap<String, Double>();
		for(String vertex : vertices) {
			Scores hitsScores = ranker.getVertexScore(vertex);
			Double authorityScore = hitsScores.authority;
			//Double hubScore = hitsScores.hub;	
			verticesMap.put(vertex, authorityScore);
			//temp.put(vertex, hubScore);
		}
		return verticesMap;
	}

	public static TreeMap<String, Double> eigenvectorScoring(Graph<String, WeightedEdge>  graph) {
		
		EigenvectorCentrality<String, WeightedEdge> ranker = 
				new EigenvectorCentrality<String, WeightedEdge>(graph, WeightedEdge.getEdgeTransformer());
		ranker.evaluate();
		
		Collection<String> vertices = graph.getVertices();
		TreeMap<String, Double> verticesMap = new TreeMap<String, Double>();
		for(String vertex : vertices) {
			Double score = ranker.getVertexScore(vertex);
			verticesMap.put(vertex, score);
		}
		
		return verticesMap;
	}
	
	public static Map<String, Double> divrankScoring(Graph<String, WeightedEdge>  graph) {
		
		Map<String, Double> priors = new HashMap<String, Double>();			
		for(String vertex : graph.getVertices()) {
			priors.put(vertex, 1.0 / graph.getVertexCount());
		}

		return divrankScoring(graph, priors);
	}
	
	public static Map<String, Double> divrankScoring(Graph<String, WeightedEdge>  graph, final Map<String, Double> priors) {

		List<String> vertices = new ArrayList<String>(graph.getVertices());
		
		System.out.println("#priors: " + priors.size() + ", #vertices: " + vertices.size());
		
		double sum = 0;
		double[] initialScores = new double[vertices.size()];			
		
		int i = 0;
		for(String vertex : vertices) {
			initialScores[i] = priors.get(vertex);
			sum += initialScores[i];
			i++;
		}
		System.out.println("Initial SUM: " + sum);

		SparseDoubleMatrix2D matrix = GraphMatrixOperations.graphToSparseMatrix(graph);
		
		IntArrayList iIndinces = new IntArrayList();
		IntArrayList jIndinces = new IntArrayList();
		DoubleArrayList weights = new DoubleArrayList();
		matrix.getNonZeros(iIndinces, jIndinces, weights);
	
		SparseMatrix affinityMatrix = new SparseHashMatrix(vertices.size(), vertices.size());
		for(int index=0; index<weights.size(); index++) {
			affinityMatrix.set(iIndinces.get(index), jIndinces.get(index), weights.get(index));
		}
		DoubleVector initialRanks = new DenseVector(initialScores);
		
		DivRank ranker = new DivRank(d);
		DoubleVector ranks = ranker.rankMatrix(affinityMatrix, initialRanks);
		
		double maxScore = 0;
		Map<String, Double> verticesMap = new TreeMap<String, Double>();
		for(int index = 0 ; index<vertices.size(); index++) {
			
			String vertex = vertices.get(index);
			double score = ranks.get(index);
			if(score > maxScore) {
				maxScore = score;
			}
			
			verticesMap.put(vertex, score);
		}
	
		if(maxScore > 0) {
			for(Entry<String, Double> ve : verticesMap.entrySet()) {
				verticesMap.put(ve.getKey(), ve.getValue()/maxScore);
			}
		}
		
		sum = 0;
		for(Double weight : verticesMap.values()) {
			sum += weight;
		}
		System.out.println("Final SUM: " + sum);
		System.out.println("Max DR: " + Collections.max(verticesMap.values()));
		System.out.println("Min DR: " + Collections.min(verticesMap.values()));
		
		return verticesMap;
	}

	public static Map<String, Double> getPriors(Collection<String> ids, Map<String, Integer> popularities) {
		
		Map<String, Double> priors = new HashMap<String, Double>();
		Double popularitySum = 0d;
		for(String id : ids) {
			Integer popularity = popularities.get(id);
			if(popularity != null) {
				popularitySum += (popularity+1);
			}
		}
		
		for(String id : ids) {
			Double popularity = popularities.get(id).doubleValue();
			if(popularity != null) {
				priors.put(id, (popularity+1)/popularitySum);
			}
			else {
				priors.put(id, .0);
			}
		}
		return priors;
	}
	
	private static String dataset = "BaltimoreRiots";
	
	public static void main(String...args) throws IOException {
		String visualGraphFile = "/disk1_data/Datasets/" + dataset + "/graphs/visual_items_graph_pruned.graphml";
		
		Graph<String, WeightedEdge> visualGraph = GraphUtils.loadGraph(visualGraphFile);
		System.out.println("Visual Graph: #Vertices " + visualGraph.getVertexCount() + ", #edges: " + visualGraph.getEdgeCount()
				 + ", Density: " + GraphUtils.getGraphDensity(visualGraph) + ", MinMax: " + GraphUtils.getMinMaxWeight(visualGraph));
	
		visualGraph = GraphUtils.filter(visualGraph, 0);
		visualGraph = GraphUtils.filter(visualGraph, 0.5);
		System.out.println("Visual Graph: #Vertices " + visualGraph.getVertexCount() + ", #edges: " + visualGraph.getEdgeCount()
				 + ", Density: " + GraphUtils.getGraphDensity(visualGraph) + ", MinMax: " + GraphUtils.getMinMaxWeight(visualGraph));

		GraphUtils.saveGraph(visualGraph, "/home/manosetro/Desktop/v_graph.graphml");
	}
	
}
