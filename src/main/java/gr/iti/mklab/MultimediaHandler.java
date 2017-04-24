package gr.iti.mklab;

import edu.berkeley.nlp.util.IOUtils;
import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.Pair;
import gr.iti.mklab.visual.aggregation.VladAggregatorMultipleVocabularies;
import gr.iti.mklab.visual.datastructures.IVFPQ;
import gr.iti.mklab.visual.datastructures.Linear;
import gr.iti.mklab.visual.datastructures.PQ;
import gr.iti.mklab.visual.dimreduction.PCA;
import gr.iti.mklab.visual.extraction.AbstractFeatureExtractor;
import gr.iti.mklab.visual.extraction.SURFExtractor;
import gr.iti.mklab.visual.vectorization.ImageVectorization;
import gr.iti.mklab.visual.vectorization.ImageVectorizationResult;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class MultimediaHandler {

	private static String hostname = "160.40.50.207";
	
	private static String dataset = "Sundance2013";
	
	public static void main(String[] args) throws Exception {
		
		//download("/disk1_data/Datasets/" + dataset + "/media/", 48);
		index("/disk1_data/Datasets/" + dataset + "/media/", 
				"/disk1_data/Datasets/" + dataset + "/linear", 
				"/disk1_data/Datasets/" + dataset + "/ivfpq", 
				"/disk2_data/VisualIndex/learning_files");
	}
	
	public static void download(String directory, int poolSize) throws Exception {
		
		MorphiaDAO<Item> dao = new MorphiaDAO<Item>(hostname, dataset, Item.class);
		System.out.println(dao.count() + " items");

		Map<String, String> media = new HashMap<String, String>();
		Iterator<Item> it = dao.iterator();
		while(it.hasNext()) {
			Item item = it.next();
			media.putAll(item.getMediaItems());
		}
		System.out.println(media.size() + " unique media urls!");
		
		final File outputDir = new File(directory);
		if(!outputDir.exists()) {
			outputDir.mkdirs();
		}
		
		ExecutorService pool = Executors.newFixedThreadPool(poolSize);
		Queue<Future<Pair<String, byte[]>>> futures = new LinkedBlockingQueue<Future<Pair<String, byte[]>>>();
		for(final Entry<String, String> entry : media.entrySet()) {
			File mediaFile = new File(outputDir, entry.getKey());
			if(mediaFile.exists()) {
				continue;
			}
			
			Future<Pair<String, byte[]>> future = pool.submit(new Callable<Pair<String, byte[]>>() {
				@Override
				public Pair<String, byte[]> call() throws Exception {
					try {
						String mediaId = entry.getKey();
						URL url = new URL(entry.getValue());
						URLConnection conn = url.openConnection();
						conn.setConnectTimeout(5000);
						conn.setReadTimeout(5000);
						InputStream in = conn.getInputStream();
						//InputStream in = url.openStream();
						ByteArrayOutputStream out = new ByteArrayOutputStream();
						IOUtils.copy(in, out);	
						out.close();
						in.close();

						byte[] content = out.toByteArray();
						if(content == null || content.length == 0) {
							return null;
						}
						
						return Pair.of(mediaId, content);
					}
					catch(Exception e) {
						return null;
					}
					
				}
				
			});
			futures.add(future);
		}
		
		while(!futures.isEmpty()) {
			Future<Pair<String, byte[]>> future = futures.poll();
			if(future.isDone() || future.isCancelled()) {
				Pair<String, byte[]> pair;
				try {
					pair = future.get();
					if(pair != null) {
						String mediaId = pair.left;
						byte[] content = pair.right;
						if(content != null && content.length > 0) {
							File mediaFile = new File(outputDir, mediaId);
							FileOutputStream fos = new FileOutputStream(mediaFile);
							fos.write(content);
							fos.close();
						}
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				} catch (ExecutionException e) {
					e.printStackTrace();
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			else {
				futures.offer(future);
			}
		}
		
		System.out.println("Await for termination.");
		pool.awaitTermination(1, TimeUnit.SECONDS);
	}

	public static void index(String directory, String linearIndexFolder, String ivfpqIndexFolder, String learningFolder) throws Exception {
		
		ExecutorService threadPool = Executors.newFixedThreadPool(12);
		
		File dir = new File(directory);
		if(!dir.isDirectory())
			return;
		
		if(!learningFolder.endsWith("/")) {
			learningFolder += "/";
		}
		
		// parameters
		int maxNumPixels = 768 * 512;
		int[] numCentroids = { 128, 128, 128, 128 };
				
		int initialLength = numCentroids.length * numCentroids[0] * AbstractFeatureExtractor.SURFLength;
				
		int targetLengthMax = 1024;
		int targetLength = 1024;
				
		int maximumNumVectors = dir.listFiles().length + 1;

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

		final Linear linear = new Linear(targetLengthMax, maximumNumVectors, false, linearIndexFolder, true, true, 0);
		final IVFPQ ivfpq = new IVFPQ(targetLength, maximumNumVectors, false, ivfpqIndexFolder, m, k_c, PQ.TransformationType.RandomPermutation, numCoarseCentroids, true, 0);
		ivfpq.loadCoarseQuantizer(coarseQuantizerFile);
		ivfpq.loadProductQuantizer(productQuantizerFile);
		ivfpq.setW(128); // how many (out of 8192) lists should be visited during search.
		
		Queue<Future<ImageVectorizationResult>> vectorizationFutures = new LinkedBlockingQueue<Future<ImageVectorizationResult>>();
		for(String id : dir.list()) {
			try {
				
				if(linear.isIndexed(id)) {
					System.out.println(id + " exists!");
					continue;
				}
				
				ImageVectorization imvec = new ImageVectorization(dir.toString()+"/", id, targetLengthMax, maxNumPixels);
				Future<ImageVectorizationResult> future = threadPool.submit(imvec);
				
				vectorizationFutures.offer(future);
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}
		
		Queue<Future<Boolean>> indexFutures = new LinkedBlockingQueue<Future<Boolean>>();
		
		while(!vectorizationFutures.isEmpty()) {
			Future<ImageVectorizationResult> future = vectorizationFutures.poll();
			if(future.isDone() || future.isCancelled()) {
				try {
					final ImageVectorizationResult imvr = future.get();
					
					Future<Boolean> indexFuture = threadPool.submit(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							try {
								String id = imvr.getImageName();
								double[] vector = imvr.getImageVector();
								// the full vector is indexed in the disk-based index
								synchronized(linear) {
									linear.indexVector(id, vector);
								}		
								synchronized(ivfpq) {
									ivfpq.indexVector(id, vector);
								}
								return true;
							}
							catch(Exception e) {
								return false;
							}
						}
					});
					indexFutures.offer(indexFuture);
				}
				catch(Exception e) {
					e.printStackTrace();
				}
			}
			else {
				vectorizationFutures.offer(future);
			}
		}
		
		while(!indexFutures.isEmpty()) {
			Future<Boolean> indexFuture = indexFutures.poll();
			if(indexFuture.isDone() || indexFuture.isCancelled()) {
				// Do Nothing
			}
			else {
				indexFutures.offer(indexFuture);
			}
		}
		
		threadPool.awaitTermination(1, TimeUnit.DAYS);
		
	}
	
}
