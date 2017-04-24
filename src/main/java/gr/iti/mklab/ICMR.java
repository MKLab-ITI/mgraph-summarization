package gr.iti.mklab;

import edu.uci.ics.jung.graph.Graph;
import gr.iti.mklab.JudgementsApi.ImageJudgement;
import gr.iti.mklab.clustering.GraphClusterer;
import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.dao.MySqlDAO;
import gr.iti.mklab.index.VisualIndex;
import gr.iti.mklab.models.ClusterVector;
import gr.iti.mklab.models.Event;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.NamedEntity;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.models.WeightedEdge;
import gr.iti.mklab.ranking.GraphRanker;
import gr.iti.mklab.summarization.itcr.ITSummarizer;
import gr.iti.mklab.utils.CollectionsUtils;
import gr.iti.mklab.utils.GraphUtils;
import gr.iti.mklab.utils.IOUtil;
import gr.iti.mklab.utils.L2;
import gr.iti.mklab.utils.Sorter;
import gr.iti.mklab.vocabulary.Vocabulary;

import java.io.FileWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.commons.io.IOUtils;

public class ICMR {

	//private static String vIndexModels = "/disk2_data/VisualIndex/learning_files";
	//private static String vIndexDirectory = "/disk1_data/Datasets/Events2012/VisualIndex";
	
	private static VisualIndex vIndex;
	
	public static void main(String...args) throws Exception {
		
		//vIndex = new VisualIndex(vIndexModels, vIndexDirectory);
		
		Map<String, Item> itemsMap = new HashMap<String, Item>();
		Set<String> boostedTerms = new HashSet<String>();
		
		MorphiaDAO<Item> dao = new MorphiaDAO<Item>("", "ICMR2015", Item.class);
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
		
		List<Event> events = MySqlDAO.getEvents();
		System.out.println(events.size() + " events");
		
		for(double d = 0; d<=1; d=d + 0.1) {
			
			List<String> lines = new ArrayList<String>();
			
			for(Event event : events) {

				String eventid = event.getEventId();
				if(eventid.equals("241104") || eventid.equals("252589") || eventid.equals("422239") 
						|| eventid.equals("302595")) {
					continue;
				}

				String title = event.getTitle();

				System.out.println("==============================================================");
				System.out.println(eventid + " => " + title);
				System.out.println("Clusters: " + event.getClusters());

				Set<String> ids = MySqlDAO.getEventTweets(eventid, itemsMap);
				System.out.println(ids.size() + " tweets exist.");

				Map<String, Item> eventItems = CollectionsUtils.mapSlice(itemsMap, ids);
				Map<String, Vector> eventVectors = CollectionsUtils.mapSlice(vectorsMap, ids);
				System.out.println(eventItems.size() + " items, " + eventVectors.size() + " vectors");

				Graph<String, WeightedEdge> visualGraph = GraphUtils.loadGraph("/disk1_data/Datasets/Events2012/visual_graphs/" + eventid + ".graphml");
				System.out.println("Visual Graph: " + visualGraph.getVertexCount() + " vertices, " + visualGraph.getEdgeCount() + " edges.");

				Map<String, String> images = MySqlDAO.getEventImages(ids, eventItems);

				System.out.println("Detect Cliques and Fold");
				Collection<Collection<String>> cliques = GraphClusterer.cluster(GraphUtils.filter(visualGraph, 0.5), false);
				System.out.println("Cliques: " + cliques.size());
				GraphUtils.fold(visualGraph, cliques);
				Vector.fold(eventVectors, cliques);
				Item.fold(eventItems, cliques);
				System.out.println(eventItems.size() + " items, " + eventVectors.size() + " vectors");

				String clustersFile = "/disk1_data/Datasets/Events2012/clusters/" + eventid + ".tsv";
				Map<String, ClusterVector> clusters = IOUtil.loadClusters(clustersFile, eventItems, eventVectors);
				System.out.println(clusters.size());

				ITSummarizer summarizer = new ITSummarizer(eventItems, eventVectors, clusters);
				Set<String> s = new HashSet<String>();
				s.addAll(visualGraph.getVertices());
				
				GraphRanker.d = d;
				
				Map<String, Double> divRanks = summarizer.summarize(visualGraph);
				List<Entry<String, Double>> sortedDivRanks = Sorter.sort(divRanks);

				String line = "";
				for(int i=0; i<Math.min(10, sortedDivRanks.size()); i++) {

					Entry<String, Double> entry = sortedDivRanks.get(i);
					String tweetid = entry.getKey();
					System.out.println(entry + " -> " + images.get(tweetid));

					double relevance = 0;
					String[] parts = tweetid.split("-");

					String imageid = null;
					for(String id : parts) {
						imageid = images.get(id);
						ImageJudgement judgement = judgements.get(imageid);
						if(judgement!= null && judgement.relevance>relevance) {
							relevance = judgement.relevance;
						}
					}

					line += relevance + "\t";
				}
				lines.add(line);
			}

			Writer writer = new FileWriter("/disk1_data/Datasets/Events2012/results/A" + Math.round(10*d) + ".tsv");
			IOUtils.writeLines(lines, "\n", writer);
			writer.close();
		}
		
	}

	public static void getRandom(List<Event> events, Map<String, Item> itemsMap) throws Exception {
		Map<String, ImageJudgement> judgements = JudgementsApi.load();
		
		Random R = new Random();
		List<String> lines = new ArrayList<String>();
		double totalAvgSimilarity = 0;
		for(Event event : events) {
			String eventid = event.getEventId();
			if(eventid.equals("241104") || eventid.equals("252589") || eventid.equals("422239") || eventid.equals("302595")){
				continue;
			}
			
			//System.out.println(eventid);
			Set<String> ids = MySqlDAO.getEventTweets(eventid, itemsMap);
			//System.out.println(ids.size() + " tweets");
			Map<String, String> images = MySqlDAO.getEventImages(ids, itemsMap);
			List<String> imgIds = new ArrayList<String>(new HashSet<String>(images.values()));
			System.out.println(eventid + ": " + imgIds.size() + " images");
			
			Set<String> s = new HashSet<String>();
			
			while(s.size() < Math.min(5, imgIds.size()-1)) {
				int index = R.nextInt(imgIds.size()-1);
				String id = imgIds.get(index);
				if(vIndex.isIndexed(id))
					s.add(id);
			}
			
			String line = "";
			for(String imageid: s) {
				ImageJudgement judgement = judgements.get(imageid);
				if(judgement != null) {
					line += (judgement.relevance>2.5?1:0) + "\t";
				}
				else {
					line += "0" + "\t";
				}
			}
			lines.add(line);
			
			double avgSimilarity = 0;
			int pairs = 0;
			
			List<String> summary = new ArrayList<String>(s);
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
		}
		
		System.out.println("TOTAL AVG SIM: " + (totalAvgSimilarity/46.));
		//System.out.println("Save");
		//Writer writer = new FileWriter("/disk1_data/Datasets/Events2012/random_results.tsv");
		//IOUtils.writeLines(lines, "\n", writer);
		//writer.close();
		
	}
}
