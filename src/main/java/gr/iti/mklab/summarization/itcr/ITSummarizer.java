package gr.iti.mklab.summarization.itcr;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
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

import org.apache.commons.io.IOUtils;

import edu.uci.ics.jung.graph.DirectedGraph;
import edu.uci.ics.jung.graph.Graph;
import gr.iti.mklab.JudgementsApi;
import gr.iti.mklab.JudgementsApi.ImageJudgement;
import gr.iti.mklab.clustering.GraphClusterer;
import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.dao.MySqlDAO;
import gr.iti.mklab.index.VisualIndex;
import gr.iti.mklab.models.Cluster;
import gr.iti.mklab.models.ClusterVector;
import gr.iti.mklab.models.Event;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.NamedEntity;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.models.WeightedEdge;
import gr.iti.mklab.ranking.GraphRanker;
import gr.iti.mklab.utils.CollectionsUtils;
import gr.iti.mklab.utils.GraphUtils;
import gr.iti.mklab.utils.IOUtil;
import gr.iti.mklab.utils.Sorter;
import gr.iti.mklab.vocabulary.Vocabulary;

/*
 * Image-Text CoRanking method for Visual Summarization
 * 
 */
public class ITSummarizer {

	private Map<String, Item> itemsMap;
	private Map<String, Vector> vectorsMap;

	private Map<String, ClusterVector> clusterVectors;
	private Map<String, String> associations;

	private int sinlgeItemClusters = 0;
	
	private ClusterVector largestCluster;
	private Map<String, Cluster> clustersMap = null;

	public ITSummarizer(Map<String, Item> itemsMap, Map<String, Vector> vectorsMap, Map<String, 
			ClusterVector> clusterVectors) {
		
		this.itemsMap = itemsMap;
		this.vectorsMap = vectorsMap;
		this.clusterVectors = clusterVectors;
		
		this.associations = new HashMap<String, String>();
		for(String cvId : clusterVectors.keySet()) {
			ClusterVector cv = clusterVectors.get(cvId);
			for(String itemId : cv.getFocusSet()) {
				associations.put(itemId, cvId);
			}
			
			if(cv.getFocusSet().size() == 1) {
				sinlgeItemClusters++;
			}
		}
		
		this.largestCluster = Collections.max(clusterVectors.values());
		
		System.out.println("largestCluster: " + largestCluster + ", sinlgeItemClusters: " + sinlgeItemClusters +
				", associations: " + associations.size());
	}

	
	public ITSummarizer(Map<String, Item> itemsMap, Map<String, Vector> vectorsMap, Map<String, 
			ClusterVector> clusterVectors, Map<String, Cluster> clustersMap) {
		this(itemsMap, vectorsMap, clusterVectors);
		
		this.clustersMap  = clustersMap;
	}
	
	public Map<String, Double> getTopicScores(Set<String> ids) {
			
		Map<String, Double> scores = new HashMap<String, Double>();
		for(String id : ids) {
			Item item = itemsMap.get(id);
			Vector vector = vectorsMap.get(id);
			
			if(item == null || vector == null) {
				continue;
			}
			
			Double coverage = 0.;
			String topicId = associations.get(id);
			ClusterVector cv = clusterVectors.get(topicId);
			if(cv != null) {
				// Proximity of vector to topic's centroid
				Double similarity = cv.cosine(vector);
				// Topic's significance
				double significance = 
						Math.exp(Math.max(0.6, cv.getNumOfVectors() / largestCluster.getNumOfVectors())) / Math.exp(1);
				
				double density = 1.;
				if(clustersMap != null) {
					Cluster cluster = clustersMap.get(topicId);
					if(cluster != null) {
						density = cluster.getDensity();	
					}
				}
				coverage = similarity * significance * density;
			
			}
			scores.put(id, coverage);
		}
		
		return scores;
	}
	
