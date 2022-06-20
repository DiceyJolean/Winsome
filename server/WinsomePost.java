package server;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

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

    // Vote può assumere solo i valori LIKE o UNLIKE
    public static enum Vote{
            LIKE,
            UNLIKE;

            // Necessario per serializzare in JSON
            public String getVote(){
                return this.name();
            }
        }

    /**
     * Crea un nuovo post in Winsome, con titolo, autore e contenuto
     * 
     * @param idPost Numero identificativo del post
     * @param title Titolo del post
     * @param author Autore del post
     * @param content Contenuto testuale del post
     * @throws IllegalArgumentException Se idPost ha un valore negativo
     * @throws NullPointerException Se title, authot o content sono null
     */
    public WinsomePost(int idPost, String title, String author, String content)
    throws IllegalArgumentException, NullPointerException {
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

    /**
     * Restituisce il numero identificativo del post
     * 
     * @return l'id del post
     */
    public int getIdPost(){
        return idPost;
    }

    /**
     * Restituisce il titolo del post
     * 
     * @return il titolo del post
     */
    public String getTitle(){
        return title;
    }

    /**
     * Restituisce l'autore del post
     * 
     * @return l'autore del post
     */
    public String getAuthor(){
        return author;
    }

    /**
     * Restituisce il contenuto del post
     * 
     * @return il contenuto del post
     */
    public String getContent(){
        return content;
    }


    // ho già sincronizzato this all'interno dei metodi addvote addcomment e toprint
    
    /**
     * Restituisce una stringa che rappresenta l'intero oggetto post in formato leggibile
     * 
     * @return il post in formato stringa leggibile
     */
    public String toPrint(){
        // Raccolgo in un'unica struttura i vecchi e nuovi voti
        Map<String, Vote> votes = new HashMap<String, Vote>(oldVotes);
        votes.putAll(newVotes);

        // Rendo più leggibili i voti del post
        String votesPrettyPrinting = "{";

        for ( Entry<String, Vote> entry : votes.entrySet() )
            votesPrettyPrinting = votesPrettyPrinting + entry.getKey() + "=" + entry.getValue() + ", ";

        // Controllo necessario nel caso non ci fossero voti a questo post
        if ( votesPrettyPrinting.endsWith(", ") )
            votesPrettyPrinting = votesPrettyPrinting.substring(0, votesPrettyPrinting.length()-2);
        votesPrettyPrinting = votesPrettyPrinting + "}";

        // Raccolgo in un'unica struttura i vecchi e nuovi commenti
        Map<String, ArrayList<String>> comments = new HashMap<String, ArrayList<String>>(oldComments);

        for ( Entry<String, ArrayList<String>> entry : newComments.entrySet() )
            if ( comments.get(entry.getKey()) != null )
                comments.get(entry.getKey()).addAll(entry.getValue());
            else
                comments.put(entry.getKey(), entry.getValue());

        // Rendo più leggibili i commenti del post
        String commentsPrettyPrinting = "{";
        for ( Entry<String, ArrayList<String>> entry : comments.entrySet() )
            commentsPrettyPrinting = commentsPrettyPrinting + entry.getKey() + "=" + entry.getValue().toString() + ", ";

        // Controllo necessario nel caso in cui non fossero stati fatti commenti a questo post
        if ( commentsPrettyPrinting.endsWith(", ") )
            commentsPrettyPrinting = commentsPrettyPrinting.substring(0, commentsPrettyPrinting.length()-2);
        commentsPrettyPrinting = commentsPrettyPrinting + "}";

        int nIter;
        synchronized ( this ){ // Mi serve la sincronizzazione per evitare la race condition con il thread che calcola le ricompense e aggiorna il campo nIterations
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
    
    /**
     * Restituisce l'insieme degli utenti che hanno rewinnato questo post
     * 
     * @return gli utenti che hanno effettuato il rewin di questo post
     */
    public Set<String> getRewinners(){
        return rewinners;
    }

    /**
     * Aggiunge un voto al post, un utente può votare un post solo una volta.
     * Non è possibile votare un proprio post
     * 
     * @param user Utente che vota il post
     * @param value Valore del voto, può essere solo 1 o -1
     * @return true se l'operazione è andata a buon fine, altrimenti solleva eccezione
     * @throws WinsomeException Se l'autore del post e l'utente che vota coincidono o se l'utente aveva già votato questo post
     * @throws NullPointerException Se user è null
     * @throws IllegalArgumentException Se value ha un valore negativo o se ha un valore diverso da 1 o -1
     */
    public boolean addRate(String user, int value)
    throws WinsomeException, NullPointerException, IllegalArgumentException {
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
        
        synchronized (this) { // Sincronizzo per evitare la race condition con il thread che calcola le ricompense
        if ( this.oldVotes.get(user) == null ) // Controllo che l'utente non abbia già aggiunto un voto prima dell'ultima iterazione del reward
            if ( this.newVotes.putIfAbsent(user, vote) == null ) // Controllo che l'utente non abbia già aggiunto un voto dopo l'ultima iterazione del reward
                return true;
        }

        throw new WinsomeException("L'utente aveva già votato il post in precedenza");
    }

    /**
     * Aggiunge un commento al post, un utente può commentare lo stesso post più volte.
     * Non è possibile commentare un proprio post
     * 
     * @param user Utente che commenta il post
     * @param comment Testo del commento
     * @return true se l'operazione è andata a buon fine, altrimenti solleva eccezione
     * @throws WinsomeException Se l'autore del post e l'utente che commenta coincidono
     * @throws NullPointerException Se user o comment sono null
     */
    public boolean addComment(String user, String comment)
    throws WinsomeException, NullPointerException {
        if ( comment == null || user == null )
            throw new NullPointerException();

        // Non posso commentare un mio post
        if ( author.equals(user) )
            throw new WinsomeException("Non è possibile commentare un proprio post");
    
        // Aggiungo sempre in newComment
        // Sposto tra new e old dopo il calcolo del reward
        synchronized ( this ){
            
            // Se non era presente l'entry adesso l'ho creata
            newComments.putIfAbsent(user, new ArrayList<String>());
            // Aggiungo un commento a quelli già presenti dello stesso utente
            newComments.get(user).add(comment);

            return true;
        }
    }

    /**
     * Aggiungo un utente a quelli che hanno rewinnato questo post.
     * Non è possibile rewinnare un proprio post
     * 
     * @param user utente che ha effettuato il rewin di questo post
     * @return true se l'operazione ha successo, altrimenti solleva eccezione
     * @throws WinsomeException Se l'autore del post e l'utente che effettua il rewin coincidono
     * @throws NullPointerException Se user è null
     */
    public boolean rewinPost(String user)
    throws WinsomeException, NullPointerException {
        if ( user == null )
            throw new NullPointerException();

        if ( user.equals(author) )
            throw new WinsomeException("Non è possibile effettuare il rewin di un proprio post");

        rewinners.add(user); // Non controllo il valore di ritorno perché non ci sono effetti collaterali
        return true;
    }


    /**
     * I metodi che seguono sono chiamati esclusivamente dal thread per il calcolo delle ricompense
     * Non sono sincronizzati! Il thread per il calcolo delle ricompense si occuperà di gestire la concorrenza
     * In questo modo la ricompensa relativa ai singoli post viene calcolata sulla stessa istanza dell'oggetto
     */

    /**
     * Sposta i nuovi voti e i nuovi commenti insieme ai vecchi, ovvero quelli già contati dal thread che calcola le ricompense.
     * Questa funzione deve essere invocata solo dal thread che effettua il calcolo delle ricompense di Winsome
     * 
     * @return true se l'operazione è andata a buon fine
     */
    protected boolean switchNewOld(){

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
        }

        return true;
    }

    /**
     * Somma i voti di questo post, 
     * per ogni voto positivo viene aggiunto l'utente che ha messo il voto all'insieme dei curatori
     * 
     * @param curators Insieme dei curatori di questo post per poterlo aggiornare
     * @return la somma dei voti di questo post
     */
    protected int countVote(Set<String> curators){
        int voteSum = 0;
        for ( HashMap.Entry<String, Vote> vote : newVotes.entrySet() ){
            
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


    /**
     * Somma i commenti di questo post utilizzando la formula indicata nella specifica di Winsome, 
     * per ogni commento viene aggiunto l'autore del commento all'insieme dei curatori
     * 
     * @param curators Insieme dei curatori di questo post per poterlo aggiornare
     * @return la somma dei commenti di questo post
     */
    protected double countComments(Set<String> curators){
        double commentSum = 0;

        for ( Entry<String, ArrayList<String>> entry : newComments.entrySet() ){
            // Per ogni entry dell'hashmap, conto quanti commenti ha fatto ogni singolo user
            commentSum = commentSum + 2/(1 + Math.pow(Math.E, entry.getValue().size()*(-1)));
            // Poi aggiungo l'autore di quei commenti ai curatori
            curators.add(entry.getKey());
        }

        commentSum++;
        return commentSum;
    }

    /**
     * Restituisce il numero di iterazioni del calcolo delle ricompense su questo post
     * 
     * @return il numero di iterazioni del calcolo delle ricompense
     */
    protected int getIterations(){
        return nIterations;
    }

    /**
     * Incrementa il numero di iterazioni del calcolo delle ricompense su questo post
     */
    public void increaseIterations(){
        nIterations++;
    }

}