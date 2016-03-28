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

import java.util.ArrayList;
import java.util.Collection;
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
	
	private static double aplha = 0.15; //damping factor in PageRank
	
	// Stoping criteria
	private static double tolerance = 0.000001;
	private static int maxIterations = 200;
	
	public static Map<String, Double> pagerankScoring(Graph<String, WeightedEdge>  graph) {
		
		Transformer<WeightedEdge, Double> edgeTransformer = WeightedEdge.getEdgeTransformer();
		PageRank<String, WeightedEdge> ranker = new PageRank<String, WeightedEdge>(graph, edgeTransformer , aplha);
		
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
				new PageRankWithPriors<String, WeightedEdge>(graph, edgeTransformer, priorsTransformer, aplha);
		
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
		
		Transformer<WeightedEdge, Double> edgeTransformer = WeightedEdge.getEdgeTransformer();
		HITS<String, WeightedEdge> ranker = 
				new HITS<String, WeightedEdge>(graph, edgeTransformer , aplha);
		
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
		
		List<String> vertices = new ArrayList<String>(graph.getVertices());
		
		double[] initialScores = new double[vertices.size()];			
		for(int i=0; i<vertices.size(); i++) {
			initialScores[i] = 1.0 / vertices.size();
		}
		DoubleVector initialRanks = new DenseVector(initialScores);
		
		SparseDoubleMatrix2D matrix = GraphMatrixOperations.graphToSparseMatrix(graph);
		IntArrayList i = new IntArrayList();
		IntArrayList j = new IntArrayList();
		DoubleArrayList w = new DoubleArrayList();
		matrix.getNonZeros(i, j, w);
		SparseMatrix affinityMatrix = new SparseHashMatrix(vertices.size(), vertices.size());
		for(int index=0; index<w.size(); index++) {
			affinityMatrix.set(i.get(index), j.get(index), w.get(index));
		}
		
		System.out.println("Run DivRank");
		DivRank ranker = new DivRank(aplha);
		DoubleVector ranks = ranker.rankMatrix(affinityMatrix, initialRanks);
		
		double maxScore = 0;
		Map<String, Double> verticesMap = new TreeMap<String, Double>();
		for(int index=0 ; index<vertices.size(); index++) {
			
			double score = ranks.get(index);
			if(score > maxScore)
				maxScore = score;
			
			verticesMap.put(vertices.get(index), score);
		}
	
		if(maxScore > 0) {
			for(Entry<String, Double> ve : verticesMap.entrySet()) {
				verticesMap.put(ve.getKey(), ve.getValue()/maxScore);
			}
		}
		
		return verticesMap;
	}
	
	public static Map<String, Double> divrankScoring(Graph<String, WeightedEdge>  graph, final Map<String, Double> priors) {

		List<String> vertices = new ArrayList<String>(graph.getVertices());
		
		System.out.println("#priors: " + priors.size() + ", #vertices: " + vertices.size());
		
		double sum = 0;
		double[] initialScores = new double[vertices.size()];			
		for(int i=0; i<vertices.size(); i++) {
			initialScores[i] = priors.get(vertices.get(i));
			sum += initialScores[i];
		}
		System.out.println("Initial SUM: " + sum);

		SparseDoubleMatrix2D matrix = GraphMatrixOperations.graphToSparseMatrix(graph);
		IntArrayList i = new IntArrayList();
		IntArrayList j = new IntArrayList();
		DoubleArrayList w = new DoubleArrayList();
		matrix.getNonZeros(i, j, w);
		SparseMatrix affinityMatrix = new SparseHashMatrix(vertices.size(), vertices.size());
		for(int index=0; index<w.size(); index++) {
			affinityMatrix.set(i.get(index), j.get(index), w.get(index));
		}
		DoubleVector initialRanks = new DenseVector(initialScores);
		
		DivRank ranker = new DivRank(aplha);
		DoubleVector ranks = ranker.rankMatrix(affinityMatrix, initialRanks);

		
		
		double maxScore = 0;
		Map<String, Double> verticesMap = new TreeMap<String, Double>();
		for(int index=0 ; index<vertices.size(); index++) {
			
			double score = ranks.get(index);
			if(score > maxScore)
				maxScore = score;
			
			verticesMap.put(vertices.get(index), score);
		}
	
		if(maxScore > 0) {
			for(Entry<String, Double> ve : verticesMap.entrySet()) {
				verticesMap.put(ve.getKey(), ve.getValue()/maxScore);
			}
		}
		
		return verticesMap;
	}

	public static Map<String, Double> getPriors(Collection<String> ids, Map<String, Integer> popularities) {
		
		Map<String, Double> priors = new HashMap<String, Double>();
		Double popularitySum = 0d;
		for(String id : ids) {
			Integer popularity = popularities.get(id);
			popularitySum += popularity;
		}
		
		for(String id : ids) {
			Double popularity = popularities.get(id).doubleValue();
			priors.put(id, popularity/popularitySum);
		}
		return priors;
	}
}
