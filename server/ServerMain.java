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

    private static final boolean DEBUG = false;
    
    private static String multicastAddress = null;
    private static String filename = null;
    private static String rmiServiceName = null;
    private static RMIServiceInterface stub = null;
    private static int multicastPort = -1;
    private static int tcpPort = -1;
    private static int rmiPort = -1;
    private static int rewardPeriod = -1;
    private static int autosavePeriod = -1;
    private static float percAuth = -1;

    public static void main (String[] args){

        // Leggo i parametri per la configurazione iniziale dal file passato come argomento
        File configFile = new File(args[0]);
        if ( !configFile.exists() || !configFile.isFile() ){
            System.err.println("SERVER: Errore nel file di configurazione, terminazione");
            System.exit(1);
        }

        // Leggo i parametri per la configurazione iniziale
        // Working
        try (
            BufferedReader input = new BufferedReader(new FileReader(configFile))
        ){
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
                            System.exit(1);
                        break;
                    }
                    case "TCP_PORT":{
                        tcpPort = Integer.parseInt(token[1]);
                        if ( tcpPort < 1024 || tcpPort > 65535 )
                            System.exit(1);
                        break;
                    }
                    case "RMI_PORT":{
                        rmiPort = Integer.parseInt(token[1]);
                        if ( rmiPort < 1024 || rmiPort > 65535 )
                            System.exit(1);
                        break;
                    }
                    case "RMI_NAME_SERVICE":{
                        rmiServiceName = new String(token[1]);
                        break;
                    }
                    case "REWARD_PERIOD":{
                        rewardPeriod = Integer.parseInt(token[1]);
                        if ( rewardPeriod < 0 )
                            System.exit(1);
                        break;
                    }
                    case "PERC_AUTH":{
                        percAuth = Float.parseFloat(token[1]);
                        if ( percAuth < 0 || percAuth > 1 )
                            System.exit(1);
                        break;
                    }
                    case "AUTOSAVE_PERIOD":{
                        autosavePeriod = Integer.parseInt(token[1]);
                        if ( autosavePeriod < 0 )
                            System.exit(1);
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
                System.exit(1);
            }
        } catch ( Exception e ){
            e.printStackTrace();
            System.exit(1);
        }
        
        if ( DEBUG ) System.out.println("SERVER: Parametri di configurazione corretti");

        WinsomeState state = null;

        // Ripristino lo stato di Winsome e avvio il thread per il salvataggio periodico
        WinsomeDB database = null;
        try{
            state = new WinsomeState(filename, database, autosavePeriod);
            database = state.loadWinsomeState();
            if ( database == null ){
                System.err.println("SERVER: Errore nel caricamento del database, terminazione\n");
                System.exit(1);
            }
        } catch ( IOException e ){
            e.printStackTrace();
            System.exit(1);
        }
        
        // Preparazione del servizio RMI
        try {
            WinsomeRMIService serviceRMI = new WinsomeRMIService(database);
            // Rappresentante del servizio che deve essere reperito in qualche modo dal client
            stub = ( RMIServiceInterface ) UnicastRemoteObject.exportObject(serviceRMI, 0);
            LocateRegistry.createRegistry(rmiPort);
            Registry r = LocateRegistry.getRegistry(rmiPort);
            r.rebind(rmiServiceName, stub);

            if ( DEBUG ) System.out.println("SERVER: Servizio RMI pronto su (" + rmiServiceName + ", " + rmiPort + ")\n");
        } catch ( RemoteException e ){
            e.printStackTrace();
            System.exit(1);
        }
        
        RewardCalculator rewardCalculator = null;
        try{
            rewardCalculator = new RewardCalculator(rewardPeriod, percAuth, multicastPort, multicastAddress, database);
        } catch ( UnknownHostException e ){
            e.printStackTrace();
            System.exit(1);
        }

        if ( DEBUG ) System.out.println("SERVER: RewardCalculator avviato");

        // Apertura della connessione TCP con NIO
        // Working
        ServerSocketChannel serverSocketChannel = null;
        Selector selector = null;
        try{
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.socket().bind(new InetSocketAddress(tcpPort));
            serverSocketChannel.configureBlocking(false);

            selector = Selector.open();
            // Prossima volta che mi sveglio dalla select dovrà essere una richiesta di connesione
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);   

        } catch ( Exception e ){
            e.printStackTrace();
            System.exit(1);
        }

        Worker worker = new Worker(selector, database, multicastAddress, multicastPort, stub);

        System.out.println("SERVER: Avvio del server");
        state.start();
        rewardCalculator.start();
        worker.start();
        System.out.println("SERVER: Avvio avvenuto con successo");

        try(
            BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        ){
            for (String line = input.readLine(); !line.equals("quit"); line = input.readLine() ) ;

            return;
        } catch ( IOException e ){
            e.printStackTrace();
        } finally {

            // Ricevo quit dal terminale, quindi termino i thread e termino il main
            
            try{
                rewardCalculator.terminate();
                rewardCalculator.join();

                worker.terminate();
                worker.join();

                state.terminate();
                state.join();

            } catch ( InterruptedException e ){
                e.printStackTrace();
                System.exit(1);
            }
            
            try {
                selector.close();
                serverSocketChannel.close();
            } catch ( IOException e ){
                e.printStackTrace();
                System.exit(1);
            }
            System.out.println("SERVER: In chiusura");
            System.exit(0);
            
        }
    }

}
