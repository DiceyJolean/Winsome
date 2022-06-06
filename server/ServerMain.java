package server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

import shared.*;

// import server.RewardCalculator.RewardCalculatorConfigurationException;
// import shared.*;

public class ServerMain {

    private static final boolean DEBUG = true;
    private static final int KILOBYTE = 1024;
    
    private static volatile Boolean toStop = false; // Deve essere un oggetto per il passaggio per riferimento

    private static String multicastAddress = null;
    private static String filename = null;
    private static String rmiServiceName = null;
    private static WinsomeDB database = null;
    private static RMIServiceInterface stub = null;
    private static int multicastPort = -1;
    private static int tcpPort = -1;
    private static int rmiPort = -1;
    private static int rewardPeriod = -1;
    private static int autosavePeriod = -1;
    private static float percAuth = -1;

    private static String toSimplePost(Set<WinsomePost> tmp){
        StringBuilder s = new StringBuilder();

        for ( WinsomePost post : tmp )
            s = s.append("\tId: " + post.getIdPost() + "\n\tAutore: " + post.getAuthor() + "\n\tTitolo: " + post.getTitle() + "\n");

        if ( s.length() == 0 )
            s.append("Nessun post presente\n");

        return s.toString();
    }

    private static String processRequest(String request){
        String reply = ""; // Messaggio di risposta da inviare al client
        Communication description = Communication.Success; // Descrizione dell'esito dell'operazione
        String attr = ""; // Eventuali attributi da restituire al client
        
        // La richiesta è nel formato OPERATION;USERNAME;ATTRIBUTI
        String[] token = request.split(";");

        Operation operation = Operation.valueOf(new String(token[0]));
        String user = new String(token[1]);

        if ( DEBUG ) System.out.println("SERVER: Processo la richiesta di " + operation + " dell'utente " + user);

        try{
            switch ( operation ){
                case ADD_COMMENT:{
                    description = database.addComment(user, Integer.parseInt(token[2]), token[3]) ? Communication.Success : Communication.Failure;
                    break;
                }
                case CREATE_POST:{
                    database.createPost(user, token[2], token[3]);
                    break;
                }
                case DELETE_POST:{
                    description = database.deletePost(user, Integer.parseInt(token[2])) ? Communication.Success : Communication.Failure;
                    break;
                }
                case FOLLOW_USER:{
                    // user inizia a seguire
                    description = database.followUser(user, token[2]) ? Communication.Success : Communication.Failure;
                    // notifico all'utente che viene seguito che user ha iniziato a seguirlo
                    stub.doCallback(token[2], "FOLLOW;" + user +";");
                    break;
                }
                case GET_WALLET:{
                    attr = Arrays.toString( database.getWallet(user).toArray() );
                    break;
                }
                // status = database.getWalletInBitcoin(user); TODO 
                case LIST_FOLLOWING:{
                    attr = Arrays.toString( database.listFollowing(user).toArray() );
                    break;
                }
                case LIST_USERS:{
                    attr = Arrays.toString(database.listUsers(user).toArray());
                    break;
                }
                case LOGIN:{
                    description = database.login(user, new String(token[2])) ? Communication.Success : Communication.Failure;
                    break;
                }
                case LOGOUT:{
                    description = database.logout(user) ? Communication.Success : Communication.Failure;
                    break;
                }
                case RATE_POST:{
                    description = database.ratePost(user, Integer.parseInt(token[2]), Integer.parseInt(token[3])) ? Communication.Success : Communication.Failure;
                    break;
                }
                case REWIN_POST:{
                    description = database.rewinPost(user, Integer.parseInt(token[2])) ? Communication.Success : Communication.Failure;
                    break;
                }
                case SHOW_FEED:{
                    Set<WinsomePost> tmp = database.showFeed(user);
                    if ( tmp == null ){
                        description = Communication.Failure;
                        break;
                    }
                    description = Communication.Success;
                    attr = toSimplePost(tmp) + ";";

                    break;
                }
                case SHOW_POST:{
                    attr = database.showPost(Integer.parseInt(token[2])).toPrint() + "\n;";
                    break;
                }
                case UNFOLLOW_USER:{
                    // user smette di seguire
                    description = database.unfollowUser(user, token[2]) ? Communication.Success : Communication.Failure;
                    // TODO devo mandare la notifica di callback
                    stub.doCallback(token[2], "UNFOLLOW;" + user +";");
                    break;
                }
                case VIEW_BLOG:{
                    Set<WinsomePost> tmp = database.viewBlog(user);
                    if ( tmp == null ){
                        description = Communication.Failure;
                        break;
                    }
                    description = Communication.Success;
                    attr = toSimplePost(tmp) + ";";

                    break;
                }
                default:{
                    description = Communication.Failure;
                    if ( DEBUG ) System.out.println("SERVER: Qualcosa è andato storto nel processare la richiesta: " + operation);
                    
                    return description.toString() + "\n" + attr.toString() + "\n";
                }
            }
        } catch ( Exception e ){
            description = Communication.Failure;
            e.printStackTrace();
        } finally {
            reply = description + "\n" + attr.toString() + "\n";
        }            

        if ( DEBUG ) System.out.println("SERVER: Restituisco " + reply + " da processRequest");
        return reply;
    }

