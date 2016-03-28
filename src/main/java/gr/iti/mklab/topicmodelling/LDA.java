package gr.iti.mklab.topicmodelling;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.io.IOUtils;

import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.Topic;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.utils.Sorter;
import gr.iti.mklab.vocabulary.Vocabulary;
import cc.mallet.pipe.CharSequence2TokenSequence;
import cc.mallet.pipe.CharSequenceLowercase;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.TokenSequence2FeatureSequence;
import cc.mallet.pipe.TokenSequenceRemoveStopwords;
import cc.mallet.pipe.iterator.CsvIterator;
import cc.mallet.topics.MarginalProbEstimator;
import cc.mallet.topics.ParallelTopicModel;
import cc.mallet.topics.TopicInferencer;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.IDSorter;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

public class LDA implements TopicDetector {
	
	private int numTopics;	
	private int wordPerTopic = 20;
	
	private static double minProbability = 0.2;
	
	private ParallelTopicModel _model = null;
	
	private Map<Integer, List<String>>  associations;
	private Map<Integer, Topic> topicsMap;
	
	public LDA(String modelFilename) {
		try {
			this.loadModel(modelFilename);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}

	public LDA(int K) {
		numTopics = K;
		// Create a model with 100 topics, alpha_t = 0.01, beta_w = 0.01
		double alpha_t = 0.01, beta_w = 0.01;
		_model = new ParallelTopicModel(numTopics, numTopics*alpha_t, beta_w);
	}
	
	public LDA(int K, int W) {
		this(K);
		wordPerTopic = W;
	}
	
	@Override
	public void run(Map<String, Vector> vectors, Map<String, Item> items)
			throws IOException {
		
		Collection<String> stopwords = Vocabulary.getStopwords();
		InstanceList instances = getInstances(items.values(), stopwords);
	
		this.associations = getTopicAssociations(instances);
		this.topicsMap = new HashMap<Integer, Topic>();
		
		Map<Integer, Map<String, Double>> topicWords = getTopicWords(wordPerTopic);
		for (Integer topicId : topicWords.keySet()) {
			Map<String, Double> words = topicWords.get(topicId);
			Topic topic = new Topic(topicId, words);
			topicsMap.put(topicId, topic);
		}
	
	}
	
	@Override
	public int getNumOfTopics() {
		return _model.getNumTopics();
	}
	
	@Override
	public List<Topic> getTopics() {
		List<Topic> topics = new ArrayList<Topic>();
		topics.addAll(topicsMap.values());
		return topics;
	}
	
	@Override
	public Map<Integer, Topic> getTopicsMap() {
		return topicsMap;
	}
	
	@Override
	public Map<Integer, List<String>> getTopicAssociations() {
		return associations;
	}
	
	@Override
	public void saveModel(String serializedModelFile) throws IOException {
		if(_model != null) {
			_model.write(new File(serializedModelFile));
		}
	}
	
	@Override
	public void loadModel(String serializedModelFile) throws Exception {
		_model = ParallelTopicModel.read(new File(serializedModelFile));
		numTopics = _model.getNumTopics();
	}
	
	public void train(InstanceList instances) throws IOException {
		_model.addInstances(instances);
		// Use four parallel samplers, which each look at one half the corpus and combine
		// statistics after every iteration.
		_model.setNumThreads(16);
		// Run estimator. For real applications, use 1000 to 2000 iterations
		_model.setNumIterations(1500);
		// Estimate model
		_model.estimate();
	}
	
	private Map<Integer, Map<String, Double>> getTopicWords(int w) {
		Map<Integer, Map<String, Double>> map = new HashMap<Integer, Map<String, Double>>();
		Alphabet topicAlphabet = _model.getAlphabet();
		
		ArrayList<TreeSet<IDSorter>> topicSortedWords = _model.getSortedWords();		
		
		for (int topic = 0; topic < _model.numTopics; topic++) {
			TreeSet<IDSorter> set = topicSortedWords.get(topic);		
			
			double sum = 0.0;
			for ( IDSorter s : set ) {
				sum += s.getWeight();
			}
			
			Map<String, Double> words = new HashMap<String, Double>();
			for(IDSorter idSorter : set) {
				double weight = idSorter.getWeight() / sum;
				String word = (String) topicAlphabet.lookupObject(idSorter.getID());
				words.put(word, weight);
			}
			
			// Sort by weight
			List<Entry<String, Double>> sortedWords = Sorter.sort(words);
			
			w = Math.min(w, sortedWords.size());
			Map<String, Double> temp = new HashMap<String, Double>();
			for(int i=0; i<w; i++) {
				Entry<String, Double> first = sortedWords.get(i);
				temp.put(first.getKey(), first.getValue());
			}
			
			map.put(topic, temp);
		}
		return map;
	}
	
	public Map<Integer, List<String>> getTopicAssociations(InstanceList instances) {
		Map<Integer, List<String>> associations = new TreeMap<Integer, List<String>>();
		
		if(_model == null)
			return associations;
		
		TopicInferencer inferencer = _model.getInferencer();
		for(Instance instance : instances) {
			String id = instance.getName().toString();
			
			double[] probabilities = inferencer.getSampledDistribution(instance, 100, 10, 10);
			int index = 0;
			double maxProb = 0;
			for(int i=0; i<probabilities.length; i++) {
				if(probabilities[i] > maxProb) {
					index = i;
					maxProb = probabilities[i];
				}
			}
			if(maxProb > minProbability) {
				List<String> list = associations.get(index);
				if(list == null) {
					list = new ArrayList<String>();
					associations.put(index, list);
				}
				list.add(id);
			}
		}
		return associations;
	}
	
	public Map<Integer, List<String>> getTopicAssociations(InstanceList instances, int iterations) {
		Map<Integer, Set<String>> associations = new HashMap<Integer, Set<String>>();
		for(int i=0; i<iterations ; i++) {
			Map<Integer, List<String>> temp = getTopicAssociations(instances);
			for(Entry<Integer, List<String>> e : temp.entrySet()) {
				Integer topicId = e.getKey();
				List<String> newAssociations = e.getValue();
				
				Set<String> set = associations.get(topicId);
				if(set == null) {
					set = new HashSet<String>();
					associations.put(topicId, set);
				}
				set.addAll(newAssociations);
			}
		}
		
		Map<Integer, List<String>> resp = new HashMap<Integer, List<String>>();
		for(Entry<Integer, Set<String>> e : associations.entrySet()) {
			Integer topicId = e.getKey();
			Set<String> set = e.getValue();
			
			List<String> list = new ArrayList<String>();
			list.addAll(set);
			resp.put(topicId, list);
		}
		
		return resp;
	}
	
	public Map<Integer, List<String>> getMultipleAssociations(InstanceList instances) {
		Map<Integer, List<String>> associations = new TreeMap<Integer, List<String>>();
		
		if(_model == null)
			return associations;
		
		TopicInferencer inferencer = _model.getInferencer();
		for(Instance instance : instances) {
			String id = instance.getName().toString();
			
			double[] probabilities = inferencer.getSampledDistribution(instance, 100, 10, 10);
			for(int i=0; i<probabilities.length; i++) {
				if(probabilities[i] > minProbability) {
					List<String> list = associations.get(i);
					if(list == null) {
						list = new ArrayList<String>();
						associations.put(i, list);
					}
					list.add(id);
				}
			}
			
		}
		return associations;
	}
	
	public double[] evaluate(InstanceList instances) {
		
		MarginalProbEstimator evaluator = 
				new MarginalProbEstimator (_model.numTopics, _model.alpha, _model.alphaSum,
						_model.beta, _model.typeTopicCounts, _model.tokensPerTopic);
		
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PrintStream docProbabilityStream = new PrintStream(out);
		
		evaluator.evaluateLeftToRight(instances, 100, false, docProbabilityStream);
		
		String s = out.toString();
		String[] parts = s.split("\n");
		double[] p = new double[parts.length];
		for(int i = 0; i<p.length; i++) {
			p[i] = Double.parseDouble(parts[i]);
		}
		return p;
	}
	
	
	
	public static InstanceList getInstances(String dataFile) throws IOException {
		List<Pipe> pipeList = getPipelist();
		InstanceList instances = new InstanceList (new SerialPipes(pipeList));

		Reader fileReader = new InputStreamReader(new FileInputStream(new File(dataFile)), "UTF-8");
		CsvIterator it = new CsvIterator (fileReader, Pattern.compile("^(\\S*)[\\s,]*(\\S*)[\\s,]*(.*)$"), 3, 2, 1);
		instances.addThruPipe(it); // data, label, name fields
		
		return instances;
	}
	
	public static InstanceList getInstances(Collection<Item> items) throws IOException {
		return getInstances(items, new ArrayList<String>());
	}
	
	public static InstanceList getInstances(Collection<Item> items, Collection<String> stopwords) throws IOException {
		List<Pipe> pipeList = getPipelist(stopwords);
		InstanceList instances = new InstanceList (new SerialPipes(pipeList));

		for(Item item : items) {
			String id = item.getId();
			Instance instance = new Instance(item.getText(), id, id, id);
			instances.addThruPipe(instance);
		}		
		return instances;
	}
	
	public static InstanceList getInstances(Map<String, String> texts) throws IOException {
		return getInstances(texts, new ArrayList<String>());
	}
	
	public static InstanceList getInstances(Map<String, String> texts, Collection<String> stopwords) throws IOException {
		List<Pipe> pipeList = getPipelist(stopwords);
		InstanceList instances = new InstanceList (new SerialPipes(pipeList));

		for(Entry<String, String> e : texts.entrySet()) {
			String id = e.getKey();
			String text = e.getValue();
			
			if(text.length() < 50)
				continue;
			
			Instance instance = new Instance(text, id, id, id);
			instances.addThruPipe(instance);
			
		}
		return instances;
	}
	
	public static InstanceList getInstancesWithTimePooling(Collection<Item> items, int time, TimeUnit tu, Collection<String> stowords) throws IOException {
		long div = TimeUnit.MILLISECONDS.convert(time, tu);
		Map<String, String> textPerBin = new HashMap<String, String>();
		for(Item item : items) {
			long publicationTime = item.getPublicationTime();
			String bin = Long.toString(publicationTime/(div));
			String text = textPerBin.get(bin);
			if(text == null) 
				text = item.getText();
			else
				text = text + " " + item.getText();
			textPerBin.put(bin, text);
		}
		
		return getInstances(textPerBin, stowords);
	}
	
	public static InstanceList getInstancesWithAuthorPooling(Collection<Item> items, Collection<String> stowords) throws IOException {
		
		Map<String, String> textPerUser = new HashMap<String, String>();
		for(Item item : items) {
			String username = item.getUsername();
			String text = textPerUser.get(username);
			if(text == null) 
				text = item.getText();
			else
				text = text + " " + item.getText();
			textPerUser.put(username, text);
		}
		
		return getInstances(textPerUser, stowords);
	}
	
	public static InstanceList getInstancesWithHashtagPooling(Collection<Item> items, Collection<String> stowords) throws IOException {
		
		Map<String, String> textPerHashtag = new HashMap<String, String>();
		for(Item item : items) {
			List<String> hashtags = item.getHashtags();
			for(String hashtag : hashtags) {
				String text = textPerHashtag.get(hashtag);
				if(text == null) 
					text = item.getText();
				else
					text = text + " " + item.getText();
				textPerHashtag.put(hashtag, text);
			}
		}
		return getInstances(textPerHashtag, stowords);
	}
	
	private static List<Pipe> getPipelist() {
		return getPipelist(new ArrayList<String>());
	}
	
	private static List<Pipe> getPipelist(Collection<String> stopwords) {
		// Begin by importing documents from text to feature sequences
		List<Pipe> pipeList = new ArrayList<Pipe>();

		// Pipes: lowercase, tokenize, remove stopwords, map to features
		pipeList.add(new CharSequenceLowercase());
		pipeList.add(new CharSequence2TokenSequence(Pattern.compile("\\p{L}[\\p{L}\\p{P}]+\\p{L}")));
		
		TokenSequenceRemoveStopwords stopwordsRemovalPipe = 
				new TokenSequenceRemoveStopwords(new File("mallet/stoplists/en.txt"), "UTF-8", false, false, false);
		if(!stopwords.isEmpty()) {
			stopwordsRemovalPipe.addStopWords(stopwords.toArray(new String[stopwords.size()]));
		}
		pipeList.add(stopwordsRemovalPipe);
		pipeList.add(new TokenSequence2FeatureSequence());
				
		return pipeList;
	}
	
	public static void estimateNumberOfTopics(InstanceList instancesTrain, InstanceList instancesEval, String filename) throws IOException {
		FileOutputStream output = new FileOutputStream(filename);
		Map<Integer, Double> evaluations = new HashMap<Integer, Double>();
		for(int K = 10; K<510; K = K + 20) {
			try {				
				int[] lengths = getDocumentsLengths(instancesEval);
				
				LDA model = new LDA(K);
				model.train(instancesTrain);

				double[] probs = model.evaluate(instancesEval);
				double P = getPerplexity(probs, lengths);
				
				IOUtils.write(K + "\t" + P + "\n", output);
				output.flush();
				
				evaluations.put(K, P);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static double[] getPerplexities(double[] probs, int[] lengths) {
		if(probs.length != lengths.length) {
			System.out.println("Error: Arrays are not of the same length!");
			return null;
		}
		
		double[] perplexity = new double[probs.length];
		for(int i=0; i<probs.length; i++) {
			if(lengths[i]==0)
				perplexity[i] = 0;
			else
				perplexity[i] = Math.exp(-probs[i]/lengths[i]);
		}
		return perplexity;
	}
	
	public static double getPerplexity(double[] probs, int[] lengths) {
		if(probs.length != lengths.length) {
			System.out.println("Error: Arrays are not of the same length!");
			return 0;
		}
		
		double p = 0;
		int l = 0;
		for(int i=0; i<probs.length; i++) {
			if(lengths[i]==0)
				continue;
			
			p += probs[i];
			l += lengths[i];
		}
		
		double perplexity = Math.exp(-p/l);
		return perplexity;
	}
	
	public static int[] getDocumentsLengths(InstanceList instances) {
		int[] lengths = new int[instances.size()];
		int i = 0;
		for(Instance instance : instances) {
			FeatureSequence words = (FeatureSequence) instance.getData();
			lengths[i++] = words.size();
		}
		
		return lengths;
	}
	
}
