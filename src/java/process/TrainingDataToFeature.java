package process;

import java.io.*;
import java.util.*;

import feature.FeatureConfig;
import feature.FeatureMeta;
import feature.Tokenize;
import feature.TwitterFeature;
import fileFetch.GetAllFiles;
import global.GlobalVar;

//need to change for new classification: getFeaturesForOneFile(): if (elements.length > 1)
public class TrainingDataToFeature {

    int nGram;
    int textIndex;
    int labelIndex;
    int idIndex;
    int targetIndex;
    Tokenize tokenize;
    boolean usingNgramFreq;
    boolean usingWordFeature;
    Hashtable<String, Integer> wordFeature_to_index = new Hashtable<String, Integer>();
    Hashtable<String, Integer> nonWordFeature_to_index = new Hashtable<String, Integer>();
    int featureNum;
    TwitterFeature twitterFeature;
    int vocSize;
    int trainDataNum = 0;
    HashSet<String> selectedTrigramSet;

    TrainingDataToFeature(String trainDataFile, String trainFeatureFile, String trainFeatureTokenFile,
	    String featureToIndexDir, Tokenize tokenize, int nGram, FeatureConfig config,
	    int labelIndex, int textIndex, int idIndex, int targetIndex, int posHalfWindowSize, int ngramPOS,
	    int ngramDependency) {
	this.nGram = nGram;
	this.labelIndex = labelIndex;
	this.textIndex = textIndex;
	this.idIndex = idIndex;
	this.tokenize = tokenize;
	this.targetIndex = targetIndex;
	usingNgramFreq = config.configs.contains("usingNgramFreq");
	usingWordFeature = config.configs.contains("usingWordFeature") || usingNgramFreq;
	String wordFeature_to_indexFile = featureToIndexDir + "wordFeature_to_index";
	String nonWordFeature_to_indexFile = featureToIndexDir + "nonWordFeature_to_index";

	if (usingWordFeature) {
	    GetVocabulary getVocabulary = new GetVocabulary(tokenize, nGram, trainDataFile, labelIndex, textIndex);
	    selectedTrigramSet = getVocabulary.selectedTrigramSet;
	    vocSize = getVocabulary.wordSet.size() + selectedTrigramSet.size();
	    getVocabulary.wordSet.clear();
	} else {
	    vocSize = 0;
	}

	twitterFeature = new TwitterFeature(config, true);

	getFeaturesForFiles(trainDataFile, trainFeatureFile, trainFeatureTokenFile);
	writeFeatureToIndex(wordFeature_to_indexFile, wordFeature_to_index);
	writeFeatureToIndex(nonWordFeature_to_indexFile, nonWordFeature_to_index);
	featureNum = wordFeature_to_index.size() + nonWordFeature_to_index.size();
    }

    void writeFeatureToIndex(String file, Hashtable<String, Integer> feature_to_index) {
	try {
	    // Create file
	    FileWriter fstreamOut = new FileWriter(file);
	    BufferedWriter out = new BufferedWriter(fstreamOut);

	    for (Map.Entry<String, Integer> entry : feature_to_index.entrySet()) {
		out.write(entry.toString() + "\n");
	    }

	    // Close the output stream
	    out.close();
	    fstreamOut.close();
	} catch (Exception e) {// Catch exception if any
	    e.printStackTrace();
	}
    }

    void getUnigramFeatures(Hashtable<Integer, FeatureMeta> tokenIndex_to_featureMeta, List<String> tokenList) {
	for (String token : tokenList) {
	    addOneTokenToFeature(token, tokenIndex_to_featureMeta);

	    if (token.startsWith("#")) {
		token = token.substring(1);
		addOneTokenToFeature(token, tokenIndex_to_featureMeta);
	    }
	}

    }

    void addOneTokenToFeature(String token, Hashtable<Integer, FeatureMeta> tokenIndex_to_featureMeta) {
	int tokenIndex = FeatureConfig.getTokenIndex(wordFeature_to_index, token, true);
	FeatureMeta featureMeta = tokenIndex_to_featureMeta.get(tokenIndex);
	if (featureMeta == null) {
	    featureMeta = new FeatureMeta(1, token, 0, false);
	    tokenIndex_to_featureMeta.put(tokenIndex, featureMeta);
	} else {
	    if (usingNgramFreq) {
		featureMeta.freq++;
	    }
	}
    }

