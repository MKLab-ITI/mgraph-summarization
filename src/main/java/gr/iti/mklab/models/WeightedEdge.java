package gr.iti.mklab.models;

import java.io.Serializable;

import org.apache.commons.collections15.Transformer;

public class WeightedEdge implements Serializable {

	private static final long serialVersionUID = 6414876106698759515L;
	
	private Double w;
		
	public WeightedEdge(Double w) {
		this.w = w;
	}
		
	public Double getWeight() {
		return w;
	}
		
	public void setWeight(Double w) {
		this.w = w;
	}
		
	public String toString() {
		return w.toString();
	}
		
	public static Transformer<WeightedEdge, Double> getEdgeTransformer() {
		return new Transformer<WeightedEdge, Double>() {
			@Override
			public Double transform(WeightedEdge edge) {
				return edge.getWeight();
			}	
		};
	}
}	