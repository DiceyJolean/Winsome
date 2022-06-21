package server;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Classe che implementa il thread che periodicamente calcola le ricompense
 */
public class RewardCalculator extends Thread {

    private int period; // Periodo ogni quanto viene effettuato il calcolo delle ricompense
    private double percAuth; // Percentuale della ricompensa che spetta all'autore del post
    private double percCur; // Percentuale della ricompensa che spetta ai curatori
    private int port; // Porta su cui inviare in multicast la notifica che le ricompense sono state calcolate
    private InetAddress address; // Indirizzo su cui inviare in multicast la notifica che le ricompense sono state calcolate

    private volatile boolean toStop = false; // Variabile per la terminazione del thread
    // Puntatore al DB dei post per poter richiedere la lista degli utenti
    private WinsomeDB database;
    
    /**
     * Crea un'istanza del thread che calcola le ricompense in Winsome
     * 
     * @param period Periodo ogni quanto il thread esegue il calcolo (in millisecondi)
     * @param percAuth Percentuale di ricompensa che spetta all'autore del post (tra 1 e 0)
     * @param port Porta per il multicast
     * @param address Indirizzo per il multicast
     * @param db Database di Winsome
     * @throws UnknownHostException
     * @throws NullPointerException
     * @throws IllegalArgumentException
     */
    public RewardCalculator(int period, double percAuth, int port, String address, WinsomeDB db)
    throws UnknownHostException, NullPointerException, IllegalArgumentException {
        if ( db == null )
            throw new NullPointerException();

        if ( percAuth < 0 || percAuth > 1 )
            throw new IllegalArgumentException("La percentuale autore deve essere un numero compreso tra 0 e 1");

        this.period = period;
        this.percAuth = percAuth;
        this.percCur = 1 - percAuth;
        this.port = port;
        try{
            this.address = InetAddress.getByName(address);
            if ( !this.address.isMulticastAddress() )
                throw new IllegalArgumentException(address + " non è un indirizzo per il multicast");

        } catch ( UnknownHostException e ){
            throw e;
        }

        this.database = db;
    }
    
    /**
     * Funzione che permette al thread di terminare correttamente
     */
    public void terminate(){
        toStop = true;
        this.interrupt();
    }

