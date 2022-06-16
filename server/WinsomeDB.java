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

    private final boolean DEBUG = true;

    private Map<Integer, WinsomePost> posts;
    private AtomicInteger newPost; // Ha senso che sia atomico se solo il server crea i post, e per giunta uno per volta
    // Qual è il senso di rendere la struttura concurrent se poi utilizzo le lock?
    protected ReadWriteLock lock; // Chiunque può creare un database, ma solo chi è nel package server può utilizzare la lock
    private Map<String, WinsomeUser> users;
    private Map<String, Set<String>> tags;

    public WinsomeDB(){
        lock = new ReentrantReadWriteLock();
        posts = new HashMap<Integer, WinsomePost>(); // La concorrenza è gestita con la readwrite lock
        users = new ConcurrentHashMap<String, WinsomeUser>(); // Ok per la putIfAbsent visto che la register è una sezione critica
        // Non è concurrent perché soltanto il main del server vi accede
        // In scrittura all'inserimento di un nuovo utente, in lettura alla richiesta di listUsers
        tags = new HashMap<String, Set<String>>();
        newPost = new AtomicInteger(0);
    }

    protected boolean addUser(WinsomeUser user){
        if ( user == null )
            return false;

        lock.writeLock().lock();
        if ( users.putIfAbsent(user.getNickname(), user) != null ){
            lock.writeLock().unlock();
            if ( DEBUG ) System.out.println("Inserimento di " + user.getNickname() + " fallito, nickname già in uso");
            
            return false;
        }
        lock.writeLock().unlock();

        // Aggiorno la struttura dei tags
        Set<String> userTags = user.getTags();
        for ( String tag : userTags ){
            tag.toLowerCase();
            // Se non era presente lo aggiungo
            synchronized ( tags ){
                tags.putIfAbsent(tag, new HashSet<String>());
                // Poi aggiungo l'utente all'insieme di quelli che hanno indicato quel tag
                tags.get(tag).add(user.getNickname());
            }
        }


        if ( DEBUG ) System.out.println("Inserimento di " + user.getNickname() + " avvenuto con successo\n");
        return true;
    }

    private WinsomePost removePost(int id){
        if ( id < 0 )
            return null;

        // TODO quando viene chiamato questo modifico la struttura è già bloccata in modalità scrittura
        // lock.writeLock().lock();
        WinsomePost removed = posts.remove(id);
        // lock.writeLock().unlock();

        // Devo rendere consistenti i rewin di questo post
        Set<String> rewinners = removed.getRewinners();
        for ( String rewinner : rewinners )
            users.get(rewinner).removeRewin(id);

        return removed;
    }

    // Restituisce un riferimento ai post pubblicati dall'utente
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




    



    /*
    Qui ci vanno le funzioni del database che chiamerà l'api del server
    */

    protected boolean followUser(String user, String toFollow)
    throws WinsomeException {
        // user inizia a seguire toFollow

        if ( user == null || toFollow == null )
            throw new NullPointerException();

        WinsomeUser follower = users.get(user); // Chi segue
        if ( follower == null )
            throw new WinsomeException("L'utente non è iscritto a Winsome");

        if ( !follower.isLogged() )
            throw new WinsomeException("L'utente non ha effettuato il login");

        WinsomeUser followed = users.get(toFollow); // Chi viene seguito
        if ( followed == null )
            throw new WinsomeException("L'utente che si vuole seguire non è iscritto a Winsome");
        
        lock.writeLock().lock();
        try {
            return followed.addFollower(user) && follower.addFollowing(toFollow);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    protected boolean unfollowUser(String username, String toUnfollow)
    throws WinsomeException {
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

    protected Set<String> listUsers(String username)
    throws WinsomeException {
        if ( username == null )
            throw new NullPointerException();

        WinsomeUser user = users.get(username);
        if ( user == null )
            throw new WinsomeException("L'utente non è iscritto a Winsome");

        if ( !user.isLogged() )
            throw new WinsomeException("L'utente non ha effettuato il login");

        Set<String> usersWithTagInCommon = new HashSet<String>();
        Set<String> userTags = user.getTags(); // Se lancia NullPointerExeption la gestisce il server
        
        for ( String tag : userTags )
            // Per ogni tag aggiungo all'insieme degli utenti con un tag in comune gli utenti presenti nel campo value della struttura dei tags
            usersWithTagInCommon.addAll(tags.get(tag)); // Se lancia NullPointerException la gestisce il server

        // Tolgo dall'insieme l'utente che ha fatto la richiesta
        usersWithTagInCommon.remove(username);
        return usersWithTagInCommon;
    }

    protected Set<String> listFollowing(String username)
    throws WinsomeException {
        if ( username == null )
            throw new NullPointerException();

        WinsomeUser user = users.get(username);
        if ( user == null )
            throw new WinsomeException("L'utente non è iscritto a Winsome");

        if ( !user.isLogged() )
            throw new WinsomeException("L'utente non ha effettuato il login");
        
        return user.getFollowing();
    }

    protected boolean login(String username, String password)
    throws WinsomeException {
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

    protected boolean logout(String username)
    throws WinsomeException {
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

    // Post pubblicati e rewinnati dall'utente
    protected Set<WinsomePost> viewBlog(String username, boolean checkLogin)
    throws WinsomeException {
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

    protected boolean createPost(String author, String title, String content)
    throws WinsomeException, IllegalAccessException, NullPointerException {
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

    // I post di tutti i miei seguiti più i loro rewin, ovvero
    // il blog di tutti i miei utenti seguiti
    protected Set<WinsomePost> showFeed(String username, boolean checkLogin)
    throws WinsomeException {
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
                if ( removePost(idPost) != null )
                    return true;
        } catch ( WinsomeException e ){
            throw e;
        } finally {
            lock.writeLock().unlock();
        }

        return false;
    }

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

    protected Queue<WinsomeWallet> getWallet(String username)
    throws WinsomeException {
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
         * il wallet viene toccato da tutti i thread, reward, server e backup.
         * Rendendo essa una coda concorrente risolvo? Mi sembra di sì
         */
    }

















    protected boolean updateReward(Map<String, Double> rewardPerUser){
        // la concurrent hashmap accede alla struttura degli utenti, 
        // per cui se il server contemporaneamente esegue altre istruzioni sugli utenti si possono verificare race condition

        for ( WinsomeUser user : users.values() )
            if ( rewardPerUser.get(user.getNickname()) != null ){
                lock.writeLock().lock();
                user.updateReward(Calendar.getInstance().getTime(), rewardPerUser.get(user.getNickname()));
                lock.writeLock().unlock();
            }

        return true;
    }

    // Restituisce un riferimento agli utenti presenti 
    // TODO ??? questa si lascia così? users è una concurrenthashmpa...
    protected Map<String, WinsomeUser> getUsers(){
        return users;
    }

    // Per evitare ridondanza nel file database recupero il Database di Winsome soltanto tramite la struttura degli utenti
    protected boolean loadDatabase(Map<String, WinsomeUser> users){
        if ( users == null )
            return false;

        this.users = users;
        for ( WinsomeUser user : this.users.values() ){
            for ( WinsomePost post : user.getPosts() ){
                newPost.incrementAndGet();
                posts.putIfAbsent(post.getIdPost(), post); // Utilizzo putIfAbsent invece che la put per non sovrascrivere, evito modifiche malevole al db
            }
            for ( String tag : user.getTags() ){
                tags.putIfAbsent(tag, new HashSet<String>());
                // Poi aggiungo l'utente all'insieme di quelli che hanno indicato quel tag
                tags.get(tag).add(user.getNickname());
            }
        }

        return true;
    }

}