    void getBigramFeatures(Hashtable<Integer, FeatureMeta> tokenIndex_to_featureMeta, List<String> tokenList) {

	//insert start and end symbol to the token list
	tokenList.add(tokenList.size(), "</s>");
	String lastToken = "<s>";

	for (String token : tokenList) {
	    String biGram = lastToken + "_" + token;
	    addOneTokenToFeature(biGram, tokenIndex_to_featureMeta);

	    boolean lastIsHash = false;
	    boolean tokenIsHash = false;
	    String lastTokenRmHash = "";
	    String tokenRmHash = "";
	    if (lastToken.startsWith("#")) {
		lastIsHash = true;
		lastTokenRmHash = lastToken.substring(1);
	    }
	    if (token.startsWith("#")) {
		tokenIsHash = true;
		tokenRmHash = token.substring(1);
	    }


	    if (lastIsHash && !tokenIsHash) {
		biGram = lastTokenRmHash + "_" + token;
		addOneTokenToFeature(biGram, tokenIndex_to_featureMeta);
	    } else if (!lastIsHash && tokenIsHash) {
		biGram = lastToken + "_" + tokenRmHash;
		addOneTokenToFeature(biGram, tokenIndex_to_featureMeta);
	    } else if (lastIsHash && tokenIsHash) {
		biGram = lastToken + "_" + tokenRmHash;
		addOneTokenToFeature(biGram, tokenIndex_to_featureMeta);
		biGram = lastTokenRmHash + "_" + token;
		addOneTokenToFeature(biGram, tokenIndex_to_featureMeta);
		biGram = lastTokenRmHash + "_" + tokenRmHash;
		addOneTokenToFeature(biGram, tokenIndex_to_featureMeta);
	    }

	    lastToken = token;
	}
    }

