package gr.iti.mklab.summarization.etree;
import gr.iti.mklab.models.ClusterVector;

import java.util.ArrayList;

public class Node {

    private ArrayList<Node> children;
    private ClusterVector cv;
    
    // Constructor
    public Node() {
        cv = new ClusterVector();
        children = new ArrayList<Node>();
    }
    
    public Node(ClusterVector cv) {
    	this.cv = cv;
        children = new ArrayList<Node>();
    }

    public ArrayList<Node> getChildren() {
        return children;
    }

    public boolean isLeaf() {
    	return children.isEmpty();
    }
    
    public void addChild(Node child) {
        children.add(child);
        cv.merge(child.cv);
    }
    
    public void removeChild(Node child) {
        children.remove(child);
        cv.subtrack(child.cv);
    }
    
    double similarity(Node other) {
    	return this.cv.cosine(other.cv);
    }
}