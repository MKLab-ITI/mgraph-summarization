package gr.iti.mklab.models;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Entity(noClassnameStored=true)
public class Cluster implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4331825140703231289L;

	public Cluster() {
		
	}
	
	public Cluster(String id) {
		this.id = id;
	}
	
	@Id
	private String id;
	
	private List<String> members = new ArrayList<String>();
	
	private Integer size = 0;
	
	private Double avgWeight = .0;
	
	private Double minWeight = .0;
	
	private Double maxWeight = .0;
	
	private Double density = .0;
	
	// community, hub, outlier or unclustered
	private String type;
	
	private List<Integer> neighborClusters = new ArrayList<Integer>();
	
	public String getId() {
		return id;
	}
	
	public List<String> getMembers() {
		return members;
	}

	public void setMembers(Collection<String> members) {
		this.members.addAll(members);
		this.size = this.members.size();
	}	
	
	public void addMember(String member) {
		this.members.add(member);
		this.size = this.members.size();
	}	
	
	public boolean hasMember(String id) {
		if(members.contains(id))
			return true;
		
		return false;
	}
	
	public Integer getSize() {
		return size;
	}

	public void setSize(Integer size) {
		this.size = size;
	}

	public Double getAvgWeight() {
		return avgWeight;
	}

	public void setAvgWeight(Double avgWeight) {
		this.avgWeight = avgWeight;
	}

	public Double getMinWeight() {
		return minWeight;
	}

	public void setMinWeight(Double minWeight) {
		this.minWeight = minWeight;
	}

	public Double getDensity() {
		return density;
	}

	public void setDensity(Double density) {
		this.density = density;
	}

	public Double getMaxWeight() {
		return maxWeight;
	}

	public void setMaxWeight(Double maxWeight) {
		this.maxWeight = maxWeight;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public List<Integer> getNeighborClusters() {
		return neighborClusters;
	}

	public void setNeighborClusters(Collection<Integer> neighborClusters) {
		this.neighborClusters.addAll(neighborClusters);
	}
	
	public ClusterVector getClusterVector(Map<String, Item> itemsMap, Map<String, Vector> vectorsMap) {
		ClusterVector cv = new ClusterVector();
		for(String member : members) {
			Item item = itemsMap.get(member);
			Vector vector = vectorsMap.get(member);
			if(item != null && vector != null) {
				cv.addVector(member, vector, item.getPublicationTime());
			}
		}
		return cv;
	}

	public static void main(String...args) throws IOException, ClassNotFoundException {
		
		List<Cluster> clusters = new ArrayList<Cluster>();
		Cluster c1 = new Cluster("1");
		c1.setAvgWeight(0.5);
		c1.setDensity(0.2);
		c1.setMembers(Arrays.asList(new String[]{"x", "y", "z"}));
		clusters.add(c1);
		
		Cluster c2 = new Cluster("2");
		c2.setAvgWeight(0.65);
		c2.setDensity(0.43);
		c2.setMembers(Arrays.asList(new String[]{"a", "b", "c"}));
		clusters.add(c2);
		
		FileOutputStream fileOut = new FileOutputStream("/home/manosetro/Desktop/clusters.bin");
		ObjectOutputStream out = new ObjectOutputStream(fileOut);
		out.writeObject(clusters);
		out.close();
		fileOut.close();
		
		 FileInputStream fileIn = new FileInputStream("/home/manosetro/Desktop/clusters.bin");
         ObjectInputStream in = new ObjectInputStream(fileIn);
         @SuppressWarnings("unchecked")
		List<Cluster> e = (List<Cluster>) in.readObject();
         in.close();
         fileIn.close();
         
         for(Cluster c : e) {
        	 System.out.println(c.getId());
        	 System.out.println(c.density);
        	 System.out.println(c.getAvgWeight());
        	 System.out.println(c.getMembers());
         }
	}
}
