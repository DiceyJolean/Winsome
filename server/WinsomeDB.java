package server;

import java.io.Serializable;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Classe che rappresenta il database di Winsome,
 * ovvero raccoglie tutti i post e gli utenti
 */
public class WinsomeDB implements Serializable {

    private final boolean DEBUG = true;

    private Map<Integer, WinsomePost> posts;
    private AtomicInteger newPost; // Ha senso che sia atomico se solo il server crea i post, e per giunta uno per volta
    // Qual è il senso di rendere la struttura concurrent se poi utilizzo le lock?
    private Map<String, WinsomeUser> users;
    private Map<String, Set<String>> tags;

    public WinsomeDB(){
        this.posts = new ConcurrentHashMap<Integer, WinsomePost>(); // TODO
        this.users = new ConcurrentHashMap<String, WinsomeUser>(); // Ok per la putIfAbsent visto che la register è una sezione critica
        // Non è concurrent perché soltanto il main del server vi accede
        // In scrittura all'inserimento di un nuovo utente, in lettura alla richiesta di listUsers
        this.tags = new HashMap<String, Set<String>>();
        newPost = new AtomicInteger(0);
    }

    protected boolean addUser(WinsomeUser user){
        if ( user == null )
            return false;

        if ( users.putIfAbsent(user.getNickname(), user) != null ){
            if ( DEBUG ) System.out.println("DATABASE: Inserimento di " + user.getNickname() + " fallito, infatti la get restituisce: \n" + users.get(user.getNickname()));
            
            return false;
        }

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


        if ( DEBUG ) System.out.println("DATABASE: Inserimento di " + user.getNickname() + " avvenuto con successo\n");
        return true;
    }

    public WinsomeUser getUser(String username){
        if ( username == null )
            return null;

        return users.get(username);
    }

    public WinsomePost getPost(int id){
        if ( id < 0 )
            return null;

        return posts.get(id);
    }

    public WinsomePost removePost(int id){
        if ( id < 0 )
            return null;

        WinsomePost removed = posts.remove(id);
        
        // Devo rendere consistenti i rewin di questo post
        Set<String> rewinners = removed.getRewinners();
        for ( String rewinner : rewinners ){
            users.get(rewinner).removeRewin(id);
        }

        return removed;
    }




    /*
    Qui ci vanno le funzioni del database che chiamerà l'api del server
    */

    public boolean followUser(String user, String toFollow){
        // user inizia a seguire toFollow

        if ( user == null || toFollow == null )
            return false;

        if ( user.equals(toFollow) )
            // Non è possibile seguire se stessi
            return false;

        WinsomeUser follower = users.get(user); // Chi segue
        if ( follower == null )
            return false;

        WinsomeUser followed = users.get(toFollow); // Chi viene seguito
        if ( followed == null )
            return false;

        return follower.addFollowing(toFollow) && followed.addFollower(user);
    }
    
    public boolean unfollowUser(String user, String toUnfollow){
        // user smette di seguire toUnfollow

        if ( user == null || toUnfollow == null )
            return false;

        if ( user.equals(toUnfollow) )
            // Non è possibile seguire se stessi
            return false;

        WinsomeUser follower = users.get(user); // Chi segue
        if ( follower == null )
            return false;

        WinsomeUser followed = users.get(toUnfollow); // Chi viene seguito
        if ( followed == null )
            return false;

        return followed.removeFollower(user) && follower.removeFollowing(toUnfollow);
    }

    public Set<String> listUsers(String username){
        if ( username == null )
            return null;

        Set<String> usersWithTagInCommon = new HashSet<String>();
        Set<String> userTags = users.get(username).getTags();
        
        for ( String tag : userTags )
            // Per ogni tag aggiungo all'insieme degli utenti con un tag in comune gli utenti presenti nel campo value della struttura dei tags
            usersWithTagInCommon.addAll(tags.get(tag));

        // Tolgo dall'insieme l'utente che ha fatto la richiesta
        usersWithTagInCommon.remove(username);
        return usersWithTagInCommon;
    }

    public Set<String> listFollowing(String username){
        if ( username == null )
            return null;

        return users.get(username).getFollowing();
    }

    public boolean login(String username, String password){
        if ( username == null || password == null )
            return false;

        WinsomeUser toLogin = users.get(username);
        if ( toLogin == null ){
            System.err.println("DATABASE: " + username + " non è presente nel database per poter effettuare il login");
            return false;
        }

        return toLogin.login(password);
    }

    public boolean logout(String username){
        if ( username == null )
            return false;

        WinsomeUser toLogout = users.get(username);
        if ( toLogout == null ){
            System.err.println("DATABASE: " + username + " non è registrato a Winsome");
            return false;
        }

        return toLogout.logout();
    }

    // Post pubblicati e rewinnati dall'utente
    public Set<WinsomePost> viewBlog(String username){
        if ( username == null )
            return null;

        Set<WinsomePost> blog = getPostPerUser(username);
        Set<Integer> rewin = getUser(username).getRewin();

        for ( Integer idPost : rewin )
            blog.add(((posts.get(idPost))));

        return blog;
    }

