package PartA;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.tartarus.snowball.SnowballStemmer;
import org.tartarus.snowball.ext.englishStemmer;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;


public class Parse extends Thread {
    private HashSet<String> dict_stopWords;
    private HashMap<String, Term> dict_terms;
    private ArrayList<String> list_sortedTerms;
    private List<List<String>> list_termsByAlhabet = new ArrayList<List<String>>();
    private HashMap<String, Integer> dict_months;
    private HashMap<String, String> dict_replaceWords;
    private Doc doc;
    private String[] docText;
    private static Indexer indexer;
    private static CityIndexer indexer_city;
    private SnowballStemmer stemmer;
    private enum wordType {NUMBER, SYMBOL, WORD, CITY, NULL;};
    private String path; // corpus path
    //private String path = "d:\\documents\\users\\eransar\\Downloads\\temp";
    private int numofTerm;
    private int index;
    private int filenum;
    private int debug_size;

    private boolean isSteam;
    private boolean first_chunk;
    private volatile String currentfilename;



    public Parse(String stopWordsPath, String PathOfPosting) throws IOException {
        this.dict_stopWords = new HashSet<String>();
        this.dict_replaceWords = new HashMap<String, String>();
        this.dict_terms = new HashMap<>();
        this.dict_months = init_months();
        this.list_sortedTerms = new ArrayList<String>();
        this.list_termsByAlhabet = new ArrayList<List<String>>();
        this.indexer = new Indexer();
        this.stemmer = new englishStemmer();
        this.indexer_city = CityIndexer.getInstance();
        this.index = 1;
        this.first_chunk = true;
        this.path = PathOfPosting;
        //Initializers
        init_stopWords(stopWordsPath);
        init_replace();
        indexer_city.startConnection();
    }



    public void Reset(){
        indexer = new Indexer();
    }


    /**
     * Parsing A document and filling termsInfo HashMap
     */
    public void ParseDoc(Doc doc, String TEXT) {
        this.doc = doc;
        TEXT = replaceText(TEXT);
        docText = (TEXT.split(" "));
        try {
            startParse();
        } catch (ParseException e) {

        }
    }

    /**
     * removing the chars that not needed
     */
    private String replaceText(String text) {

        StringBuilder sb = new StringBuilder(text);
        for (Map.Entry<String, String> entry : dict_replaceWords.entrySet()) {

            String key = entry.getKey();
            String value = entry.getValue();

            if (text.length() == 0 || (text.charAt(0) == '$' && key.equals("$"))) {
                continue;
            }
            int start = sb.indexOf(key, 0);
            while (start > -1) {
                int end = start + key.length();
                int nextSearchStart = start + value.length();
                sb.replace(start, end, value);
                start = sb.indexOf(key, nextSearchStart);
            }
        }
        return sb.toString();
    }

    /**
     * init the stopwords to buffer
     *
     * @throws IOException
     */
    public void init_stopWords(String path) throws IOException {
        //URL url = getClass().getClassLoader().getResource(path);
        //URL url = getClass().getClassLoader().getResource("stop_words.txt");
        File file = new File(path);
        FileReader fr = new FileReader(file);
        BufferedReader bufferedReader = new BufferedReader(fr);
        StringBuffer stringBuffer = new StringBuffer();
        String line;

        while ((line = bufferedReader.readLine()) != null) {
            if (!line.equals(System.lineSeparator())) {
                dict_stopWords.add(line);
            }
        }
    }

    private void printTerm() {
        int i = 0;
        for (String name : dict_terms.keySet()) {
            String key = name.toString();
            System.out.println(i + ". " + key);
            i++;
        }
    }

