package com.ealsoinc.resumeparser;

import static gate.Utils.stringFor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.ToTextContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Corpus;
import gate.Document;
import gate.Factory;
import gate.FeatureMap;
import gate.Gate;
import gate.util.GateException;
import gate.util.Out;

public class ResumeParser {

	private Map<String, String> attributeMap = new HashMap<String, String>();
	private static String parsedFileContent;
	
	private File parseToHTMLUsingApacheTikka(String file)
			throws IOException, SAXException, TikaException {
		
		String OUTPUT_FILE_NAME = ResumeParser.getOutputFilename(file);
		System.out.println(OUTPUT_FILE_NAME);
		File outputFile;
		
		ContentHandler handler = new ToTextContentHandler();
		InputStream stream = new FileInputStream(file);
		AutoDetectParser parser = new AutoDetectParser();
		
		Metadata metadata = new Metadata();
		try {
			parser.parse(stream, handler, metadata);
			parsedFileContent = handler.toString();
			outputFile = ResumeParser.fileWriter(OUTPUT_FILE_NAME, parsedFileContent);
		} finally {
			stream.close();
		}
		
		return outputFile;
	}
	
	private static File fileWriter(String filename, String content) throws IOException {
			FileWriter htmlFileWriter = new FileWriter(filename);
			htmlFileWriter.write(content);
			htmlFileWriter.flush();
			htmlFileWriter.close();
			return new File(filename);
	}
	
	private static String getOutputFilename(String file) {
		
		// determine extension
				String ext = FilenameUtils.getExtension(file);
				String outputFileFormat = "";
				
				// ContentHandler handler;
				if (ext.equalsIgnoreCase("html") | ext.equalsIgnoreCase("pdf")
						| ext.equalsIgnoreCase("doc") | ext.equalsIgnoreCase("docx")) {
				
					outputFileFormat = ".html";
					// handler = new ToXMLContentHandler();
				} else if (ext.equalsIgnoreCase("txt") | ext.equalsIgnoreCase("rtf")) {
					outputFileFormat = ".txt";
				} else {
					System.out.println("Input format of the file " + file
							+ " is not supported.");
					return null;
				}
				
				//System.out.println();
				return FilenameUtils.getFullPath(file) + "temp_files\\" + FilenameUtils.removeExtension(FilenameUtils.getName(file)) + outputFileFormat;
	}

