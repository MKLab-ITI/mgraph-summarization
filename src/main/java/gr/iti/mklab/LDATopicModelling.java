package gr.iti.mklab;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.IOUtils;

import gr.iti.mklab.models.Topic;
import gr.iti.mklab.topicmodelling.LDA;

import cc.mallet.types.InstanceList;

public class LDATopicModelling {

	public static int numTopics = 200;
	
	public static String timePooledDocs = "./topics/time_pooled_docs_unique.csv";
	public static String authorPooledDocs = "./topics/author_pooled__docs_unique.csv";
	public static String hashtagPooledDocs = "./topics/hashtag_pooled_docs_unique.csv";
	
	public static String timePooledModelFile = "./topics/models/LDA_time_pooled_+"+numTopics+"_unique.model";
	public static String authorPooledModelFile = "./topics/models/LDA_author_pooled_+"+numTopics+"_unique.model";
	public static String hashtagPooledModelFile = "./topics//models/LDA_hashtag_pooled_+" + numTopics + "_unique.model";
	
	public static void main(String[] args) throws Exception {
		//trainAll();

		String singleDocsData = "./topics/docs_unique.csv";
		InstanceList instancesOriginal = LDA.getInstances(singleDocsData);
		
		
		LDA timePooledmodel = new LDA(timePooledModelFile);
		LDA authorPooledmodel = new LDA(authorPooledModelFile);
		LDA hashtagPooledmodel = new LDA(hashtagPooledModelFile);
	
		double[] timeProbs = timePooledmodel.evaluate(instancesOriginal);
		double[] authorProbs = authorPooledmodel.evaluate(instancesOriginal);
		double[] hashtagProbs = hashtagPooledmodel.evaluate(instancesOriginal);
		
		int[] lengths = LDA.getDocumentsLengths(instancesOriginal);
		
		double timePerp = LDA.getPerplexity(timeProbs, lengths);
		double authorPerp = LDA.getPerplexity(authorProbs, lengths);
		double hashtagPerp = LDA.getPerplexity(hashtagProbs, lengths);
		
		System.out.println("Time:    " + timePerp);
		System.out.println("Author:  " + authorPerp);
		System.out.println("Hashtag: " + hashtagPerp);
	}
	
	public static void estimateNumberOfTopics(String filename) throws IOException {
		FileOutputStream output = new FileOutputStream(filename);
		Map<Integer, Double> evaluations = new HashMap<Integer, Double>();
		for(int K = 10; K<510; K = K + 20) {
			String pooledDocsData = "topics/time_docs.csv";
			String singleDocsData = "topics/docs.csv";
			
			try {
				InstanceList instancesTrain = LDA.getInstances(pooledDocsData);
				InstanceList instancesEval = LDA.getInstances(singleDocsData);
				
				int[] lengths = LDA.getDocumentsLengths(instancesEval);
				
				LDA model = new LDA(K);
				model.train(instancesTrain);

				double[] probs = model.evaluate(instancesEval);
				double P = LDA.getPerplexity(probs, lengths);
				
				IOUtils.write(K + "\t" + P + "\n", output);
				output.flush();
				
				evaluations.put(K, P);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		for(Entry<Integer, Double> e : evaluations.entrySet()) {
			System.out.println(e);
		}
	}
	
	public static void estimateNumberOfTopics_2(String filename) throws IOException {
		FileOutputStream output = new FileOutputStream(filename);
		Map<Integer, Double> evaluations = new HashMap<Integer, Double>();
		for(int K = 10; K<510; K = K + 20) {
			String pooledDocsData = "topics/time_docs.csv";
			
			try {
				InstanceList instancesTrain = LDA.getInstances(pooledDocsData);
	
				LDA model = new LDA(K);
				model.train(instancesTrain);

				Map<Integer, Topic> topics = new HashMap<Integer, Topic>(); //Topic.getTopics(model, 500);
				double s = Topic.getTopicsAvgSimilarity(topics);
				
				IOUtils.write(K + "\t" + s + "\n", output);
				output.flush();
				
				evaluations.put(K, s);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		for(Entry<Integer, Double> e : evaluations.entrySet()) {
			System.out.println(e);
		}
	}
	
}