    void getTrigramFeatures(Hashtable<Integer, FeatureMeta> tokenIndex_to_featureMeta, List<String> tokenList) {

	String lastlastToken = "<s>";
	String lastToken = tokenList.get(0);
	for (int i = 1; i < tokenList.size(); i++) {
	    String token = tokenList.get(i);
	    String triGram = lastlastToken + "_" + lastToken + "_" + token;
	    if (selectedTrigramSet.contains(triGram)) {
		addOneTokenToFeature(triGram, tokenIndex_to_featureMeta);

		if (lastlastToken.startsWith("#")) {
		    String lastlastTokenRmHash = lastlastToken.substring(1);
		    triGram = lastlastTokenRmHash + "_" + lastToken + "_" + token;
		    addOneTokenToFeature(triGram, tokenIndex_to_featureMeta);
		}

		if (lastToken.startsWith("#")) {
		    String lastTokenRmHash = lastToken.substring(1);
		    triGram = lastlastToken + "_" + lastTokenRmHash + "_" + token;
		    addOneTokenToFeature(triGram, tokenIndex_to_featureMeta);
		}

		if (token.startsWith("#")) {
		    String tokenRmHash = token.substring(1);
		    triGram = lastlastToken + "_" + lastToken + "_" + tokenRmHash;
		    addOneTokenToFeature(triGram, tokenIndex_to_featureMeta);
		}
	    }

//	    System.out.print(triGram + "; ");
	    lastlastToken = lastToken;
	    lastToken = token;
	}

    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    void sortAndWrite(Hashtable<Integer, FeatureMeta> tokenIndex_to_featureMeta, BufferedWriter out,
	    BufferedWriter outToken, int indexOffset) throws IOException {

	List list = new LinkedList(tokenIndex_to_featureMeta.entrySet());

	//sort list based on comparator, order from small to large
	Collections.sort(list, new Comparator() {
	    public int compare(Object o1, Object o2) {
		return ((Comparable) ((Map.Entry) (o1)).getKey())
			.compareTo(((Map.Entry) (o2)).getKey());
	    }
	});

	//tranverse
	for (Iterator it = list.iterator(); it.hasNext();) {
	    Map.Entry entry = (Map.Entry) it.next();
	    FeatureMeta featureMeta = (FeatureMeta) entry.getValue();

	    int featureIndex = indexOffset + (Integer) entry.getKey();
	    if (featureMeta.usingDoubleFeature) {
		out.write(" " + featureIndex + ":" + featureMeta.doubleFeatureValue);
	    } else {
		out.write(" " + featureIndex + ":" + featureMeta.freq);
	    }

	    if (GlobalVar.testing) {
		if (featureMeta.usingDoubleFeature) {
		    outToken.write(" " + featureIndex + ":" + featureMeta.featureStr + ":" + featureMeta.doubleFeatureValue);
		} else {
		    outToken.write(" " + featureIndex + ":" + featureMeta.featureStr + ":" + featureMeta.freq);
		}
	    }

	}

    }

    void getFeaturesForOneFile(String filePath, BufferedWriter out,
	    BufferedWriter outToken) {
	try {
	    FileInputStream fstream = new FileInputStream(filePath);

	    DataInputStream in = new DataInputStream(fstream);
	    BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF8"));

	    String strLine;

	    while ((strLine = br.readLine()) != null) {

		String[] elements = strLine.split("\t");
		if (elements.length > 1) {
		    trainDataNum++;

		    String text = elements[textIndex];
		    List<String> tokenList = tokenize.tokenizeText(text);
		    String label = elements[labelIndex];
		    long docID = Long.parseLong(elements[idIndex]);
		    String target = elements[targetIndex];
		    if (label.equals("0")) {
			label = "-1";
		    }

		    out.write(docID + "\t" + label);

		    Hashtable<Integer, FeatureMeta> tokenIndex_to_featureMeta = new Hashtable<Integer, FeatureMeta>();

		    if (usingWordFeature) {
			getUnigramFeatures(tokenIndex_to_featureMeta, tokenList);
			if (nGram > 1) {
			    getBigramFeatures(tokenIndex_to_featureMeta,
				    tokenList);
			}
			if (nGram > 2) {
			    getTrigramFeatures(tokenIndex_to_featureMeta,
				    tokenList);
			}

		    }

		    Hashtable<Integer, FeatureMeta> nonWordFeatureIndex_to_featureMeta = new Hashtable<Integer, FeatureMeta>();
		    Hashtable<Integer, FeatureMeta> twitterFeatureIndex_to_featureMeta = twitterFeature
			    .getTwitterFeatures(text, nonWordFeature_to_index, target);
		    nonWordFeatureIndex_to_featureMeta.putAll(twitterFeatureIndex_to_featureMeta);

		    if (GlobalVar.testing) {
			outToken.write(label + "\t" + docID + "\t");
		    }

		    if (usingWordFeature) {
			sortAndWrite(tokenIndex_to_featureMeta, out, outToken, 0);
		    }

		    sortAndWrite(nonWordFeatureIndex_to_featureMeta, out, outToken, vocSize);

		    out.write("\n");
		    if (GlobalVar.testing) {
			outToken.write("\t" + text + "\n");
		    }
		}
	    }

	    br.close();
	    in.close();
	    fstream.close();
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    void getFeaturesForFiles(String dataFile, String trainFeatureFile, String trainFeatureTokenFile) {
	FileWriter fstreamOut = null;
	BufferedWriter out = null;

	FileWriter fstreamOutToken = null;
	BufferedWriter outToken = null;
	try {
	    fstreamOut = new FileWriter(trainFeatureFile);
	    out = new BufferedWriter(fstreamOut);

	    fstreamOutToken = new FileWriter(trainFeatureTokenFile);
	    outToken = new BufferedWriter(fstreamOutToken);
	} catch (IOException e) {
	    e.printStackTrace();
	}

	getFeaturesForOneFile(dataFile, out, outToken);

	try {
	    out.close();
	    fstreamOut.close();
	    outToken.close();
	    fstreamOutToken.close();
	} catch (IOException e) {
	    e.printStackTrace();
	}

    }

    public static void main(String[] args) {
	String trainDataDir = "data/train/";
	String trainFeatureFile = "results/pipeline/trainFeature.txt";
	String trainFeatureTokenFile = "results/pipeline/trainFeatureTokens.txt";
	String featureToIndexDir = "data/featureToIndex/";

	int nGram = 2;
	int idIndex = 1;
	int textIndex = 3;
	int targetIndex = 2;
	int labelIndex = 0;
	int posHalfWindowSize;
	int ngramPOS;
	int ngramDependency;
	posHalfWindowSize = 0;
	ngramPOS = 1;
	ngramDependency = 1;
	FeatureConfig config = new FeatureConfig("keepPunctuation,tokenURL,usingWordFeature,considerWordShape");
	Tokenize tokenize = new Tokenize(config);
	new TrainingDataToFeature(trainDataDir, trainFeatureFile, trainFeatureTokenFile,
		featureToIndexDir, tokenize, nGram, config, labelIndex, textIndex,
		idIndex, targetIndex, posHalfWindowSize, ngramPOS, ngramDependency);
    }
}
