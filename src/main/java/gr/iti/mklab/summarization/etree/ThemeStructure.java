package gr.iti.mklab.summarization.etree;

import gr.iti.mklab.models.ClusterVector;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ThemeStructure {

	
	public void HierarchicalStructure(Map<String, ClusterVector> cVectors) {
		Set<Node> H = new HashSet<Node>();
		for(ClusterVector cv : cVectors.values()) {
			Node n = new Node(cv);
			H.add(n);
		}
		while(true) {
			Node parent = getMaxSimilarity(H);
			if(parent == null)
				break;
			
			H.removeAll(parent.getChildren());
			H.add(parent);
			
			reStructure(parent);
		}
		
	}
	
	private void reStructure(Node np) {
		for(Node ni : np.getChildren()) {
			if (ni.similarity(np) == 1) {
				// Remove ni and attach all his children in np
			}
		}
		for(Node ni : np.getChildren()) {
			for(Node nj : np.getChildren()) {
				if(ni == nj)
					continue;
				
			}
		}
	}
	
	private Node getMaxSimilarity(Set<Node> H) {
		double bestSim = 0;
		Node ni = null, nj = null;
		for(Node n1 : H) {
			for(Node n2 : H) {
				if(n1 == n2)
					continue;
				double sim = n2.similarity(n1);
				if(sim > bestSim) {
					ni = n1;
					nj = n2;
					bestSim = sim;
				}
			}
		}
		if(bestSim > 0 && ni != null && nj != null) {
			Node np = new Node();
			np.addChild(ni);
			np.addChild(nj);
			return np;
		}
		else {
			return null;
		}
	}
	
}
