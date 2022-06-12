package server;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Classe che implementa il thread che periodicamente calcola le ricompense
public class RewardCalculator implements Runnable{

    private int period;
    private double percAuth;
    private double percCur;
    private int port;
    private InetAddress address;
/*
    public class RewardCalculatorConfigurationException extends Exception{

        public RewardCalculatorConfigurationException(){
            super();
        }

        public RewardCalculatorConfigurationException(String s){
            super(s);
        }
    }
*/
    private volatile boolean terminate = false; // Variabile per la terminazione del thread
    // Puntatore al DB dei post per poter richiedere la lista
    private WinsomeDB database;
    
    public RewardCalculator(int period, double percAuth, int port, String address, WinsomeDB db)
    throws UnknownHostException {
    // throws RewardCalculatorConfigurationException, NullArgumentException {
/*        
        if ( db == null )
            throw new NullArgumentException();
*/
        this.period = period;
        this.percAuth = percAuth;
        this.percCur = 1 - percAuth;
        this.port = port;
        try{
            this.address = InetAddress.getByName(address);
            /*if ( !this.address.isMulticastAddress() )
                throw new RewardCalculatorConfigurationException();*/
        } catch ( UnknownHostException e ){
            throw e;// new RewardCalculatorConfigurationException();
        }

        this.database = db;

    }
    
    public boolean terminate(){
        this.terminate = true;
        return true;
    }

    // Periodicamente richiede una copia del database dei post al server
    // Così da eseguire l'algoritmo di calcolo 
    // Per poi avvisare gli utenti iscritti al multicast
    public void run(){
        // Creo la socket UDP per il multicast

        try{
            DatagramSocket socket = new DatagramSocket();

            Double rewUpdate = Double.valueOf(0);
            double rewPost = 0;
            // Struttura che raccoglie i nomi dei curatori per ogni post
            Set<String> curators = new HashSet<String>();

            Map<String, WinsomeUser> users;
            // Raccolta delle ricompense per utente
            Map<String, Double> rewardPerUser = new HashMap<String, Double>();

            byte[] buf = ( new String("Nuove ricompense disponibili")).getBytes();
            DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);

            while ( !terminate ){
                try{
                    // Si sospende fino alla prossima esecuzione del calcolo delle ricompense
                    Thread.sleep(period);
                } catch ( InterruptedException e ){
                    // TODO Eccezione fatale
                }

                // Inizializzo la struttura che contiene per ogni utente iscritto a Winsome la propria ricompensa per questa iterazione
                rewardPerUser.clear();

                // Ottengo la lista di tutti gli utenti
                // È un riferimento, avrò gestire la concorrenza o sono tranquilla perché è una concurrenthashmap? TODO
                WinsomeDB dbCopy = database.getDBCopy();
                users = dbCopy.getUsers();
                Set<String> keySet = users.keySet();
                                
                for (String user : keySet ){
                    // Per ogni utente calcolo la ricompensa
                    
                    // Aggiungo l'utente alla struttura dati che raccoglie le ricompense
                    rewardPerUser.put(user, Double.valueOf(0)); 

                    /*
                    // Posso fare un for-each perché non modifico la struttura, invece che aver bisogno di un iteratore
                    // Accedo alla struttura in modalità lettura e ci pensa Java a sincronizzare (lock striping)

                    // Se nel frattempo qualche utente crea nuovi post?
                    // Le alternative sono due, semplicemente:
                    // - lavoro su dati "vecchi" ma le ricompense sono più giuste !!! non va bene, perché poi come aggiorno il portafoglio se ho una copia?
                    // - lavoro su un puntatore sincronizzanto,
                    // In entrambi i casi possono avvenire operazioni di utenti attualmente non sincronizzati
                    // Potrei risolvere con una readlock su tutta la struttura
                    
                    // E INVECE LASCIO LA CONCORRENZA IN MANO ALLE API JAVA DELLA CONCURRENT HASHMAP E MI FIDO DELL'ITERATORE
                    // for each per la lettura, iterator per la scrittura

                    // Lavoro su una copia, non ho necessità di sincronizzare
                    */

                    for (WinsomePost post : database.getPostPerUser(user)){
                        // Curatori del post tra i quali dividere la ricompensa
                        curators.clear();
                        rewPost = 0;
                        
                        // perché li invoca soltanto il calcolatore, quindi non devono essere synch questi metodi,
                        // piuttosto non devono essere aggiunti voti o commenti nel frattempo 
                        
                        synchronized (post){
                            /*
                             * Due parole su questa sincronizzazione...
                             * 
                             * I metodi di WinsomePost che modificano l'istanza, quindi rate, comment e rewin, sincronizzano l'istanza.
                             * Durante il calcolo del reward sincronizzo di nuovo l'istanza del WinsomePost, ma lo faccio utilizzando
                             * il blocco synchronized non nel metodo ma qui. Perché? Perché così nel frattempo non rischio che vengano
                             * aggiunti nuovi commenti mentre li ho già contati ad esempio, anche se la struttura rimarrebbe consistente.
                             */
                        
                            // I metodi di post che toccano voti e commenti sono synchronized, ma tolgo il synch su quelli che invoco qui
                            int voteSum = post.countVote(curators);
                            // In questo punto lo stesso post potrebbe ricevere alcuni commenti e non mi piace
                            // Sincronizzo durante il calcolo
                            int commentSum = post.countComments(curators);

                            post.increaseIterations(); // Lo faccio adesso per non dividere per 0

                            rewPost = ( Math.log(voteSum) + Math.log(commentSum) ) / post.getIterations();
                            if ( rewPost < 0 )
                                rewPost = 0;
                            
                            post.switchNewOld();
                        }
                    
                        for ( String curator : curators ){
                            // Per ogni curatore del post aggiorno la ricompensa totale
                            if ( rewardPerUser.get(curator) != null ){
                                rewUpdate = rewardPerUser.get(curator);
                                rewUpdate += ( rewPost * percCur ) / curators.size();
                                rewardPerUser.replace(curator, rewUpdate);
                            }
                            else 
                                rewardPerUser.replace(curator, null, (rewPost * percCur) / curators.size() );
                        }
                        
                        // Aggiorno le ricompense dell'utente con quelle calcolate sul post
                        if ( rewardPerUser.get(user) != null ){
                            rewUpdate = rewardPerUser.get(user);
                            rewUpdate += rewPost * percAuth;
                            rewardPerUser.replace(user, rewUpdate);
                        }
                        else 
                            rewardPerUser.replace(user, null, rewPost * percAuth);

                    }
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
        } catch ( Exception e ){
            // TODO Eccezione fatale che non dipende dai parametri, ha senso terminare il thread
            e.printStackTrace();
        }
    }
    

}
