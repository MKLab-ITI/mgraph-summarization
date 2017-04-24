package gr.iti.mklab;

import edu.uci.ics.jung.graph.Graph;
import gr.iti.mklab.index.TextIndex;
import gr.iti.mklab.models.Cluster;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.models.WeightedEdge;
import gr.iti.mklab.utils.GraphUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Test {

	private static String dataset = "BaltimoreRiots";
	
	public static void main(String[] args) throws ClassNotFoundException, IOException {
		TextIndex tIndex = new TextIndex("/disk1_data/Datasets/" + dataset + "/TextIndex");
		tIndex.open();
		System.out.println(tIndex.count() + " documents in text index!");
		
		Map<String, Vector> vectorsMap = tIndex.createVocabulary();
		System.out.println(vectorsMap.size() + " vectors");
		
		String visualGraphFile = "/disk1_data/Datasets/" + dataset + "/graphs/visual_items_graph_pruned.graphml";
		Graph<String, WeightedEdge> visualGraph = GraphUtils.loadGraph(visualGraphFile);
		System.out.println("Visual Graph: #Vertices " + visualGraph.getVertexCount() + ", #edges: " + visualGraph.getEdgeCount()
				 + ", Density: " + GraphUtils.getGraphDensity(visualGraph) + ", MinMax: " + GraphUtils.getMinMaxWeight(visualGraph));
		
		Collection<String> mItemIds = visualGraph.getVertices();
		
		FileInputStream fileIn = new FileInputStream("/disk1_data/Datasets/" + dataset + "/graphs/clusters_folded.bin");
		ObjectInputStream in = new ObjectInputStream(fileIn);
		
		@SuppressWarnings("unchecked")
		List<Cluster> clusters = (List<Cluster>) in.readObject();
		in.close();
		fileIn.close();

		for(Cluster cluster : clusters) {
			if(cluster.getDensity() > 0.6 && cluster.getSize()>6) {
				
				Set<String> set = new HashSet<String>(cluster.getMembers());
				set.retainAll(mItemIds);
				if((double)set.size() / (double)cluster.getSize() < 0.5)
					continue;
				
				System.out.println("Id: " + cluster.getId());
				System.out.println("Density: " + cluster.getDensity());
				System.out.println("Size: " + cluster.getSize());
				System.out.println("Members: " + cluster.getMembers());
				
				List<String> members = new ArrayList<String>(cluster.getMembers());
				for(int i=0; i<cluster.getMembers().size()-1; i++) {
					String IDi = members.get(i);
					Vector Vi = vectorsMap.get(IDi);
					if(Vi != null) {
						for(int j=i+1; j<cluster.getMembers().size(); j++) {
							String IDj = members.get(j);
							Vector Vj = vectorsMap.get(IDj);
							if(Vj != null) {
								Double cosine = Vi.cosine(Vj);
								if(cosine>0.1) {
									System.out.println(IDi + " - " + IDj + " " + cosine);
								}
							}
						}
					}
				}
				System.out.println("\n\n");
				for(int i=0; i<cluster.getMembers().size()-1; i++) {
					String IDi = members.get(i);
					if(visualGraph.containsVertex(IDi)) {
						for(int j=i+1; j<cluster.getMembers().size(); j++) {
							String IDj = members.get(j);
							if(visualGraph.containsVertex(IDj)) {
								WeightedEdge edge = visualGraph.findEdge(IDi, IDj);
								if(edge != null) {
									System.out.println(IDi + " - " + IDj + " " + edge.getWeight());
								}
							}
						}
					}
				}
				
				System.out.println("==============================================");
			}
		}
		
	}

}
