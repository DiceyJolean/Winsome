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
                    rewardPerUser.putIfAbsent(user, Double.valueOf(0)); 

                    database.lock.readLock().lock(); try { // Per ogni utente accedo in lettura al database

                    for (WinsomePost post : database.getPostPerUser(user)){
                        // Curatori del post tra i quali dividere la ricompensa
                        curators.clear();
                        rewPost = 0;
                        
                        int voteSum = post.countVote(curators);
                        double commentSum = post.countComments(curators);

                        database.lock.readLock().unlock();
                        database.lock.writeLock().lock(); try {

                        post.increaseIterations(); // Lo faccio adesso per non dividere per 0
                        post.switchNewOld();

                        } finally { database.lock.writeLock().unlock(); }
                        database.lock.readLock().lock();

                        rewPost = ( Math.log(voteSum) + Math.log(commentSum) ) / post.getIterations();
                        if ( rewPost < 0 )
                            rewPost = 0;
                        
                        for ( String curator : curators ){
                            // Per ogni curatore del post aggiorno la ricompensa totalew
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
                        
                    } } finally { database.lock.readLock().unlock(); }
                }

                // Delego a WinsomeDB l'aggiornamento dei portafogli degli utenti, così gestisce la concorrenza
                if ( database.updateReward(rewardPerUser) )
                    // Invio la notifica che le ricompense sono state aggiorate
                    socket.send(packet);
            }
            socket.close();
            System.out.println("REWARD: Terminazione");
        } catch ( Exception e ){
            e.printStackTrace();
        }
    }
    

}
