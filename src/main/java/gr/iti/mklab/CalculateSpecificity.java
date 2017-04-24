package gr.iti.mklab;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

import edu.uci.ics.jung.graph.Graph;
import gr.iti.mklab.clustering.GraphClusterer;
import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.MediaItem;
import gr.iti.mklab.models.WeightedEdge;
import gr.iti.mklab.utils.GraphUtils;

public class CalculateSpecificity {

	private static String dataset = "Sundance2013";
	
	public static void main(String...args) throws Exception {
		
		MorphiaDAO<MediaItem> dao = new MorphiaDAO<MediaItem>("160.40.50.207", dataset, MediaItem.class);
		
		Graph<String, WeightedEdge> visualGraph = GraphUtils.loadGraph("/disk1_data/Datasets/" + dataset + "/graphs/visual_graph.graphml");
		System.out.println("Visual Graph: " + visualGraph.getVertexCount() + " vertices, " + visualGraph.getEdgeCount() + " edges "
				+ GraphUtils.getGraphDensity(visualGraph));
		
		GraphClusterer.scanMu = 2;
		GraphClusterer.scanEpsilon = 0.55;
		Collection<Collection<String>> cliques = GraphClusterer.cluster(GraphUtils.filter(visualGraph, 0.55), true);
		System.out.println("Cliques: " + cliques.size());
		
		for(Collection<String> clique : cliques) {

			DBObject obj = new BasicDBObject();
			obj.put("title", "Sundance Film Festival, 2013");
			obj.put("members", clique);
			obj.put("size", clique.size());
			obj.put("relevance", 0);
			obj.put("numOfJudgements", 0);
			obj.put("judgements", new Long[0]);
			
			Iterator<String> it = clique.iterator();
			while(it.hasNext()) {
				String id = it.next();
				MediaItem mi = dao.get(id);
				if(mi != null) {
					obj.put("_id", mi.getId());
					obj.put("height", mi.getHeight());
					obj.put("width", mi.getWidth());
					obj.put("concept", mi.getConcept());
					obj.put("conceptScore", mi.getConceptScore());
					break;
				}
			}
			dao.save(obj, "Cliques");
		}
		
//		Map<String, Item> itemsMap = new HashMap<String, Item>();
//		MorphiaDAO<Item> dao = new MorphiaDAO<Item>("160.40.50.207", dataset, Item.class);
//		List<Item> items = dao.get();
//		for(Item item : items) {
//			itemsMap.put(item.getId(), item);
//		}
//		
//		HashMap<String, String> imageEventMap = new HashMap<String, String>();
		
		//Set<String> ids = MySqlDAO.getEventTweets(eventid, itemsMap);
		//Map<String, String> images = MySqlDAO.getEventImages(ids, itemsMap);
			
		//for(String imgId : images.values()) {
		//	imageEventMap.put(imgId, eventid);
		//}
		
		
//		Map<String, Double> score = new HashMap<String, Double>();
//		for(Set<String> clique : cliques) {
//			Set<String> temp = new HashSet<String>();
//			for(String id : clique) {
//				String eventid = imageEventMap.get(id);
//				if(eventid != null) {
//					temp.add(eventid);
//				}
//			}
//			for(String id : clique) {
//				score.put(id, Math.log(50./temp.size()));
//			}
//		}
//		
//		List<String> lines = new ArrayList<String>();
//		for(String id : score.keySet()) {
//			String line = id + "\t" + score.get(id);
//			lines.add(line);
//		}
//		
//		OutputStream output = new FileOutputStream("/disk1_data/Datasets/Events2012/specificity.txt");
//		IOUtils.writeLines(lines, "\n", output);
//		output.close();
	}
	
}
