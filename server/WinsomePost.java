package server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import server.RewardCalculator.SumAndCurators;
import shared.NullArgumentException;

public class WinsomePost{

    private int idPost; // Id del post
    private String title; // Titolo del post
    private String author; // Autore del post
    private String content; // Contenuto (testo) del post
    private HashMap<String, Vote> newVotes; // Insieme dei voti ricevuti dal post, un utente può votare solo una volta (dopo l'ultima iterazione del rewarding)
    private HashMap<String, Vote> oldVotes; // Insieme dei voti ricevuti dal post, un utente può votare solo una volta (prima dell'ultima iterazione del rewarding)
    private HashMap<String, ArrayList<String>> newComments; // Insieme dei commenti ricevuti dal post (dopo l'ultima iterazione del rewarding)
    private HashMap<String, ArrayList<String>> oldComments; // Insieme dei commenti ricevuti dal post (prima dell'ultima iterazione del rewarding)
    private Set<String> rewinners; // Insieme degli utenti che hanno rewinnato il post
    private AtomicInteger nIterations; // Numero di iterazioni del rewarding eseguite sul post

    // Vote only could be LIKE or UNLIKE
    public static enum Vote{
            LIKE,
            UNLIKE;
        }
    
    public WinsomePost(int idPost, String author, String content){
        this.idPost = idPost;
        this.title = new String(title);
        this.author = new String(author);
        this.content = new String(content);
        this.newVotes = new HashMap<String, Vote>();
        this.oldVotes = new HashMap<String, Vote>();
        this.newComments = new HashMap<String, ArrayList<String>>();
        this.oldComments = new HashMap<String, ArrayList<String>>();
        this.rewinners = new HashSet<String>();
        this.nIterations = new AtomicInteger(0);
    }

    // =========== Getter
    // Non sono sincronizzati perché non possono essere modificati

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

    // =========== Setter

    public boolean addVote(String user, int value)
    throws NullArgumentException, IllegalArgumentException {
        if ( user == null )
            throw new NullArgumentException();

        Vote vote;
        switch ( value ){
            case 1:{
                vote = Vote.LIKE;
                break;
            }
            case -1:{
                vote = Vote.UNLIKE;
                break;
            }
            default:{
                throw new IllegalArgumentException();
            }
        }
        
        // Controllo che l'utente non abbia già aggiunto un voto prima dell'ultima iterazione del reward
        synchronized (this) {
        if ( this.oldComments.get(user) == null )
            if ( this.newVotes.putIfAbsent(user, vote) != null )
                return true;
        }

        return false;                
    }

    public boolean addVote(String user, Vote vote)
    throws NullArgumentException {
        if ( user == null || vote == null )
            throw new NullArgumentException();
        
        // Controllo che l'utente non abbia già aggiunto un voto prima dell'ultima iterazione del reward
        synchronized ( this ){
        if ( this.oldComments.get(user) == null )
            if (this.newVotes.putIfAbsent(user, vote) != null )
                return true;
        }

        return false;
    }

    public boolean addComment(String user, String comment)
    throws NullArgumentException {
        if ( comment == null || user == null )
            throw new NullArgumentException();

        // Aggiungo sempre in newComment
        // Sposto tra new e old dopo il calcolo del reward
        synchronized ( this ){
            return this.newComments.get(user).add(comment);
        }
    }

    public boolean rewinPost(String user)
    throws NullArgumentException {
        if ( user == null )
            throw new NullArgumentException();

        synchronized (this){
            return this.rewinners.add(user);
        }
    }

    // Synch perché qualcuno potrebbe accedere alla struttura nel frattempo
    public synchronized boolean switchNewOld(){

        // Sposto i voti nuovi nella struttura di quelli già contati
        // L'operazione è sicura, perché ho già controllato i duplicati al momento 
        // dell'inserimento del voto per evitare doppio voto dallo stesso utente
        oldVotes.putAll(newVotes);
        newVotes.clear();

        for ( Entry<String, ArrayList<String>> entry : newComments.entrySet() ){
            // Sposto i nuovi commenti nella struttura di quelli già contati
            if ( this.oldComments.get(entry.getKey()) != null ){
                // Se questo autore aveva già commentato questo post
                // Aggiungo i commenti nel vecchio array di commenti
                this.oldComments.get(entry.getKey()).addAll(entry.getValue());
            }
            else{
                // Creo una nuova entry in oldComments per fare lo switch
                this.oldComments.put(entry.getKey(), entry.getValue());
            }
            // E svuoto il vecchio array
            this.newComments.get(entry.getKey()).clear();

            // Posso scegliere se togliere o no anche l'autore del commento
            // Lo tolgo, così ho un'iterazione in meno essendoci una entry in meno
            // Inoltre a pelle mi sembra più corretto per quel che riguarda la formula
            this.newComments.remove(entry.getKey());

            // TODO controllare se clear e remove danno eccezione all'interno dell'iterazione   
        }

        return true;
    }

    public synchronized int countVote(Set<String> curators){
        int voteSum = 0;
        for ( ConcurrentHashMap.Entry<String, Vote> vote : newVotes.entrySet() ){
            
            if ( vote.getValue() == Vote.LIKE ){
                curators.add(vote.getKey());
                voteSum++;
            }
            else
                voteSum--;
        }

        if ( voteSum < 0 )
            voteSum = 0;
        voteSum++;

        return voteSum;
    }

    public synchronized int countComments(Set<String> curators){
        int commentSum = 0;

        for ( Entry<String, ArrayList<String>> entry : newComments.entrySet() ){
            // Per ogni entry dell'hashmap, conto quanti commenti ha fatto ogni singolo user
            commentSum += 2/(1 + Math.pow(Math.E, entry.getValue().size()*(-1)));
            // Poi aggiungo l'autore di quei commenti ai curatori
            curators.add(entry.getKey());
        }

        commentSum++;
        return commentSum;
    }

    public int getIterations(){
        return this.nIterations.get();
    }

    public int increaseIterations(){
        return this.nIterations.incrementAndGet();
    }

}