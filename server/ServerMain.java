package server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import shared.*;

public class ServerMain {
    private static final int SUCCESS = 0;
    private static final int FAILURE = 1;
    
    private static String multicastAddress = null; // Indirizzo per il multicast
    private static String filename = null; // Nome del file sove salvare lo stato di Winsome
    private static String rmiServiceName = null; // Nome del servizio RMI
    private static RMIServiceInterface stub = null; // Interfaccia del servizio RMI
    private static int multicastPort = -1; // Porta per il multicast
    private static int tcpPort = -1; // Porta per la socket TCP
    private static int rmiPort = -1; // Porta per il servizio RMI
    private static int rewardPeriod = -1; // Periodo ogni quanto viene effettuato il calcolo delle ricompense
    private static int autosavePeriod = -1; // Periodo ogni quanto viene effettuato il salvataggio dello stato
    private static float percAuth = -1; // Percentuale di ricompensa che spetta all'autore del post

    public static void main (String[] args){

        // Leggo i parametri per la configurazione iniziale dal file passato come argomento
        File configFile = new File(args[0]);
        if ( !configFile.exists() || !configFile.isFile() ){
            System.err.println("Impossibile trovare il file di configurazione, terminazione");
            System.exit(FAILURE);
        }

        // Leggo i parametri per la configurazione iniziale
        try (
            BufferedReader input = new BufferedReader(new FileReader(configFile))
        ){
            // Il formato del file di configurazione è NOME=valore\n...NOME=valore\n
            for ( String line = input.readLine(); line != null; line = input.readLine() ){
                String[] token = line.split("=");

                switch ( token[0] ){
                    case "MULTICAST_ADDRESS":{
                        multicastAddress = new String(token[1]);
                        break;
                    }
                    case "MULTICAST_PORT":{
                        multicastPort = Integer.parseInt(token[1]);
                        if ( multicastPort < 1024 || multicastPort > 65535 )
                            System.exit(FAILURE);
                        break;
                    }
                    case "TCP_PORT":{
                        tcpPort = Integer.parseInt(token[1]);
                        if ( tcpPort < 1024 || tcpPort > 65535 )
                            System.exit(FAILURE);
                        break;
                    }
                    case "RMI_PORT":{
                        rmiPort = Integer.parseInt(token[1]);
                        if ( rmiPort < 1024 || rmiPort > 65535 )
                            System.exit(FAILURE);
                        break;
                    }
                    case "RMI_NAME_SERVICE":{
                        rmiServiceName = new String(token[1]);
                        break;
                    }
                    case "REWARD_PERIOD":{
                        rewardPeriod = Integer.parseInt(token[1]);
                        if ( rewardPeriod < 0 )
                            System.exit(FAILURE);
                        break;
                    }
                    case "PERC_AUTH":{
                        percAuth = Float.parseFloat(token[1]);
                        if ( percAuth < 0 || percAuth > 1 )
                            System.exit(FAILURE);
                        break;
                    }
                    case "AUTOSAVE_PERIOD":{
                        autosavePeriod = Integer.parseInt(token[1]);
                        if ( autosavePeriod < 0 )
                            System.exit(FAILURE);
                        break;
                    }
                    case "DATABASE":{
                        filename = new String(token[1]);
                        break;
                    }
                    default:{
                        break;
                    }
                }

            }
            if ( multicastAddress == null || autosavePeriod < 0 || rewardPeriod < 0 || rmiPort < 0 || rmiServiceName == null || tcpPort < 0 || multicastPort < 0 || filename == null || percAuth < 0 ){
                System.err.println("SERVER: Errore nei parametri di configurazione, terminazione");
                System.exit(FAILURE);
            }
        } catch ( Exception e ){
            e.printStackTrace();
            System.exit(FAILURE);
        }
        
        WinsomeState state = null;

        // Ripristino lo stato di Winsome
        WinsomeDB database = null;
        try{
            state = new WinsomeState(filename, database, autosavePeriod);
            database = state.loadWinsomeState();
            if ( database == null ){
                System.err.println("SERVER: Errore nel caricamento del database, terminazione\n");
                System.exit(FAILURE);
            }
        } catch ( IOException e ){
            e.printStackTrace();
            System.exit(FAILURE);
        }
        
        // Preparazione del servizio RMI
        try {
            WinsomeRMIService serviceRMI = new WinsomeRMIService(database);
            // Rappresentante del servizio che deve essere reperito in qualche modo dal client
            stub = ( RMIServiceInterface ) UnicastRemoteObject.exportObject(serviceRMI, 0);
            LocateRegistry.createRegistry(rmiPort);
            Registry r = LocateRegistry.getRegistry(rmiPort);
            r.rebind(rmiServiceName, stub);

        } catch ( RemoteException e ){
            e.printStackTrace();
            System.exit(FAILURE);
        }
        
        // Creo il thread per il calcolo delle ricompense
        RewardCalculator rewardCalculator = null;
        try{
            rewardCalculator = new RewardCalculator(rewardPeriod, percAuth, multicastPort, multicastAddress, database);
        } catch ( NullPointerException | IllegalArgumentException e ){
            System.err.println("SERVER: Errore nei parametri di configurazione per il calcolo delle ricompense");
            System.exit(FAILURE);
        } catch ( UnknownHostException e ){
            e.printStackTrace();
            System.exit(FAILURE);
        }

        // Apertura della connessione TCP con NIO
        ServerSocketChannel serverSocketChannel = null;
        Selector selector = null;
        try{
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.socket().bind(new InetSocketAddress(tcpPort));
            serverSocketChannel.configureBlocking(false);

            selector = Selector.open();
            // Prossima volta che si viene risvegliati dalla select dovrà essere una richiesta di connesione
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);   

        } catch ( Exception e ){
            e.printStackTrace();
            System.exit(FAILURE);
        }

        // Creazione del thread che si occupa di servire le richieste dei client
        Worker worker = new Worker(selector, database, multicastAddress, multicastPort, stub);

        System.out.println("SERVER: Avvio del server");
        state.start();
        rewardCalculator.start();
        worker.start();
        System.out.println("SERVER: Avvio avvenuto con successo");

        // Il server si sospende finché non legge "quit", poi termina
        try(
            BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        ){
            for (String line = input.readLine(); !line.equals("quit"); line = input.readLine() ) ;

            return;
        } catch ( IOException e ){
            e.printStackTrace();
        } finally {

            // Ricevo quit dal terminale, quindi termino i thread, chiudo le connessioni e termino il main
            // Metto la chiusura in un blocco finally per permettere ai thread di terminare anche in caso di eccezioni
            try{
                rewardCalculator.terminate();
                rewardCalculator.join();

                worker.terminate();
                worker.join();

                state.terminate();
                state.join();

            } catch ( InterruptedException e ){
                e.printStackTrace();
                System.exit(FAILURE);
            }
            
            try {
                selector.close();
                serverSocketChannel.close();
            } catch ( IOException e ){
                e.printStackTrace();
                System.exit(FAILURE);
            }
            System.out.println("SERVER: In chiusura");
            System.exit(SUCCESS);
        }
    }

}
