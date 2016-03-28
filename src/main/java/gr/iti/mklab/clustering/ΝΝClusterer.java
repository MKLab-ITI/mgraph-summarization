package gr.iti.mklab.clustering;

import gr.iti.mklab.models.ClusterVector;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class ΝΝClusterer {

	public static List<ClusterVector> cluster(Map<String, Vector> vectors, Map<String, Item> items,
			double similarityThreshold) {
	
		int k = 0;
		List<ClusterVector> clusters = new ArrayList<ClusterVector>();
		for(Entry<String, Vector> e : vectors.entrySet()) {
			try {
			
				if(++k % 500 == 0) {
					System.out.print(".");
					if(k%10000==0)
						System.out.println("  " + k + " tweets. " + clusters.size() + " clusters");
				}
				
				String id = e.getKey();
				Vector vector = e.getValue();
				
				Item item = items.get(id);
				
				long ts = item.getPublicationTime();
				
				double bestSimilarity = 0;
				ClusterVector cluster = null;
				for(ClusterVector candidateCluster : clusters) {
					Double similarity = candidateCluster.cosine(vector);			
					if(similarity > bestSimilarity) {
						bestSimilarity = similarity;
						cluster = candidateCluster;
					}
				}
				
				if(bestSimilarity < similarityThreshold) {
					cluster = new ClusterVector();
					clusters.add(cluster);
				}
				cluster.addVector(id, vector, ts);
				
			} catch (Exception ex) {
				ex.printStackTrace();
				continue;
			}
		}
		return clusters;
	}

}