    public static void main (String[] args){

        // Leggo i parametri per la configurazione iniziale dal file passato come argomento
        File configFile = new File(args[0]);
        if ( !configFile.exists() || !configFile.isFile() ){
            // TODO terminazione
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
            if ( multicastAddress == null || autosavePeriod < 0 || rewardPeriod < 0 || rmiPort < 0 || rmiServiceName == null || tcpPort < 0 || multicastPort < 0 ){
                System.err.print("SERVER: Errore nei parametri di configurazione\n" + 
                "TCP_PORT = " + tcpPort + "\n" +
                "MULTICAST_ADDRESS = " + multicastAddress + "\n" +
                "MULTICAST_PORT = " + multicastPort + "\n" +
                "RMI_PORT = " + rmiPort + "\n" +
                "RMI_NAME_SERVICE = " + rmiServiceName + "\n" +
                "REWARD_PERIOD = " + rewardPeriod + "\n" +
                "PERC_AUTH = " + percAuth + "\n" +
                "AUTOSAVE_PERIOD = " + autosavePeriod + "\n" +
                "DATABASE = " + filename + "\n"
                );
                System.exit(1);
            }
        } catch ( Exception e ){
            e.printStackTrace();
            System.exit(1);
        }
        
        if ( DEBUG ) System.out.println("SERVER: Parametri di configurazione corretti");

        WinsomeState state = null;

        // Ripristino lo stato di Winsome e avvio il thread per il salvataggio periodico
        // Working
        try{
            state = new WinsomeState(filename, database, autosavePeriod);
            database = state.loadWinsomeState();
            if ( database == null ){
                System.err.println("SERVER: Errore nel caricamento del database\n");
                System.exit(1);
            }
            Thread s = new Thread(state);
            s.start();
        } catch ( IOException e ){
            e.printStackTrace();
            System.exit(1);
        }
                
        // Preparazione del servizio RMI
        // Working
        try {
            WinsomeRMIService serviceRMI = new WinsomeRMIService(database);
            // Rappresentante del servizio che deve essere reperito in qualche modo dal client
            stub = ( RMIServiceInterface ) UnicastRemoteObject.exportObject(serviceRMI, 0);
            LocateRegistry.createRegistry(rmiPort);
            Registry r = LocateRegistry.getRegistry(rmiPort);
            r.rebind(rmiServiceName, stub);

            if ( DEBUG ) System.out.println("Server: Servizio RMI pronto su (" + rmiServiceName + ", " + rmiPort + ")\n");
        } catch ( RemoteException e ){
            e.printStackTrace();
            System.exit(1);
        }
        
        RewardCalculator rewardCalculator = null;
        try{
            rewardCalculator = new RewardCalculator(rewardPeriod, percAuth, multicastPort, multicastAddress, database);
        } catch ( UnknownHostException e ){
            System.err.println(e.getMessage());
            System.exit(1);
        }
        /*
        catch ( NullArgumentException e ){
            System.err.println(e.getMessage());
            System.exit(1);
        }
        */
        Thread rwc = new Thread(rewardCalculator);
        rwc.start();
        
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
            System.err.println(e.getMessage());
            System.exit(1);
        }

