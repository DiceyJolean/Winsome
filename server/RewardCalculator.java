package server;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

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
        private int period;
        
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

            if ( period < 0 )
                throw new RewardCalculatorConfigurationException("Il periodo per il calcolo delle ricompense è troppo breve");



            return false;
        }
    }

    public class SumAndCurators{
        private int sum;
        private Set<String> curators;
        
        public SumAndCurators(int sum, Set<String> curators){
            this.sum = sum;
            this.curators = curators;
        }

        public Set<String> getCurators(){
            Set<String> copy = new HashSet<String>();
            copy.addAll(this.curators);
            return copy;
        }

        public boolean addCurator(String user){
            return this.curators.add(user);
        }

        public boolean updateSum(int sum){
            if ( sum > this.sum )
                this.sum += sum;

            return true;
        }

        public boolean resetSum(){
            this.sum = 0;

            return true;
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

            while ( !terminate ){
                try{
                    Thread.sleep(config.period);
                } catch ( InterruptedException e ){
                    // TODO Eccezione fatale
                }

                // Ottengo una copia della lista dei post
                // Mi piace l'idea della copia per due motivi
                // - non devo mantenere la struttura bloccata per la sincronizzazione
                // - tutte le ricompense vengono calcolate sullo stesso stato del DB
                
                Set<String> users = database.getUsers(); // Deep copy dei nickname degli utenti
                for (String user : users ){

                    // Per ogni utente calcolo la ricompensa
                    // Faccio tutto senza sincronizzazione perché sto lavorando su una copia
                    // Se nel frattempo avviene qualche modifica, verrà elaborata alla prossima iterazione
                    // Se qualche utente si cancella da Winsome, semplicemente l'utente non farà più parte del multicast

                    Set<String> curators = new HashSet<String>();
                    double reward = 0;

                    for (WinsomePost post : database.getPostPerUser(user)){
                        int voteSum = post.countVote(curators);
                        int commentSum = post.countComments(curators);


                        reward += ( Math.log(voteSum) + Math.log(commentSum) ) / post.getIterations();
                        if ( reward < 0 )
                            reward = 0;
                        
                        post.increaseIterations();
                    }

                    // Adesso posso avvisare l'utente e i curatori
                }


            }


            socket.close();
        } catch ( Exception e ){
            // TODO Eccezione fatale che non dipende dai parametri, ha senso terminare il thread

        }
    }
    

}
