package server;

import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import server.bcrypt.src.BCrypt;

// TODO nessun metodo è sincronizzato, va bene? Potrebbero accedere allo stesso utente più thread?
// Accederebbero prima al DB di user, potrebbe capitare di dover fare due operazioni contemporaneamente? Perché no...
// Potrei cambiare i Set con code sincronizzate, logged con AtomicInteger?
/**
 * Classe che rappresenta un utente all'interno di Winsome
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
    private Queue<WinsomeWallet> wallet; // Lista con lo storico degli aggiornamenti del portafoglio dell'utente

    /**
     * Crea un nuovo utente Winsome con associata la password hashata e la lista dei tag (NON modificabile)
     * 
     * @param username Nickname univoco dell'utente
     * @param psw Password per il login
     * @param tags Lista di tag (minimo uno, al più cinque)
     * @throws IndexOutOfBoundsException Se sono indicati più di cinque tag, o meno di uno
     * @throws NullPointerException Se username o psw sono null
     */
    public WinsomeUser(String username, String psw, Set<String> tags)
    throws IndexOutOfBoundsException, NullPointerException {
        if ( tags.size() < 1 || tags.size() > 5 )
            throw new IndexOutOfBoundsException();

        if ( username == null || psw == null )
            throw new NullPointerException();

        // Salvataggio della password hashata per non salvarla in chiaro
        String salt = BCrypt.gensalt();
        String hashedPsw = BCrypt.hashpw(psw, salt);

        // Inizializzo l'utente con i dati passati come parametro
        this.psw = hashedPsw;
        this.nickname = username;
        this.tags = new HashSet<String>(tags);

        // Inizializzo l'utente con le nuove strutture
        this.follower = new HashSet<String>();
        this.following = new HashSet<String>();
        this.loggedIn = false;
        this.wallet = new ConcurrentLinkedQueue<WinsomeWallet>(); // Il portafoglio sarà una struttura concorrente perché vi accedono il worker e il thread per il calcolo del reward in race condition
        this.postRewinned = new HashSet<Integer>();
        this.blog = new HashSet<WinsomePost>();
    }

    /**
     * Si ottiene l'insieme (NON modificabile) dei tag associati all'utente
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
        return nickname;
    }

    /**
     * Funzione per testare se l'utente è attualmente loggato in Winsome
     * 
     * @return true se l'utente è attualmente loggato, false altrimenti
     */
    public boolean isLogged(){
        return loggedIn;
    }

    /**
     * Effettua il login di un utente, se era già loggato solleva eccezione
     * 
     * @param psw Password in chiaro dell'utente
     * @return true se l'operazione ha avuto successo, altrimenti solleva eccezione
     * @throws WinsomeException Se l'utente era già attualmente loggato o se la password è errata
     * @throws NullPointerException Se psw è null
     */
    public boolean login(String psw)
    throws WinsomeException, NullPointerException {
        if ( loggedIn )
            throw new WinsomeException("L'utente ha già effettuato il login");

        if ( psw == null )
            throw new NullPointerException();

        if ( !BCrypt.checkpw(psw, this.psw) )
            throw new WinsomeException("Le password è errata");
            
        loggedIn = true; // Il login ha avuto successo
        return true;
    }

    /**
     * Restituisce l'insieme degli utenti seguiti da questo utente
     * 
     * @return l'insieme (NON null) dei following
     */
    public Set<String> getFollowing(){
        return following;
    }

    /**
     * Aggiunge un nuovo follower a quelli che già seguono l'utente,
     * se il follower seguiva l'utente già in precedenza solleva eccezione
     * 
     * @param user L'utente che inizia a seguire, da aggiungere ai follower
     * @return true se l'operazione è andata a buon fine, altrimenti solleva eccezione
     * @throws WinsomeException Se user prova a seguire se stesso, o se già seguiva questo utente
     * @throws NullPointerException Se user è null
     */
    public boolean addFollower(String user)
    throws WinsomeException, NullPointerException {
        if ( user == null )
            throw new NullPointerException();

        if ( nickname.equals(user) )
            throw new WinsomeException("Non è possibile seguire se stessi");
        
        if ( follower.contains(user) )
            throw new WinsomeException(user + " stava già seguendo " + nickname);

        synchronized ( this ){ // Sincronizzo per la race condition durante la registrazione alla callback
            follower.add(user);
        }

        return true;
    }

    /**
     * Aggiunge un utente ai seguiti, se era già presente, l'operazione ha comunque successo
     * 
     * @param user L'utente da aggiungere ai seguiti
     * @return true se l'operazione ha avuto successo, altrimenti solleva eccezione
     * @throws NullPointerException Se user è null
     * @throws WinsomeException Se user prova a seguire se stesso
     */
    public boolean addFollowing(String user)
    throws NullPointerException, WinsomeException {

        if ( user == null )
            throw new NullPointerException();

        if ( nickname.equals(user) )
            throw new WinsomeException("Non è possibile essere seguiti da se stessi");

        following.add(user); // Non controllo il valore di ritorno perché non ci sono effetti collaterali
        return true;
    }
    
    /**
     * Aggiunge un post al'insieme di quelli rewinnati,
     * se il post era già stato rewinnato l'operazione ha comunque successo
     * 
     * @param postId ID del post rewinnato
     * @return true se l'operazione ha avuto successo
     * @throws IllegalArgumentException Se postId è minore di zero
     */
    public boolean addRewin(int postId)
    throws IllegalArgumentException {
        if ( postId < 0 )
            throw new IllegalArgumentException();
        
        postRewinned.add(postId); // Non controllo il valore di ritorno perché non ci sono effetti collaterali
        return true;
    }

    /**
     * Aggiunge un post al blog dell'utente
     * 
     * @param post Post da aggiungere al blog
     * @return true se l'operazione ha avuto successo, altrimenti solleva eccezione
     * @throws WinsomeException Se l'utente non è l'autore del post
     * @throws NullPointerException
     */
    public boolean addPost(WinsomePost post)
    throws WinsomeException, NullPointerException {
        if ( post == null )
            throw new NullPointerException();

        if ( !post.getAuthor().equals(nickname) )
            throw new WinsomeException("Incostistenza tra autore del post e utente");

        blog.add(post); // Non controllo il valore di ritorno perché non ci sono effetti collaterali
        return true;
    }

    // Restituisce una copia dei follower di questo utente
    // TODO questa funzione va sincronizzata in qualche modo perché viene invocata dai client tramite RMI
    /**
     * Restituisce l'insieme dei follower di questo utente
     * @return Una deep copy dei follower
     */
    public Set<String> getFollower(){
        synchronized ( this ){ // Sincronizzo per la race condition durante la registrazione alla callback
            return new HashSet<String>(follower); // Restituisco una copia per non far modificare questa struttura direttamente dai client
        }
    }

    /** 
     * Effettua il logout dell'utente
    */
    /**
     * Effettua il logout dell'utente, se non era attualmente loggato solleva eccezione
     * 
     * @return true se l'operazione è andata a buon fine, altrimenti solleva eccezione
     * @throws WinsomeException Se l'utente non era attualmente loggato
     */
    public boolean logout()
        throws WinsomeException{
        if ( !loggedIn )
            throw new WinsomeException("L'utente non è loggato");

        loggedIn = false;
        return true;
    }

    /**
     * Restituisce lo storico del portafoglio dell'utente
     * 
     * @return lo storico del portafoglio
     */
    public Queue<WinsomeWallet> getReward(){
        return wallet;
    }

    /**
     * Aggiorna il valore del portafoglio dell'utente
     * 
     * @param newReward Saldo da aggiungere al portafoglio
     * @throws IllegalArgumentException Se newReward è minore di zero
     */
    /**
     * Aggiorna il portafoglio dell'utente
     * 
     * @param date Data dell'aggiornamento
     * @param newReward Nuovo valore da aggiungere
     * @return true se l'operazione è andata a buon fine, altrimenti solleva eccezione
     * @throws IllegalArgumentException Se date ha valore negativo
     * @throws NullPointerException Se newReward è null
     */
    public boolean updateReward(Date date, double newReward)
    throws IllegalArgumentException, NullPointerException {
        if ( newReward < 0 )
            throw new IllegalArgumentException();

        if ( date == null )
            throw new NullPointerException();
            
        if ( newReward == 0 )
            return true; // Non segno il nuovo aggiornamento se il valore del portafoglio non è cambiato

        wallet.add(new WinsomeWallet(date, newReward));
        return true;
    }

    /**
     * Rimuove un utente dai follower
     * 
     * @param user Utente da rimuovere dai follower
     * @return true se la rimozione ha avuto successo, false altrimenti
     * @throws NullArgumentException Se user è null
     */
    /**
     * Rimuove un utente dai follower, se user non era un follower dell'utente solleva eccezione
     * 
     * @param user Utente che smette di seguire questo utente
     * @return true se l'operazione è andata a buon fine, altrimenti solleva eccezione
     * @throws WinsomeException Se user e l'utente coincidono, o se user non stava seguendo l'utente
     * @throws NullPointerException Se user è null
     */
    public boolean removeFollower(String user)
    throws WinsomeException, NullPointerException {
        if ( user == null )
            throw new NullPointerException();
        
        if ( nickname.equals(user) )
            throw new WinsomeException("Non è possibile seguire se stessi");

        if ( !follower.contains(user) )
            throw new WinsomeException(user + " non stava seguendo " + nickname);

        synchronized ( this ){ // Sincronizzo per la race condition durante la registrazione alla callback
            follower.remove(user);
        }

        return true;
    }

    /**
     * Rimuove un utente dai seguiti
     * 
     * @param user Utente da rimuovere dai seguiti
     * @return true se la rimozione ha avuto successo, false altrimenti
     * @throws NullArgumentException Se user è null
     */
    /**
     * Rimuove un utente da quelli seguiti, se non era presente, l'operazione ha comunque successo
     * 
     * @param user L'utente da rimuovere
     * @return true se l'operazione è andata a buon fine, altrimenti solleva eccezione
     * @throws WinsomeException Se user e l'utente coincidono
     * @throws NullPointerException Se user è null
     */
    public boolean removeFollowing(String user)
    throws WinsomeException, NullPointerException {
        if ( user == null )
            throw new NullPointerException();
    
        if ( nickname.equals(user) )
            throw new WinsomeException("Non è possibile essere seguiti da se stessi");

        following.remove(user);  // Non controllo il valore di ritorno perché non ci sono effetti collaterali
        return true;
    }

    /**
     * Rimuove un post dal blog dell'utente
     * 
     * @param post Post da eliminare
     * @return true se l'operazione è andata a buon fine, altrimenti solleva eccezione
     * @throws WinsomeException Se il post non era nel blog dell'utente
     * @throws NullPointerException Se post è null
     */
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
     * Rimuove un post da quelli rewinnati, se non era rewinnato, l'operazione ha comunque successo
     * 
     * @param postId ID del post da rimuovere
     * @return true se la rimozione ha avuto successo, altrimenti solleva eccezione
     * @throws IllegalArgumentException Se postId è minore di zero
     */
    public boolean removeRewin(int postId)
    throws IllegalArgumentException {
        if ( postId < 0 )
            throw new IllegalArgumentException();

        postRewinned.remove(postId);
        return true;
    }

    /**
     * Restituisce l'insieme dei post rewinnati
     * 
     * @return i post rewinnati
     */
    public Set<Integer> getRewin(){
        return postRewinned;
    }

    /**
     * Restituisce l'insieme dei post pubblicati dall'utente
     * 
     * @return i post pubblicati dall'utente
     */
    public Set<WinsomePost> getPosts(){
        return blog;
    }

    @Override
    public String toString(){
        return nickname + " " + psw + " " + tags.toString();
    }

}
