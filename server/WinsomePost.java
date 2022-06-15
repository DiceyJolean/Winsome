package server;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public class WinsomePost implements Serializable {

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

    public WinsomePost(int idPost, String title, String author, String content){
        if ( idPost < 0 )
            throw new IllegalArgumentException();

        if ( title == null || author == null || content == null )
            throw new NullPointerException();

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
        return idPost;
    }

    public String getTitle(){
        return title;
    }

    public String getAuthor(){
        return author;
    }

    public String getContent(){
        return content;
    }


    // ho già sincronizzato this all'interno dei metodi addvote addcomment e toprint
    
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

        int nIter;
        synchronized ( this ){
            nIter = nIterations;
        }

        return "\n\tID: " + idPost +
            "\n\tTITOLO: " + title +
            "\n\tCONTENUTO: " + content +
            "\n\tAUTORE: " + author +
            "\n\tVOTI: " + votesPrettyPrinting +
            "\n\tCOMMENTI: " + commentsPrettyPrinting +
            "\n\tREWINNERS: " + rewinners.toString() +
            "\n\tN_ITER: " + nIter + "\n";
    }
    
    public Set<String> getRewinners(){
        return new HashSet<String>(rewinners);
    }

    public synchronized boolean addRate(String user, int value)
    throws WinsomeException, NullPointerException, IllegalArgumentException {
    // throws NullArgumentException, IllegalArgumentException {
        if ( user == null )
            throw new NullPointerException();

        // Non posso votare un mio post
        if ( author.equals(user) )
            throw new WinsomeException("Non è possibile votare un proprio post");

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
        if ( this.oldVotes.get(user) == null )
            if ( this.newVotes.putIfAbsent(user, vote) == null )
                return true;
        }

        throw new WinsomeException("L'utente aveva già votato il post in precedenza");
    }

    public synchronized boolean addComment(String user, String comment)
    throws WinsomeException, NullPointerException {
        if ( comment == null || user == null )
            throw new NullPointerException();

        // Non posso commentare un mio post
        if ( author.equals(user) )
            throw new WinsomeException("Non è possibile commentare un proprio post");
    
        // Aggiungo sempre in newComment
        // Sposto tra new e old dopo il calcolo del reward
        synchronized ( this ){
            // TODO sistemare la concorrenza con il reward calculator
            
            // Se non era presente l'entry adesso l'ho creata
            newComments.putIfAbsent(user, new ArrayList<String>());
            // Aggiungo un commento a quelli già presenti dello stesso utente
            newComments.get(user).add(comment);
            return true;
        }
    }

    public boolean rewinPost(String user)
    throws WinsomeException, NullPointerException {
        if ( user == null )
            throw new NullPointerException();

        if ( user.equals(author) )
            throw new WinsomeException("Non è possibile effettuare il rewin di un proprio post");

        rewinners.add(user);
        return true;
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
            else
                // Creo una nuova entry in oldComments per fare lo switch
                this.oldComments.put(entry.getKey(), entry.getValue());

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