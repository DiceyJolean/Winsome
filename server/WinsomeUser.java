package server;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import server.bcrypt.src.BCrypt;

// TODO nessun metodo è sincronizzato, va bene? Potrebbero accedere allo stesso utente più thread?
// Accederebbero prima al DB di user, potrebbe capitare di dover fare due operazioni contemporaneamente? Perché no...
// Potrei cambiare i Set con code sincronizzate, logged con AtomicInteger?
/**
 * Struttura che rappresenta un utente winsome all'interno del SERVER
 * Nessun metodo potrà essere invocato direttamente dal client
*/
public class WinsomeUser implements Serializable {
        
    private String nickname; // Nome univoco dell'utente all'interno di Winsome
    private String psw; // Password dell'utente per effettuare il login
    private Set<String> follower; // Insieme dei follower dell'utente
    private Set<String> following; // Insieme degli utenti seguiti dall'utente
    private boolean loggedIn; // Flag che indica se l'utente è attualmente loggato
    private Set<String> tags; // Insieme dei tag dell'utente
    private Set<Integer> postRewinned; // Insieme segli id dei post rewinnati dall'utente
    private Set<WinsomePost> blog; // Insieme dei post pubblicati da questo utente TODO ridondanza?? Sarà solo un riferimento, giustamente!
    private List<WinsomeWallet> wallet; // Lista con lo storico degli aggiornamenti del portafoglio dell'utente

    /**
     * Crea un nuovo utente Winsome con associata password (hashata) e lista di tag (NON modificabile)
     * 
     * @param nickname Nickname univoco dell'utente
     * @param psw Password per il login
     * @param tags Lista di tag (minimo uno, al più cinque)
     * @return Un nuovo oggetto utente di Winsome
     * @throws IndexOutOfBoundsException se sono indicati più di 5 tags o meno di 1
     * @throws NullPointerException se nickname o psw sono null
     */
    public WinsomeUser(String nickname, String psw, Set<String> tags)
    throws IndexOutOfBoundsException, NullPointerException {
    // throws IndexOutOfBoundsException, NullArgumentException {
        if ( tags.size() < 1 || tags.size() > 5 )
            throw new IndexOutOfBoundsException();

        if ( nickname == null || psw == null )
            throw new NullPointerException();

        String salt = BCrypt.gensalt();
        String hashedPsw = BCrypt.hashpw(psw, salt);

        this.psw = hashedPsw;
        this.nickname = nickname;
        this.follower = new HashSet<String>();
        this.following = new HashSet<String>();
        this.loggedIn = false;
        this.wallet = new ArrayList<WinsomeWallet>();
        this.postRewinned = new HashSet<Integer>();
        this.blog = new HashSet<WinsomePost>();
        this.tags = new HashSet<String>();
        this.tags.addAll(tags);
    }

    /**
     * Si ottiene la psw hashata dell'utente
     * 
     * @return La psw per il login dell'utente
     */
    public String getPsw(){
        // Gli oggetti String sono immutabili, quindi posso restituire il riferimento
        return psw;
    }

    /**
     * Si ottiene l'insieme dei tag associati all'utente
     * 
     * @return Una deep copy dell'insieme dei tag dell'utente
     */
    public Set<String> getTags(){
        // Restituisco una deep copy perché la struttura dei tag non deve poter essere modificata
        return new HashSet<String>(this.tags);
    }

    /**
     * Si ottiene il nickname dell'utente
     * 
     * @return Il nickname dell'utente
     */
    public String getNickname(){
        // Gli oggetti String sono immutabili, quindi posso restituire il riferimento
        return nickname;
    }

    public boolean isLogged(){
        return loggedIn;
    }

    /**
     * Effettua il login di un utente
     * 
     * @param psw Password dell'utente che vuole connettersi
     * @return true se il login ha avuto successo, false altrimenti
     */
    public boolean login(String psw)
    throws WinsomeException, NullPointerException {
        if ( loggedIn )
            throw new WinsomeException("L'utente ha già effettuato il login");

        if ( psw == null )
            throw new NullPointerException();

        if ( !BCrypt.checkpw(psw, this.psw) )
            throw new WinsomeException("Le password è errata");
            
        loggedIn = true;
        return true;
    }

    public Set<String> getFollowing(){
        return following;
    }

    /**
     * Aggiunge un nuovo follower a quelli dell'utente
     * 
     * @param user Utente da aggiunge ai follower
     * @return true se l'inserimento ha avuto successo, false altrimenti
     * @throws NullPointerException se user è null
     */
    public boolean addFollower(String user)
    throws WinsomeException, NullPointerException {
        if ( user == null )
            throw new NullPointerException();

        if ( nickname.equals(user) )
            // Non è possibile seguire se stessi
            throw new WinsomeException("Non è possibile seguire se stessi");
            
        follower.add(user);
        return true;
    }

    /**
     * Aggiunge un nuovo utente ai seguiti
     * 
     * @param user Utente da aggiungere ai seguiti
     * @return true se l'inserimento ha avuto successo, false altrimenti
     * @throws NullPointerException se user è null
     */
    public boolean addFollowing(String user)
    throws NullPointerException, WinsomeException {

        if ( user == null )
            throw new NullPointerException();

        if ( nickname.equals(user) )
            // Non è possibile essere seguiti da se stessi
            throw new WinsomeException("Non è possibile essere seguiti da se stessi");

        following.add(user);
        return true;
    }
    
