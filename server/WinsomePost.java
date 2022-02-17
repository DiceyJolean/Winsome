package server;

import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class WinsomePost{

    private int idPost; // Id del post
    private String title; // Titolo del post
    private String author; // Autore del post
    private String content; // Contenuto (testo) del post
    private ConcurrentHashMap<String, Integer> vote; // Insieme dei voti ricevuti dal post, un utente può votare solo una volta
    private ConcurrentLinkedQueue<Entry<String, String>> comment; // Insieme dei commenti ricevuti dal post
    private ConcurrentLinkedQueue<String> rewinner; // Insieme degli utenti che hanno rewinnato il post

    public WinsomePost(int idPost, String author, String content){
        this.idPost = idPost;
        this.title = new String(title);
        this.author = new String(author);
        this.content = new String(content);
        this.vote = new ConcurrentHashMap<String, Integer>();
        this.comment = new ConcurrentLinkedQueue<Entry<String, String>>();
        this.rewinner = new ConcurrentLinkedQueue<String>();
    }

    // =========== Getter 

    public int getIdPost(){
        return this.idPost;
    }

    public String getTitle(){
        return this.title;
    }

    public String getAuthor(){
        return this.author;
    }

    public String getContent(){
        return this.content;
    }

    public ConcurrentLinkedQueue<Entry<String, String>> getComment(){
        ConcurrentLinkedQueue<Entry<String, String>> clone = new ConcurrentLinkedQueue<Entry<String, String>>();
        clone.addAll(this.comment);
        return clone;
    }

    public ConcurrentLinkedQueue<String> getRewinner(){
        ConcurrentLinkedQueue<String> clone = new ConcurrentLinkedQueue<String>();
        clone.addAll(this.rewinner);
        return clone;
    }

    // =========== Setter

    public void addVote(String user, int value){
        this.vote.putIfAbsent(user, value);
    }

}