package server;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Classe che rappresenta il database di Winsome,
 * ovvero raccoglie tutti i post e gli utenti
 */
public class WinsomeDB implements Serializable {
    private final boolean DEBUG = true;

    private Map<Integer, WinsomePost> posts;
    // Qual è il senso di rendere la struttura concurrent se poi utilizzo le lock?
    private Map<String, WinsomeUser> users;

    public WinsomeDB(){
        this.posts = new ConcurrentHashMap<Integer, WinsomePost>();
        this.users = new ConcurrentHashMap<String, WinsomeUser>();
    }

    public boolean addUser(WinsomeUser user){
        if ( user == null )
            return false;

        if ( users.putIfAbsent(user.getNickname(), user) != null ){
            if ( DEBUG ) System.out.println("DATABASE: Inserimento di " + user.getNickname() + " fallito, chiave duplicata\n");

            return false;
        }

        if ( DEBUG ) System.out.println("DATABASE: Inserimento di " + user.getNickname() + " avvenuto con successo\n");
        return true;
    }

    public boolean addPost(WinsomePost post){
        if ( post == null )
            return false;

        if ( posts.putIfAbsent(post.getIdPost(), post) != null ){
            if ( DEBUG ) System.out.println("DATABASE: Inserimento del post n. " + post.getIdPost() + " fallito\n");

            return false;
        }

        if ( DEBUG ) System.out.println("DATABASE: Inserimento del post n. " + post.getIdPost() + " fallito avvenuto con successo\n");
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

    

    // Restituisce una deep copy dei post pubblicati dall'utente
    public Set<WinsomePost> getPostPerUser(String user){

        // La get su users è threadsafe e getPosts restituisce una deep copy
        return users.get(user).getPosts(); 
    }

    // Restituisce un riferimento agli utenti presenti 
    public Map<String, WinsomeUser> getUsers(){
        return users;
    }

    // Per evitare un eccessivo carico del file database recupero il Database di Winsome soltanto tramite la struttura degli utenti
    public boolean loadDatabase(Map<String, WinsomeUser> users){
        if ( users == null )
            return false;

        this.users = users;
        for ( WinsomeUser user : this.users.values() )
            for ( WinsomePost post : user.getPosts() )
                addPost(post);

        return true;
    }

    public boolean addFollower(String user, String follower){
        try{
            return this.users.get(user).addFollower(follower);
        } catch ( Exception e ){
            return false;
        }
    }

    // TODO deve restituire una copia
    public WinsomeDB getDBCopy(){

        return this;
    }
    
}