    /**
     * doing parse on etch term
     *
     * @throws ParseException
     */
    private void startParse() throws ParseException {

        for (index = 0; index < docText.length; index++) {
            //if it's a line separator. increase line number
//            System.out.println("Begin : "+docText[index]);
            if (docText[index].length() == 0 /*empty string */ || docText[index].equals("-")) {
                continue;
            }
            if (docText[index].charAt(0) == '-' || docText[index].charAt(0) == '.' || docText[index].charAt(0) == '+') {
                docText[index] = docText[index].substring(1);
            }
            //not stopWord
            if (dict_stopWords.contains(docText[index]) || dict_stopWords.contains(docText[index].toUpperCase()) || dict_stopWords.contains(docText[index].toLowerCase())) {
                continue;
            } else {
                if (docText[index].length() == 0 || docText[index].equals(" ")) {
                    continue;
                }
                if (docText[index].equals(doc.getCITY())) {
                    indexer_city.addToCityIndexer(doc, index);
                }
                //check the term type
                wordType type = identifyDoc(docText[index]); // identifying the word
                //what to do by the type
                if (type == wordType.NUMBER) {
                    parseNumber(docText[index], index);
                    long endTime = System.currentTimeMillis();

                } else if (type == wordType.SYMBOL) {
                    parseSymbol(docText[index], index);
                } else if (type == wordType.WORD) {
                    try {
                        parseWord(index);
                    } catch (ParseException e) {
                        e.printStackTrace();
                        return;
                    }
                }
            }
        }

    }

    /**
     * DOING parse on symbol.
     *
     * @param str
     * @param index
     */
    private void parseSymbol(String str, int index) {
        Term tempTerm = new Term();
        tempTerm.setType("Symbol");
        if(str.length()==1){
            tempTerm.setName(str);
            handleTerm(tempTerm);
            return;
        }
        ArrayList<String> first_keywords = getFirstKeyWords();
        NumberFormat format = NumberFormat.getInstance(Locale.ENGLISH);
        String substring = str.substring(1, str.length());

        try {
            Number number = format.parse(substring);
            double number_term = number.doubleValue();
            if (index + 1 < docText.length && first_keywords.contains(docText[index + 1])) {
                switch (docText[index + 1]) {
                    case "million":
                        tempTerm.setName(convertDouble(number_term) + " " + "M" + " " + "Dollars");
                        break;
                    case "billion":
                        tempTerm.setName(convertDouble(number_term * 1000) + " " + "M" + " " + "Dollars");
                        break;
                    default:
                        tempTerm.setName(docText[index] + " " + docText[index + 1]);
                }
            } else if (number_term < 1000000) {
                tempTerm.setName(substring + " " + "Dollars");
            } else
                tempTerm.setName(convertDouble(number_term / 1000000) + " " + "M" + " " + "Dollars");
        } catch (ParseException e) {
            e.printStackTrace();
        }

        handleTerm(tempTerm);

    }

    /**
     * doing parse to word(not symbol && not number)
     *
     * @param index
     * @throws ParseException
     */
    private void parseWord(int index) throws ParseException {
        Term tempTerm = new Term();
        tempTerm.setType("Word");
        if (dict_stopWords.contains(docText[index])) {
            return;
        }
        if (docText[index].contains("-")) {
            tempTerm.setName(docText[index]);
        }
        //month that starts with word example : MAY 19945
        else if (dict_months.containsKey(docText[index]) && isNotOutBound(index + 1) && isNumber(docText[index + 1])) {
            if (dict_months.get(docText[index]) < 10) {
                tempTerm.setName(docText[index + 1] + "-" + "0" + dict_months.get(docText[index]));
            } else {

                tempTerm.setName(docText[index + 1] + "-" + dict_months.get(docText[index]));
            }
        }
        //between number and number
        else if (docText[index].equals("between") && isNotOutBound(index + 1) && isNumber(docText[index + 1])
                && isNotOutBound(index + 2) && docText[index + 2].equals("and") && isNotOutBound(index + 3)
                && isNumber(docText[index + 3])) {
            tempTerm.setName(docText[index] + " " + docText[index + 1] + " " + docText[index + 2] + " " + docText[index + 3]);
        } else {
            //if word is lowercase - check for uppercase in the first letter in the dict_terms map
            if (testAllLowerCase(docText[index]) &&
                    (dict_terms.containsKey(docText[index].substring(0, 1).toUpperCase() + docText[index].substring(1))
                            || dict_terms.containsKey(docText[index].toUpperCase()))) {
                if (dict_terms.containsKey(docText[index].substring(0, 1).toUpperCase() + docText[index].substring(1))) {
                    tempTerm = dict_terms.remove(docText[index].substring(0, 1).toUpperCase() + docText[index].substring(1));
                    tempTerm.setName(docText[index]);
                } else {
                    tempTerm = dict_terms.remove(docText[index].toUpperCase());

                    tempTerm.setName(docText[index]);
                }
            } else if (testAllLowerCase(docText[index]) && dict_terms.containsKey(docText[index].toUpperCase())) {
                return; //dont add lowercase wh
            } else {
                tempTerm.setName(docText[index]);
            }
        }
        handleTerm(tempTerm);
    }

