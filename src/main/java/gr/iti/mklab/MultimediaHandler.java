package gr.iti.mklab;

import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.visual.aggregation.VladAggregatorMultipleVocabularies;
import gr.iti.mklab.visual.datastructures.IVFPQ;
import gr.iti.mklab.visual.datastructures.Linear;
import gr.iti.mklab.visual.datastructures.PQ;
import gr.iti.mklab.visual.dimreduction.PCA;
import gr.iti.mklab.visual.extraction.AbstractFeatureExtractor;
import gr.iti.mklab.visual.extraction.SURFExtractor;
import gr.iti.mklab.visual.utilities.Normalization;
import gr.iti.mklab.visual.vectorization.ImageVectorization;
import gr.iti.mklab.visual.vectorization.ImageVectorizationResult;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class MultimediaHandler {

	static String hostname = "160.40.50.207";
	static String dbname = "WWDC14";
	static String collName = "Statuses";
	
	public static void main(String[] args) throws Exception {
		
		MorphiaDAO<Item> dao = new MorphiaDAO<Item>(hostname ,dbname, Item.class);
		System.out.println(dao.count() + " items");

		Set<String> mediaUrls = new HashSet<String>();
		Iterator<Item> it = dao.iterator();
		while(it.hasNext()) {
			Item item = it.next();
			mediaUrls.addAll(item.getMediaItems().values());
		}
		
		System.out.println(mediaUrls.size() + " unique media urls!");
		//download(mediaUrls, "./wwdc_media/media", 24);
		
		index("./wwdc_media/media", "./wwdc_media/linear", "./wwdc_media/ivfpq", "/disk2_data/VisualIndex/learning_files");
	}
	
	public static void index(String directory, String linearIndexFolder, String ivfpqIndexFolder, String learningFolder) throws Exception {
		
		File dir = new File(directory);
		if(!dir.isDirectory())
			return;
		
		if(!learningFolder.endsWith("/"))
			learningFolder += "/";
		
		// parameters
		int maxNumPixels = 768 * 512;
		int[] numCentroids = { 128, 128, 128, 128 };
				
		int initialLength = numCentroids.length * numCentroids[0] * AbstractFeatureExtractor.SURFLength;
				
		int targetLengthMax = 1024;
		int targetLength = 1024;
				
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
		ImageVectorization.setVladAggregator(new VladAggregatorMultipleVocabularies(codebookFiles,
						numCentroids, AbstractFeatureExtractor.SURFLength));

		if (targetLengthMax < initialLength) {
			PCA pca = new PCA(targetLengthMax, 1, initialLength, true);
			pca.loadPCAFromFile(pcaFile);
			ImageVectorization.setPcaProjector(pca);
		}

		Linear linear = new Linear(targetLengthMax, maximumNumVectors, false, linearIndexFolder, true, true, 0);
		IVFPQ ivfpq_1 = new IVFPQ(targetLength, maximumNumVectors, false, ivfpqIndexFolder, m, k_c, PQ.TransformationType.RandomPermutation, numCoarseCentroids, true, 0);
		ivfpq_1.loadCoarseQuantizer(coarseQuantizerFile);
		ivfpq_1.loadProductQuantizer(productQuantizerFile);
		ivfpq_1.setW(128); // how many (out of 8192) lists should be visited during search.
		
		for(String id : dir.list()) {
			System.out.println(id);
			
			ImageVectorization imvec = new ImageVectorization(dir.toString()+"/", id, targetLengthMax, maxNumPixels);
			
			ImageVectorizationResult imvr = imvec.call();
			double[] vector = imvr.getImageVector();
			
			// the full vector is indexed in the disk-based index
			linear.indexVector(id, vector);
			
			// the vector is truncated to the correct dimension and renormalized before sending to the ram-based index
			double[] newVector = Arrays.copyOf(vector, targetLength);
			if (newVector.length < vector.length) {
				Normalization.normalizeL2(newVector);
			}
			ivfpq_1.indexVector(id, newVector);
			
		}
		
	}
	
}
