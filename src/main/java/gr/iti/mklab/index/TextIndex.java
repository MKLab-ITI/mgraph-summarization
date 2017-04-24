package gr.iti.mklab.index;

import edu.uci.ics.jung.graph.Graph;
import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.NamedEntity;
import gr.iti.mklab.models.Pair;
import gr.iti.mklab.models.TFIDFVector;
import gr.iti.mklab.models.Vector;
import gr.iti.mklab.models.WeightedEdge;
import gr.iti.mklab.utils.GraphUtils;
import gr.iti.mklab.utils.ItemsUtils;
import gr.iti.mklab.utils.StringUtils;
import gr.iti.mklab.vocabulary.Vocabulary;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.FloatField;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.index.FieldInfo.IndexOptions;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.DefaultSimilarity;
import org.apache.lucene.search.similarities.TFIDFSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.QueryBuilder;
import org.apache.lucene.util.Version;
import org.mongodb.morphia.query.Query;

public class TextIndex {
	
	private TFIDFSimilarity similarity = new DefaultSimilarity();
	
	private static Analyzer analyzer = new Analyzer() {
		@Override
		protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
			Tokenizer source = new StandardTokenizer(Version.LUCENE_46, reader);
			TokenStream filter = new  LowerCaseFilter(Version.LUCENE_46, source);
			StopFilter stopFilter = new StopFilter(Version.LUCENE_46, filter, StopAnalyzer.ENGLISH_STOP_WORDS_SET);
			
			return new TokenStreamComponents(source, stopFilter);
		}
	};
	
	private static Analyzer ngramAnalyzer = new Analyzer() {
		@Override
		protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
			Tokenizer source = new StandardTokenizer(Version.LUCENE_46, reader);
			TokenStream filter = new  LowerCaseFilter(Version.LUCENE_46, source);
			ShingleFilter ngramFilter = new ShingleFilter(filter, 2, 2);
			ngramFilter.setOutputUnigrams(false);
			
			return new TokenStreamComponents(source, ngramFilter);
		}
	};
	
	private static Analyzer entitiesAnalyzer = new Analyzer() {
		@Override
		protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
			Tokenizer source = new KeywordTokenizer(reader);
			TokenStream filter = new  LowerCaseFilter(Version.LUCENE_46, source);			
			return new TokenStreamComponents(source, filter);
		}
	};
	
	private static Map<String, Analyzer> analyzersPerField = new HashMap<String, Analyzer>();
	{
		analyzersPerField.put("text", analyzer);
		analyzersPerField.put("ngrams", ngramAnalyzer);
		analyzersPerField.put("person", entitiesAnalyzer);
		analyzersPerField.put("location", entitiesAnalyzer);
		analyzersPerField.put("organization", entitiesAnalyzer);
	}
	private static PerFieldAnalyzerWrapper analyzersWrapper = new PerFieldAnalyzerWrapper(analyzer, analyzersPerField);
	
	private String indexDir;
	private IndexSearcher searcher;

	private IndexWriter iwriter = null;

	private DirectoryReader reader;
	
	public TextIndex(String indexDir) throws IOException {
		this.indexDir = indexDir;
	}
	
	private void getSearcher(String indexDir) throws IOException {
		FSDirectory dir = FSDirectory.open(new File(indexDir));
		reader = DirectoryReader.open(dir);
		searcher = new IndexSearcher(reader);
	}
	
	public void open() {
		try {
			getSearcher(indexDir);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public Map<String, Double> search(String text) {
		Map<String, Double> similar = new HashMap<String, Double>();
		try {
			TokenStream tokenStream = analyzer.tokenStream("text", text);
			CharTermAttribute charTermAtt = tokenStream.addAttribute(CharTermAttribute.class);
			tokenStream.reset();
			BooleanQuery bQuery = new BooleanQuery();
			while (tokenStream.incrementToken()) {
				String token = charTermAtt.toString();
				TermQuery tq = new TermQuery(new Term("text", token));
				
				bQuery.add(tq, Occur.SHOULD);
			}
			tokenStream.close();
			
			TopDocs results = searcher.search(bQuery, 100);
			ScoreDoc[] hits = results.scoreDocs;
			for(ScoreDoc hit : hits) {				
				Document doc = searcher.doc(hit.doc);
				similar.put(doc.get("id"), new Double(hit.score));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return similar;
	}
	
	public Map<String, Double> expandedSearch(String text) {
		Map<String, Double> similar = new HashMap<String, Double>();
		try {
			TermQuery query = new TermQuery(new Term("text", text));
			
			TopDocs results = searcher.search(query, 100000);
			ScoreDoc[] hits = results.scoreDocs;
			for(ScoreDoc hit : hits) {				
				Document doc = searcher.doc(hit.doc);
				similar.put(doc.get("id"), new Double(hit.score));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return similar;
	}
	
	public Map<String, Double> searchIndex(Item item, int n) throws IOException {
		Map<String, Double> similar = new HashMap<String, Double>();
		
		QueryBuilder builder = new QueryBuilder(analyzer);
		try {
			String textQuery = item.getText();
			textQuery = StringUtils.clean(textQuery, item.getUrls());
			textQuery = textQuery.replaceAll("\\r\\n|\\r|\\n", " ");	
			textQuery = QueryParser.escape(textQuery);
	    	
			org.apache.lucene.search.Query luceneQuery = 
					builder.createMinShouldMatchQuery("text", textQuery, 0.6f);
			
			TopDocs topDocs = searcher.search(luceneQuery, n);
			for(ScoreDoc scoredDoc : topDocs.scoreDocs) {
				Document document = searcher.doc(scoredDoc.doc);
				String id = document.get("id");
				if(id != null) {
					similar.put(document.get("id"), (double) scoredDoc.score);
				}
			}
		} catch (Exception e) { }
		
		return similar;
	}
	
	public List<Document> searchDocuments(String text) {
		List<Document> documents = new ArrayList<Document>();
		try {
			TokenStream tokenStream = analyzer.tokenStream("text", text);
			CharTermAttribute charTermAtt = tokenStream.addAttribute(CharTermAttribute.class);
			tokenStream.reset();
			BooleanQuery bQuery = new BooleanQuery();
			while (tokenStream.incrementToken()) {
				String token = charTermAtt.toString();
				TermQuery tq = new TermQuery(new Term("text", token));
				tq.setBoost(2f);
				
				bQuery.add(tq, Occur.MUST);
			}
			tokenStream.close();
			
			TopDocs results = searcher.search(bQuery, 100000);
			ScoreDoc[] hits = results.scoreDocs;
			for(ScoreDoc hit : hits) {				
				Document doc = searcher.doc(hit.doc);
				doc.add(new FloatField("score", hit.score, FloatField.TYPE_STORED));
				documents.add(doc);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return documents;
	}
	
	private void initializeIndexing() {
		try {
			Directory directory = FSDirectory.open(new File(indexDir));
			IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_46, analyzersWrapper);
	    	iwriter = new IndexWriter(directory, config);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void finalizeIndexing() {
		if(iwriter != null) {
			try {
				iwriter.commit();
				iwriter.close();
				iwriter = null;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void index(Collection<Item> items) throws IOException {
		index( items.iterator(), true);
	}

	public void index(Iterator<Item> iterator) throws IOException {
		 index(iterator, true);
	}
	
	public void index(Iterator<Item> iterator, boolean onlyOriginal) throws IOException {
		initializeIndexing();
		
	    int k = 0;
		while(iterator.hasNext()) {
	    	if(++k%500==0) {
	    		System.out.print(".");
	    		if(k%10000==0) {
	    			System.out.println(" ("+k+")");
	    		}
	    	}
	    	Item item = iterator.next();
	    	if(onlyOriginal && !item.isOriginal()) {
	    		continue;
	    	}
	    	index(item);
	    }
		finalizeIndexing();
	}
	
	private void index(Item item) throws IOException {
		String id = item.getId();
    	String text = item.getText();
    	
    	long publicationTime = item.getPublicationTime();
    	
    	Document document = new Document();
		
		Field idField = new StringField("id", id, Store.YES);
    	document.add(idField);
		
		FieldType fieldType = new FieldType();
		fieldType.setStored(true);
		fieldType.setIndexed(true);
		fieldType.setStoreTermVectors(true);
		
		text = StringUtils.clean(text, item.getUrls());
		document.add(new Field("text", text, fieldType));
		document.add(new Field("ngrams", text, fieldType));
		
		document.add(new LongField("publicationTIme", publicationTime, LongField.TYPE_STORED));
		
		if(item.getNamedEntities() != null) {
			for(NamedEntity entity : item.getNamedEntities()) {
				String name = entity.getName();
				String fieldName = entity.getType().toLowerCase();
			
				FieldType type = new FieldType();
				type.setIndexed(true);
				type.setOmitNorms(true);
				type.setIndexOptions(IndexOptions.DOCS_ONLY);
				type.setStored(true);
				type.setTokenized(false);
				type.setStoreTermVectors(true);
				Field field = new Field(fieldName, name, type);
				
				document.add(field);
			}
		}
		
		if(iwriter != null) {
			iwriter.addDocument(document);
		}
	}
	
	public int count() {
		if(reader != null)
			return reader.numDocs();
		else {
			return -1;
		}
	}
	
	private double idf(String term, String field) {
		try {
			// TOO SLOW!!!
			//TopDocs docs = searcher.search(new WildcardQuery(new Term(field, "*")), Integer.MAX_VALUE);
			//long numDocs = docs.totalHits;
			
			long numDocs = reader.numDocs();
			long docFreq = reader.docFreq(new Term(field, term));
			
			double idf = (double) similarity.idf(docFreq, numDocs);
			return idf;
		} catch (IOException e) {
			return .0;
		}
	}
	
	public Document getDoc(String id) throws IOException {
		TermQuery tq = new TermQuery(new Term("id", id));
		TopDocs results = searcher.search(tq, 1);
		ScoreDoc[] hits = results.scoreDocs;
		if(hits.length > 0) {
			Document doc = searcher.doc(hits[0].doc);
			return doc;
		}
		else {
			return null;
		}
	}
	
	public List<String> getDocumentTerms(String id) throws IOException {
		List<String> terms = new ArrayList<String>();
		try {
			TermQuery tq = new TermQuery(new Term("id", id));
			TopDocs results = searcher.search(tq, 1);
			ScoreDoc[] hits = results.scoreDocs;
			if(hits.length > 0) {
				int docID = hits[0].doc;
				Terms tv = reader.getTermVector(docID, "text");
				if(tv != null) {
					TermsEnum termsEnum = tv.iterator(null);
			    	while(termsEnum.next() != null) {
			    		BytesRef term = termsEnum.term();			
			    		if(term != null) {
			    			terms.add(term.utf8ToString());
			    		}
			    	}
				}
			}
		}
		catch(Exception e) {}
		return terms;
	}
	
	public TFIDFVector getTFIDF(String id) throws IOException {
		TFIDFVector vector = new TFIDFVector();
		try {
			TFIDFVector textVector = getTFIDF(id, "text");
			if(textVector != null) {
				vector.mergeVector(textVector);
			}
		
			TFIDFVector ngramsVector = getTFIDF(id, "ngrams");
			if(ngramsVector != null) {
				vector.mergeVector(ngramsVector);
			}
		
			TFIDFVector personsVector = getTFIDF(id, "person");
			if(personsVector != null) {
				vector.mergeVector(personsVector);
			}
		
			TFIDFVector locationsVector = getTFIDF(id, "location");
			if(locationsVector != null) {
				vector.mergeVector(locationsVector);
			}
		
			TFIDFVector organizationsVector = getTFIDF(id, "organization");
			if(organizationsVector != null) {
				vector.mergeVector(organizationsVector);
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		vector.updateLength();

		return vector;
	}
	
	public TFIDFVector getTFIDF(String id, String field) throws IOException {
		if(field == null) {
			return getTFIDF(id);
		}
		
		TFIDFVector vector = new TFIDFVector();
		try {
			field = field.toLowerCase().trim();
			TermQuery tq = new TermQuery(new Term("id", id));
			TopDocs results = searcher.search(tq, 1);
			ScoreDoc[] hits = results.scoreDocs;
			if(hits.length > 0) {
				int docID = hits[0].doc;
				Map<String, Pair<Double, Double>> termsMap = getTermsMap(docID, field);
				vector.addTerms(termsMap);
			}
		}
		catch(Exception e) {
			
		}
		return vector;
	}
	
	
	private Map<String, Pair<Double, Double>> getTermsMap(int docID, String field) throws IOException {
		Map<String, Pair<Double, Double>> termsMap = new HashMap<String, Pair<Double, Double>>();
		Terms tv = reader.getTermVector(docID, field);
		if(tv != null) {
			TermsEnum termsEnum = tv.iterator(null);
	    	while(termsEnum.next() != null) {
	    		BytesRef term = termsEnum.term();			
	    		if(term != null) {
	    			
	    			String termStr = termsEnum.term().utf8ToString();
		    		double tf = similarity.tf(termsEnum.totalTermFreq());
		    		double idf = this.idf(termStr, field);
		    		
		    		Pair<Double, Double> tfIdf = Pair.of(tf, idf);
		    		termsMap.put(termStr, tfIdf);
	    		}
	    	}
		}
		return termsMap;
	}
	
	private List<String> getTerms(int docID, String field) throws IOException {
		List<String> terms = new ArrayList<String>();
		Terms tv = reader.getTermVector(docID, field);
		if(tv != null) {
			TermsEnum termsEnum = tv.iterator(null);
	    	while(termsEnum.next() != null) {
	    		BytesRef term = termsEnum.term();			
	    		if(term != null) {
	    			terms.add(term.utf8ToString());
	    		}
	    	}
		}
		return terms;
	}
	
	public Map<String, Vector> getVectorsMap(Collection<String> ids) {
		return getVectorsMap(ids, null);
	}
	
	public Map<String, Vector> getVectorsMap(Collection<String> ids, String field) {
		Map<String, Vector> vectorsMap = new HashMap<String, Vector>();
		for(String itemId : ids) {
			try {
			TFIDFVector vector = this.getTFIDF(itemId, field);
	    		if(vector.getLength() == 0) {
	    			continue;
	    		}
	    	
	    		vectorsMap.put(itemId, vector);
	    		if(vectorsMap.size() % 50000 == 0) {
		    		System.out.println(vectorsMap.size() + " vectors");
		    	}
			}
	    	catch(Exception e) {
	    		
	    	}
		}
		return vectorsMap;
	}
	
	public void close() {	
		try {
			if(searcher != null) {
				IndexReader reader = searcher.getIndexReader();
				if(reader != null) {
					reader.close();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public Map<String, Vector> createVocabulary(String field) throws IOException {
		Vocabulary.reset();
		Map<String, Vector> vectors = new HashMap<String, Vector>();
		
		Bits liveDocs = MultiFields.getLiveDocs(reader);
		for (int docID=0; docID<reader.maxDoc(); docID++) {
			
		    if (liveDocs != null && !liveDocs.get(docID)) {
		        continue;
		    }
		    
		    Document doc = reader.document(docID);
		    String id = doc.get("id");
		    
		    Set<String> terms = new HashSet<String>();
		    terms.addAll(getTerms(docID, "text"));
		    if(!terms.isEmpty()) {
		    	Vector vector = new Vector(terms);
				vectors.put(id, vector);
				Vocabulary.addDoc(terms);
		    }
		}
		
		Collection<String> stowords = Vocabulary.getStopwords();
		Vocabulary.removeWords(stowords);
		
		return vectors;
	}
	
	public Map<String, Vector> createVocabulary() throws IOException {
		Vocabulary.reset();
		Map<String, Vector> vectors = new HashMap<String, Vector>();
		
		Bits liveDocs = MultiFields.getLiveDocs(reader);
		for (int docID=0; docID<reader.maxDoc(); docID++) {
			
		    if (liveDocs != null && !liveDocs.get(docID)) {
		        continue;
		    }
		    
		    Document doc = reader.document(docID);
		    String id = doc.get("id");
		    
		    Set<String> terms = new HashSet<String>();
		    terms.addAll(getTerms(docID, "text"));
		    terms.addAll(getTerms(docID, "ngrams"));
		    
		    List<String> persons = getTerms(docID, "person");
		    terms.addAll(persons);
		    
		    List<String> locations = getTerms(docID, "location");
		    terms.addAll(locations);
		    
		    List<String> organizations = getTerms(docID, "organization");
		    terms.addAll(organizations);
		    
		    if(!terms.isEmpty()) {
		    	Vector vector = new Vector(terms);
				vectors.put(id, vector);
				Vocabulary.addDoc(terms);
				
				if(!persons.isEmpty()) {
					Vocabulary.addBoostedTerms(persons);
				}
				if(!locations.isEmpty()) {
					Vocabulary.addBoostedTerms(locations);
				}
				if(!organizations.isEmpty()) {
					Vocabulary.addBoostedTerms(organizations);
				}
		    }
		}
		
		Collection<String> stowords = Vocabulary.getStopwords();
		Vocabulary.removeWords(stowords);
		
		for(Vector vector : vectors.values()) {
			vector.updateLength();
		}
		
		return vectors;
	}
	
	private static String dataset = "BaltimoreRiots";
	/**
	 * Get items from MongoDB and index them to Lucene
	 */
	public static void main(String[] args) throws Exception {

		MorphiaDAO<Item> dao = new MorphiaDAO<Item>("160.40.50.207", dataset, Item.class);
		Query<Item> query = dao.getQuery().filter("original =", Boolean.TRUE);
		
		TextIndex indexer = new TextIndex("/disk1_data/Datasets/" + dataset + "/TextIndex");
		indexer.index(dao.iterator());
		
		indexer.open();
		System.out.println(indexer.count() + " documents in text index!");
		
		Iterator<Item> iterator = dao.iterator(query);
		Map<String, Item> itemsMap = ItemsUtils.loadUniqueItems(iterator);
		System.out.println(itemsMap.size() + " items");
		
		Map<String, Vector> vectorsMap = new HashMap<String, Vector>();
		for(String itemId : itemsMap.keySet()) {
			Item item = itemsMap.get(itemId);
			if(item.isOriginal()) {
				TFIDFVector vector = indexer.getTFIDF(itemId);
	    		if(vector.getLength() == 0) {

	    			continue;
	    		}
	    	
	    		vectorsMap.put(itemId, vector);
	    		if(vectorsMap.size() % 10000 == 0) {
	    			System.out.println(vectorsMap.size() + " vectors");
	    		}
			}
			else {
				System.out.println(itemId + " is not original");
			}
		}
		System.out.println(vectorsMap.size() + " vectors");
		
		//Graph<String, WeightedEdge> tGraph = GraphUtils.generateTextualGraph(vectorsMap, 0.1);
		Graph<String, WeightedEdge> tGraph = GraphUtils.generateTextualItemsGraph(itemsMap, vectorsMap, 0.1, indexer);
		GraphUtils.saveGraph(tGraph, "/disk1_data/Datasets/" + dataset + "/graphs/textual_items_graph.graphml");
		
	}
}
