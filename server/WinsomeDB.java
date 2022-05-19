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
        users.putIfAbsent(user.getNickname(), user);

        if ( DEBUG ) System.out.println("DATABASE: Inserimento di " + user.getNickname() + " avvenuto con successo\n");
        return true;
    }

    public boolean addPost(WinsomePost post){
        posts.putIfAbsent(post.getIdPost(), post);

        return true;
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
