package gr.iti.mklab.topicmodelling;

import edu.uci.ics.jung.graph.Graph;
import gr.iti.mklab.Config;
import gr.iti.mklab.analysis.ItemFilter;
import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.models.WeightedEdge;
import gr.iti.mklab.utils.GraphUtils;
import gr.iti.mklab.vocabulary.Vocabulary;

import java.util.List;
import java.util.Map;

public class TopicDetectionMain {
	
	public static void main(String[] args) throws Exception {
		
		MorphiaDAO<Item> dao = new MorphiaDAO<Item>(Config.hostname ,Config.dbname, Item.class);
		System.out.println(dao.count() + " items");

		// Aggressive Filtering 
		ItemFilter filter = new ItemFilter();
		List<Item> items = filter.filter(dao.iterator());
		System.out.println(items.size() + " items after filtering");

		Map<String, Vector> vectors = Vocabulary.createVocabulary(items);
		System.out.println(vectors.size() + " vectors");
		
		Graph<String, WeightedEdge> graph = GraphUtils.generateGraph(vectors, 0.3);
		System.out.println("Vertices: " + graph.getVertexCount());
		System.out.println("Edges: " + graph.getEdgeCount());
	}

}
