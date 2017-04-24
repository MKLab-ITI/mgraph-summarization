package gr.iti.mklab.index;

import edu.uci.ics.jung.graph.Graph;
import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.WeightedEdge;
import gr.iti.mklab.utils.GraphUtils;
import gr.iti.mklab.visual.aggregation.VladAggregatorMultipleVocabularies;
import gr.iti.mklab.visual.datastructures.IVFPQ;
import gr.iti.mklab.visual.datastructures.Linear;
import gr.iti.mklab.visual.datastructures.PQ;
import gr.iti.mklab.visual.dimreduction.PCA;
import gr.iti.mklab.visual.extraction.AbstractFeatureExtractor;
import gr.iti.mklab.visual.extraction.SURFExtractor;
import gr.iti.mklab.visual.utilities.Answer;
import gr.iti.mklab.visual.utilities.Normalization;
import gr.iti.mklab.visual.utilities.Result;
import gr.iti.mklab.visual.vectorization.ImageVectorization;
import gr.iti.mklab.visual.vectorization.ImageVectorizationResult;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.imageio.ImageIO;

import org.apache.commons.io.IOUtils;
import org.mongodb.morphia.query.Query;

public class VisualIndex {

	// parameters
	private static int[] numCentroids = { 128, 128, 128, 128 };
	int initialLength = numCentroids.length * numCentroids[0] * AbstractFeatureExtractor.SURFLength;
	
	private static int maxNumPixels = 768 * 512;
	private static int targetLengthMax = 1024;
	private static int targetLength = 1024;
	
	private Linear linearIndex;
	private IVFPQ ivfpqIndex;
	
	private String[] codebookFiles;
	private String pcaFile;

	private int maximumNumVectors = 40000;
	private int m = 128, k_c = 256, numCoarseCentroids = 8192;

	public VisualIndex(String learningFolder, String indexFolder) throws Exception {
						
		if(!learningFolder.endsWith("/")) {
			learningFolder = learningFolder + "/";
		}
		
		codebookFiles = new String[4];
		codebookFiles[0] = learningFolder + "surf_l2_128c_0.csv";
		codebookFiles[1] = learningFolder + "surf_l2_128c_1.csv"; 
		codebookFiles[2] = learningFolder + "surf_l2_128c_2.csv";
		codebookFiles[3] = learningFolder + "surf_l2_128c_3.csv";
						
		pcaFile = learningFolder + "pca_surf_4x128_32768to1024.txt";

		String coarseQuantizerFile = learningFolder + "qcoarse_1024d_8192k.csv";
		String productQuantizerFile = learningFolder + "pq_1024_128x8_rp_ivf_8192k.csv";

		String linearIndexFolder = indexFolder + "/linear"; 
		String ivfpqIndexFolder = indexFolder + "/ivfpq"; 
		
		this.linearIndex = new Linear(targetLengthMax, maximumNumVectors, false, linearIndexFolder, true, true, 0);
		
		this.ivfpqIndex = new IVFPQ(targetLength, maximumNumVectors, false, ivfpqIndexFolder, m, k_c, PQ.TransformationType.RandomPermutation, numCoarseCentroids, true, 0);
		ivfpqIndex.loadCoarseQuantizer(coarseQuantizerFile);
		ivfpqIndex.loadProductQuantizer(productQuantizerFile);
		ivfpqIndex.setW(256); // how many (out of 8192) lists should be visited during search.
		
	}
	
	public void initilizeVectorization() throws Exception {
		ImageVectorization.setFeatureExtractor(new SURFExtractor());
		ImageVectorization.setVladAggregator(new VladAggregatorMultipleVocabularies(codebookFiles, numCentroids, AbstractFeatureExtractor.SURFLength));

		if (targetLengthMax < initialLength) {
			PCA pca = new PCA(targetLengthMax, 1, initialLength, true);
			pca.loadPCAFromFile(pcaFile);
			ImageVectorization.setPcaProjector(pca);
		}
	}
	
	public void index(String imagesDir) throws IOException {
		File dir = new File(imagesDir);
		
		if(!dir.isDirectory()) {
			return;
		}
		
		for(File imageFile : dir.listFiles()) {
			String id = imageFile.getName();
			try {
				if(linearIndex.isIndexed(id))
					continue;
				
				System.out.println("Index: " + id);
				
				BufferedImage image = ImageIO.read(imageFile);
				
				ImageVectorization imvec = new ImageVectorization(id, image, targetLengthMax, maxNumPixels);
				ImageVectorizationResult imvr = imvec.call();
				double[] vector = imvr.getImageVector();
				
				// INDEX 
				linearIndex.indexVector(id, vector);
				ivfpqIndex.indexVector(id, vector);
				
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}
			
		}
	}
	
