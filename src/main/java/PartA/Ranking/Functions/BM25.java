package PartA.Ranking.Functions;

import PartA.*;
import PartA.Ranking.RankingInstance;
import PartA.Ranking.RankingObject;

import java.util.ArrayList;

/**
 * Ranking by BM25 function
 */
public class BM25 extends ARankerFunction{

    private final double b = 0.45;
    private final double k = 1.2;
    private int M ;
    private RankingObject rankingObject;
    private Indexer indexer;


    public BM25(RankingObject rankingObject){
        super(rankingObject);
        this.indexer=Indexer.getInstance();
        this.M = indexer.getDict_docs().size();
        this.rankingObject = rankingObject;
    }

    /**
     * Calculate by BM25 function and returns the result
     * @return
     */
    public double calculate(){
        double result=0;
        int average_doc=indexer.getDocsaverage();
        for (RankingInstance in : rankingObject.getTerms_data().values()){
            int count_wordinquery=in.getCount_query();
            int count_wordindoc=in.getCount_doc();
            int word_df= (indexer.getDictionary().get(in.getTerm_name()).getTerm_df());
            result += count_wordinquery*
                    (((k+1)*count_wordindoc) /(count_wordindoc+k*(1-b+b*((rankingObject.getLength())/(average_doc)))))* Math.log10((M+1)/(word_df));
        }
        return result;
    }

}

