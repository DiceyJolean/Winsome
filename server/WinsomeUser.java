package server;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import server.bcrypt.src.BCrypt;
import shared.*;

// TODO nessun metodo è sincronizzato, va bene? Potrebbero accedere allo stesso utente più thread?
// Accederebbero prima al DB di user, potrebbe capitare di dover fare due operazioni contemporaneamente? Perché no...
// Potrei cambiare i Set con code sincronizzate, logged con AtomicInteger?
/**
 * Struttura che rappresenta un utente winsome all'interno del SERVER
 * Nessun metodo potrà essere invocato direttamente dal client
*/
public class WinsomeUser implements Serializable {
        
    private String nickname;
    private String psw;
    private Set<String> follower;
    private Set<String> following;
    private int loggedIn;
    private Set<String> tag;
    private Set<Integer> postRewinned; // I post sono indicati univocamente dal loro postID
    private Set<WinsomePost> blog; // Insieme dei post pubblicati da questo utente TODO ridondanza?? Sarà solo un riferimento, giustamente!
    private double wallet;

    /**
     * Crea un nuovo utente Winsome con associata password (hashata) e lista di tag (NON modificabile)
     * 
     * @param nickname Nickname univoco dell'utente
     * @param psw Password per il login
     * @param tags Lista di tag (al più cinque)
     * @return Un nuovo oggetto utente di Winsome
     * @throws IndexOutOfBoundsException se sono indicati più di 5 tags o meno di 1
     * @throws NullArgumentException se nickname o psw sono null
     */
    public WinsomeUser(String nickname, String psw, Set<String> tags)
    throws IndexOutOfBoundsException, NullArgumentException {
        if ( tags.size() < 1 || tags.size() > 5 )
            throw new IndexOutOfBoundsException();

        if ( nickname == null || psw == null )
            throw new NullArgumentException();

        String salt = BCrypt.gensalt();
        String hashedPsw = BCrypt.hashpw(psw, salt);

        this.psw = hashedPsw;
        this.nickname = nickname;
        this.follower = new HashSet<String>();
        this.following = new HashSet<String>();
        this.loggedIn = 0;
        this.wallet = 0;
        this.postRewinned = new HashSet<Integer>();
        this.blog = new HashSet<WinsomePost>();
        this.tag = new HashSet<String>();
        this.tag.addAll(tags);
    }

    /**
     * Si ottiene la psw hashata dell'utente
     * @return La psw per il login dell'utente
     */
    public String getPsw(){
        // Gli oggetti String sono immutabili, quindi posso restituire il riferimento
        return this.psw;
    }

    /**
     * Si ottiene l'insieme dei tag associati all'utente
     * @return Una deep copy dell'insieme dei tag dell'utente
     */
    public Set<String> getTags(){
        // Restituisco una deep copy perché la struttura dei tag non deve poter essere modificata
        return new HashSet<String>(this.tag);
    }

    /**
     * Si ottiene il nickname dell'utente
     * @return Il nickname dell'utente
     */
    public String getNickname(){
        // Gli oggetti String sono immutabili, quindi posso restituire il riferimento
        return this.nickname;
    }

    /**
     * Effettua il login di un utente
     * 
     * @param psw Password dell'utente che vuole connettersi
     * @return true se il login ha avuto successo, false altrimenti
     * @throws NullArgumentException se psw è null
     */
    public boolean login(String psw)
    throws NullArgumentException {
        // TODO caso di più login di un utente da client diversi
        if ( psw == null )
            throw new NullArgumentException();

        if ( BCrypt.checkpw(psw, this.psw) )
            this.loggedIn = 1;

        return ( this.loggedIn == 1 );
    }

    /**
     * Aggiunge un nuovo follower a quelli dell'utente
     * 
     * @param user Utente da aggiunge ai follower
     * @return true se l'inserimento ha avuto successo, false altrimenti
     * @throws NullArgumentException se user è null
     */
    public boolean addFollower(String user)
    throws NullArgumentException {
        if ( user == null )
            throw new NullArgumentException();

        return this.follower.add(user);
    }

    /**
     * Aggiunge un nuovo utente ai seguiti
     * 
     * @param user Utente da aggiungere ai seguiti
     * @return true se l'inserimento ha avuto successo, false altrimenti
     * @throws NullArgumentException se user è null
     */
    public boolean addFollowing(String user)
    throws NullArgumentException {
        if ( user == null )
            throw new NullArgumentException();

        return this.following.add(user);
    }
    
    /**
     * Aggiunge un post a quelli rewinnati
     * 
     * @param postId ID del post rewinnato
     * @return true se l'inserimento ha avuto successo, false altrimenti
     * @throws IllegalArgumentException se postId è minore di zero
     */
    public boolean addRewin(int postId)
    throws IllegalArgumentException {
        if ( postId < 0 )
            throw new IllegalArgumentException();

        return this.postRewinned.add(postId);
    }

    /**
     * Aggiunge un post al blog dell'utente
     * 
     * @param post Post da aggiungere al blog
     * @return true se l'inserimento ha avuto successo, false altrimenti
     * @throws NullArgumentException se post è null
     */
    public boolean addPost(WinsomePost post)
    throws NullArgumentException {
        if ( post == null )
            throw new NullArgumentException();

        return this.blog.add(post);
    }

    /** 
     * Effettua il logout dell'utente
    */
    public void logout(){
        this.loggedIn = 0;
    }

    /**
     * Restituisce il valore del portafoglio dell'utente
     * 
     * @return il valore del portafoglio
     */
    public double getReward(){
        return this.wallet;
    }

    /**
     * Aggiorna il valore del portafoglio dell'utente
     * 
     * @param newReward Saldo da aggiungere al portafoglio
     * @throws IllegalArgumentException se newReward è minore di zero
     */
    public void updateReward(Double newReward)
    throws IllegalArgumentException {
        if ( newReward == null )
            this.wallet += 0;

        if ( newReward.doubleValue() < 0 )
            throw new IllegalArgumentException();

        this.wallet += newReward.doubleValue();
    }

    /**
     * Rimuove un utente dai follower
     * 
     * @param user Utente da rimuovere dai follower
     * @return true se la rimozione ha avuto successo, false altrimenti
     * @throws NullArgumentException se user è null
     */
    public boolean removeFollower(String user)
    throws NullArgumentException {
        if ( user == null )
            throw new NullArgumentException();

        return this.following.remove(user);
    }

    /**
     * Rimuove un utente dai seguiti
     * 
     * @param user Utente da rimuovere dai seguiti
     * @return true se la rimozione ha avuto successo, false altrimenti
     * @throws NullArgumentException se user è null
     */
    public boolean removeFollow(String user)
    throws NullArgumentException {
        if ( user == null )
            throw new NullArgumentException();

        return this.following.remove(user);
    }

    /**
     * Rimuove un post da quelli rewinnati
     * 
     * @param postId ID del post da rimuovere
     * @return true se la rimozione ha avuto successo, false altrimenti
     * @throws IllegalArgumentException se postId è minore di zero
     */
    public boolean removeRewin(int postId)
    throws IllegalArgumentException {
        if ( postId < 0 )
            throw new IllegalArgumentException();

        return this.postRewinned.remove(postId);
    }

    /**
     * Restituisce una deep copy dei post pubblicati dall'utente
     * @return Una copia dei post pubblicati dall'utente
     */
    public Set<WinsomePost> getPosts(){
        return new HashSet<WinsomePost>(this.blog);
    }

}
