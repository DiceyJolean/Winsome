package server;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class WinsomeVote{

    private ConcurrentHashMap<String, Integer> vote; // Insieme dei voti ricevuti dal post, un utente può votare solo una volta
    private AtomicInteger value; // Contatore

    public WinsomeVote(){
        vote = new ConcurrentHashMap<String, Integer>();
        value = new AtomicInteger(0);
    }

    public ConcurrentHashMap<String, Integer> getVotes(){
        ConcurrentHashMap<String, Integer> clone = new ConcurrentHashMap<String, Integer>();
        clone.putAll(vote);
        return clone;
    }

    public Integer getValue(){
        return this.value.get();
    }

    
}