package server;

import java.io.Serializable;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Classe che rappresenta il database di Winsome,
 * ovvero raccoglie tutti i post e gli utenti
 */
public class WinsomeDB implements Serializable {
    private final boolean DEBUG = false;

    private Map<Integer, WinsomePost> posts;
    private AtomicInteger newPost; // Non è necessario che sia atomic perché solo il worker crea e cancella post (quindi non si verificano race condition)

    protected ReadWriteLock lock; // Chiunque può creare un database, ma solo chi è nel package server può utilizzare la lock
    private Map<String, WinsomeUser> users;
    
    private Map<String, Set<String>> tags;

    /**
     * Crea un nuovo database Winsome con le strutture inizializzate
     */
    public WinsomeDB(){
        lock = new ReentrantReadWriteLock();
        posts = new HashMap<Integer, WinsomePost>(); // La concorrenza è gestita con la readwrite lock
        users = new ConcurrentHashMap<String, WinsomeUser>(); // Concurrent perché possono verificarsi race condition con RMI
        tags = new HashMap<String, Set<String>>();
        newPost = new AtomicInteger(0);
    }






    /**
     * Aggiunge un nuovo utente a Winsome. Questo metodo viene invocato dal thread che rispristina lo stato
     * iniziale di Winsome e dalle funzioni RMI durante la registrazione di un nuovo utente
     * 
     * @param user Utente da aggiungere a Winsome
     * @return true se l'inserimento è andato a buon fine, altrimenti solleva eccezione
     * @throws NullPointerException Se user è null
     * @throws WinsomeException Se era già presente un utente con lo stesso nickname in Winsome
     */
    protected boolean addUser(WinsomeUser user)
    throws WinsomeException, NullPointerException {
        if ( user == null )
            throw new NullPointerException();

        // La lock è necessaria per evitare race condition tra le registrazioni di nuovi utenti e l'autosalvataggio dello stato
        lock.writeLock().lock();
        try {
            if ( users.putIfAbsent(user.getNickname(), user) != null ){
                if ( DEBUG ) System.out.println("Inserimento di " + user.getNickname() + " fallito, nickname già in uso");
            
                throw new WinsomeException("Nickname già in uso");
            }
        } finally {
            lock.writeLock().unlock();
        }

        // Aggiorno la struttura dei tags
        Set<String> userTags = user.getTags();
        for ( String tag : userTags ){
            tag.toLowerCase();
            // Se non era presente lo aggiungo
            synchronized ( tags ){ // Devo sincronizzare perché più utenti possono registrarsi contemporaneamente
                // Non ho scelto una struttura concorrente perché l'unica race condition sarebbe qui
                // E avrei comunque bisogno di un blocco synchronized perché devo invocare due metodi su tags

                tags.putIfAbsent(tag, new HashSet<String>());
                // Poi aggiungo l'utente all'insieme di quelli che hanno indicato quel tag
                tags.get(tag).add(user.getNickname());
            }
        }

        if ( DEBUG ) System.out.println("Inserimento di " + user.getNickname() + " avvenuto con successo\n");
        return true;
    }

    /**
     * Rimuove il post dal database e rimuove i rewin del post.
     * 
     * @param id ID del post da rimuovere
     * @return true se l'operazione è andata a buon fine, false altrimenti
     */
    private boolean removePost(int id){

        // Non è necessario sincronizzare perché quando viene invocato questo metodo la struttura è già bloccata in modalità scrittura
        WinsomePost removed = posts.remove(id);
        if ( removed == null )
            return false;

        // Devo rendere consistenti i rewin di questo post
        Set<String> rewinners = removed.getRewinners();
        for ( String rewinner : rewinners )
            users.get(rewinner).removeRewin(id);

        return true;
    }