    public boolean testAllLowerCase(String str) {
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c >= 65 && c <= 90) {
                return false;
            }
        }
        return true;
    }

    public boolean isNotOutBound(int i) {
        return i < docText.length;
    }

    /**
     * check what is the type of the term
     *
     * @param str
     * @return
     */
    public wordType identifyDoc(String str) {
        try {
            if (isSymbol(str)) {
//                    System.out.println("Symbol : " + str);
                return wordType.SYMBOL;
            } else if (str.charAt(0) < 48 || str.charAt(0) > 57) {

                return wordType.WORD;
            } else if (isNumber(str)) {
//                    System.out.println("Number : " + str);
                return wordType.NUMBER;
            }
        } catch (ParseException e) {
//                System.out.println("Word : " + str);
            return wordType.WORD;


        }
        return wordType.WORD;
    }

    /**
     * check if the term is symbol(char(0)==$)
     *
     * @param str
     * @return
     * @throws ParseException
     */
    public boolean isSymbol(String str) throws ParseException {

        HashSet<Character> symbols = new HashSet<>();
        symbols.add('$');
        symbols.add('&');
        symbols.add('=');
        if (str.equals("")) {
            return false;
        }
        if (symbols.contains(str.charAt(0))) {
            if(str.length() <2){
                return true;
            }
            else{
                NumberFormat format = NumberFormat.getInstance(Locale.ENGLISH);
                String substring = str.substring(1, str.length());
                Number number = format.parse(substring);
                return true;
            }

        }

        return false;
    }

    /**
     * check if the term is number
     * true if yes | false if not
     *
     * @param str
     * @return
     * @throws ParseException
     */
    private boolean isNumber(String str) throws ParseException {
//        if(str.charAt(0) >=48 && str.charAt(0) <=57){
//            return true;
//        }
//        return false;

        try {
            NumberFormat format = NumberFormat.getInstance(Locale.ENGLISH);
            Number number = format.parse(str);
            double test = number.doubleValue();
        } catch (ParseException e) {
            return false;
        }
        return true;
    }

    /**
     * check if the term is fraction
     *
     * @param str
     * @return
     */
    public boolean isFraction(String str) {

        if (str.contains("/")) {
            String separator = "/";
            String[] new_str = str.split(Pattern.quote(separator));
            if (str.length() <= 2 || new_str.length <= 1) {
                return false;
            }
            NumberFormat format = NumberFormat.getInstance(Locale.ENGLISH);
            try {
                Number first = format.parse(new_str[0]);
                Number second = format.parse(new_str[1]);
                return true;
            } catch (ParseException e) {
                return false;
            }
        }
        return false;
    }



    /*
                     Starting Parsing functions
     */

    /**
     * Handles dict_terms of type Number
     *
     * @param str given String for term
     */
    public void parseNumber(String str, int index) {
        Term tempTerm = new Term();
        tempTerm.setType("Number");
        ArrayList<String> first_keywords = getFirstKeyWords(); // An Array of listed keywords that can appear after a term
//        Term
        HashSet<String> toReturn = new HashSet<>();
        boolean fraction = str.contains("/");
        boolean fractionAndText = fraction && index + 1 < docText.length && first_keywords.contains(docText[index + 1]);  //???
        NumberFormat format = NumberFormat.getInstance(Locale.ENGLISH);
        Number number = null;
        try {
            number = format.parse(docText[index]);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        double number_term = number.doubleValue();
        //has a keyword after the word
        if (!fraction && index + 1 < docText.length && first_keywords.contains(docText[index + 1])) {
            //number + value + U.S + Dollar
            if (index + 2 < docText.length && docText[index + 2].equals("U.S.") && index + 3 < docText.length && docText[index + 3].equals("Dollars")) {
                switch (docText[index + 1]) {
                    case "million":
                        break;
                    case "billion":
                        number_term = number_term * 1000;
                        break;
                    case "trillion":
                        number_term = number_term * 1000000;
                        break;
                }
                tempTerm.setName(convertDouble(number_term) + " " + "M" + " " + "Dollars");
//                    toReturn.add(convertDouble(number_term) + " " + "M" + " " + "Dollars");
            } else {
                if (!fraction) {
                    switch (docText[index + 1]) {
                        case "Thousand":
                            number_term = number_term;
                            tempTerm.setName(convertDouble(number_term) + "K");
//                                toReturn.add(convertDouble(number_term) + "K");
                            break;
                        case "Million":
                            number_term = number_term;
                            tempTerm.setName(convertDouble(number_term) + "M");
//                                toReturn.add(convertDouble(number_term) + "M");
                            break;
                        case "Trillion":
                        case "Billion":
                            number_term = number_term;
                            tempTerm.setName(convertDouble(number_term) + "B");
//                                toReturn.add(convertDouble(number_term) + "B");
                            break;
                        case "percent":
                        case "percentage":
                            tempTerm.setName(convertDouble(number_term) + "%");
//                                toReturn.add(convertDouble(number_term) + "%");
                            break;
                        case "Dollars":
                            tempTerm.setName(transformNumber(number_term));
//                                toReturn.add(transformNumber(number_term));
                            break;

                    }
                }


            }
        }
        //Fraction
        else if (index + 1 < docText.length && isFraction(docText[index + 1])) {
            if (number_term < 1000000 && index + 2 < docText.length && docText[index + 2].equals("Dollars")) {
                tempTerm.setName(convertDouble(number_term) + " " + docText[index + 1] + " " + "Dollars");
//                    toReturn.add(convertDouble(number_term) + " " + docText[index + 1] + " " + "Dollars");
                index = index++; //skip the next word in the document :O
//                    Parser.setIndex(index + 1);
            } else {
                tempTerm.setName(convertDouble(number_term) + " " + docText[index + 1]);
//                    toReturn.add(convertDouble(number_term) + " " + docText[index + 1]);
            }

//                return toReturn; // ??? check why //TODO : Check why !

        } else if (fraction) {
            if (index + 1 < docText.length && first_keywords.contains(docText[index + 1])) {
                tempTerm.setName(docText[index] + " " + docText[index + 1]);
//                    toReturn.add(docText[index] + " " + docText[index + 1]);
            } else {
                tempTerm.setName(docText[index]);
//                    toReturn.add(docText[index]);
            }

        } else if (index + 1 < docText.length && init_months().containsKey(docText[index + 1])) {
            String month = "" + init_months().get(docText[index + 1]);
            if (init_months().get(docText[index + 1]) < 10) {
                month = "0" + init_months().get(docText[index + 1]);
            }
            if (number_term < 10) {
                tempTerm.setName(month + "-" + "0" + convertDouble(number_term));
//                    toReturn.add(month + "-" + "0" + convertDouble(number_term));
            } else {
                tempTerm.setName(month + "-" + convertDouble(number_term));
//                    toReturn.add(month + "-" + convertDouble(number_term));
            }
        } else {
            if (!fraction) {
                if (number_term >= 1000 && number_term < 1000000) {
                    tempTerm.setName(convertDouble(number_term / 1000) + "K");
//                        toReturn.add(convertDouble(number_term / 1000) + "K");
                } else if (number_term >= 1000000 && number_term < 1000000000) {
                    tempTerm.setName(convertDouble(number_term / 1000000) + "M");
//                        toReturn.add(convertDouble(number_term / 1000000) + "M");
                } else if (number_term >= 1000000000) {
                    tempTerm.setName(convertDouble(number_term / 1000000000) + "B");
//                        toReturn.add(convertDouble(number_term / 1000000000) + "B");
                } else {
                    tempTerm.setName(convertDouble(number_term));
//                        toReturn.add(convertDouble(number_term));
                }
            }

        }

        handleTerm(tempTerm); //
    }

//    public HashMap<Term, HashMap<Doc, Integer>> getTermsInfo() {

    public HashMap<String, Term> getDict_terms() {
        return dict_terms;
    }
    //    }

    public void setStem(boolean steam) {
        isSteam = steam;
    }

    private void handleTerm(Term toCheck) {
        if(toCheck.getName().length()==0){
            return;
        }
        if(toCheck.getName().charAt(0) >=48 && toCheck.getName().charAt(0) <=57){
            toCheck.setType("Number");
        }
        /**
         * Stemming
         */
        if (isSteam) {
            stemmer.setCurrent(toCheck.getName());
            if (stemmer.stem()) {
                toCheck.setName(stemmer.getCurrent());
            }
        }

        /*
            if found term not avilable in the term list && not a stop word
                set doc frequency of the term to 1
                increase doc distnict term by 1
                add to dict_terms
                set corpus frequency to 1
                set df to 1
                insert it to the dict_terms with the document found.
                set doc frequency
                set term location in doc
         */
        if (dict_terms.get(toCheck.getName()) == null) {
            //if (!dict_stopWords.contains(toCheck.getName()) && !dict_stopWords.contains(toCheck.getName().toUpperCase()) && !dict_stopWords.contains(toCheck.getName().toLowerCase())) {
            toCheck.getDocFrequency().put(doc, 1);
//            ArrayList<Integer> doclocations = new ArrayList<>();
//            doclocations.add(index);
//            toCheck.getDoclocations().put(doc,doclocations);
            doc.setDistinctwords(doc.getDistinctwords() + 1);
            toCheck.setDf(1);
            dict_terms.put(toCheck.getName(), toCheck);
//                 System.out.println("New Term : "+toCheck.getName());
            //}
        }
        // if found term already exists in the term list:
            /*
                Get the term from the list
                increase it's corpus frequency
                see if the term has current doc in it's docfreq hashmap
                    if not - add it, increase df,
                    if it does - increase the freq by 1, see if freq > max tf
                add the term back to the hashmap and overwrite the old term
             */
        else {
//             System.out.println(toCheck.getName());
            Term UsedTerm = dict_terms.get(toCheck.getName());
            UsedTerm.setDf(UsedTerm.getDf() + 1);
            if (UsedTerm.getDocFrequency().get(doc) == null) {
                UsedTerm.getDocFrequency().put(doc, 1);
//                ArrayList<Integer> doclocations = new ArrayList<>();
//                doclocations.add(index);
//                UsedTerm.getDoclocations().put(doc,doclocations);
//                System.out.println("Used Term "+UsedTerm.getName());
            } else {

                UsedTerm.getDocFrequency().put(doc, UsedTerm.getDocFrequency().get(doc) + 1);
                updateDocMaxTf(UsedTerm.getDocFrequency().get(doc));
//                ArrayList<Integer> doclocations = UsedTerm.getDoclocations().get(doc);
//                doclocations.add(index);
//                UsedTerm.getDoclocations().put(doc,doclocations);
//                System.out.println("Used Term "+UsedTerm.getName());
            }
        }
    }

    //        return termsInfo;

    /*
                    Begining of Utilities functions
     */
    public void updateDocMaxTf(int term_tf) {
        if (doc.getMaxtf() < term_tf) {
            doc.setMaxtf(term_tf);
        }
    }


//    public void SaveToDisk() {
//        ArrayList<String> sorted_arraylist = new ArrayList<String>(dict_terms.keySet());
//        Collections.sort(sorted_arraylist);
//
//        numofTerm += dict_terms.size();
//        int counter = 0;
//
//        System.out.println(dict_terms.size());
//        try {
//            File statText = new File(path + filenum + ".txt");
//            FileOutputStream is = new FileOutputStream(statText);
//            OutputStreamWriter osw = new OutputStreamWriter(is);
//            StringBuilder termdocs = new StringBuilder();
//            Writer w = new BufferedWriter(osw);
//            for (String term_name : sorted_arraylist) {
//                Term temp = dict_terms.get(term_name);
//                for (Map.Entry<Doc, Integer> doc : temp.getDocFrequency().entrySet()) {
//                    termdocs.append(" " + doc.getKey().getDOCNO() + "," + doc.getValue() + "," + doc.getKey().getFile());
//                }
//                w.append(temp.getDf() + " " + termdocs + System.lineSeparator());
////
//                indexer.addToHashMap(term_name, filenum + " " + counter);
//                counter++;
//                termdocs = new StringBuilder();
//            }
//
////            for (Map.Entry<String,Term> term : dict_terms.entrySet()){
////                for(Map.Entry<Doc,Integer> doc : term.getValue().getDocFrequency().entrySet()){
////                    termdocs.append(" "+doc.getKey().getDOCNO()+","+doc.getValue()+","+doc.getKey().getFile());
////                }
////
////                w.append(term.getValue().getName()+" "+term.getValue().getDf()+" "+termdocs+System.lineSeparator());
////
////                indexer.addToHashMap(term.getKey(),filenum+" "+counter);
////                counter++;
////                termdocs = new StringBuilder();
////            }
//
//            w.close();
//        } catch (Exception IOException) {
//            IOException.printStackTrace();
//        }
//        filenum++;
//        dict_terms.clear();
//    }


    public void writeToDisk() throws IOException {
        if (first_chunk) {
            indexer.initFiles(this.path);
            first_chunk = false;
        }
        //sort the dictionary
        list_sortedTerms = new ArrayList<String>(dict_terms.keySet());
//        Collections.sort(list_sortedTerms);

        fillAlphabetArrays(list_sortedTerms);
        //get data from files
//        ExecutorService pool = Executors.newFixedThreadPool(2,new FileThreadFactory("n"));
        HashSet<String> file_names = indexer.getFile_names();
        for (String file_name : file_names)
        {
            int place = indexer.getDict_files().get(file_name);
            File toOpen = new File(path+File.separator+"\\"+file_name+".txt");
            BufferedReader in = new BufferedReader(new FileReader(toOpen));
            String str;
            //opening file and adding it all to array
            List<String> file_content = new ArrayList<String>();
            while((str = in.readLine()) != null){
                file_content.add(str);
            }
            in.close();

            file_content=mergeArrays(list_termsByAlhabet.get(place),file_content, file_name);
            File toWrite = new File(path+File.separator+"\\"+file_name+".txt");
            FileWriter writer = new FileWriter(toWrite,false);

            for (int i = 0; i < file_content.size() ; i++) {
                writer.write(file_content.get(i)+System.lineSeparator());
            }
            writer.close();
            System.out.println("File : "+file_name+" Size : "+file_content.size());
            debug_size+=file_content.size();

//            pool.submit(this);
        }
//        writeDictionaryDebug();


        dict_terms.clear();
        list_termsByAlhabet.clear();

        File p = new File(path);
        System.out.println("Folder Size : "+FileUtils.sizeOf(p));
        System.out.println("Dictionary size : "+indexer.getDictionary().size());
        System.out.println("Number wrote to disk : "+debug_size);
        debug_size=0;
//        File path = new File("C:\\Users\\eransar\\AppData\\Local\\Temp\\0.txt");


    }

    private void writeDictionaryDebug() {
        try {
            indexer.printTofile(path);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private List<String> mergeArrays(List<String> chunk_content , List<String> file_content, String filename) {
        for (int i = 0; i < chunk_content.size() ; i++) {

            Pointer find_location=indexer.isExist(chunk_content.get(i));
            if(find_location==null){

                Term OtherTerm=dict_terms.get(chunk_content.get(i));
                StringBuilder termData = new StringBuilder("");
                for (Map.Entry<Doc, Integer> _doc : OtherTerm.getDocFrequency().entrySet()){

                    termData.append("|"+_doc.getKey().getDOCNO()+","+_doc.getValue()+","+_doc.getKey().getFile()); //DOCNO,FrequencyInDoc,File Name of Doc
                }
                file_content.add(OtherTerm.getDf()+" "+termData);
                Pointer OtherPointer = new Pointer(filename,file_content.size()-1,OtherTerm.getDf());
                indexer.getDictionary().put(OtherTerm.getName(),OtherPointer);
//                indexer.addToHashMap(OtherTerm.getName(),filename+" "+(file_content.size()-1));

            }
            else {
                //find index and change the line

                String lineToChange = null;
                lineToChange = file_content.get(indexer.getLineNumber(chunk_content.get(i)));

                Term OtherTerm = dict_terms.get(chunk_content.get(i));
                String[] currentline = lineToChange.split(" ");
                int currentdf=Integer.parseInt(currentline[0]);
                int chunkdf = OtherTerm.getDf();
                int newdf=currentdf+chunkdf;
                StringBuilder termData = new StringBuilder("");
                for (Map.Entry<Doc, Integer> _doc : OtherTerm.getDocFrequency().entrySet()){
                    termData.append("|"+_doc.getKey().getDOCNO()+","+_doc.getValue()+","+_doc.getKey().getFile());
                }
                file_content.set(indexer.getLineNumber(chunk_content.get(i)),newdf+" "+currentline[1]+"|"+termData);
                Pointer p1 =indexer.getDictionary().get(OtherTerm.getName());


                indexer.getDictionary().put(OtherTerm.getName(),new Pointer(filename,p1.getLine_number(),newdf));

            }
        }
        return file_content;
    }

    private void fillAlphabetArrays(ArrayList<String> list_sortedTerms) {
        for (int i = 0; i <31 ; i++) {
            list_termsByAlhabet.add(new ArrayList<String>());
        }
        int place=0;
        for (int i = 0; i < list_sortedTerms.size() ; i++) {
            switch ((dict_terms.get(list_sortedTerms.get(i)).getType())){
                case "Number":
                    place = indexer.getDict_files().get("numbers");
                    list_termsByAlhabet.get(place).add(list_sortedTerms.get(i));
                    break;
                case "Symbol":
                    place = indexer.getDict_files().get("symbols");
                    list_termsByAlhabet.get(place).add(list_sortedTerms.get(i));
                    break;
                case "City":
                    place = indexer.getDict_files().get("cities");
                    list_termsByAlhabet.get(place).add(list_sortedTerms.get(i));
                    break;
                default:

                    try {
                        place = indexer.getDict_files().get(""+list_sortedTerms.get(i).toLowerCase().charAt(0));
                    } catch (Exception e) {
                        place = indexer.getDict_files().get("others");
                    }

                    list_termsByAlhabet.get(place).add(list_sortedTerms.get(i));


            }

        }
    }

    @Override
    public void run() {
        System.out.println(Thread.currentThread().getName());


    }

    public int findSpaceIndex(String str) {
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == ' ') {
                return i;
            }

        }
        return -1;
    }


    /**
     * Convert Double to String without leaving Zero Trails behind
     *
     * @param d given double
     * @return formatted string
     */
    public String convertDouble(double d) {
        String result = "" + d;
        return result = result.indexOf(".") < 0 ? result : result.replaceAll("0*$", "").replaceAll("\\.$", "");
    }

    /**
     * Helper function to convert double to string with additional parameters
     *
     * @param number given double
     * @return
     */
    public String transformNumber(double number) {
        if (number >= 1000000) {
            number = number / 1000000;
            return convertDouble(number) + " " + "M" + " " + "Dollars";
        }
        return number + " " + "Dollars";
    }

    /**
     * List of all dict_months by names and order
     *
     * @return
     */
    public HashMap<String, Integer> init_months() {

        HashMap<String, Integer> parse_months = new HashMap<String, Integer>();
        parse_months.put("JAN", 1);
        parse_months.put("Jan", 1);
        parse_months.put("JANUARY", 1);
        parse_months.put("January", 1);
        parse_months.put("FEB", 2);
        parse_months.put("Feb", 2);
        parse_months.put("February", 2);
        parse_months.put("FEBRUARY", 2);
        parse_months.put("Mar", 3);
        parse_months.put("MAR", 3);
        parse_months.put("March", 3);
        parse_months.put("MARCH", 3);
        parse_months.put("Apr", 4);
        parse_months.put("APR", 4);
        parse_months.put("April", 4);
        parse_months.put("APRIL", 4);
        parse_months.put("May", 5);
        parse_months.put("MAY", 5);
        parse_months.put("June", 6);
        parse_months.put("JUNE", 6);
        parse_months.put("July", 7);
        parse_months.put("JULY", 7);
        parse_months.put("Aug", 8);
        parse_months.put("AUG", 8);
        parse_months.put("August", 8);
        parse_months.put("AUGUST", 8);
        parse_months.put("Sept", 9);
        parse_months.put("SEPT", 9);
        parse_months.put("September", 9);
        parse_months.put("SEPTEMBER", 9);
        parse_months.put("Oct", 10);
        parse_months.put("OCT", 10);
        parse_months.put("October", 10);
        parse_months.put("OCTOBER", 10);
        parse_months.put("Nov", 11);
        parse_months.put("NOV", 11);
        parse_months.put("November", 11);
        parse_months.put("NOVEMBER", 11);
        parse_months.put("Dec", 12);
        parse_months.put("DEC", 12);
        parse_months.put("December", 12);
        parse_months.put("DECEMBER", 12);
        return parse_months;
    }

    public int terms_size() {
        return dict_terms.size();
    }

    public void setDoc(Doc doc) {
        this.doc = doc;
    }

    public Doc getDoc() {

        return doc;
    }

    public String getPath() {
        return path;
    }


    public int getNumofTerm() {
        return numofTerm;
    }

    public ArrayList<String> getFirstKeyWords() {
        ArrayList<String> keywords = new ArrayList<String>();
        keywords.add("Thousand");
        keywords.add("Million");
        keywords.add("Trillion");
        keywords.add("Billion");
        keywords.add("percent");
        keywords.add("percentage");
        keywords.add("Dollars");
        keywords.add("billion");
        keywords.add("million");
        keywords.add("trillion");
        return keywords;
    }
    private void init_replace() {
        dict_replaceWords.put(",", "");
        dict_replaceWords.put("\n\n", " ");
        dict_replaceWords.put("\\r\\n", " ");
        dict_replaceWords.put("\t", " ");
        dict_replaceWords.put("." + "\n", " ");
        dict_replaceWords.put(".)", "");
        dict_replaceWords.put(")", " ");
        dict_replaceWords.put("(", " ");
        dict_replaceWords.put(" '", " ");
        dict_replaceWords.put("' ", " ");
        dict_replaceWords.put(": ", " ");
        dict_replaceWords.put(". \n", " ");
        dict_replaceWords.put(". ", " ");
        dict_replaceWords.put(" .", " ");
        dict_replaceWords.put(".) ", " ");
        dict_replaceWords.put("--", " ");
        dict_replaceWords.put("- ", " ");
        dict_replaceWords.put(";", " ");
        dict_replaceWords.put(";\n", " ");
        dict_replaceWords.put("[", "");
        dict_replaceWords.put("]", "");
        dict_replaceWords.put("'", "");
        dict_replaceWords.put("'s", "");
        dict_replaceWords.put("-\n", "");
        dict_replaceWords.put("\"", "");
        dict_replaceWords.put("?", "");
        dict_replaceWords.put(".\"", "");
        dict_replaceWords.put(".,", "");
        dict_replaceWords.put("!", "");
        dict_replaceWords.put("\n", " ");
        dict_replaceWords.put("\\", " ");
        dict_replaceWords.put("//", " ");
        dict_replaceWords.put("*", " ");
        dict_replaceWords.put("+/", " ");
        dict_replaceWords.put(" -", " ");
        dict_replaceWords.put("....", " ");
        dict_replaceWords.put("...", " ");
        dict_replaceWords.put("..", " ");
        dict_replaceWords.put("|", " ");
        dict_replaceWords.put("#", " ");
    }
}

