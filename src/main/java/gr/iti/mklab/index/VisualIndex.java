package gr.iti.mklab.index;

import gr.iti.mklab.models.Item;
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
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.imageio.ImageIO;

import org.apache.commons.io.IOUtils;

public class VisualIndex {

	// parameters
	private static int[] numCentroids = { 128, 128, 128, 128 };
	int initialLength = numCentroids.length * numCentroids[0] * AbstractFeatureExtractor.SURFLength;
	
	private static int maxNumPixels = 768 * 512;
	private static int targetLengthMax = 1024;
	private static int targetLength = 1024;
	
	private Linear linearIndex;
	private IVFPQ ivfpqIndex;


	public VisualIndex(String learningFolder, String indexFolder) throws Exception {
						
		int maximumNumVectors = 20000;

		int m = 128, k_c = 256, numCoarseCentroids = 8192;

		String[] codebookFiles = { 
			learningFolder + "surf_l2_128c_0.csv",
			learningFolder + "surf_l2_128c_1.csv", 
			learningFolder + "surf_l2_128c_2.csv",
			learningFolder + "surf_l2_128c_3.csv" 
		};
						
		String pcaFile = learningFolder + "pca_surf_4x128_32768to1024.txt";

		String coarseQuantizerFile = learningFolder + "qcoarse_1024d_8192k.csv";
		String productQuantizerFile = learningFolder + "pq_1024_128x8_rp_ivf_8192k.csv";

		ImageVectorization.setFeatureExtractor(new SURFExtractor());
		ImageVectorization.setVladAggregator(new VladAggregatorMultipleVocabularies(codebookFiles, numCentroids, AbstractFeatureExtractor.SURFLength));

		if (targetLengthMax < initialLength) {
			PCA pca = new PCA(targetLengthMax, 1, initialLength, true);
			pca.loadPCAFromFile(pcaFile);
			ImageVectorization.setPcaProjector(pca);
		}

		String linearIndexFolder = indexFolder + "/linear"; 
		String ivfpqIndexFolder = indexFolder + "/ivfpq"; 
		
		this.linearIndex = new Linear(targetLengthMax, maximumNumVectors, false, linearIndexFolder, true, true, 0);
		
		this.ivfpqIndex = new IVFPQ(targetLength, maximumNumVectors, false, ivfpqIndexFolder, m, k_c, PQ.TransformationType.RandomPermutation, numCoarseCentroids, true, 0);
		ivfpqIndex.loadCoarseQuantizer(coarseQuantizerFile);
		ivfpqIndex.loadProductQuantizer(productQuantizerFile);
		ivfpqIndex.setW(128); // how many (out of 8192) lists should be visited during search.
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
			Answer answer = ivfpqIndex.computeNearestNeighbors(k, mediaId);
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
	
	double[] getVector(String mediaId) {
		int iid = linearIndex.getInternalId(mediaId);
		double[] vector = linearIndex.getVector(iid);
		
		return vector;
	}
	
	public static void main(String[] args) {


	}

}