    /**
     * Restituisce un riferimento ai post pubblicati dall'utente.
     * Questo metodo viene invocato dal thread per effettuare il calcolo delle ricompense.
     * 
     * @param user Utente di cui si vogliono i post
     * @return i post di cui user è l'autore (null è un valore valido)
     */
    protected Set<WinsomePost> getPostPerUser(String user){
        /*
        // TODO qui concorrenza? restituisco una copia dei post
        lock.readLock().lock();
        Set<WinsomePost> tmp = new HashSet<WinsomePost>(users.get(user).getPosts());
        // TODO visto che lavoro su una copia dei post, non è necessario sincronizzare post durante la print, addrate e addcomment
        // TODO sì, ma così come si fa lo switch tra vecchi e nuovi? come si fa a incrementare niter?
        lock.readLock().unlock();
        */

        // TODO qui va bloccata la lettura in qualche modo. Metti che un post viene eliminato nel frattempo
        // ok, ho messo il metodo readLock e readUnlock che il reward chiamerà prima e dopo aver fatto con postperuser
        return users.get(user).getPosts(); 
    }

    /**
     * Aggiorna i portafogli degli utenti. Questo metodo viene
     * invocato dal thread per effettuare il calcolo delle ricompense.
     * Il metodo è concorrente
     * 
     * @param rewardPerUser Struttura che contiene le ricompense per gli utenti
     * @return true se l'operazione è andata a buon fine
     */
    protected boolean updateReward(Map<String, Double> rewardPerUser){

        for ( WinsomeUser user : users.values() )
            if ( rewardPerUser.get(user.getNickname()) != null ){
                lock.writeLock().lock(); // Necessario sicnronizzare in scrittura per evitare race condition con il thread che effettua il backup
                user.updateReward(Calendar.getInstance().getTime(), rewardPerUser.get(user.getNickname()));
                lock.writeLock().unlock();
            }

        return true;
    }

    /**
     * Restituisce un riferimento alla struttura degli utenti.
     * Questo metodo viene invocato da più thread, non è sincronizzato
     * 
     * @return Gli utenti di Winsome
     */
    protected Map<String, WinsomeUser> getUsers(){
        return users;
    }

    /**
     * Ripristina le strutture del database.
     * Le informazioni sui post e sui tag vengono recuperate tramite gli utenti.
     * Questo meotdo viene invocato dal thread che effettua il backup
     * 
     * @param users La struttura degli utenti del database.
     * @return true se l'operazione ha avuto successo
     */
    protected boolean loadDatabase(Map<String, WinsomeUser> users){
        if ( users == null )
            return false;

        this.users = users;
        for ( WinsomeUser user : this.users.values() ){
            for ( WinsomePost post : user.getPosts() ){
                newPost.incrementAndGet(); // Aggiorno l'identificativo dei post
                posts.putIfAbsent(post.getIdPost(), post); // Utilizzo putIfAbsent invece che la put per non sovrascrivere, evito modifiche malevole al db
            }
            for ( String tag : user.getTags() ){
                tags.putIfAbsent(tag, new HashSet<String>()); // Evito di sovrascrivere se il tag era già presente
                // Aaggiungo l'utente all'insieme di quelli che hanno indicato quel tag
                tags.get(tag).add(user.getNickname());
            }
        }

        return true;
    }



    



    /*
    Qui ci vanno le funzioni del database che chiamerà il worker
    */