	public Map<String, Double> getScores(Set<String> ids) {
		
		Map<String, Double> scores = new HashMap<String, Double>();
		Map<String, Double> specificities = getSpecificity(ids);
	
		for(String id : ids) {
			Item item = itemsMap.get(id);
			Vector vector = vectorsMap.get(id);
			
			if(item == null || vector == null) {
				continue;
			}
			
			Double attention = Math.log(item.getReposts()+2)/Math.log(2);
			
			Double coverage = .0, density = 1., significance = .0, textualSimilarity = .0;
			String topicId = associations.get(id);
			ClusterVector cv = clusterVectors.get(topicId);
			if(cv != null) {
				// Proximity of vector to topic's centroid
				textualSimilarity = cv.cosine(vector);
				//System.out.println("|V|=" + vector.getLength());
				//System.out.println("|CV|=" + cv.getLength());
				//System.out.println(id + " - " + topicId + " => " + similarity);
				//System.out.println("===========================================");
				
				// Topic's significance
				significance = Math.exp(Math.max(0.6, cv.getNumOfVectors() / largestCluster.getNumOfVectors())) / Math.exp(1);

				if(clustersMap != null) {
					Cluster cluster = clustersMap.get(topicId);
					if(cluster != null) {
						density = cluster.getDensity();	
					}
				}
				
				coverage = textualSimilarity * significance * density;
				
			}
			Double specificity = specificities.get(id);
			
			//if(coverage == 0) {
			//	System.out.println("ID: " + id);
			//	System.out.println("similarity: " + textualSimilarity);
			//	System.out.println("significance: " + significance);
			//	System.out.println("density: " + density);
			//	System.out.println("|V|=" + vector.getLength());
				//System.out.println("|CV|=" + cv.getLength());
			//	System.out.println("topicId: " + topicId);
			//	System.out.println("==========================================");
			//}
			if(specificity>1) {
				System.out.println(id + " - attention: " + attention + ", coverage: " + coverage + ", specificity: "
					+ specificity);
			}
			
			scores.put(id, attention * coverage * specificity);
		}
		
		return scores;
	}
	
	public Map<String, Double> getScores(Set<String> ids, Map<String, String> images) {
		
		Map<String, Double> specificities = getSpecificity(ids, images);
		
		Map<String, Double> scores = new HashMap<String, Double>();
		for(String id : ids) {
			Item item = itemsMap.get(id);
			Vector vector = vectorsMap.get(id);
			
			if(item == null || vector == null)
				continue;
			
			Double attention = Math.log(item.getReposts()+2)/Math.log(2);
			Double specificity = specificities.get(id);
			if(specificity == null)
				specificity = 1.;
				
			Double coverage = 0.;
			String topicId = associations.get(id);
			ClusterVector cv = clusterVectors.get(topicId);
			if(cv != null) {
				// Proximity of vector to topic's centroid
				Double similarity = cv.cosine(vector);
					
				// Topic's significance
				double significance = Math.exp(5 * cv.getNumOfVectors() / largestCluster.getNumOfVectors());
					
				coverage = similarity * significance;
			}
			scores.put(id, attention * coverage * specificity);
		}
		
		return scores;
	}
	
	public Map<String, Double> getSpecificity(Set<String> ids) {
		Map<String, Double> specificities = new HashMap<String, Double>();
		for(String id : ids) {
			double specificity = 1.;
			String topicId = associations.get(id);
			if(clustersMap != null) {
				Cluster cluster = clustersMap.get(topicId);
				if(cluster != null) {
					List<Integer> nc = cluster.getNeighborClusters();
					if(nc != null && !nc.isEmpty()) {
						specificity = Math.log10((clusterVectors.size()-sinlgeItemClusters) / nc.size());
					}
				}
			}
			specificities.put(id, specificity);
		}
		
		return specificities;
	}
	