	public Map<String,String> loadGateAndAnnie(File file) throws GateException,
			IOException {
		Out.prln("Initialising basic system...");

		 Gate.setGateHome(new File("C:\\GATE"));
		 Gate.setPluginsHome(new File("C:\\GATE\\plugins"));
		 Gate.setSiteConfigFile(new File("C:\\GATE\\gate.xml"));
		Gate.init();
		Out.prln("...basic system initialised");

		// initialize ANNIE (this may take several minutes)
		Annie annie = new Annie();
		annie.initAnnie();

		// create a GATE corpus and add a document for each command-line
		// argument
		Corpus corpus = Factory.newCorpus("Annie corpus");
		//String current = new File(".").getAbsolutePath();
		URL u = file.toURI().toURL();
		FeatureMap params = Factory.newFeatureMap();
		params.put("sourceUrl", u);
		params.put("preserveOriginalContent", new Boolean(true));
		params.put("collectRepositioningInfo", new Boolean(true));
		Out.prln("Creating doc for " + u);
		Document resume = (Document) Factory.createResource(
				"gate.corpora.DocumentImpl", params);
		corpus.add(resume);

		// tell the pipeline about the corpus and run it
		annie.setCorpus(corpus);
		annie.execute();

		Iterator<Document> iter = corpus.iterator();
		//JSONObject parsedJSON = new JSONObject();
		Out.prln("Started parsing...");
		// while (iter.hasNext()) {
		if (iter.hasNext()) { // should technically be while but I am just
								// dealing with one document
			//JSONObject profileJSON = new JSONObject();
			Document doc = (Document) iter.next();
			AnnotationSet defaultAnnotSet = doc.getAnnotations();

			AnnotationSet curAnnSet;
			Iterator<Annotation> it;
			Annotation currAnnot;

			// Name
			curAnnSet = defaultAnnotSet.get("NameFinder");
			if (curAnnSet.iterator().hasNext()) { // only one name will be
													// found.
				currAnnot = (Annotation) curAnnSet.iterator().next();
				String gender = (String) currAnnot.getFeatures().get("gender");
				if (gender != null && gender.length() > 0)
					attributeMap.put("gender", gender); 
				else
					attributeMap.put("gender", "");

				// Needed name Features
				//JSONObject nameJson = new JSONObject();
				String[] nameFeatures = new String[] { "firstName", "middleName", "surname" };
				for (String feature : nameFeatures) {
					String s = (String)currAnnot.getFeatures().get(feature);
					Out.prln(s);
					if (s != null && s.length() > 0) 
						attributeMap.put(feature, s);
					else
						attributeMap.put(feature, "");
				}
				//profileJSON.put("name", nameJson);
			} // name

			// title
			curAnnSet = defaultAnnotSet.get("TitleFinder");
			if (curAnnSet.iterator().hasNext()) { // only one title will be
													// found.
				currAnnot = (Annotation) curAnnSet.iterator().next();
				String title = stringFor(doc, currAnnot);
				if (title != null && title.length() > 0) {
					//profileJSON.put("title", title);
					attributeMap.put("title", title);
				}
			}// title

			// email,address,phone,url
			String[] annSections = new String[] { "EmailFinder", "AddressFinder", "PhoneFinder", "URLFinder" };
			String[] annKeys = new String[] { "email", "address", "phone", "url" };
			
			for (short i = 0; i < annSections.length; i++) {
				String annSection = annSections[i];
				curAnnSet = defaultAnnotSet.get(annSection);
				it = curAnnSet.iterator();
				List<String> sectionArray = new ArrayList<String>();
				while (it.hasNext()) { // extract all values for each
										// address,email,phone etc..
					currAnnot = (Annotation) it.next();
					String s = stringFor(doc, currAnnot);
					Out.prln(s);
					if (s != null && s.length() > 0) {
						sectionArray.add(s);
					}
				}
				if (sectionArray.size() > 0)
					attributeMap.put(annKeys[i], listToString(sectionArray));
				else
					attributeMap.put(annKeys[i], "");
			}
			
		}// if
		Out.prln("Completed parsing...");
		return attributeMap;
	}

	private static String listToString(List<String> sectionArray) {
		String res ="";
		if(sectionArray.size() <= 0) {
			return "";
		}
		else if(sectionArray.size() == 1) {
			return sectionArray.get(0);
		}
		else {
		for(String val: sectionArray) {
			if(!val.equals(sectionArray.get(sectionArray.size())))
			res = res + val + ",";
			else
				res = res + val;	
		}
		return res;
	}
	}
	
	public Map<String, String> returnInfo(String filename) {
		Map<String, String> parsedMap = null;
		try {
			ResumeParser resumeParser = new ResumeParser();
			File tikkaConvertedFile = resumeParser.parseToHTMLUsingApacheTikka(filename);
			if (tikkaConvertedFile != null) {
				parsedMap = resumeParser.loadGateAndAnnie(tikkaConvertedFile);
			}
			if(parsedMap.get("phone").equals("")) {
				parsedMap.put("phone", PhoneNoMatcher.findPhoneNo(ResumeParser.parsedFileContent));
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			System.out.println("Sad Face :( .Something went wrong.");
			e.printStackTrace();
		}
		return parsedMap;
	}
	
	
	public static void main(String[] args) {
		
		Map<String, String> parsedMap = null;
		
		try {
			ResumeParser resumeParser = new ResumeParser();
			parsedMap = resumeParser.returnInfo("C:\\Users\\mohd.tousif\\Downloads\\resume\\resume\\10.pdf");
			//System.out.println(ResumeParser.parsedFileContent);
			//System.out.println(PhoneNoMatcher.findPhoneNo(ResumeParser.parsedFileContent));
			if(parsedMap.get("phone").equals("")) {
				parsedMap.put("phone", PhoneNoMatcher.findPhoneNo(ResumeParser.parsedFileContent));
			}
				
				//Out.prln("Writing to output...");
				Out.prln(parsedMap);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			System.out.println("Sad Face :( .Something went wrong.");
			e.printStackTrace();
		}
	}
}
