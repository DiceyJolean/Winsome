package server;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import shared.*;

// Classe che implementa il thread che periodicamente calcola le ricompense
public class RewardCalculator implements Runnable{

    public class RewardCalculatorConfigurationException extends Exception{

        public RewardCalculatorConfigurationException(){
            super();
        }

        public RewardCalculatorConfigurationException(String s){
            super(s);
        }
    }

    class ConfigRewardCalc{
        private String host;
        private InetAddress address;
        private int port;
        private int period; // Periodo di tempo ogni quanto il thread calcola le ricompense
        private int percAuth; // Perchntuale di ricompensa che spetta all'autore del post
        private int percCur; // Percentuale di ricompensa che spetta ai curatori del post
        
        // Restituire indietro un valore invece che propagare un'eccezione
        boolean validate()
        throws RewardCalculatorConfigurationException {

            try {
                address = InetAddress.getByName(config.host);
            } catch ( UnknownHostException e ){
                throw new RewardCalculatorConfigurationException("L'indirizzo IP dell'host non può essere determinato");
            }

            if ( !address.isMulticastAddress() ){
                throw new RewardCalculatorConfigurationException("L'indirizzo IP non è un indirizzo di multicast");
            }

            if ( port < 1024 || port > 65535 )
                throw new RewardCalculatorConfigurationException("Numero di porta non valido");

            if ( period < 0 )
                throw new RewardCalculatorConfigurationException("Il periodo per il calcolo delle ricompense è troppo breve");

            if ( percAuth < 0 || percCur < 0 || ( percAuth + percCur ) != 1 )
                throw new RewardCalculatorConfigurationException("Le percentuali di ricompensa per autore e curatori non sono valide");


            return false;
        }
    }

    ConfigRewardCalc config; // Parametri per la configurazione iniziale del thread
    private volatile boolean terminate = false; // Variabile per la terminazione del thread
    // Puntatore al DB dei post per poter richiedere la lista
    private WinsomeDB database;
    
    public RewardCalculator(ConfigRewardCalc config, WinsomeDB db)
    throws RewardCalculatorConfigurationException, NullArgumentException {
        
        if ( config == null || db == null )
            throw new NullArgumentException();

        try {
            config.validate();
        } catch ( Exception e ){
            throw new RewardCalculatorConfigurationException();
        }
        
        this.config = config;
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

            double rewUpdate = 0;
            double rewPost = 0;
            // Struttura che raccoglie i nomi dei curatori per ogni post
            Set<String> curators = new HashSet<String>();

            ConcurrentHashMap<String, WinsomeUser> users;
            // Raccolta delle ricompense per utente
            HashMap<String, Double> rewardPerUser = new HashMap<String, Double>();

            byte[] buf = ( new String("Nuove ricompense disponibili")).getBytes();
            DatagramPacket packet = new DatagramPacket(buf, buf.length, config.address, config.port);

            while ( !terminate ){
                try{
                    Thread.sleep(config.period);
                } catch ( InterruptedException e ){
                    // TODO Eccezione fatale
                }

                rewardPerUser.clear();

                // Ottengo la lista di tutti gli utenti
                users = database.getUsers();

                for (String userName : database.getUsers().keySet() ){
                    rewardPerUser.put(userName, null); // Aggiungo l'utente alla struttura dati che raccoglie le ricompense

                    // Posso fare un for-each perché non modifico la struttura, invece che aver bisogno di un iteratore
                    // Accedo alla struttura in modalità lettura e ci pensa Java a sincronizzare (lock striping)

                    // Se nel frattempo qualche utente crea nuovi post?
                    // Le alternative sono due, semplicemente:
                    // - lavoro su dati "vecchi" ma le ricompense sono più giuste
                    // - lavoro su un puntatore sincronizzanto,
                    // In entrambi i casi possono avvenire operazioni di utenti attualmente non sincronizzati
                    // Potrei risolvere con una readlock su tutta la struttura
                    
                    // E INVECE LASCIO LA CONCORRENZA IN MANO ALLE API JAVA DELLA CONCURRENT HASHMAP E MI FIDO DELL'ITERATORE

                    // Per ogni utente calcolo la ricompensa

                    for (WinsomePost post : database.getPostPerUser(userName)){
                        // Curatori del post tra i quali dividere la ricompensa
                        curators.clear();
                        rewPost = 0;
                        // I metodi di post sono synchronized, ma lo tolgo su questi che chiamo qui
                        synchronized (post){
                            int voteSum = post.countVote(curators);
                            // In questo punto lo stesso post potrebbe ricevere alcuni commenti e non mi piace
                            // Sincronizzo durante il calcolo
                            int commentSum = post.countComments(curators);

                            rewPost = ( Math.log(voteSum) + Math.log(commentSum) ) / post.getIterations();
                            if ( rewPost < 0 )
                                rewPost = 0;
                            
                            post.increaseIterations();
                            post.switchNewOld();
                        }
                    
                        for ( String curator : curators ){
                            // Per ogni curatore del post aggiorno la ricompensa totale
                            rewardPerUser.putIfAbsent(curator, null);

                            rewUpdate = rewardPerUser.get(curator);
                            rewUpdate += ( rewPost * config.percCur ) / curators.size();
                            rewardPerUser.replace(curator, rewUpdate);
                        }
                        
                        // Aggiorno le ricompense dell'utente con quelle calcolate sul post
                        rewUpdate = rewardPerUser.get(userName);
                        rewUpdate += ( rewPost * config.percAuth );
                        rewardPerUser.replace(userName, rewUpdate);
                    }
                }
                // Ho calcolato le ricompense per tutti gli utenti
                // Per aggiornare il portafoglio degli utenti devo accedere alla struttura in modo concorrente
                for ( String user : users.keySet() ){
                    synchronized(user){
                        // Sincronizzo perché il thread che fa il backup potrebbe leggere il portafoglio
                        // A questo punto potrebbero essere stati aggiunti altri utenti al database
                        
                        users.get(user).updateReward(rewardPerUser.get(user));
                    }
                }

                // Invio la notifica che le ricompense sono state aggiorate
                socket.send(packet);
            }

            socket.close();
        } catch ( Exception e ){
            // TODO Eccezione fatale che non dipende dai parametri, ha senso terminare il thread

        }
    }
    

}
