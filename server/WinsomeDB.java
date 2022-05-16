package server;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Classe che rappresenta il database di Winsome,
 * ovvero raccoglie tutti i post e gli utenti
 */
public class WinsomeDB {

    private ConcurrentHashMap<Integer, WinsomePost> posts;
    // Qual è il senso di rendere la struttura concurrent se poi utilizzo le lock?
    private ConcurrentHashMap<String, WinsomeUser> users;

    public WinsomeDB(){
        this.posts = new ConcurrentHashMap<Integer, WinsomePost>();
        this.users = new ConcurrentHashMap<String, WinsomeUser>();
    }

    public boolean addUser(WinsomeUser user){
        if ( users.putIfAbsent(user.getNickname(), user) == null )
            return false;

        return true;
    }

    // Restituisce una deep copy dei post pubblicati dall'utente
    public Set<WinsomePost> getPostPerUser(String user){
        // La get su users è threadsafe e getPosts restituisce una deep copy
        return users.get(user).getPosts(); 
    }

    // Restituisce un riferimento agli utenti presenti 
    public ConcurrentHashMap<String, WinsomeUser> getUsers(){
        
        return users;
    }
    
}
