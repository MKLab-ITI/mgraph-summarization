package gr.iti.mklab;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.imageio.ImageIO;

import gr.iti.mklab.dao.MorphiaDAO;
import gr.iti.mklab.detector.smal.ConceptDetector;
import gr.iti.mklab.index.VisualIndex;
import gr.iti.mklab.models.Item;
import gr.iti.mklab.models.MediaItem;
import gr.iti.mklab.utils.ConceptType;
import gr.iti.mklab.visual.utilities.ImageIOGreyScale;

public class DetectType {

	public static void main(String[] args) throws Exception {
		
		String dataset = "Sundance2013";
		
		ConceptType[] conceptValues = ConceptType.values();
		File mediaFolder = new File("/disk1_data/Datasets/"+dataset+"/media/");
		
		VisualIndex visualIndex = new VisualIndex("/disk2_data/VisualIndex/learning_files", "/disk1_data/Datasets/"+dataset);
		
		ConceptDetector detector = new ConceptDetector("./twitter_training_params.mat");
		
		MorphiaDAO<Item> dao = new MorphiaDAO<Item>("160.40.50.207" , dataset, Item.class);
		MorphiaDAO<MediaItem> mDao = new MorphiaDAO<MediaItem>("160.40.50.207" , dataset, MediaItem.class);
		System.out.println(dao.count() + " items");
		
		Map<String, MediaItem> mediaItems = new HashMap<String, MediaItem>();
		Iterator<Item> it = dao.iterator();
		while(it.hasNext()) {
			Item item = it.next();
			Map<String, String> mItems = item.getMediaItems();
			if(mItems != null) {
				for(Entry<String, String> e : mItems.entrySet()) {
					String mediaId = e.getKey();
					
					MediaItem mediaItem = mediaItems.get(mediaId);
					if(mediaItem == null) {
						mediaItem = new MediaItem(mediaId, e.getValue());
						File mediaFile = new File(mediaFolder, mediaId);
						if(!mediaFile.exists()) {
							continue;
						}
						
						BufferedImage image = null;
						try {
							image = ImageIO.read(mediaFile);
						} catch (Exception ex) {
							try {
								image = ImageIOGreyScale.read(mediaFile);
							}
							catch(Exception ex2) {
								
							}
						}
						if(image != null) {
							mediaItem.setWidth(image.getWidth());
							mediaItem.setHeight(image.getHeight());
						}
						
						mediaItem.setTitle("");
						
						mediaItems.put(e.getKey(), mediaItem);
					}
					mediaItem.addReference(item.getId());
				}
			}
		}
		
		System.out.println(mediaItems.size() + " media items");
		
		List<String> ids = new ArrayList<String>();
		List<double[]> vectors = new ArrayList<double[]>();
		for(String mediaId : mediaItems.keySet()) {
			try {
				double[] vector = visualIndex.getVector(mediaId);
				if(vector != null && vector.length > 0) {
					ids.add(mediaId);
					vectors.add(vector);
				}
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}
		System.out.println(vectors.size() + " vectors");
		
		double[][] concepts = detector.detect(vectors.toArray(new double[vectors.size()][]));
		for(int index=0; index<concepts.length; index++) {
			String mediaId = ids.get(index);
			int conceptIndex = (int) concepts[index][0];
			double score = concepts[index][1];
			ConceptType conceptType = conceptValues[conceptIndex-1];	
			MediaItem mediaItem = mediaItems.get(mediaId);
			if(mediaItem != null) {
				mediaItem.setConcept(conceptType.name(), score);
				
				mDao.save(mediaItem);
			}
		}
	}

}