    public boolean createPost(String author, String title, String content){
        if ( author == null || title == null || content == null )
            return false;

        // TODO ma la gestione della concorrenza?
        // Qui o il reward lavora su una copia, o devo sincronizzare
        // Per ora sincronizzo
        WinsomePost post = new WinsomePost(newPost.incrementAndGet(), title, author, content);
        WinsomeUser user = users.get(author);
        // Qui inizia la race condition con il reward calculator
        synchronized(user){
            user.addPost(post);
        }
        // Qui finisce immagino, dipende se il reward lavora su posts, ma mi sembra di no
        posts.put(post.getIdPost(), post);

        return true;
    }

    // I post di tutti i miei seguiti più i loro rewin, ovvero
    // il blog di tutti i miei utenti seguiti
    public Set<WinsomePost> showFeed(String username){
        if ( username == null )
            return null;

        Set<WinsomePost> feed = new HashSet<WinsomePost>();
        Set<String> following = users.get(username).getFollowing();
        for ( String user : following )
            feed.addAll(viewBlog(user));

        return feed;
    }

    public WinsomePost showPost(int idPost){
        if ( idPost < 0 )
            return null;

        return posts.get(idPost);
    }

    public boolean deletePost(String username, int idPost){
        if ( idPost < 0 || username == null )
            return false;

        WinsomePost toRemove = posts.get(idPost);
        WinsomeUser author = users.get(toRemove.getAuthor());

        // Se l'utente che ha richiesto la delete non è l'autore del post
        if ( author == null || !toRemove.getAuthor().equals(username) || !author.equals(users.get(username)) )
            return false;

        // Se l'eliminazione va a buon fine
        if ( author.removePost(toRemove) )
            if ( posts.remove(idPost) != null )
                return true;

        return false;
    }

    public boolean rewinPost(String username, int idPost){
        if ( idPost < 0 || username == null )
            return false;

        // Posso fare il rewind di un post solo se è nel mio feed
        if ( showFeed(username).contains(posts.get(idPost)) )
            if ( users.get(username).addRewin(idPost) )
                if ( posts.get(idPost).rewinPost(username) )
                    return true;

        return false;
    }

    public boolean ratePost(String username, int idPost, int vote){
        if ( idPost < 0 || username == null )
            return false;

        // Posso fare il rate di un post solo se è nel mio feed
        if ( showFeed(username).contains(posts.get(idPost)) )
            if ( posts.get(idPost).addVote(username, vote) )
                return true;

        return false;
    }

    public boolean addComment(String username, int idPost, String comment){
        if ( idPost < 0 || username == null || comment == null )
            return false;

        // Posso commentare un post solo se è nel mio feed
        if ( showFeed(username).contains(posts.get(idPost)) )
            if ( posts.get(idPost).addComment(username, comment) )
                return true;
        
        return false;
    }

    public List<WinsomeWallet> getWallet(String username){
        if ( username == null )
            return null;

        return users.get(username).getReward();
    }

    public double getWalletInBitcoin(String username){
        if ( username == null )
            return 0.0;

        // TODO
        return 0.0;
    }








    public boolean updateReward(Map<String, Double> rewardPerUser){
        // la concurrent hashmap accede alla struttura degli utenti, 
        // per cui se il server contemporaneamente 

        for ( WinsomeUser user : users.values() )
            if ( rewardPerUser.get(user.getNickname()) != null )
                user.updateReward(Calendar.getInstance().getTime(), rewardPerUser.get(user.getNickname()));

        return true;
    }

    public synchronized WinsomeDB getCopy(){
        // TODO

        return this;
    }

    // Restituisce una deep copy dei post pubblicati dall'utente
    public Set<WinsomePost> getPostPerUser(String user){

        // La get su users è threadsafe e getPosts restituisce una deep copy
        return users.get(user).getPosts(); 
    }

    // Restituisce un riferimento agli utenti presenti 
    public Map<String, WinsomeUser> getUsers(){
        return users;
    }

    private boolean addPost(WinsomePost post){
        if ( post == null )
            return false;

        if ( posts.putIfAbsent(post.getIdPost(), post) != null ){
            // if ( DEBUG ) System.out.println("DATABASE: Inserimento del post n. " + post.getIdPost() + " fallito\n");

            return false;
        }

        // if ( DEBUG ) System.out.println("DATABASE: Inserimento del post n. " + post.getIdPost() + " avvenuto con successo\n");
        return true;
    }

    // Per evitare ridondanza nel file database recupero il Database di Winsome soltanto tramite la struttura degli utenti
    public boolean loadDatabase(Map<String, WinsomeUser> users){
        if ( users == null )
            return false;

        this.users = users;
        for ( WinsomeUser user : this.users.values() ){
            for ( WinsomePost post : user.getPosts() ){
                newPost.incrementAndGet();
                addPost(post);
            }
            for ( String tag : user.getTags() ){
                tags.putIfAbsent(tag, new HashSet<String>());
                // Poi aggiungo l'utente all'insieme di quelli che hanno indicato quel tag
                tags.get(tag).add(user.getNickname());
            }
        }

        return true;
    }
/*
    public boolean addFollower(String user, String follower){
        try{
            return this.users.get(user).addFollower(follower);
        } catch ( Exception e ){
            return false;
        }
    }
*/
    // TODO deve restituire una copia
    public WinsomeDB getDBCopy(){

        return this;
    }
    
}
