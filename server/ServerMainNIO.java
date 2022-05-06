package server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// import server.RewardCalculator.RewardCalculatorConfigurationException;
// import shared.*;

public class ServerMainNIO {

    private static boolean DEBUG = true;
    private static final int KILOBYTE = 1024;
    
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

        ServerSocketChannel serverSocketChannel;
        Selector selector = null;
        try{
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.socket().bind(new InetSocketAddress(tcpPort));
            serverSocketChannel.configureBlocking(false);

            selector = Selector.open();
            // Prossima volta che mi sveglio dalla select dovrà essere una richiesta di connesione
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);   

        } catch ( Exception e ){
            System.err.println(e.getMessage());
            System.exit(1);
        }

        while(true){ // Termina con interruzione da terminale
            try{
                if ( DEBUG) System.out.printf("SERVER: In attesa di nuove richieste sulla porta %d\n", tcpPort);
                selector.select();
                // Tra i canali registrati sul selettore selector, seleziona quelli 
                // pronti per almeno una delle operazioni di I/O dell'interest set.
            } catch ( Exception e ){
                System.err.println(e.getMessage());
                break;
            }
            // Il selector si sveglia, c'è stata una richiesta su un canale

            Set <SelectionKey> readyKeys = selector.selectedKeys();
            Iterator <SelectionKey> iterator = readyKeys.iterator();
            while ( iterator.hasNext() ){
                SelectionKey key = iterator.next();
                // Rimuove la chiave dal Selected Set, ma non dal Registered Set
                iterator.remove();
                try{
                    if ( key.isAcceptable() ){
                        // Nuova connessione accettata dal channel
                        // Connessione implicita lato Server
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        SocketChannel client = server.accept();
                        if ( DEBUG ) System.out.println("SERVER: Connessione accettata");
                        client.configureBlocking(false);
                        
                        // Nuovo client, l'operazione che voglio associare è la lettura
                        client.register(selector, SelectionKey.OP_READ);
                    }
                    else if ( key.isReadable() ){
                        // "zittisco" il client finché il worker non ha finito di servirlo
                        // Sarà poi il worker a reinsere la chiave
                        // key.cancel();
                        
                        SocketChannel client = (SocketChannel) key.channel();
                        String msg = ( String ) key.attachment();
                        client.configureBlocking(false);

                        if ( DEBUG ) System.out.println("SERVER: Provo a leggere cosa mi ha inviato un client\n");
                        ByteBuffer buffer = ByteBuffer.allocate(KILOBYTE);
                        buffer.clear();

                        int byteRead = client.read(buffer);

                        buffer.flip();
                        if ( msg == null )
                            msg = StandardCharsets.UTF_8.decode(buffer).toString();
                        else
                            msg = msg + StandardCharsets.UTF_8.decode(buffer).toString();
                        

                        if ( byteRead == KILOBYTE ){
                            // Ho riempito il buffer, potrei non aver letto tutto
                            key.attach(msg);
                            if ( DEBUG ) System.out.println("SERVER: Lettura incompleta, compongo il messaggio al ciclo successivo\n");
                        }
                        else if ( byteRead < KILOBYTE ){
                            // Ho letto tutto quello che il client ha inviato al server
                            if ( DEBUG ) System.out.println("SERVER: Leggo una richiesta dal client:\n\t"+ msg +"\n");

                            key.interestOps(SelectionKey.OP_WRITE);
                        }
                        else if ( byteRead == -1 ){
                            key.cancel();
                            key.channel().close();
                            if ( DEBUG ) System.out.println("SERVER: Socket chiusa dal client\n");
                        }
                        // Worker worker = new Worker(client, selector, "OP_READ");
                        // pool.execute(worker);
                    }
                    else if ( key.isWritable() ){
                        // "zittisco" il client finché il worker non ha finito di servirlo
                        // Sarà poi il worker a reinsere la chiave
                        // key.cancel();
                        
                        SocketChannel client = (SocketChannel) key.channel();
                        String msg = ( String ) key.attachment();
                        // TODO a msg dovrò appendere il resto del messaggio
                        client.configureBlocking(false);

                        if ( DEBUG ) System.out.println("SERVER: Provo a scrivere a un client\n");
                        ByteBuffer buffer = ByteBuffer.wrap(msg.getBytes());
                        int byteWrote = client.write(buffer);

                        if ( byteWrote == msg.length() ){
                            // Ho scritto tutto
                            if ( DEBUG ) System.out.println("SERVER: Ho inviato un messaggio al client\n");
                            key.interestOps(SelectionKey.OP_READ);                            
                        }
                        

                        // Worker worker = new Worker(client, selector, "OP_WRITE");
                        // pool.execute(worker);
                    }
                } catch ( Exception e ){
                    key.cancel();
                    try {
                        key.channel().close();
                    } catch ( Exception ex ){
                        System.err.println(e.getMessage());
                        System.exit(1);
                    }
                }
            }
        }
    }

}
