package PartA.Ranking;

import PartA.Doc;
import PartA.Term;

import java.util.*;
import java.util.stream.Collectors;

public class Ranker {
    private HashMap<Doc,ArrayList<Term>> map_docs;
    private HashMap<String,RankingObject> map_ranked_docs; //String is the name of the DOC ( DOCNO)
    private TreeSet<RankingObject> sorted_rankingobject;
    private boolean semantics;


    public Ranker(){
        map_docs = new HashMap<>();
        map_ranked_docs = new HashMap<>();
        semantics = false;
        this.sorted_rankingobject=new TreeSet<>();

    }

    public void calculateBM25(){
        for (RankingObject rank: map_ranked_docs.values()){
            BM25 bm25 = new BM25(rank);
            rank.setRank(bm25.calculate());
        }

    }
    public void sortSet(){

       sorted_rankingobject.addAll(map_ranked_docs.values());
    }

    public HashMap<Doc, ArrayList<Term>> getMap_docs() {
        return map_docs;
    }

    public void setMap_docs(HashMap<Doc, ArrayList<Term>> map_docs) {
        this.map_docs = map_docs;
    }

    public HashMap<String, RankingObject> getMap_ranked_docs() {
        return map_ranked_docs;
    }

    public void setMap_ranked_docs(HashMap<String, RankingObject> map_ranked_docs) {
        this.map_ranked_docs = map_ranked_docs;
    }

    public boolean isSemantics() {
        return semantics;
    }

    public void setSemantics(boolean semantics) {

        this.semantics = semantics;
    }
}