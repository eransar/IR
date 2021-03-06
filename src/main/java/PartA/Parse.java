package PartA;

import java.io.*;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Parse class is used to analyze a given text and format it by the rules of the search engine.
 */
public class
Parse {
    private HashSet<String> dict_stopWords;
    private HashMap<String, Integer> dict_months;
    private LinkedHashMap<String, String> dict_replaceWords;
    private HashMap<String,Integer> dic_docterms;
    private Doc doc;
    private String[] docText;
    private static Indexer indexer;
    private static CityIndexer indexer_city;
    private Stemmer stemmer;
    private String path; // corpus path
    private int index;
    private int docmaxtf;
    private boolean isSteam;
    public boolean use_searcher;
    public enum wordType {NUMBER, SYMBOL, WORD};
    private ArrayList<Term> queryTerms;




    public Parse(String stopWordsPath, String PathOfPosting) throws IOException {
        this.indexer = Indexer.getInstance();
        this.dict_months = init_months();
        this.dict_stopWords = new HashSet<String>();
        this.dict_replaceWords = new LinkedHashMap<>();
        indexer.setPath(PathOfPosting);
        this.indexer_city = CityIndexer.getInstance();
        this.index = 1;
        this.stemmer=Stemmer.getInstance();
        this.path = PathOfPosting;
        this.use_searcher=false;
        //Initializers
        init_stopWords(stopWordsPath);
        init_replace();
        indexer_city.startConnection();
    }

    public Parse(boolean use_searcher){
        this.indexer = Indexer.getInstance();
        this.dict_months = init_months();
        this.dict_stopWords = new HashSet<String>();
        this.dict_replaceWords = new LinkedHashMap<>();
        queryTerms = new ArrayList<>();
        this.indexer_city = CityIndexer.getInstance();
        this.index = 0;
        this.stemmer=Stemmer.getInstance();
        this.use_searcher=use_searcher;
        //Initializers
        init_replace();
        //indexer_city.startConnection();
    }




    /**
     * Parsing A document and filling termsInfo HashMap
     */
    public void ParseDoc(Doc doc, String TEXT) {
        int doc_size = TEXT.length();
        doc.setLENGTH(doc_size);
        if(!use_searcher){
            indexer.addtoAvg(doc_size);
        }
        docmaxtf = 0;
        dic_docterms = new HashMap<>();
        this.doc = doc;
        TEXT = replaceText(TEXT);
        docText = (TEXT.split(" "));
        try {
            startParse();
        } catch (ParseException e) {

        }
        if (!use_searcher) {
            if (!doc.getLANGUAGE().equals("")) {
                indexer.getSet_languages().add(doc.getLANGUAGE());
            }
            filterEntities();
            doc.init_TreeSet();
            doc.init_arrEntities();
            doc.ClearEntitiesSet();
            doc.ClearEntitiesTreeSet();
            indexer.getDict_docs().put(this.doc.getDOCNO(), this.doc);
        }
    }

    public ArrayList<Term> parseQuery(String query) {
        query = replaceText(query);
        String[] words = query.split(" ");
        ArrayList terms = new ArrayList();
        for (int i = 0; i < words.length; i++) {
            if (words[i].length() == 0 /*empty string */ || words[i].equals("-")) {
                return null;
            }

            if (words[i].charAt(0) == '-' || words[i].charAt(0) == '.' || words[i].charAt(0) == '+') {
                words[i] = words[i].substring(1);
            }

            wordType wordType = identifyWord(words[i]);
            switch (wordType) {
                case WORD:
//                    terms.add(parseWord(words[i]);)
                    break;
                case NUMBER:

                    break;
                case SYMBOL:
            }
        }

        return null;

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
                dict_stopWords.add(line.toLowerCase());
            }
        }
    }


    /**
     * This method start the parse for the current doc.
     *
     * @throws ParseException
     */
    private void startParse() throws ParseException {

        for (index = 0; index < docText.length; index++) {
            //if it's a line separator. increase line number

            if (docText[index].length() == 0 /*empty string */ || docText[index].equals("-")) {
                continue;
            }
            if (docText[index].charAt(0) == '-' || docText[index].charAt(0) == '.' || docText[index].charAt(0) == '+') {
                docText[index] = docText[index].substring(1);
            }
            //not stopWord
            //if (dict_stopWords.contains(docText[index]) || dict_stopWords.contains(docText[index].toUpperCase()) || dict_stopWords.contains(docText[index].toLowerCase())) {
            //  continue;}
            // } else {
            if (docText[index].length() == 0 || docText[index].equals(" ")) {
                continue;
            }
            if (!use_searcher && docText[index].equals(doc.getCITY())) {
                indexer_city.addToCityIndexer(doc, index);
            }
            //check the term type
            wordType type = identifyWord(docText[index]); // identifying the word
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



    /**
     * After term identified as symbol this method will parse by symbol rules.
     *
     * @param str
     * @param index
     */
    private void parseSymbol(String str, int index) {
        Term tempTerm = new Term();
        tempTerm.setType("Symbol");
        if (str.length() == 1) {
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
        if (use_searcher) {
            queryTerms.add(tempTerm);
        } else {
            handleTerm(tempTerm);
        }
    }


    /**
     * After term identified as word this method will parse it by word rules.
     *
     * @param index
     * @throws ParseException
     */
    private void parseWord(int index) throws ParseException {
        Term tempTerm = new Term();
        tempTerm.setType("Word");
        if (docText[index].contains("-")) {
            tempTerm.setName(docText[index]);
        } else if (docText[index].equals("Street")) {
            if ((index - 2) >= 0 && isNumber(docText[index - 2]) && !isNumber(docText[index - 1]) && !isSymbol(docText[index - 1])) {
                tempTerm.setName(docText[index - 2] + " " + docText[index - 1] + " " + docText[index]);
            }
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
        }
        //if word is lowercase - check for uppercase in the first letter in the dict_cache ma
        else {
            tempTerm.setName(docText[index]);
        }
        if (!use_searcher) {
            handleTerm(tempTerm);
        } else {
            queryTerms.add(tempTerm);
        }

    }


    /**
     * Find if a string is all lowercase
     * @param str
     * @return
     */
    public boolean testAllLowerCase(String str) {
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c >= 65 && c <= 90) {
                return false;
            }
        }
        return true;
    }

    /**
     * find if string is out of range of main string array
     * @param i
     * @return
     */
    public boolean isNotOutBound(int i) {
        return i < docText.length;
    }

    /**
     * check what is the type of the term
     *
     * @param str
     * @return
     */
    public wordType identifyWord(String str) {
        try {
            if (isSymbol(str)) {
                return wordType.SYMBOL;
            } else if (str.charAt(0) < 48 || str.charAt(0) > 57) {

                return wordType.WORD;
            } else if (isNumber(str)) {
                return wordType.NUMBER;
            }
        } catch (ParseException e) {
            return wordType.WORD;


        }
        return wordType.WORD;
    }

    /**
     * Check if the term is a symbol
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
     * Check if the given string is a number.
     *
     * @param str given string
     * @return
     * @throws ParseException
     */
    private boolean isNumber(String str) throws ParseException {

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
     * check if a given string is a fraction
     * @param str given string
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
       ***************Starting Parsing functions*********************
     */

    /**
     * Parsing a string that identified as a number and send it to handleTerm.
     * @param str given String of the term
     * @param index given index of the term in the cache dictionary
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

                            break;
                        case "Million":
                            number_term = number_term;
                            tempTerm.setName(convertDouble(number_term) + "M");

                            break;
                        case "Trillion":
                        case "Billion":
                            number_term = number_term;
                            tempTerm.setName(convertDouble(number_term) + "B");

                            break;
                        case "percent":
                        case "percentage":
                            tempTerm.setName(convertDouble(number_term) + "%");

                            break;
                        case "Dollars":
                            tempTerm.setName(transformNumber(number_term));

                            break;

                    }
                }


            }
        }
        //Fraction
        else if (index + 1 < docText.length && isFraction(docText[index + 1])) {
            if (number_term < 1000000 && index + 2 < docText.length && docText[index + 2].equals("Dollars")) {
                tempTerm.setName(convertDouble(number_term) + " " + docText[index + 1] + " " + "Dollars");
                index = index++; //skip the next word in the document :O

            } else {
                tempTerm.setName(convertDouble(number_term) + " " + docText[index + 1]);

            }


        } else if (fraction) {
            if (index + 1 < docText.length && first_keywords.contains(docText[index + 1])) {
                tempTerm.setName(docText[index] + " " + docText[index + 1]);

            } else {
                tempTerm.setName(docText[index]);

            }

        } else if (index + 1 < docText.length && dict_months.containsKey(docText[index + 1])) {
            String month = "" + dict_months.get(docText[index + 1]);
            if (dict_months.get(docText[index + 1]) < 10) {
                month = "0" + dict_months.get(docText[index + 1]);
            }
            if (number_term < 10) {
                tempTerm.setName(month + "-" + "0" + convertDouble(number_term));

            } else {
                tempTerm.setName(month + "-" + convertDouble(number_term));

            }
        } else {
            if (!fraction) {
                if (number_term >= 1000 && number_term < 1000000) {
                    tempTerm.setName(convertDouble(number_term / 1000) + "K");
                } else if (number_term >= 1000000 && number_term < 1000000000) {
                    tempTerm.setName(convertDouble(number_term / 1000000) + "M");
                } else if (number_term >= 1000000000) {
                    tempTerm.setName(convertDouble(number_term / 1000000000) + "B");
                } else {
                    tempTerm.setName(convertDouble(number_term));
                }
            }

        }

        if(use_searcher){
            queryTerms.add(tempTerm);
        }
        else{
            handleTerm(tempTerm); //
        }

    }

    public void HandleHeader(String header) {
        String tmp = replaceText(header);
        String[] headerSplit = tmp.split(" ");
        StringBuilder temp = new StringBuilder("");
        for (String s : headerSplit) {
            if (!dict_stopWords.contains(s.toLowerCase())) {
                /*** Stemming***/
                if (isSteam) {
                    stemmer.setCurrent(s);
                    if (stemmer.stem()) {
                        temp.append(" "+stemmer.getCurrent());
                    }
                } else {
                    temp.append(" "+s);
                }
            }
        }
        doc.setHEADER(temp.toString());
    }




    private void handleTerm(Term toCheck) {
        if(toCheck.getName().length()==0){
            return;
        }
        if(toCheck.getName().charAt(0) >=48 && toCheck.getName().charAt(0) <=57){
            toCheck.setType("Number");
        }

        if (!dict_stopWords.contains(toCheck.getName()) && !dict_stopWords.contains(toCheck.getName().toUpperCase()) && !dict_stopWords.contains(toCheck.getName().toLowerCase())) {
            /**
             * Stemming
             */
            if (isSteam) {
                stemmer.setCurrent(toCheck.getName());
                if (stemmer.stem()) {
                    toCheck.setName(stemmer.getCurrent());
                }
            }
            if (toCheck.getType().equals("Word") && toCheck.getName().charAt(0) >= 65 && toCheck.getName().charAt(0) <= 90) {
                updateCacheDicationary(indexer.getDict_capitals(), toCheck);
                doc.addEntity(toCheck);
            } else {
                updateCacheDicationary(indexer.getDict_cache(), toCheck);
            }


        /*
            if found term not avilable in the term list && not a stop word
                set doc frequency of the term to 1
                increase doc distnict term by 1
                add to dict_cache
                set corpus frequency to 1
                set df to 1
                insert it to the dict_cache with the document found.
                set doc frequency
                set term location in doc
         */
        }
    }

    private void insertOnlyCapitalForRank(Term toCheck) {

    }

    public void updateCacheDicationary(HashMap<String,Term> dict, Term toCheck){
        if(!dic_docterms.containsKey(toCheck.getName().toLowerCase())){
            doc.setDistinctwords(doc.getDistinctwords() + 1);
            dic_docterms.put(toCheck.getName(),1);
            updateDocMaxTf(1);
        }
        else{
            int tf = dic_docterms.get(toCheck.getName().toLowerCase())+1;
            dic_docterms.put(toCheck.getName().toLowerCase(),tf);
            updateDocMaxTf(tf);
        }

        if (dict.get(toCheck.getName()) == null) {
            toCheck.getDocFrequency().put(doc, 1);
            toCheck.setDf(1);
            dict.put(toCheck.getName(), toCheck);

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

            Term UsedTerm = dict.get(toCheck.getName());
            UsedTerm.setDf(UsedTerm.getDf() + 1);
            if (UsedTerm.getDocFrequency().get(doc) == null) {
                UsedTerm.getDocFrequency().put(doc, 1);

            } else {

                UsedTerm.getDocFrequency().put(doc, UsedTerm.getDocFrequency().get(doc) + 1);
                updateDocMaxTf(UsedTerm.getDocFrequency().get(doc));

            }
        }
    }


    /*
                    Begining of Utilities functions
     */
    public void updateDocMaxTf(int term_tf) {
        if (docmaxtf < term_tf) {
            doc.setMaxtf(term_tf);
            docmaxtf=term_tf;
        }
    }

    public boolean isAllUpperCase(String toCheck){

        for (int i = 0; i <toCheck.length() ; i++) {
            if(!(toCheck.charAt(i) >=65 && toCheck.charAt(i)<=90)){
                return false;
            }
        }
        return true;
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



    public String getPath() {
        return path;
    }

    /**
     * ArrayList of Keywords that needed for the parsing rules in the Parser.
     * @return
     */
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

    /**
     * Initialize the replace words HashMap
     */
    private void init_replace() {
        dict_replaceWords.put("'s", " ");
        dict_replaceWords.put(",", " ");
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



    //<editor-fold desc="Getters and Setters">
    public void setStem(boolean steam) {
        isSteam = steam;
    }

    public void setDoc(Doc doc) {
        this.doc = doc;
    }

    public Doc getDoc() {

        return doc;
    }

    public void filterEntities() {
        List<Term> toRemove = new ArrayList();
        for (Term Entity : doc.getMap_entities().values()) {
            String tmp = Entity.getName().toLowerCase();
                if (dic_docterms.containsKey(tmp)) {
                    Entity.setName(tmp);
                    toRemove.add(Entity);
                }
        }
        for (Term t : toRemove){
            doc.getMap_entities().remove(t.getName().toUpperCase());
        }
    }


    public ArrayList<Term> getQueryTerms(boolean stemming) {
        if(stemming){
            for(int i=0 ; i<queryTerms.size() ; i++){
                stemmer.setCurrent(queryTerms.get(i).getName());
                if(stemmer.stem()){
                    Term t= new Term(queryTerms.get(i));
                    t.setName(stemmer.getCurrent());
                    queryTerms.set(i,t);
                }
            }
        }


        for (int i = 0; i <queryTerms.size() ; i++) {
            if(indexer.getDictionary().get(queryTerms.get(i).getName().toLowerCase())!=null){
                Term Otherterm = queryTerms.get(i);
                Otherterm.setName(Otherterm.getName().toLowerCase());
                queryTerms.set(i,Otherterm);
            }
            else if (indexer.getDictionary().get(queryTerms.get(i).getName().toUpperCase())!=null){
                Term Otherterm = queryTerms.get(i);
                Otherterm.setName(Otherterm.getName().toUpperCase());
                queryTerms.set(i,Otherterm);
            }
            else{
                queryTerms.remove(i);
            }
        }
        return queryTerms;
    }

    public void setQueryTerms(ArrayList<Term> queryTerms) {
        this.queryTerms = queryTerms;
    }

    //</editor-fold>
}

