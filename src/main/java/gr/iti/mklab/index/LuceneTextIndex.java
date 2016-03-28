package gr.iti.mklab.index;

import gr.iti.mklab.Config;
import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.models.Item;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
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
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.mongodb.morphia.query.Query;

public class LuceneTextIndex {

	/**
	 * Get items from MongoDB and index them to Lucene
	 */
	public static void main(String[] args) throws Exception {

		MorphiaDAO<Item> dao = new MorphiaDAO<Item>(Config.hostname ,Config.dbname, Item.class);
		
		Query<Item> query = dao.getQuery().filter("accepted =", Boolean.TRUE);
		
		System.out.println(dao.count(query) + " items to index");
		
		LuceneTextIndex indexer = new LuceneTextIndex(Config.luceneIndex);
		
		indexer.open(true);
		indexer.index(dao.iterator(query));
		indexer.close();
	
	}
	
	
	//Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_46);
	private static Analyzer analyzer = new Analyzer() {
		@Override
		protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
			Tokenizer source = new StandardTokenizer(Version.LUCENE_46, reader);
			TokenStream filter = new  LowerCaseFilter(Version.LUCENE_46, source);
			return new TokenStreamComponents(source, filter);
		}
	};
	
	private String indexDir;
	private IndexSearcher searcher;

	private IndexWriter iwriter = null;
	
	public LuceneTextIndex(String indexDir) throws IOException {
		this.indexDir = indexDir;
	}
	
	private IndexSearcher getSearcher(String indexDir) throws IOException {
		IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(indexDir)));
		IndexSearcher searcher = new IndexSearcher(reader);
		
		return searcher;
	}
	
	public void open(boolean write) {
		if(write) {
			try {
				Directory directory = FSDirectory.open(new File(indexDir));
				IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_46, analyzer);
		    	iwriter = new IndexWriter(directory, config);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		else {
			try {
				searcher = getSearcher(indexDir);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public int size() {
		return searcher.getIndexReader().numDocs();
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
				tq.setBoost(2f);
				
				bQuery.add(tq, Occur.MUST);
			}
			tokenStream.close();
			
			TopDocs results = searcher.search(bQuery, 100000);
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
	
	public void index(Collection<Item> items) throws IOException {
	    int k = 0;
	    for(Item item : items) {
	    	if(++k%100==0) {
	    		System.out.print(".");
	    		if(k%10000==0) {
	    			System.out.println(" ("+k+")");
	    		}
	    	}
	    	index(item);
	    }
	}

	public void index(Iterator<Item> iterator) throws IOException {
	    int k = 0;
		while(iterator.hasNext()) {
	    	if(++k%500==0) {
	    		System.out.print(".");
	    		if(k%10000==0) {
	    			System.out.println(" ("+k+")");
	    		}
	    	}
	    	Item item = iterator.next();
	    	index(item);
	    }
	}
	
	public void index(Item item) throws IOException {
		String id = item.getId();
    	String text = item.getText();
    	
    	long publicationTIme = item.getPublicationTime();
    	
    	Document document = new Document();
		
		Field idField = new StringField("id", id, Store.YES);
    	document.add(idField);
		
		FieldType fieldType = new FieldType();
		fieldType.setStored(true);
		fieldType.setIndexed(true);
		fieldType.setStoreTermVectors(true);
		document.add(new Field("text", text, fieldType));
		
		document.add(new LongField("publicationTIme", publicationTIme, LongField.TYPE_STORED));
		if(iwriter != null) {
			iwriter.addDocument(document);
		}
	}
	
	public void close() {
		if(iwriter != null) {
			try {
				iwriter.close();
				iwriter = null;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
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
}