	public void index(List<Item> items) throws IOException {
		for(Item item : items) {
			
			Map<String, String> mediaItems = item.getMediaItems();

			for(Entry<String, String> mi : mediaItems.entrySet()) {
				
				String mediaId = mi.getKey();
				String mediaUrl = mi.getValue();
			
				try {
					URL url = new URL(mediaUrl);
					byte[] content = IOUtils.toByteArray(url);
					
					BufferedImage img = ImageIO.read(new ByteArrayInputStream(content));
					
					ImageVectorization imvec = new ImageVectorization(mediaId, img , targetLengthMax, maxNumPixels);
					
					ImageVectorizationResult imvr = imvec.call();
					double[] vector = imvr.getImageVector();
					
					// the full vector is indexed in the disk-based index
					linearIndex.indexVector(mediaId, vector);
					
					// the vector is truncated to the correct dimension and renormalized before sending to the ram-based index
					double[] newVector = Arrays.copyOf(vector, targetLength);
					if (newVector.length < vector.length) {
						Normalization.normalizeL2(newVector);
					}
					ivfpqIndex.indexVector(mediaId, newVector);
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public Map<String, Double> search(String mediaId, int k) {
		Map<String, Double> response = new HashMap<String, Double>();
		try {
			double[] queryVector = getVector(mediaId);
			if(queryVector == null) {
				return response;
			}
			else {
				return search(queryVector, k);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return response;
	}

	public Map<String, Double> search(double[] queryVector, int k) {
		Map<String, Double> response = new HashMap<String, Double>();
		try {
			Answer answer = ivfpqIndex.computeNearestNeighbors(k, queryVector);
			if(answer != null) {
				Result[] results = answer.getResults();
				if(results != null) {
					for(Result result : results) {
						String nearestId = result.getId();
						double distance = result.getDistance();
						response.put(nearestId, distance);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return response;
	}
	
	public double[] getVector(String mediaId) {
		int iid = linearIndex.getInternalId(mediaId);
		if(iid < 0) {
			return null;
		}
		
		double[] vector = linearIndex.getVector(iid);
		return vector;
	}
	
	public boolean isIndexed(String mediaId) {
		return linearIndex.isIndexed(mediaId);
	}
	
	private static String dataset = "Sundance2013";
	
	public static void main(String[] args) throws Exception {
		
//		Graph<String, WeightedEdge> visualGraph = GraphUtils.loadGraph("/disk1_data/Datasets/" + dataset + "/graphs/visual_items_graph.graphml");
//		System.out.println("Visual Graph: #Vertices " + visualGraph.getVertexCount() + ", #edges: " + visualGraph.getEdgeCount());
//		visualGraph = GraphUtils.filter(visualGraph, 0.3);
//		System.out.println("Visual Graph: #Vertices " + visualGraph.getVertexCount() + ", #edges: " + visualGraph.getEdgeCount());
//		GraphUtils.saveGraph(visualGraph, "/disk1_data/Datasets/" + dataset + "/graphs/visual_items_graph.graphml");
		
		/* 
		VisualIndex vIndex = new VisualIndex("/disk2_data/VisualIndex/learning_files", "/disk1_data/Datasets/"+dataset);
		
		MorphiaDAO<Item> dao = new MorphiaDAO<Item>("160.40.50.207", dataset, Item.class);
		Query<Item> query = dao.getQuery().filter("original =", Boolean.TRUE);
		System.out.println(dao.count() + " items");

		int items = 0;
		Map<String, List<String>> mediaItems = new HashMap<String, List<String>>();
		Iterator<Item> it = dao.iterator(query);
		while(it.hasNext()) {
			Item item = it.next();
			if(item.getMediaItems() != null) {
				for(String mId : item.getMediaItems().keySet()) {
					List<String> itemsList = mediaItems.get(mId);
					if(itemsList == null) {
						itemsList = new ArrayList<String>();
						mediaItems.put(mId, itemsList);
					}
					items++;
					itemsList.add(item.getId());
				}
			}
		}
		System.out.println(mediaItems.size() + " unique media items in " + items + " items");
		
		Graph<String, WeightedEdge> visualGraph = GraphUtils.generateVisualItemGraph(mediaItems, 0.2, vIndex);
		GraphUtils.saveGraph(visualGraph, "/disk1_data/Datasets/" + dataset + "/graphs/visual_items_graph.graphml");
		*/
		//String imagesDir = "/disk1_data/Datasets/Events2012/images";
		//vIndexer.initilizeVectorization();
		//vIndexer.index(imagesDir);
		
	}

}
