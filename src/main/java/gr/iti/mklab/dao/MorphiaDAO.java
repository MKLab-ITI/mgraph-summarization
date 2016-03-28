package gr.iti.mklab.dao;

import java.util.Collection;
import java.util.Iterator;

import gr.iti.mklab.Config;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.NamedEntity;

import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.dao.BasicDAO;
import org.mongodb.morphia.dao.DAO;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.QueryResults;

import com.mongodb.MongoClient;

public class MorphiaDAO<K> {

	private DAO<K, String> dao;
	private Datastore ds;
	private Class<K> clazz;

	public MorphiaDAO (String hostname, String dbname, Class<K> clazz) throws Exception {
		Morphia morphia = new Morphia();
		MongoClient mongoClient = new MongoClient(hostname);
		
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
	
	public Iterator<K> iterator(Query<K> q) {
		QueryResults<K> res = dao.find(q);
		return res.iterator();
	}
	
	public Query<K> getQuery() {
		return ds.createQuery(clazz);
	}

	public static void main(String...args) throws Exception {
		MorphiaDAO<Item> dao = new MorphiaDAO<Item>(Config.hostname ,Config.dbname, Item.class);
		Iterator<Item> it = dao.iterator();
		while(it.hasNext()) {
			Collection<NamedEntity> entities = it.next().getNamedEntities();
			for(NamedEntity ne : entities) {
				System.out.println(ne.getName() + ":"+ne.getType());
			}
		}
		
	}
	
}
