package gr.iti.mklab.models;

import java.io.Serializable;

public class Pair<L,R> implements Serializable {
      /**
	 * 
	 */
	private static final long serialVersionUID = 9087787283642490761L;
	
	
	public final L left;
	public final R right;

	public Pair(L left, R right) {
		this.left = left;
		this.right = right;
	}

	public static <L,R> Pair<L,R> of(L left, R right) {
		return new Pair<L,R>(left, right);
	}
      
	public String toString() {
		return left + "\t" + right;
	}
	
}