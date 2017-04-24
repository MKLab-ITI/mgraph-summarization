package gr.iti.mklab.clustering;

import edu.uci.ics.jung.graph.Graph;
import gr.iti.mklab.clustering.scan.Community;
import gr.iti.mklab.clustering.scan.ScanCommunityDetector;
import gr.iti.mklab.clustering.scan.ScanCommunityStructure;
import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.models.Cluster;
import gr.iti.mklab.models.ClusterVector;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.MediaItem;
import gr.iti.mklab.models.Pair;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.models.WeightedEdge;
import gr.iti.mklab.utils.GraphUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;


public class GraphClusterer {

	public static double scanEpsilon = 0.6;
	public static int scanMu = 4;
	
    public static List<ClusterVector> cluster(String filename, Map<String, Vector> vectors, Map<String, Item> items) {
		try {
			Graph<String, WeightedEdge> graph = GraphUtils.loadGraph(filename);
			return cluster(graph, vectors, items);
		} catch (IOException e) {
			e.printStackTrace();
			return new ArrayList<ClusterVector>();
		}
	}	
    
	public static List<ClusterVector> cluster(Graph<String, WeightedEdge> graph, Map<String, Vector> vectors, Map<String, Item> items) {
			
		ScanCommunityDetector<String, WeightedEdge> detector = new ScanCommunityDetector<String, WeightedEdge>(scanEpsilon, scanMu);
		ScanCommunityStructure<String, WeightedEdge> structure = detector.getCommunityStructure(graph);
		
		int numCommunities = structure.getNumberOfCommunities();
		System.out.println("#communities " + numCommunities);
		System.out.println("#members " + structure.getNumberOfMembers());
		System.out.println("#hubs " + structure.getHubs().size());
		System.out.println("#outliers " + structure.getOutliers().size());
		
		List<ClusterVector> clusters = new ArrayList<ClusterVector>();
		
        for(int index = 0; index<numCommunities; index++) {
        	Community<String, WeightedEdge> community = structure.getCommunity(index);
        	if(community != null) {
        		ClusterVector clusterVector = new ClusterVector();
        		clusters.add(clusterVector);
        		for(String member : community.getMembers()) {
        			Vector vector = vectors.get(member);
        			if(vector == null) {
        				continue;
        			}
                        
        			Item item = items.get(member);    
        			if(item == null) {
        				continue;
        			}
                        
        			long publicationTime = item.getPublicationTime();
        			clusterVector.addVector(member, vector, publicationTime);
        		}
        	}
        }

        /* */
        for(String hub : structure.getHubs()) {
            Collection<String> hubNeighbours = graph.getNeighbors(hub);
            Map<ClusterVector, Integer> temp = new HashMap<ClusterVector, Integer>();
            for(String neighbor : hubNeighbours) {
            	for(ClusterVector cluster : clusters) {
            		if(cluster.getFocusSet().contains(neighbor)) {
            			Integer count = temp.get(cluster);
            			if(count == null) {
            				count = 0;
            			}
            			temp.put(cluster, ++count);
            		}
            	}
            }
            
            ClusterVector cluster = null;
            Integer maxCount = 0;
            for(Entry<ClusterVector, Integer> e : temp.entrySet()) {
            	if(maxCount < e.getValue() && e.getValue()>1) {
            		cluster = e.getKey();
            	}
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
	
	public static List<Cluster> clusterGraph(Graph<String, WeightedEdge> graph, boolean singleItemClusters) {
		Set<String> clustered = new HashSet<String>();
		List<Cluster> clusters = new ArrayList<Cluster>();
		
		ScanCommunityDetector<String, WeightedEdge> detector = new ScanCommunityDetector<String, WeightedEdge>(scanEpsilon, scanMu);
		ScanCommunityStructure<String, WeightedEdge> structure = detector.getCommunityStructure(graph);
		
		int numCommunities = structure.getNumberOfCommunities(); 
		System.out.println("#communities " + numCommunities);
		System.out.println("#members " + structure.getNumberOfMembers());
		System.out.println("#hubs " + structure.getHubs().size());
		System.out.println("#outliers " + structure.getOutliers().size());

		Integer index = 0;
		for(index = 0; index <= numCommunities; index++) {
			if(index%1000==0) {
				System.out.print(".");
				if(index>0 && index%10000 == 0) {
					System.out.println(" (" + index + ")");
				}
			}
				
			Community<String, WeightedEdge> community = structure.getCommunity(index);
			if(community != null) {
				List<String> members = community.getMembers();
				if(members != null && !members.isEmpty()) {
					clustered.addAll(members);
            		  
					Cluster cluster = new Cluster(index.toString());
					cluster.setMembers(members);
					cluster.setType("community");
					if(members.size() > 1) {
						Graph<String, WeightedEdge> clusterGraph = GraphUtils.filter(graph, members);
						double avgWeight = GraphUtils.getAvgWeight(clusterGraph);
          				double density = GraphUtils.getGraphDensity(clusterGraph);
          				Pair<Double, Double> minMax = GraphUtils.getMinMaxWeight(clusterGraph);
          				
          				cluster.setAvgWeight(avgWeight);
          				cluster.setMinWeight(minMax.left);
          				cluster.setMaxWeight(minMax.right);
          				cluster.setDensity(density);
					}
					clusters.add(cluster);
				}
			}
        }
		
		if(singleItemClusters) {
			if(structure.getHubs() != null) {
				for(String hub : structure.getHubs()) {
					clustered.add(hub);
					++index;
					if(index%1000==0) {
						System.out.print(".");
						if(index>0 && index%10000 == 0) {
							System.out.println(" (" + index + ")");
						}
					}
					
	        		Cluster cluster = new Cluster(index.toString());
	          	  	cluster.addMember(hub);
	          	  	cluster.setType("hub");
	          	  	
	          	  	Set<Integer> neighborCommunities = new HashSet<Integer>();
	          	  	Collection<String> neighbors = graph.getNeighbors(hub);
	          	  	if(neighbors != null) {
	          	  		for(String vertex : neighbors) {
	          	  			int cId = structure.getCommunityIndex(vertex);
	          	  			if(cId != -1) {
	          	  				neighborCommunities.add(cId);
	          	  			}
	          	  		}
	          	  		cluster.setNeighborClusters(neighborCommunities);
	          	  	}
	          	  	clusters.add(cluster);

	        	}
	        }
	        	
	        if(structure.getOutliers() != null) {
	        	for(String outlier : structure.getOutliers()) {
	        		clustered.add(outlier);
	        		
	        		++index;
	    			if(index%1000==0) {
	    				System.out.print(".");
	    				if(index>0 && index%10000 == 0) {
	    					System.out.println(" (" + index + ")");
	    				}
	    			}
	    			
	        		Cluster cluster = new Cluster(index.toString());
	          	  	cluster.addMember(outlier);
	          	  	cluster.setType("outlier");
	          	  	
	          	  	Set<Integer> neighborCommunities = new HashSet<Integer>();
	          	  	Collection<String> neighbors = graph.getNeighbors(outlier);
	          	  	if(neighbors != null) {
	          	  		for(String vertex : neighbors) {
	          	  			int cId = structure.getCommunityIndex(vertex);
	          	  			if(cId != -1) {
	          	  				neighborCommunities.add(cId);
	          	  			}
	          	  		}
	          	  		cluster.setNeighborClusters(neighborCommunities);
	          	  	}
	          	  	clusters.add(cluster);
	        	}
	        }
	        	
	        Set<String> unclusteredVertices = new HashSet<String>();
	        unclusteredVertices.addAll(graph.getVertices());
	        unclusteredVertices.removeAll(clustered);			
	    	for(String unclusteredVertex : unclusteredVertices) {
	    		++index;
				if(index%1000==0) {
					System.out.print(".");
					if(index>0 && index%10000 == 0) {
						System.out.println(" (" + index + ")");
					}
				}
				
	    		Cluster cluster = new Cluster(index.toString());
	    		cluster.addMember(unclusteredVertex);
          	  	cluster.setType("unclustered");
          	  	
          	  	Set<Integer> neighborCommunities = new HashSet<Integer>();
          	  	Collection<String> neighbors = graph.getNeighbors(unclusteredVertex);
          	  	if(neighbors != null) {
          	  		for(String vertex : neighbors) {
          	  			int cId = structure.getCommunityIndex(vertex);
          	  			if(cId != -1) {
          	  				neighborCommunities.add(cId);
          	  			}
          	  		}
          	  		cluster.setNeighborClusters(neighborCommunities);
          	  	}
          	  	clusters.add(cluster);
	    	}
		}
		 
		return clusters;
	}
	
    public static Collection<Collection<String>> cluster(Graph<String, WeightedEdge> graph, boolean singleItemClusters) {
    	
    	Set<String> clustered = new HashSet<String>();
    	Collection<Collection<String>> clusters = new ArrayList<Collection<String>>();
    	 
    	ScanCommunityDetector<String, WeightedEdge> detector = new ScanCommunityDetector<String, WeightedEdge>(scanEpsilon, scanMu);
		ScanCommunityStructure<String, WeightedEdge> structure = detector.getCommunityStructure(graph);
		
		int numCommunities = structure.getNumberOfCommunities();
        
		System.out.println("#communities " + numCommunities);
		System.out.println("#members " + structure.getNumberOfMembers());
		System.out.println("#hubs " + structure.getHubs().size());
		System.out.println("#outliers " + structure.getOutliers().size());
		
		for(Integer i=0; i<=numCommunities; i++) {
        	Community<String, WeightedEdge> community = structure.getCommunity(i);
              if(community != null) {
            	  Set<String> cluster = new HashSet<String>();
            	  cluster.addAll(community.getMembers());
            	  clustered.addAll(cluster);
            	  clusters.add(cluster);
              }
        }
        
        if(singleItemClusters) {
        	List<String> singleItems = new ArrayList<String>();
        	singleItems.addAll(structure.getHubs());
        	singleItems.addAll(structure.getOutliers());
        	for(String item : singleItems) {
        		Set<String> cluster = new HashSet<String>();
          	  	cluster.add(item);
          	  	clustered.addAll(cluster);
          	  	clusters.add(cluster);
        	}
        	
        	Set<String> unclustered = new HashSet<String>();
    		unclustered.addAll(graph.getVertices());
    		unclustered.removeAll(clustered);			
    		for(String item : unclustered) {
    			Set<String> cluster = new HashSet<String>();
          	  	cluster.add(item);
          	  	clusters.add(cluster);
    		}
        }

		return clusters;
	}	

    public static String dataset = "WWDC14";
    
    public static void main(String...args) throws Exception {
		
    	/*
    	MorphiaDAO<Item> dao = new MorphiaDAO<Item>("xxx.xxx.xxx.xxx" , "WWDC14", Item.class);
		System.out.println(dao.count() + " items");
		
		Map<String, Item> itemsMap = new HashMap<String, Item>();
		Iterator<Item> it = dao.iterator();
		while(it.hasNext()) {
			Item item = it.next();
			itemsMap.put(item.getId(), item);
		}
		Map<String, Vector> vectorsMap = Vocabulary.createVocabulary(itemsMap.values(), 2);
		*/
		
		Graph<String, WeightedEdge> graph = GraphUtils.loadGraph("/disk1_data/Datasets/" + dataset + "/graphs/visual_graph.graphml");
		System.out.println(graph.getVertexCount() + " vertices");
		System.out.println(graph.getEdgeCount() + " edges");
		System.out.println("Density: " + (2.*graph.getEdgeCount()/((double)graph.getVertexCount()*(graph.getVertexCount()-1))));
		
		graph = GraphUtils.filter(graph, 0.5);
		GraphUtils.saveGraph(graph, "/disk1_data/Datasets/" + dataset + "/graphs/visual_graph_pruned.graphml");
		//Graph<String, WeightedEdge> graph = GraphUtils.loadGraph("/disk1_data/Datasets/" + dataset + "/graphs/visual_graph_pruned.graphml");
		//System.out.println(graph.getVertexCount() + " vertices");
		//System.out.println(graph.getEdgeCount() + " edges");
		//System.out.println("Density: " + (2.*graph.getEdgeCount()/((double)graph.getVertexCount()*(graph.getVertexCount()-1))));
		
		MorphiaDAO<MediaItem> mDao = new MorphiaDAO<MediaItem>("xxx.xxx.xxx.xxx" , dataset, MediaItem.class);
		Collection<Collection<String>> clusters = GraphClusterer.cluster(graph, true);
		System.out.println(clusters.size() + " clusters");
		for(Collection<String> cluster : clusters) {
			
			double avgWeight = .0, minWeight = Double.MAX_VALUE, maxWeight = .0, density = 1;
			if(cluster.size() > 1) {
				Graph<String, WeightedEdge> clusterGraph = GraphUtils.filter(graph, cluster);
				avgWeight = GraphUtils.getAvgWeight(clusterGraph);
				density = GraphUtils.getGraphDensity(clusterGraph);
				
				Pair<Double, Double> minMax = GraphUtils.getMinMaxWeight(clusterGraph);
				minWeight = minMax.left;
				maxWeight = minMax.right;
			}
					
			String mId = (String) cluster.toArray()[0];
			MediaItem mItem = mDao.get(mId);
			if(mItem != null) {
				DBObject obj = new BasicDBObject();
				obj.put("_id", mItem.getId());
				obj.put("title", mItem.getTitle());
				obj.put("concept", mItem.getConcept());
				obj.put("conceptScore", mItem.getConceptScore());
				obj.put("width", mItem.getWidth());
				obj.put("height", mItem.getHeight());
				obj.put("members", cluster);
				obj.put("size", cluster.size());
				obj.put("relevance", .0);
				obj.put("numOfJudgements", 0);
				obj.put("judgements", new HashSet<Integer>());
			
				if(avgWeight > 0) {
					obj.put("avgWeight", avgWeight);
					obj.put("minWeight", minWeight);
					obj.put("maxWeight", maxWeight);
					obj.put("density", density);
				}
				
				mDao.save(obj, "Cliques");
			}
		}
		
	}
}