    /**
     * Aggiunge un follower a quelli di un utente
     * 
     * @param username Utente che inizia a seguire
     * @param toFollow L'utente da seguire
     * @return true se l'operazione è andata a buon fine, altrimenti solleva eccezione
     * @throws WinsomeException Se l'operazione non è consentita (specificato nel message)
     * @throws NullPointerException Se username o toFollow sono null
     */
    protected boolean followUser(String username, String toFollow)
    throws WinsomeException, NullPointerException {
        // username inizia a seguire toFollow

        if ( username == null || toFollow == null )
            throw new NullPointerException();

        WinsomeUser follower = users.get(username); // Chi segue
        if ( follower == null )
            throw new WinsomeException("L'utente non è iscritto a Winsome");

        if ( !follower.isLogged() )
            throw new WinsomeException("L'utente non ha effettuato il login");

        WinsomeUser followed = users.get(toFollow); // Chi viene seguito
        if ( followed == null )
            throw new WinsomeException("L'utente che si vuole seguire non è iscritto a Winsome");
        
        lock.writeLock().lock();
        try {
            return followed.addFollower(username) && follower.addFollowing(toFollow);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Toglie un follower da quelli di un utente
     * 
     * @param username Utente che smette di seguire
     * @param toUnfollow L'utente da smettere di seguire
     * @return true se l'operazione è andata a buon fine, altrimenti solleva eccezione
     * @throws WinsomeException Se l'operazione non è consentita (specificato nel message)
     * @throws NullPointerException Se username o toUnfollow sono null
     */
    protected boolean unfollowUser(String username, String toUnfollow)
    throws WinsomeException, NullPointerException {
        // user smette di seguire toUnfollow

        if ( username == null || toUnfollow == null )
            throw new NullPointerException();

        WinsomeUser follower = users.get(username); // Chi segue
        if ( follower == null )
            throw new WinsomeException("L'utente non è iscritto a Winsome");

        if ( !follower.isLogged() )
            throw new WinsomeException("L'utente non ha effettuato il login");

        WinsomeUser followed = users.get(toUnfollow); // Chi viene seguito
        if ( followed == null )
            throw new WinsomeException("L'utente che si vuole smettere di seguire non è iscritto a Winsome");

        lock.writeLock().lock();
        try{
            return followed.removeFollower(username) && follower.removeFollowing(toUnfollow);   
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Restituisce gli utenti che hanno almeno un tag in comune con quelli di un utente
     * 
     * @param username Utente di cui si voglio conoscere gli utenti con almeno un tag in comune
     * @return L'insieme degli utenti con almeno un tag in comune
     * @throws WinsomeException Se l'operazione non è consentita (specificato nel message)
     * @throws NullPointerException Se username è null
     */
    protected Set<String> listUsers(String username)
    throws WinsomeException, NullPointerException {
        if ( username == null )
            throw new NullPointerException();

        WinsomeUser user = users.get(username);
        if ( user == null )
            throw new WinsomeException("L'utente non è iscritto a Winsome");

        if ( !user.isLogged() )
            throw new WinsomeException("L'utente non ha effettuato il login");

        Set<String> usersWithTagInCommon = new HashSet<String>();
        Set<String> userTags = user.getTags(); // Se lancia NullPointerExeption la gestisce il worker
        
        for ( String tag : userTags )
            // Per ogni tag aggiungo all'insieme degli utenti con un tag in comune gli utenti presenti nel campo value della struttura dei tags
            usersWithTagInCommon.addAll(tags.get(tag)); // Se lancia NullPointerException la gestisce il worker

        // Tolgo dall'insieme l'utente che ha fatto la richiesta
        usersWithTagInCommon.remove(username);
        return usersWithTagInCommon;
    }

    /**
     * Restituisce gli utenti seguiti da un utente
     * 
     * @param username Utente di cui si vogliono conoscere gli utenti seguiti
     * @return L'insieme degli utenti seguiti
     * @throws WinsomeException Se l'operazione non è consentita (specificato nel message)
     * @throws NullPointerException Se username è null
     */
    protected Set<String> listFollowing(String username)
    throws WinsomeException, NullPointerException {
        if ( username == null )
            throw new NullPointerException();

        WinsomeUser user = users.get(username);
        if ( user == null )
            throw new WinsomeException("L'utente non è iscritto a Winsome");

        if ( !user.isLogged() )
            throw new WinsomeException("L'utente non ha effettuato il login");
        
        return user.getFollowing();
    }

    /**
     * Effettua il login di un utente
     * 
     * @param username Utente di cui si effettua il login
     * @param password Password in chiaro dell'utente
     * @return true se l'operazione è andata a buon fine, altrimenti solleva eccezione
     * @throws WinsomeException Se l'operazione non è consentita (specificato nel message)
     * @throws NullPointerException Se username o password sono null
     */
    protected boolean login(String username, String password)
    throws WinsomeException, NullPointerException {
        if ( username == null || password == null )
            throw new NullPointerException();

        WinsomeUser user = users.get(username);
            if ( user == null )
                throw new WinsomeException("L'utente non è iscritto a Winsome");

        lock.writeLock().lock();
        try{
            return user.login(password); // Ritorna true o solleva eccezione
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Effettua il logout di un utente
     * 
     * @param username L'utente di cui si effettua il logout
     * @return true se l'operazione è andata a buon fine, altrimenti solleva eccezione
     * @throws WinsomeException Se l'operazione non è consentita (specificato nel message)
     * @throws NullPointerException Se username è null
     */
    protected boolean logout(String username)
    throws WinsomeException, NullPointerException {
        if ( username == null )
            return false;

        WinsomeUser user = users.get(username);
        if ( user == null )
            throw new WinsomeException("L'utente non è iscritto a Winsome");

        if ( !user.isLogged() )
            throw new WinsomeException("L'utente non ha effettuato il login");
        
        lock.writeLock().lock();
        try{
            return user.logout(); // Ritorna true o solleva eccezione
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Restituisce il blog, ovvero l'insieme dei post pubblicati e rewinnati dall'utente
     * 
     * @param username Utente di cui si vuole ottenere il blog
     * @param checkLogin true se è necessario che l'utente abbia effettuato il login, false altrimenti
     * @return Il blog dell'utente, null è un valore valido
     * @throws WinsomeException Se l'operazione non è consentita (specificato nel message)
     * @throws NullPointerException Se username è null
     */
    protected Set<WinsomePost> viewBlog(String username, boolean checkLogin)
    throws WinsomeException, NullPointerException {
        if ( username == null )
            throw new NullPointerException();

        WinsomeUser user = users.get(username);
        if ( user == null )
            throw new WinsomeException("L'utente non è iscritto a Winsome");

        if ( checkLogin )
            if ( !user.isLogged() )
                throw new WinsomeException("L'utente non ha effettuato il login");

        Set<WinsomePost> blog = new HashSet<>(user.getPosts());
        Set<Integer> rewin = user.getRewin();

        for ( Integer idPost : rewin )
            blog.add(((posts.get(idPost))));

        return blog;
    }

    /**
     * Crea un nuovo post
     * 
     * @param author Autore del post
     * @param title Titolo del post
     * @param content Contenuto del post
     * @return true se l'operazione è andata a buon fine, altrimenti solleva eccezione
     * @throws WinsomeException Se l'operazione non è consentita (specificato nel message)
     * @throws NullPointerException Se author, title o content sono null
     */
    protected boolean createPost(String author, String title, String content)
    throws WinsomeException, NullPointerException {
        if ( author == null || title == null || content == null )
            throw new NullPointerException();

        WinsomeUser user = users.get(author);
        if ( user == null )
            throw new WinsomeException("L'utente non è iscritto a Winsome");

        if ( !user.isLogged() )
            throw new WinsomeException("L'utente non ha effettuato il login");

        // TODO ma la gestione della concorrenza?
        // Qui o il reward lavora su una copia, o devo sincronizzare
        // Per ora sincronizzo
        WinsomePost post = new WinsomePost(newPost.incrementAndGet(), title, author, content); // solleva IllegalArgument e NullPointer
        
        // Qui inizia la race condition con il reward calculator
        lock.writeLock().lock();
        user.addPost(post);
        lock.writeLock().unlock();

        // Qui finisce la sincronizzazione immagino, dipende se il reward lavora su posts, ma mi sembra di no
        posts.put(post.getIdPost(), post);

        return true;
    }

    /**
     * Restituisce il feed di un utente, ovvero i blog degli utenti seguiti
     * 
     * @param username Utente di cui si vuole ottenere il feed
     * @param checkLogin true se è necessario che l'utente abbia effettuato il login, false altrimenti
     * @return Il feed dell'utente, null è un valore valido
     * @throws WinsomeException Se l'operazione non è consentita (specificato nel message)
     * @throws NullPointerException Se username è null
     */
    protected Set<WinsomePost> showFeed(String username, boolean checkLogin)
    throws WinsomeException, NullPointerException {
        if ( username == null )
            throw new NullPointerException();

        WinsomeUser user = users.get(username);
        if ( user == null )
            throw new WinsomeException("L'utente non è iscritto a Winsome");

        if ( checkLogin )
            if ( !user.isLogged() )
                throw new WinsomeException("L'utente non ha effettuato il login");

        Set<WinsomePost> feed = new HashSet<WinsomePost>();
        Set<String> following = user.getFollowing();

        for ( String followed : following )
            feed.addAll(viewBlog(followed, false));

        return feed;
    }

    /**
     * Restituisce il post in formato leggibile
     * 
     * @param idPost Id del post che si vuole mostrare
     * @return Il post in formato leggibile
     * @throws WinsomeException Se l'operazione non è consentita (specificato nel message)
     * @throws IllegalArgumentException Se idPost ha un valore negativo
     */
    protected String showPost(int idPost)
    throws WinsomeException, IllegalArgumentException {
        if ( idPost < 0 )
            throw new IllegalArgumentException();

        // TODO se modifico nIter mentre lo sto inviando al client?
        WinsomePost post = posts.get(idPost);
        if ( post == null )
            throw new WinsomeException("Il post non è presente in Winsome");
        
        return post.toPrint(); // Sincronizzata per la sezione critica nIter
    }

    /**
     * Elimina un post da Winsome, quindi lo elimina dal blog dell'autore e rimuove i suoi rewin.
     * Chi richiede l'eliminazione di un post deve esserne l'autore
     * 
     * @param username Utente che richiede l'eliminazione
     * @param idPost Id del post da eliminare
     * @return true se l'operazione è andata a buon fine, false altrimenti
     * @throws WinsomeException Se l'operazione non è consentita (specificato nel message)
     * @throws IllegalArgumentException Se idPost ha un valore negativo
     * @throws NullPointerException Se username è null
     */
    protected boolean deletePost(String username, int idPost)
    throws WinsomeException, IllegalArgumentException, NullPointerException {
        if ( idPost < 0 )
            throw new IllegalArgumentException();

        if ( username == null )
            throw new NullPointerException();

        WinsomeUser user = users.get(username);
        if ( user == null )
            throw new WinsomeException("L'utente non è iscritto a Winsome");

        if ( !user.isLogged() )
            throw new WinsomeException("L'utente non ha effettuato il login");

        WinsomePost post = posts.get(idPost);
        if ( post == null )
            throw new WinsomeException("Il post non è presente in Winsome");

        // Se l'utente che ha richiesto la delete non è l'autore del post
        if ( !post.getAuthor().equals(username) )
            throw new WinsomeException("L'utente non è l'autore del post");

        lock.writeLock().lock();
        try{
            // Se l'eliminazione va a buon fine
            if ( user.removePost(post) )
                if ( removePost(idPost) )
                    return true;
        } catch ( WinsomeException e ){
            throw e;
        } finally {
            lock.writeLock().unlock();
        }

        return false;
    }

    /**
     * Effettua il rewin di un post per un utente.
     * Si può effettuare il rewin di un post solo se è nel proprio feed
     * 
     * @param username Utente che richiede di effettuare i login
     * @param idPost Id del post da rewinnare
     * @return true se l'operazione è andata a buon fine, altrimenti solleva eccezione
     * @throws WinsomeException Se l'operazione non è consentita (specificato nel message)
     * @throws IllegalArgumentException Se idPost ha un valore negativo
     * @throws NullPointerException Se username è null
     */
    protected boolean rewinPost(String username, int idPost)
    throws WinsomeException, IllegalArgumentException, NullPointerException {        
        if ( idPost < 0 )
            throw new IllegalArgumentException();

        if ( username == null )
            throw new NullPointerException();

        WinsomeUser user = users.get(username);
        if ( user == null )
            throw new WinsomeException("L'utente non è iscritto a Winsome");

        if ( !user.isLogged() )
            throw new WinsomeException("L'utente non ha effettuato il login");

        WinsomePost post = posts.get(idPost);
        if ( post == null )
            throw new WinsomeException("Il post non è presente in Winsome");
        
        // Posso fare il rewind di un post solo se è nel mio feed
        if ( showFeed(username, false).contains(post) )
            if ( post.rewinPost(username) ) // Restituisce true o solleva un'eccezione
                if ( user.addRewin(idPost) ) // Restituisce true o solleva un'eccezione
                    return true;
        
        throw new WinsomeException("Non è possibile effettuare il rewin di un post che non è nel proprio feed");
    }

    /**
     * Aggiunge un voto positivo o negativo a un post da parte di un utente.
     * Non possono essere aggiunti più voti a un post dallo stesso utente.
     * Un utente può votare un post solo se è presente nel proprio feed
     * 
     * @param username Utente che richiede di votare
     * @param idPost Id del post che si vuole votare
     * @param vote Valore del voto, 1 per un voto positivo, -1 per uno negativo
     * @return true se l'operazione è andata a buon fine, altrimenti solleva eccezione
     * @throws WinsomeException Se l'operazione non è consentita (specificato nel message)
     * @throws IllegalArgumentException Se idPost ha un valore negativo
     * @throws NullPointerException Se username è null
     */
    protected boolean ratePost(String username, int idPost, int vote)
    throws WinsomeException, IllegalArgumentException, NullPointerException {
        if ( idPost < 0 )
            throw new IllegalArgumentException();

        if ( username == null )
            throw new NullPointerException();

        WinsomeUser user = users.get(username);
        if ( user == null )
            throw new WinsomeException("L'utente non è iscritto a Winsome");

        if ( !user.isLogged() )
            throw new WinsomeException("L'utente non ha effettuato il login");

        WinsomePost post = posts.get(idPost);
        if ( post == null )
            throw new WinsomeException("Il post non è presente in Winsome");
        
        // Posso votare un post solo se è nel mio feed
        if ( showFeed(username, false).contains(post) )
            if ( post.addRate(username, vote) )
                return true;

        throw new WinsomeException("Non è possibile votare un post che non è nel proprio feed");
    }

    /**
     * Aggiunge un commento a un post da parte di un utente
     * 
     * @param username L'utente che commenta
     * @param idPost Id del post da commentare
     * @param comment Contenuto del commento
     * @return true se l'operazione è andata a buon fine, altrimenti solleva eccezione
     * @throws WinsomeException Se l'operazione non è consentita (specificato nel message)
     * @throws IllegalArgumentException Se idPost ha un valore negativo
     * @throws NullPointerException Se username o comment sono null
     */
    protected boolean addComment(String username, int idPost, String comment)
    throws WinsomeException, IllegalArgumentException, NullPointerException {
        if ( idPost < 0 )
            throw new IllegalArgumentException();

        if ( username == null || comment == null )
            throw new NullPointerException();

        WinsomeUser user = users.get(username);
        if ( user == null )
            throw new WinsomeException("L'utente non è iscritto a Winsome");

        if ( !user.isLogged() )
            throw new WinsomeException("L'utente non ha effettuato il login");

        WinsomePost post = posts.get(idPost);
        if ( post == null )
            throw new WinsomeException("Il post non è presente in Winsome");
        
        // Posso commentare un post solo se è nel mio feed
        if ( showFeed(username, false).contains(post) ){
            lock.writeLock().lock();
            try{
                if ( post.addComment(username, comment) ) // Restituisce true o solleva eccezione
                    return true;
            } finally {
                lock.writeLock().unlock();
            }
        }
        
        throw new WinsomeException("Non è possibile commentare un post che non è nel proprio feed");
    }

    /**
     * Restuisce lo storico del portafoglio di un utente
     * 
     * @param username Utente di cui si richiede lo storico
     * @return Lo storico del portafolgio
     * @throws WinsomeException Se l'operazione non è consentita (specificato nel message)
     * @throws NullPointerException Se username è null
     */
    protected Queue<WinsomeWallet> getWallet(String username)
    throws WinsomeException, NullPointerException {
        if ( username == null )
            throw new NullPointerException();

        WinsomeUser user = users.get(username);
        if ( user == null )
            throw new WinsomeException("L'utente non è iscritto a Winsome");

        if ( !user.isLogged() )
            throw new WinsomeException("L'utente non ha effettuato il login");

        return user.getReward(); // Ok, è una concurrent collection

        /*
         * Due parole anche qui... TODO
         * il wallet viene toccato da tutti i thread, reward, Worker e backup.
         * Rendendo essa una coda concorrente risolvo? Mi sembra di sì
         */
    }

}