        // Ciclo principale in cui il server esegue le richieste dei client
        while(true){ // Termina con interruzione da terminale
            try{
                if ( DEBUG) System.out.printf("SERVER: In attesa di nuove richieste sulla porta %d\n", tcpPort);
                selector.select();
                if ( toStop ){
                    // TODO
                    System.out.println(("SERVER: in chiusura"));
                    state.updateWinsomeState();
                    selector.close();
                    serverSocketChannel.close();
                    System.exit(0);
                }
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
                        
                        SocketChannel client = (SocketChannel) key.channel();
                        String msg = ( String ) key.attachment();
                        client.configureBlocking(false);

                        // if ( DEBUG ) System.out.println("SERVER: Provo a leggere cosa mi ha inviato un client");
                        ByteBuffer buffer = ByteBuffer.allocate(KILOBYTE);
                        buffer.clear();

                        int byteRead = client.read(buffer);
                        // if ( DEBUG ) System.out.println("SERVER: Leggo "+ byteRead + " bytes dal client");

                        buffer.flip();
                        if ( msg == null )
                            msg = StandardCharsets.UTF_8.decode(buffer).toString();
                        else
                            msg = msg + StandardCharsets.UTF_8.decode(buffer).toString();
                        
                        if ( byteRead == KILOBYTE ){
                            // Ho riempito il buffer, potrei non aver letto tutto
                            key.attach(msg);
                            // if ( DEBUG ) System.out.println("SERVER: Lettura incompleta, compongo il messaggio al ciclo successivo, per ora ho letto \""+ msg +"\"");
                        }
                        else if ( byteRead == -1 ){
                            key.cancel();
                            key.channel().close();
                            if ( DEBUG ) System.out.println("SERVER: Socket chiusa dal client\n");
                        }
                        else if ( byteRead < KILOBYTE ){
                            // Ho letto tutto quello che il client ha inviato al server
                            if ( DEBUG ) System.out.println("SERVER: Leggo una richiesta dal client");
                            // Elaboro la richiesta
                            // Metto la risposta nell'attachment
                            String reply = processRequest(msg);
                            // if ( DEBUG ) System.out.println("SERVER: Spero di leggere questo messaggio! Significa che processRequest è ritornata");
                            key.attach(reply);

                            key.interestOps(SelectionKey.OP_WRITE);
                            
                        }
                    }
                    else if ( key.isWritable() ){
                        
                        SocketChannel client = (SocketChannel) key.channel();
                        client.configureBlocking(false);

                        String reply = ( String ) key.attachment();

                        if ( reply == null ){
                            System.err.println("SERVER: Errore con il client, chiudo la connessione");
                            key.cancel();
                            client.close();
                        }
                        // if ( DEBUG ) System.out.println("SERVER: Sto per inviare la risposta al client, analiziamola:\n" + reply);
                        // if ( DEBUG ) System.out.println("SERVER: Faccio la wrap della richiesta, poi la write");

                        ByteBuffer buffer = ByteBuffer.wrap(reply.getBytes());
                        int byteWrote = client.write(buffer);

                        if ( byteWrote == reply.toString().length() ){
                            // Ho scritto tutto
                            if ( DEBUG ) System.out.println("SERVER: Ho inviato la risposta al client\n");
                            key.attach(null); // Resetto l'attchament, altrimenti ritrovo la reply in allegato quando vado a leggere la prossima richiesta di questo client
                            key.interestOps(SelectionKey.OP_READ);                            
                        }
                        // TODO se non ho scritto tutto? Limit e position saranno nella giusta posizione per la prossima scrittura
                        // Sarebbe così se mandassi un byte buffer, ma mando un oggetto reply
                    }
                } catch ( Exception e ){
                    e.printStackTrace();
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
