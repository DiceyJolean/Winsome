package server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.catalog.Catalog;

public class ServerMain {


    private static boolean DEBUG = true;
    
    // private static WinsomeDB database;
    // private static PersistentState state;
    private static String multicastAddress = null;
    private static int multicastPort = -1;
    private static int tcpPort = -1;
    private static int rmiPort = -1;
    private static String rmiServiceName = null;
    private static int rewardPeriod = -1;
    private static float percAuth;
    private static int autosavePeriod = -1;
    private static int timeout = -1;

    public static void main (String[] args){

        // Leggo i parametri per la configurazione iniziale dal file passato come argomento
        File configFile = new File("config.txt");
        if ( !configFile.exists() || !configFile.isFile() ){
            // TODO terminazione
            System.exit(1);
        }

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
                    case "TIMEOUT":{
                        timeout = Integer.parseInt(token[1]);
                        if ( timeout < 0 )
                            timeout = 60000; // un minuto
                        break;
                    }
                    default:{
                        break;
                    }
                }

            }
            if ( multicastAddress == null || autosavePeriod < 0 || rewardPeriod < 0 || rmiPort < 0 || rmiServiceName == null || tcpPort < 0 || multicastPort < 0 ){
                System.err.print("SERVER: Errore nei parametri di configurazione\n" + 
                "TCP_PORT = " + tcpPort + "\n" +
                "MULTICAST_ADDRESS = " + multicastAddress + "\n" +
                "MULTICAST_PORT = " + multicastPort + "\n" +
                "RMI_PORT = " + rmiPort + "\n" +
                "RMI_NAME_SERVICE = " + rmiServiceName + "\n" +
                "REWARD_PERIOD = " + rewardPeriod + "\n" +
                "PERC_AUTH = " + percAuth + "\n" +
                "AUTOSAVE_PERIOD = " + autosavePeriod + "\n"
                );
                System.exit(1);
            }

        } catch ( Exception e ){
            System.err.println(e.getMessage());
            System.exit(1);
        }
        
        if ( DEBUG ) System.out.println("SERVER: Parametri di configurazione corretti");
        /*
        state.loadWinsomeState(database);
        RewardCalculator rewardCalculator = null;
        try{
            rewardCalculator = new RewardCalculator(rewardPeriod, percAuth, multicastPort, multicastAddress, database);
        } catch ( RewardCalculatorConfigurationException e ){
            System.err.println(e.getMessage());
            System.exit(1);
        }
        catch ( NullArgumentException e ){
            System.err.println(e.getMessage());
            System.exit(1);
        }
        rewardCalculator.run();
        */
        if ( DEBUG ) System.out.println("SERVER: RewardCalculator avviato");
        
        ExecutorService pool = Executors.newCachedThreadPool();
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(tcpPort);
        } catch ( Exception e ){
            System.err.println(e.getMessage());
            System.exit(1);
        }
        try (
            BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        ){
            // Finché non viene chiesto al Server di terminare
            for (String line = input.readLine(); !line.equals("quit"); line = input.readLine()){
                serverSocket.setSoTimeout(timeout);

                Socket client = serverSocket.accept();
                if ( client != null ){
                    Worker worker = new Worker(client);
                    pool.execute(worker);
                }

            }
        } catch ( IOException e ){
            System.err.println(e.getMessage());
            System.exit(1);
        }


    }
}
