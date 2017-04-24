package gr.iti.mklab;

import java.io.IOException;

import edu.uci.ics.jung.graph.Graph;
import gr.iti.mklab.models.WeightedEdge;
import gr.iti.mklab.utils.GraphUtils;

public class GraphTransformation {

	public static void main(String[] args) throws IOException {		
		Graph<String, WeightedEdge> tGraph = GraphUtils.loadGraph(args[0]);
		System.out.println("#Vertices " + tGraph.getVertexCount() + ", #edges: " + tGraph.getEdgeCount());
		
		double th = Double.parseDouble(args[2]);
		tGraph = GraphUtils.filter(tGraph, th);
		GraphUtils.saveGraph(tGraph, args[1]);

	}

}