    /**
     * Aggiunge un post a quelli rewinnati, 
     * se il post era già stato rewinnato l'operazione ha comunque successo 
     * dato che l'operazione ha gli stessi effetti collaterali
     * 
     * @param postId ID del post rewinnato
     * @return true se l'inserimento ha avuto successo
     * @throws IllegalArgumentException se postId è minore di zero
     */
    public boolean addRewin(int postId)
    throws IllegalArgumentException {
        if ( postId < 0 )
            throw new IllegalArgumentException();
        
        postRewinned.add(postId);
        return true;
    }

    /**
     * Aggiunge un post al blog dell'utente
     * 
     * @param post Post da aggiungere al blog
     * @return true se l'inserimento ha avuto successo, false altrimenti
     * @throws NullArgumentException se post è null
     */
    public boolean addPost(WinsomePost post)
    throws WinsomeException, NullPointerException {
        if ( post == null )
            throw new NullPointerException();

        if ( !post.getAuthor().equals(nickname) )
            throw new WinsomeException("Incostistenza tra autore del post e utente");

        blog.add(post);
        return true;
    }

    // Restituisce una copia dei follower di questo utente
    // TODO questa funzione va sincronizzata in qualche modo perché viene invocata dai client tramite RMI
    public Set<String> getFollower(){
        Set<String> copy = new HashSet<String>();
        copy.addAll(follower);

        return copy;
    }

    /** 
     * Effettua il logout dell'utente
    */
    public boolean logout()
        throws WinsomeException{
        if ( !loggedIn )
            throw new WinsomeException("L'utente non è loggato");

        loggedIn = false;
        return true;
    }

    /**
     * Restituisce il valore del portafoglio dell'utente
     * 
     * @return il valore del portafoglio
     */
    public List<WinsomeWallet> getReward(){
        return wallet;
    }

    /**
     * Aggiorna il valore del portafoglio dell'utente
     * 
     * @param newReward Saldo da aggiungere al portafoglio
     * @throws IllegalArgumentException se newReward è minore di zero
     */
    public boolean updateReward(Date date, double newReward)
    throws IllegalArgumentException {
        if ( newReward < 0 )
            throw new IllegalArgumentException();

        if ( date == null )
            throw new NullPointerException();
            
        if ( newReward == 0 )
            return true;

        if ( wallet.size() != 0 ){
            WinsomeWallet last = wallet.get(wallet.size()-1);
            newReward = newReward + last.getValue();
        }

        wallet.add(new WinsomeWallet(date, newReward));
        return true;
    }

    /**
     * Rimuove un utente dai follower
     * 
     * @param user Utente da rimuovere dai follower
     * @return true se la rimozione ha avuto successo, false altrimenti
     * @throws NullArgumentException se user è null
     */
    public boolean removeFollower(String user)
    throws WinsomeException, NullPointerException {
        if ( user == null )
            throw new NullPointerException();
        
        if ( nickname.equals(user) )
            throw new WinsomeException("Non è possibile seguire se stessi");

        follower.remove(user);
        return true;
    }

    /**
     * Rimuove un utente dai seguiti
     * 
     * @param user Utente da rimuovere dai seguiti
     * @return true se la rimozione ha avuto successo, false altrimenti
     * @throws NullArgumentException se user è null
     */
    public boolean removeFollowing(String user)
    throws WinsomeException, NullPointerException {
        if ( user == null )
            throw new NullPointerException();
    
        if ( nickname.equals(user) )
            throw new WinsomeException("Non è possibile essere seguiti da se stessi");

        following.remove(user);
        return true;
    }

    public boolean removePost(WinsomePost post)
    throws WinsomeException, NullPointerException {
        if ( post == null )
            throw new NullPointerException();

        if ( !blog.contains(post) )
            throw new WinsomeException("Il post non era presente nel blog dell'utente");

        blog.remove(post);
        return true;
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

        postRewinned.remove(postId);
        return true;
    }

    public Set<Integer> getRewin(){
        return postRewinned;
    }

    /**
     * Restituisce un riferimento ai post pubblicati dall'utente
     * @return Una copia dei post pubblicati dall'utente
     */
    public Set<WinsomePost> getPosts(){
        return blog;
    }



    @Override
    public String toString(){
        return this.nickname + " " + this.psw + " " + tags.toString();
    }

    public String toPrint(){
        String allPost = new String();
        for (WinsomePost post : this.blog ){
            allPost = allPost + post.toPrint();
        }

        return "\tNICKNAME: " + nickname +
            "\n\tPASSWORD: " + psw +
            "\n\tTAGS: " + tags.toString() +
            "\n\tFOLLOWER: " + follower.toString() +
            "\n\tSEGUITI: " + following.toString() + 
            "\n\tLOGGATO: " + loggedIn +
            "\n\tWALLET: " + wallet +
            "\n\tPOST: " + allPost +
            "\n\tREWIN: " + postRewinned.toString();

    }
}