	public Map<String, Double> getSpecificity(Set<String> ids, Map<String, String> images) {
		Map<String, Double> specificities = new HashMap<String, Double>();
		try {
			FileInputStream input = new FileInputStream("/disk1_data/Datasets/Events2012/specificity.txt");
			List<String> lines = IOUtils.readLines(input);
			for(String line : lines) {
				String[] parts = line.split("\t");
				if(parts.length == 2)
					specificities.put(parts[0], Double.parseDouble(parts[1]));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	
		Map<String, Double> scores = new HashMap<String, Double>();
		for(String id : ids) {
			Item item = itemsMap.get(id);
			Vector vector = vectorsMap.get(id);
			
			if(item == null || vector == null)
				continue;
			
			Double score = 1.;
			
			String imgId = images.get(id);
			if(imgId != null) {
				Double specificity = specificities.get(imgId);
				if(specificity != null)
					score *= specificity;
			}
			
			scores.put(id, score);
		}
		
		return scores;
	}
	
	public Map<String, Double> getPtwrScores(Set<String> ids, Map<String, String> images) {
		
		Map<String, Double> specificities = new HashMap<String, Double>();
		try {
			FileInputStream input = new FileInputStream("/disk1_data/Datasets/Events2012/specificity.txt");
			List<String> lines = IOUtils.readLines(input);
			for(String line : lines) {
				String[] parts = line.split("\t");
				if(parts.length == 2)
					specificities.put(parts[0], Double.parseDouble(parts[1]));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	
		Map<String, Double> scores = new HashMap<String, Double>();
		for(String id : ids) {
			Item item = itemsMap.get(id);
			Vector vector = vectorsMap.get(id);
			
			if(item == null || vector == null)
				continue;
			
			Double score = (double) (item.getReposts()+1);
			
			String imgId = images.get(id);
			if(imgId != null) {
				Double specificity = specificities.get(imgId);
				if(specificity != null)
					score *= specificity;
			}
			
			scores.put(id, score);
		}
		
		return scores;
	}
	
	public Map<String, Double> getsStwrScores(String eventid, Set<String> ids, Map<String, String> images) {
		Map<String, Double> ptwrScores = getPtwrScores(ids, images);
		
		Map<String, Double> scores = new HashMap<String, Double>();
		try {
			Map<String, Set<String>> clusters = MySqlDAO.getSubEvents(eventid);
			for(Set<String> cluster : clusters.values()) {
				double bestScore = 0;
				String bestId = null;
				for(String id : cluster) {
					if(!ids.contains(id))
						continue;
					
					Double score = ptwrScores.get(id);
					if(score != null && score >= bestScore) {
						bestScore = score;
						bestId = id;
					}
				}
				if(bestId != null) {
					scores.put(bestId, bestScore);
				}
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return scores;
	}
	
	public Map<String, Double> getPopularityScores(Set<String> ids) {
		
		Map<String, Double> scores = new HashMap<String, Double>();
		for(String id : ids) {
			Item item = itemsMap.get(id);
			
			if(item == null)
				continue;

			scores.put(id, (double) item.getReposts());
		}
		
		return scores;
	}
	
	public Map<String, Double> summarize(Graph<String, WeightedEdge> graph) {
		
		Set<String> ids = new HashSet<String>();
		ids.addAll(graph.getVertices());
		
		Map<String, Double> scores = getScores(ids);
		Map<String, Double> priors = normalize(scores);
		
		graph = GraphUtils.filter(graph, priors.keySet());
		DirectedGraph<String, WeightedEdge> directedGraph = GraphUtils.toDirected(graph, itemsMap);
		Graph<String, WeightedEdge> normalizedGraph = GraphUtils.normalize(directedGraph);
		
		Map<String, Double> divScores = GraphRanker.divrankScoring(normalizedGraph, priors);
		return divScores;
	}
	
	public Map<String, Double> summarize(Graph<String, WeightedEdge> graph, Map<String, String> images) {
		
		Set<String> ids = new HashSet<String>();
		ids.addAll(graph.getVertices());
		
		Map<String, Double> scores = getScores(ids, images);
		Map<String, Double> priors = normalize(scores);

		DirectedGraph<String, WeightedEdge> directedGraph = GraphUtils.toDirected(graph, itemsMap);
		Graph<String, WeightedEdge> normalizedGraph = GraphUtils.normalize(directedGraph);
		
		Map<String, Double> divScores = GraphRanker.divrankScoring(normalizedGraph, priors);
		
		return divScores;
	}

	public Map<String, Double> lexrank(Graph<String, WeightedEdge> graph) {
		
		Set<String> ids = new HashSet<String>();
		ids.addAll(graph.getVertices());
		
		Map<String, Double> scores = getScores(ids);
		Map<String, Double> priors = normalize(scores);

		DirectedGraph<String, WeightedEdge> directedGraph = GraphUtils.toDirected(graph, itemsMap);
		Graph<String, WeightedEdge> normalizedGraph = GraphUtils.normalize(directedGraph);
		
		Map<String, Double> lexScores = GraphRanker.pagerankScoring(normalizedGraph, priors);
		
		return lexScores;
	}

	public Map<String, Double> normalize(Map<String, Double> priorScores) {
		
		Map<String, Double> priors = new HashMap<String, Double>();
		Double popularitySum = 0d;
		for(String id : priorScores.keySet()) {
			Double popularity = priorScores.get(id);
			if(popularity != null) {
				popularitySum += (popularity+1);
			}
		}

		for(String id : priorScores.keySet()) {
			Double popularity = priorScores.get(id).doubleValue();
			if(popularity != null) {
				priors.put(id, (popularity+1)/popularitySum);
			}
			else {
				priors.put(id, .0);
			}
		}
		return priors;
	}

	//private static String vIndexModels = "/disk2_data/VisualIndex/learning_files";
	//private static String vIndexDirectory = "/disk1_data/Datasets/Events2012/VisualIndex";
	public static void main(String...args) throws Exception {
		
		//VisualIndex vIndex = new VisualIndex(vIndexModels, vIndexDirectory);
		
		Map<String, Item> itemsMap = new HashMap<String, Item>();
		Set<String> boostedTerms = new HashSet<String>();
		
		MorphiaDAO<Item> dao = new MorphiaDAO<Item>("160.40.50.207", "ICMR2015", Item.class);
		Iterator<Item> iterator = dao.iterator();
		while(iterator.hasNext()) {
			Item item = iterator.next();
			itemsMap.put(item.getId(), item);
			
			Collection<NamedEntity> namedEntities = item.getNamedEntities();
			for(NamedEntity namedEntity : namedEntities) {
				boostedTerms.add(namedEntity.getName());
			}
			
			for(String properNoun : item.getProperNouns()) {
				boostedTerms.add(properNoun);
			}
		}
		
		Map<String, Vector> vectorsMap = Vocabulary.createVocabulary(itemsMap.values());
		Vocabulary.addBoostedTerms(boostedTerms);

		
		Map<String, ImageJudgement> judgements = JudgementsApi.load();
		
		List<String> lines = new ArrayList<String>();
		
		List<Event> events = MySqlDAO.getEvents();
		System.out.println(events.size() + " events");
		double totalAvgSimilarity = 0;
		int e = 0;
		for(Event event : events) {
			
			String eventid = event.getEventId();
			if(eventid.equals("241104") || eventid.equals("252589") || eventid.equals("422239") 
					|| eventid.equals("302595")){
				continue;
			}
			
			e++;
			 
			String title = event.getTitle();

			System.out.println("==============================================================");
			System.out.println(eventid + " => " + title);
			System.out.println("Clusters: " + event.getClusters());
			
			Set<String> ids = MySqlDAO.getEventTweets(eventid, itemsMap);
			System.out.println(ids.size() + " tweets exist.");
			
			Map<String, Item> eventItems = CollectionsUtils.mapSlice(itemsMap, ids);
			Map<String, Vector> eventVectors = CollectionsUtils.mapSlice(vectorsMap, ids);
			System.out.println(eventItems.size() + " items, " + eventVectors.size() + " vectors");
			
			//Graph<String, WeightedEdge> textualGraph = GraphUtils.loadGraph("/disk1_data/Datasets/Events2012/textual_graphs/" + eventid + ".graphml");
			//Graph<String, WeightedEdge> socialGraph = GraphUtils.loadGraph("/disk1_data/Datasets/Events2012/social_graphs/" + eventid + ".graphml");
			Graph<String, WeightedEdge> visualGraph = GraphUtils.loadGraph("/disk1_data/Datasets/Events2012/visual_graphs/" + eventid + ".graphml");
			System.out.println("Visual Graph: " + visualGraph.getVertexCount() + " vertices, " + visualGraph.getEdgeCount() + " edges.");
			
			Map<String, String> images = MySqlDAO.getEventImages(ids, eventItems);
			
			System.out.println("Detect Cliques and Fold");
			Collection<Collection<String>> cliques = GraphClusterer.cluster(GraphUtils.filter(visualGraph, 0.5), false);
			System.out.println("Cliques: " + cliques.size());
			GraphUtils.fold(visualGraph, cliques);
			//GraphUtils.fold(textualGraph, cliques);
			Vector.fold(eventVectors, cliques);
			Item.fold(eventItems, cliques);
			System.out.println(eventItems.size() + " items, " + eventVectors.size() + " vectors");
			
			String clustersFile = "/disk1_data/Datasets/Events2012/clusters/" + eventid + ".tsv";
			Map<String, ClusterVector> clusters = IOUtil.loadClusters(clustersFile, eventItems, eventVectors);
			System.out.println(clusters.size());
			
			
			ITSummarizer summarizer = new ITSummarizer(eventItems, eventVectors, clusters);
			Set<String> s = new HashSet<String>();
			s.addAll(visualGraph.getVertices());
			
			//Map<String, Double> popRanks = summarizer.getPopularityScores(s);
			//Map<String, Double> topicRanks = summarizer.getTopicScores(s);	
			//Map<String, Double> ranks = summarizer.getScores(s);
			Map<String, Double> divRanks = summarizer.summarize(visualGraph);
			//Map<String, Double> lexRanks = summarizer.lexrank(visualGraph);
			//Map<String, Double> PtwrRanks = summarizer.getPtwrScores(s, images);
			//Map<String, Double> StwrRanks = summarizer.getsStwrScores(eventid, s, images);
			
			//List<Entry<String, Double>> sortedSTWRRanks = Sorter.sort(StwrRanks);
			//List<Entry<String, Double>> sortedPtwrRanks = Sorter.sort(PtwrRanks);
			//List<Entry<String, Double>> sortedLexRanks = Sorter.sort(lexRanks);
			//List<Entry<String, Double>> sortedTopicRanks = Sorter.sort(topicRanks);
			//List<Entry<String, Double>> sortedPopRanks = Sorter.sort(popRanks);
			//List<Entry<String, Double>> sortedRanks = Sorter.sort(ranks);
			List<Entry<String, Double>> sortedDivRanks = Sorter.sort(divRanks);
			
			//List<String> summary = new ArrayList<String>();
			String line = "";
			for(int i=0; i<Math.min(10, sortedDivRanks.size()); i++) {
				
				//Entry<String, Double> entry = sortedDivRanks.get(i);
				//Entry<String, Double> entry = sortedPtwrRanks.get(i);
				Entry<String, Double> entry = sortedDivRanks.get(i);
				//Entry<String, Double> entry = sortedDivRanks.get(i);
				//Entry<String, Double> entry = sortedPopRanks.get(i);
				//Entry<String, Double> entry = sortedRanks.get(i);
				//Entry<String, Double> entry = sortedDivRanks.get(i);
				String tweetid = entry.getKey();
				System.out.println(entry + " -> " + images.get(tweetid));

				double relevance = 0;
				String[] parts = tweetid.split("-");
				
				String imageid = null;
				for(String id : parts) {
					imageid = images.get(id);
					//if(imageid != null) {
					//	summary.add(imageid);
					//	break;
					//}
					ImageJudgement judgement = judgements.get(imageid);
					if(judgement!= null && judgement.relevance>relevance) {
						relevance = judgement.relevance;
					}
				}

				line += relevance + "\t";
			}
			lines.add(line);
			
			/* 
			double avgSimilarity = 0;
			int pairs = 0;
			
			for(int i=0; i<(summary.size()-1); i++) {
				
				String id1 = summary.get(i);
				double[] v1 = vIndex.getVector(id1);
				for(int j=i+1; j<summary.size(); j++) {
					String id2 = summary.get(j);
					
					double[] v2 = vIndex.getVector(id2);
					
					avgSimilarity += L2.similarity(v1, v2);
					pairs++;
				}	
			}
			
			avgSimilarity = avgSimilarity / pairs;
			
			totalAvgSimilarity += avgSimilarity;
			System.out.println("Summary: " + summary.size());
			System.out.println("AVG SIM: " + avgSimilarity);
			 */
		}
		
		System.out.println("TOTAL AVG SIM: " + (totalAvgSimilarity/e));
		
		Writer writer = new FileWriter("/disk1_data/Datasets/Events2012/test.tsv");
		IOUtils.writeLines(lines, "\n", writer);
		writer.close();
		
	}
	
	public static Collection<Collection<String>> cluster(Graph<String, WeightedEdge> textualGraph, Graph<String, WeightedEdge> socialGraph, 
			Graph<String, WeightedEdge> visualGraph, String eventid) throws IOException {
		textualGraph = GraphUtils.createUnifiedGraph(textualGraph, 0.33, visualGraph, 0.1, socialGraph);
		System.out.println("Graph: " + textualGraph.getVertexCount() + " vertices, " + textualGraph.getEdgeCount() + " edges.");
		
		Collection<Collection<String>> clusters = GraphClusterer.cluster(textualGraph, true);
		System.out.println(clusters.size());
		IOUtil.saveClusters(clusters, "/disk1_data/Datasets/Events2012/clusters/" + eventid + ".tsv");
		
		return clusters;
	}
	
	public static void createGraphs(String eventid, Set<String> ids, Map<String, Item> eventItems, Map<String, Vector> eventVectors,
			VisualIndex vIndex) throws IOException {
		
		// Handle Textual Graph
		Graph<String, WeightedEdge> textualGraph = GraphUtils.generateTextualItemsGraph(eventVectors, .33);
		GraphUtils.saveGraph(textualGraph, "/disk1_data/Datasets/Events2012/" + eventid + ".graphml");
		System.out.println("Textual Graph: " + textualGraph.getVertexCount() + " vertices, " + textualGraph.getEdgeCount() + " edges.");
		
		// Handle Social Interactions Graph
		DirectedGraph<String, WeightedEdge> socialGraph = GraphUtils.generateSocialGraph(eventItems);
		GraphUtils.saveGraph(socialGraph, "/disk1_data/Datasets/Events2012/social_graphs/" + eventid + ".graphml");
		System.out.println("Social Graph: " + socialGraph.getVertexCount() + " vertices, " + socialGraph.getEdgeCount() + " edges.");

		Map<String, String> images = MySqlDAO.getEventImages(ids, eventItems);
		Graph<String, WeightedEdge> visualGraph = GraphUtils.generateVisualGraph(images, .1, vIndex);
		GraphUtils.saveGraph(visualGraph, "/disk1_data/Datasets/Events2012/visual_graphs/" + eventid + ".graphml");
		
	}
	
}
