package server;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Classe che rappresenta il database di Winsome,
 * ovvero raccoglie tutti i post e gli utenti
 */
public class WinsomeDB {

    private ConcurrentHashMap<Integer, WinsomePost> posts;
    private ConcurrentHashMap<String, WinsomeUser> users;

    public WinsomeDB(){
        this.posts = new ConcurrentHashMap<Integer, WinsomePost>();
        this.users = new ConcurrentHashMap<String, WinsomeUser>();
    }


    public Set<WinsomePost> getPosts(){
        return new HashSet<WinsomePost>(posts.values());
    }

    public Set<WinsomePost> getPostPerUser(String user){
        // La get su users è threadsafe e getPosts restituisce una deep copy
        return users.get(user).getPosts(); 
    }

    public Set<String> getUsers(){
        return new HashSet<String>(users.keySet());
    }
    
}
