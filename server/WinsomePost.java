package server;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public class WinsomePost implements Serializable{

    private int idPost; // Id del post
    private String title; // Titolo del post
    private String author; // Autore del post
    private String content; // Contenuto (testo) del post
    private Map<String, Vote> newVotes; // Insieme dei voti ricevuti dal post, un utente può votare solo una volta (dopo l'ultima iterazione del rewarding)
    private Map<String, Vote> oldVotes; // Insieme dei voti ricevuti dal post, un utente può votare solo una volta (prima dell'ultima iterazione del rewarding)
    private Map<String, ArrayList<String>> newComments; // Insieme dei commenti ricevuti dal post (dopo l'ultima iterazione del rewarding)
    private Map<String, ArrayList<String>> oldComments; // Insieme dei commenti ricevuti dal post (prima dell'ultima iterazione del rewarding)
    private Set<String> rewinners; // Insieme degli utenti che hanno rewinnato il post
    private int nIterations; // Numero di iterazioni del rewarding eseguite sul post

    // Vote only could be LIKE or UNLIKE
    public static enum Vote{
            LIKE,
            UNLIKE;

            // Necessario per serializzare in JSON
            public String getVote(){
                return this.name();
            }
        }

    public class PostView{
        private int idPost;
        private String title;
        private String author;

        public PostView(WinsomePost post){
            idPost = post.getIdPost();
            title = new String(post.getTitle());
            author = new String(post.getAuthor());
        }

        public int getId(){
            return idPost;
        }

        public String getTitle(){
            return title;
        }

        public String getAuthor(){
            return author;
        }
    }

    public WinsomePost(int idPost, String title, String author, String content){
        this.idPost = idPost;
        this.title = new String(title);
        this.author = new String(author);
        this.content = new String(content);
        this.newVotes = new HashMap<String, Vote>();
        this.oldVotes = new HashMap<String, Vote>();
        this.newComments = new HashMap<String, ArrayList<String>>();
        this.oldComments = new HashMap<String, ArrayList<String>>();
        this.rewinners = new HashSet<String>();
        this.nIterations = 0;
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

    public Set<String> getRewinners(){
        return rewinners;
    }

    public String toPrint(){
        Map<String, Vote> votes = new HashMap<String, Vote>(oldVotes);
        votes.putAll(newVotes);

        String votesPrettyPrinting = "{";

        for ( Entry<String, Vote> entry : votes.entrySet() )
            votesPrettyPrinting = votesPrettyPrinting + entry.getKey() + "=" + entry.getValue() + ", ";

        if ( votesPrettyPrinting.endsWith(", ") )
            votesPrettyPrinting = votesPrettyPrinting.substring(0, votesPrettyPrinting.length()-2);
        votesPrettyPrinting = votesPrettyPrinting + "}";

        Map<String, ArrayList<String>> comments = new HashMap<String, ArrayList<String>>(oldComments);

        for ( Entry<String, ArrayList<String>> entry : newComments.entrySet() )
            if ( comments.get(entry.getKey()) != null )
                comments.get(entry.getKey()).addAll(entry.getValue());
            else
                comments.put(entry.getKey(), entry.getValue());

        String commentsPrettyPrinting = "{";
        for ( Entry<String, ArrayList<String>> entry : comments.entrySet() )
            commentsPrettyPrinting = commentsPrettyPrinting + entry.getKey() + "=" + entry.getValue().toString() + ", ";

        if ( commentsPrettyPrinting.endsWith(", ") )
            commentsPrettyPrinting = commentsPrettyPrinting.substring(0, commentsPrettyPrinting.length()-2);
        commentsPrettyPrinting = commentsPrettyPrinting + "}";

        return "\n\tID: " + idPost +
            "\n\tTITOLO: " + title +
            "\n\tCONTENUTO: " + content +
            "\n\tAUTORE: " + author +
            "\n\tVOTI: " + votesPrettyPrinting +
            "\n\tCOMMENTI: " + commentsPrettyPrinting +
            "\n\tREWINNERS: " + rewinners.toString() +
            "\n\tN_ITER: " + nIterations + "\n";
    }
    
    // =========== Setter

    public boolean addVote(String user, int value){
    // throws NullArgumentException, IllegalArgumentException {
        if ( user == null )
            return false;
            // throw new NullArgumentException();

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

    public boolean addComment(String user, String comment){
    // throws NullArgumentException {
        if ( comment == null || user == null )
            return false;
            // throw new NullArgumentException();

        // Aggiungo sempre in newComment
        // Sposto tra new e old dopo il calcolo del reward
        synchronized ( this ){
            // TODO sistemare la concorrenza con il reward calculator
            
            newComments.putIfAbsent(user, new ArrayList<String>());

            return this.newComments.get(user).add(comment);
        }
    }

    public boolean rewinPost(String user){
    // throws NullArgumentException {
        if ( user == null )
            return false;
            // throw new NullArgumentException();

        //synchronized (this){ TODO perché avevo messo synch? i rewin non sono di interesse nel calcolo della ricompensa
            return rewinners.add(user);
        //}
    }


    /**
     * I metodi che seguono sono chiamati esclusivamente dal thread per il calcolo delle ricompense
     * Non sono sincronizzati! Il thread per il calcolo delle ricompense si occuperà di gestire la concorrenza
     * In questo modo la ricompensa relativa ai singoli post viene calcolata sulla stessa istanza dell'oggetto
     */

    public boolean switchNewOld(){

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

    public int countVote(Set<String> curators){
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

    public int countComments(Set<String> curators){
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
        return nIterations;
    }

    public int increaseIterations(){
        return nIterations++;
    }

}