    // Periodicamente richiede una copia del database dei post al server
    // Così da eseguire l'algoritmo di calcolo 
    // Per poi avvisare gli utenti iscritti al multicast
    public void run(){

        try{
            // Creo la socket UDP per il multicast
            DatagramSocket socket = new DatagramSocket();

            Double rewUpdate = Double.valueOf(0);
            double rewPost = 0;
            // Struttura che raccoglie i nomi dei curatori per ogni post
            Set<String> curators = new HashSet<String>();
            // Raccolta delle ricompense per utente
            Map<String, Double> rewardPerUser = new HashMap<String, Double>();

            // Invio sempre la stessa notifica ai client
            byte[] buf = ( new String("Nuove ricompense disponibili")).getBytes();
            DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);

            while ( !toStop ){
                // Itera finché il server non invoca la terminazione
                try{
                    // Si sospende fino alla prossima esecuzione del calcolo delle ricompense
                    Thread.sleep(period);
                } catch ( InterruptedException e ){
                    // Terminazione corretta del thread
                    System.out.println("REWARD: Thread interrotto, in chiusura...");
                    socket.close();
                    break;
                }

                // Inizializzo la struttura che contiene per ogni utente iscritto a Winsome la propria ricompensa per questa iterazione
                rewardPerUser.clear();

                // Ottengo la lista di tutti gli utenti
                // Questa invocazione è threadsafe perché users è concurrent e ci accedo per ottenere una copia degli utenti attualmente iscritti
                Set<String> keySet = new HashSet<>(database.getUsers().keySet());
                                
                for (String user : keySet ){
                    // Per ogni utente calcolo la ricompensa
                    
                    // Aggiungo l'utente alla struttura dati che raccoglie le ricompense
                    rewardPerUser.put(user, Double.valueOf(0)); 

                    /*
                    Posso fare un for-each perché non modifico la struttura, invece che aver bisogno di un iteratore
                    Accedo alla struttura in modalità lettura e ci pensa Java a sincronizzare (lock striping)

                    Se nel frattempo qualche utente crea nuovi post?
                    Le alternative sono due, semplicemente:
                    - lavoro su dati "vecchi" ma le ricompense sono più giuste [EDIT] non va bene, perché poi come aggiorno il portafoglio se ho una copia?
                    - lavoro su un puntatore sincronizzanto,
                    In entrambi i casi possono avvenire operazioni di utenti attualmente non sincronizzati, quindi si presenta comunque una race condition
                    Potrei risolvere con una readwritelock su tutta la struttura del database o degli utenti
                    */

                    database.lock.readLock().lock(); try { // Per ogni utente accedo in lettura al database
                    // TODO io qui modifico post.
                    // Per il thread del backup è un problema.
                    // Sto bloccando in lettura, quindi il thread del backup può accedere a post e leggere dati incostistenti
                    // Bloccare in scrittura mi sembra terribilmente poco efficiente...
                    // Che altre soluzioni ci sono? Bloccare in scrittura soltanto mentre invoco i metodi che modificano post

                    for (WinsomePost post : database.getPostPerUser(user)){
                        // Curatori del post tra i quali dividere la ricompensa
                        curators.clear();
                        rewPost = 0;
                        
                        // perché li invoca soltanto il calcolatore, quindi non devono essere synch questi metodi,
                        // piuttosto non devono essere aggiunti voti o commenti nel frattempo 
                        
                        synchronized ( post ){
                            /*
                            * Due parole su questa sincronizzazione...
                            * 
                            * I metodi di WinsomePost che modificano l'istanza, quindi rate, comment e rewin, sincronizzano l'istanza.
                            * Durante il calcolo del reward sincronizzo di nuovo l'istanza del WinsomePost, ma lo faccio utilizzando
                            * il blocco synchronized non nel metodo ma qui. Perché? Perché così nel frattempo non rischio che vengano
                            * aggiunti nuovi commenti mentre li ho già contati ad esempio, anche se la struttura rimarrebbe consistente.
                            * Non posso lavorare su una copia del post, altrimenti non posso modificare i suoi campi
                            */
                        
                            
                            // I metodi di post che toccano voti e commenti sono synchronized, ma tolgo il synch su quelli che invoco qui
                            int voteSum = post.countVote(curators);
                            // In questo punto lo stesso post potrebbe ricevere alcuni commenti e non mi piace
                            // Sincronizzo durante il calcolo
                            double commentSum = post.countComments(curators);

                            // TODO se qui cambio da lettura a scrittura si presenta la race condition in cui l'autore del post potrebbe rimuoverlo nel frattempo
                            // Ma il fatto che sia sincronizzato lo impedisce? Sì!!!

                            database.lock.readLock().unlock();
                            database.lock.writeLock().lock(); try {

                            post.increaseIterations(); // Lo faccio adesso per non dividere per 0
                            post.switchNewOld();

                            } finally { database.lock.writeLock().unlock(); }
                            database.lock.readLock().lock(); // TODO come devo mettere qui il try-finally?
                            // Non è necessario riprendere la lock da qui in poi

                            rewPost = ( Math.log(voteSum) + Math.log(commentSum) ) / post.getIterations();
                            if ( rewPost < 0 )
                                rewPost = 0;
                            
                        } 
                        
                        for ( String curator : curators ){
                            // Per ogni curatore del post aggiorno la ricompensa totale
                            if ( rewardPerUser.get(curator) != null ){
                                // Se era già inserito nella struttura incremento il guadagno
                                rewUpdate = rewardPerUser.get(curator);
                                rewUpdate = rewUpdate + ( rewPost * percCur ) / curators.size();
                                rewardPerUser.replace(curator, rewUpdate);
                            }
                            else 
                                rewardPerUser.put(curator, (rewPost * percCur) / curators.size() );
                        }
                        
                        // Aggiorno le ricompense dell'utente con quelle calcolate sul post
                        rewUpdate = rewardPerUser.get(user); // Non dà NullPointerException perché aggiungo user alla struttura come prima operazione
                        rewUpdate = rewUpdate + (rewPost * percAuth);
                        rewardPerUser.replace(user, rewUpdate);
                        
                    }
                    } finally { database.lock.readLock().unlock(); }
                }

                /*
                // Ho calcolato le ricompense per tutti gli utenti
                // Per aggiornare il portafoglio degli utenti devo accedere alla struttura in modo concorrente
                // Non posso accedere a una copia, perché devo effettivamente aggiornare i campi del database
                
                keySet = database.getUsers().keySet();
                // Non devo sincronizzare il database perché il campo users è rappresentato da una concurrenthashmap,
                // Ha senso avere una concurrenthashmap se l'unica race condition è questa? TODO
                for ( String user : keySet ){
                    // TODO il thread che fa il backup non lavora utente per utente, quindi questa parte non va bene
                    // Il thread del backup sincronizza l'intero database, quindi operare sugli utenti dopo aver fatto
                    // la getUsers è una race condition perché non è sincronizzata, e qui allora ha senso la concurrent hashmap?
                    // Potrei sincronizzare tutto il database di nuovo, magari non sarà efficienti
                    
                    // synchronized(user){
                        // Sincronizzo perché il thread che fa il backup potrebbe leggere il portafoglio
                        // A questo punto potrebbero essere stati aggiunti altri utenti al database
                        
                        // Potrei sincronizzare il metodo per l'aggiornamento e basta
                        users.get(user).updateReward(rewardPerUser.get(user));
                    // }
                }
                */
                if ( database.updateReward(rewardPerUser) )
                    // Invio la notifica che le ricompense sono state aggiorate
                    socket.send(packet);
            }
            socket.close();
            System.out.println("REWARD: Terminazione");
        } catch ( Exception e ){
            // TODO Eccezione fatale che non dipende dai parametri, ha senso terminare il thread
            e.printStackTrace();
        }
    }
    

}
