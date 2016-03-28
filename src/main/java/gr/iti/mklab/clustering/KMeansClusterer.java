package gr.iti.mklab.clustering;

import gr.iti.mklab.models.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KMeansClusterer {

	private static int maxIterations = 100;
	
	public static List<Vector> cluster(Map<String, Vector> vectors, int k) {
		List<String> ids = new ArrayList<String>();
		List<Vector> CVs = new ArrayList<Vector>();
		for(int i=0; i<k; i++) {
			Vector cv = new Vector();
			cv.mergeVector(vectors.get(ids.get(i)));
			CVs.add(cv);
		}

		int iterations = 0;
		while(true) {
			
			Map<Integer, List<String>> assignments = new HashMap<Integer, List<String>>();
			//cluster assignment step
			for(String id : vectors.keySet()) {
				Vector vector = vectors.get(id);

				int centroid = -1;
				double bestSimilarity = 0;
				for(Vector cv : CVs) {
					double similarity = cv.cosine(vector);
					if(similarity > bestSimilarity) {
						bestSimilarity = similarity;
						centroid = CVs.indexOf(cv);
					}
				}
				List<String> cluster = assignments.get(centroid);
				if(cluster == null)
					cluster = new ArrayList<String>();
				
				cluster.add(id);
			}
			
		   	//centroid update step
		   	for(Integer index : assignments.keySet()) {
		   		Vector centroid = new Vector();
		   		for(String id : assignments.get(index)) {
		   			Vector vector = vectors.get(id);
		   			centroid.mergeVector(vector);	
		   		}
		   		CVs.add(index, centroid);
		   	}
		   	
		   	//check break conditions
		   	String oldCenter = "", newCenter = "";
		   	
		   	//for(double[] x : step.keySet()) {
		   	//	newCenter += Arrays.toString(x);
		   	//}
		   	
		   	if(oldCenter.equals(newCenter)) 
		   		break;
		   	if(++iterations >= maxIterations) 
		   		break;
		}
		
		return CVs;
	}
}
