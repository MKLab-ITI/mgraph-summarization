package gr.iti.mklab;

import java.io.IOException;

import edu.uci.ics.jung.graph.Graph;
import gr.iti.mklab.models.WeightedEdge;
import gr.iti.mklab.utils.GraphUtils;

public class statistics {

	public static void main(String[] args) throws IOException {
		
		String graphFile = "/disk1_data/Datasets/SNOW/graphs/social_items_graph.graphml";

		Graph<String, WeightedEdge> visualGraph = GraphUtils.loadGraph(graphFile);
		System.out.println("Visual Graph: #Vertices " + visualGraph.getVertexCount() + ", #edges: " + visualGraph.getEdgeCount()
				 + ", Density: " + GraphUtils.getGraphDensity(visualGraph) + ", MinMax: " + GraphUtils.getMinMaxWeight(visualGraph));
		
		
	}

}
