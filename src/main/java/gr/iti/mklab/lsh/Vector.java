package gr.iti.mklab.lsh;
public class Vector {
	
	public Long id;
	public double[] v;
	
	public Vector(Long id, double[] v) {
		this.id = id;
		this.v = v;
	}
	
	public String toString() {
		return this.id.toString();
	}
	
}