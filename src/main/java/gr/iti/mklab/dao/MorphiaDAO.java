package gr.iti.mklab.dao;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.dao.BasicDAO;
import org.mongodb.morphia.dao.DAO;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.QueryResults;
import org.mongodb.morphia.query.UpdateOperations;

import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;

public class MorphiaDAO<K> {

	private DAO<K, String> dao;
	private Datastore ds;
	private Class<K> clazz;

	public MorphiaDAO (String hostname, String dbname, Class<K> clazz) throws Exception {
		Morphia morphia = new Morphia();
		
		MongoClientOptions options = MongoClientOptions.builder()
				.cursorFinalizerEnabled(false)
				.connectTimeout(0).build();
		MongoClient mongoClient = new MongoClient(hostname, options);
		
		ds = morphia.createDatastore(mongoClient, dbname);
		dao = new BasicDAO<K, String>(clazz, mongoClient, morphia, dbname);
	
		this.clazz = clazz;
	}
	
	public K get(String id) {
		return dao.get(id);
	}
	
	public long count() {
		return dao.count();
	}
	
	public long count(Query<K> q) {
		return dao.count(q);
	}
	
	public Iterator<K> iterator() {
		QueryResults<K> res = dao.find();
		return res.iterator();
	}
	
	public List<K> get() {
		List<K> list = new ArrayList<K>();
		Iterator<K> iterator = iterator();
		while(iterator.hasNext()) {
			K k = iterator.next();
			list.add(k);
		}
		return list;
	}

	public List<K> get(Query<K> q) {
		List<K> list = new ArrayList<K>();
		Iterator<K> iterator = iterator(q);
		while(iterator.hasNext()) {
			K k = iterator.next();
			list.add(k);
		}
		return list;
	}
	
	public Iterator<K> iterator(Query<K> q) {
		QueryResults<K> res = dao.find(q);
		return res.iterator();
	}
	
	public Query<K> getQuery() {
		return ds.createQuery(clazz);
	}

	public UpdateOperations<K> getUpdateOperations() {
		return ds.createUpdateOperations(clazz);
	}
	
	public void update(Query<K> q, UpdateOperations<K> ops) {
		dao.update(q, ops);
	}
	
	public void save(K item) {
		dao.save(item);
	}

	public void save(DBObject obj, String collectionName) {
		DB db = ds.getDB();
		DBCollection collection = db.getCollection(collectionName);
		
		collection.save(obj);
	}
	
}
