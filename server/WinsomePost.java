package server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public class WinsomePost{

    private int idPost; // Id del post
    private String title; // Titolo del post
    private String author; // Autore del post
    private String content; // Contenuto (testo) del post
    private ConcurrentHashMap<String, Vote> newVotes; // Insieme dei voti ricevuti dal post, un utente può votare solo una volta
    private ConcurrentHashMap<String, Vote> oldVotes; // Insieme dei voti ricevuti dal post, un utente può votare solo una volta
    private HashMap<String, ArrayList<String>> newComments; // Insieme dei commenti ricevuti dal post
    private HashMap<String, ArrayList<String>> oldComments; // Insieme dei commenti ricevuti dal post
    private Set<String> rewinners; // Insieme degli utenti che hanno rewinnato il post
    private int nIterations;

    public static class RewardAndCurators{
        private final double reward;
        private final Set<String> curators;
        
        public RewardAndCurators(double reward, Set<String> curators){
            this.reward = reward;
            this.curators = curators;
        }

        public Set<String> getCurators(){
            Set<String> copy = new HashSet<String>();
            copy.addAll(this.curators);
            return copy;
        }
    }

    public static enum Vote{
            LIKE,
            UNLIKE;
        }
    
    public WinsomePost(int idPost, String author, String content){
        this.idPost = idPost;
        this.title = new String(title);
        this.author = new String(author);
        this.content = new String(content);
        this.newVotes = new ConcurrentHashMap<String, Vote>();
        this.oldVotes = new ConcurrentHashMap<String, Vote>();
        this.newComments = new HashMap<String, ArrayList<String>>();
        this.oldComments = new HashMap<String, ArrayList<String>>();
        this.rewinners = new HashSet<String>();
        this.nIterations = 0;
    }

    // =========== Getter 

    public int getIdPost(){
        return this.idPost;
    }

    public String getTitle(){
        return this.title;
    }

    public String getAuthor(){
        return this.author;
    }

    public String getContent(){
        return this.content;
    }

    // =========== Setter

    // value può essere solo 1 o -1
    // TODO valore di return?
    // Synch perché sono due operazioni separate, ha senso avere una struttura concorrente?
    public synchronized void addVote(String user, int value){
        Vote vote;
        if ( value == 1 )
            vote = Vote.LIKE;
        else if ( value == -1 )
            vote = Vote.UNLIKE;
        else
            throw new IllegalArgumentException();
        
        // Controllo che l'utente non abbia già aggiunto un voto prima dell'ultima iterazione del reward
        if ( this.oldComments.get(user) == null )
            this.newVotes.putIfAbsent(user, vote);
                
    }

    // TODO valore di return?
    public void addVote(String user, Vote vote){
        
        // Controllo che l'utente non abbia già aggiunto un voto prima dell'ultima iterazione del reward
        if ( this.oldComments.get(user) == null )
            this.newVotes.putIfAbsent(user, vote);
    }

    // La ricerca di un utente e poi l'aggiunta di un commento in lista non è atomica
    public synchronized void addComment(String user, String comment){
        if ( comment == null )
            throw new NullPointerException();

        // Aggiungo sempre in newComment
        // Sposto dopo il calcolo del reward tra new e old
        this.newComments.get(user).add(comment);
    }

    public synchronized RewardAndCurators rewardCalculation(){
        Set<String> curators = new HashSet<String>();
        double reward = 0;
        int voteSum = 0;
        int commentSum = 0;

        // Trovo i curatori e calcolo la somma dei voti
        for ( ConcurrentHashMap.Entry<String, Vote> vote : newVotes.entrySet() ){
            curators.add(vote.getKey());
            if ( vote.getValue() == Vote.LIKE )
                voteSum++;
            else
                voteSum--;
        }

        if ( voteSum < 0 )
            voteSum = 0;
        voteSum++;

        // Sposto i voti nuovi nella struttura di quelli già contati
        // L'operazione è sicura, perché ho già controllato i duplicati al momento 
        // dell'inserimento del voto per evitare doppio voto dallo stesso utente
        oldVotes.putAll(newVotes);
        newVotes.clear();

        // In questo ciclo farò delle remove sulla struttura iterata,
        // ma non crea problemi grazie alla weak-consistency ???
        for ( Entry<String, ArrayList<String>> entry : newComments.entrySet() ){
            commentSum += 2/(1 + Math.pow(Math.E, entry.getValue().size()*(-1)));

            // Sposto i nuovi commenti nella struttura di quelli già contati

            if ( this.oldComments.get(entry.getKey()) != null ){
                // Se questo autore aveva già commentato questo post

                // Aggiungo i commenti nel vecchio array di commenti
                this.oldComments.get(entry.getKey()).addAll(entry.getValue());
                // E svuoto il vecchio array
                this.newComments.get(entry.getKey()).clear();

                // Posso scegliere se togliere o no anche l'autore del commento
                // Lo tolgo, così ho un'iterazione in meno essendoci una entry in meno
                // Inoltre a pelle mi sembra più corretto per quel che riguarda la formula
                this.newComments.remove(entry.getKey());

                // TODO controllare se clear e remove danno eccezione
            }
        }

        commentSum++; // +1 della formula

        reward = ( Math.log(voteSum) + Math.log(commentSum) ) / this.nIterations;
        nIterations++;

        return new RewardAndCurators(reward, curators);
    }

    public synchronized void Rewin(String user){
        this.rewinners.add(user);
    }

}