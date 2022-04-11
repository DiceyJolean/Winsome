package server;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Classe che rappresenta il database di Winsome,
 * ovvero raccoglie tutti i post e gli utenti
 */
public class WinsomeDB {

    private ConcurrentHashMap<Integer, WinsomePost> posts;
    private ConcurrentHashMap<String, WinsomeUser> users;
    public ReadWriteLock lockUserDB;

    public WinsomeDB(){
        this.posts = new ConcurrentHashMap<Integer, WinsomePost>();
        this.users = new ConcurrentHashMap<String, WinsomeUser>();
        this.lockUserDB = new ReentrantReadWriteLock();
    }

    // Restituisce una deep copy dei post pubblicati dall'utente
    public Set<WinsomePost> getPostPerUser(String user){
        // La get su users è threadsafe e getPosts restituisce una deep copy
        return users.get(user).getPosts(); 
    }

    // Restituisce un riferimento agli utenti presenti 
    public Collection<WinsomeUser> getUsers(){
        return users.values();
    }
    
}
