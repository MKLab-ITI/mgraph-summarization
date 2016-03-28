package gr.iti.mklab.clustering;

import edu.uci.ics.jung.graph.Graph;
import gr.iti.mklab.clustering.scan.Community;
import gr.iti.mklab.clustering.scan.ScanCommunityDetector;
import gr.iti.mklab.clustering.scan.ScanCommunityStructure;
import gr.iti.mklab.models.ClusterVector;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.models.WeightedEdge;
import gr.iti.mklab.utils.GraphUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class GraphClusterer {

	private static double scanEpsilon = 0.5;
    private static int scanMu = 2;
	
    public static List<ClusterVector> cluster(String filename, Map<String, Vector> vectors, Map<String, Item> items) {
		
		Graph<String, WeightedEdge> graph;
		try {
			graph = GraphUtils.loadGraph(filename);
		} catch (IOException e) {
			e.printStackTrace();
			return new ArrayList<ClusterVector>();
		}
		
		System.out.println("Vertices: " + graph.getVertexCount());
		System.out.println("Edges: " + graph.getEdgeCount());
		
		return cluster(graph, vectors, items);
	}	
  	
	public static List<ClusterVector> cluster(Graph<String, WeightedEdge> graph, Map<String, Vector> vectors, Map<String, Item> items) {
			
		ScanCommunityDetector<String, WeightedEdge> detector = new ScanCommunityDetector<String, WeightedEdge>(scanEpsilon, scanMu);
		ScanCommunityStructure<String, WeightedEdge> structure = detector.getCommunityStructure(graph);
		
		int numCommunities = structure.getNumberOfCommunities();
		System.out.println("#communities: " + numCommunities);
		
        List<ClusterVector> clusters = new ArrayList<ClusterVector>();
        for(int i=0; i<numCommunities; i++) {
              Community<String, WeightedEdge> community = structure.getCommunity(i);
              if(community != null) {
            	  ClusterVector clusterVector = new ClusterVector();
            	  clusters.add(clusterVector);
            	  for(String member : community.getMembers()) {
                        Vector vector = vectors.get(member);
                        Item item = items.get(member);    
                        long publicationTime = item.getPublicationTime();
              
                        clusterVector.addVector(member, vector, publicationTime);
            	  }
              }
        }
        
        List<String> outliers = structure.getOutliers();
        int nOutliers = outliers.size();
        System.out.println("#outliers: " + nOutliers);
        
        /* */
        List<String> hubs = structure.getHubs();
        int nHubs = hubs.size();
        System.out.println("#hubs: " + nHubs);
        for(String hub : hubs) {
            Collection<String> hubNeighbours = graph.getNeighbors(hub);
            Map<ClusterVector, Integer> temp = new HashMap<ClusterVector, Integer>();
            for(String neighbor : hubNeighbours) {
            	for(ClusterVector cluster : clusters) {
            		if(cluster.getFocusSet().contains(neighbor)) {
            			Integer count = temp.get(cluster);
            			if(count == null)
            				count = 0;
            			temp.put(cluster, ++count);
            		}
            	}
            }
            
            ClusterVector cluster = null;
            Integer maxCount = 0;
            for(Entry<ClusterVector, Integer> e : temp.entrySet()) {
            	if(maxCount < e.getValue() && e.getValue()>1)
            		cluster = e.getKey();
            }
            
            if(cluster != null) {
            	Vector vector = vectors.get(hub);
            	Item item = items.get(hub);    
                long publicationTime = item.getPublicationTime();
                
            	cluster.addVector(hub, vector, publicationTime);
            }
        }
        
		return clusters;
	}
	
	

}
