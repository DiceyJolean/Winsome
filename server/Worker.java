package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;

import shared.*;

public class Worker extends Thread {
    private final static String randomURL = "https://www.random.org/decimal-fractions/?num=1&dec=4&col=1&format=plain&rnd=new";
    private volatile boolean toStop = false;
    private int KILOBYTE = 1024;
    private static boolean DEBUG = false;
    private String multicastAddress;
    private int multicastPort;
    private WinsomeDB database;
    private RMIServiceInterface stub;

    private Selector selector;

    public Worker(Selector selector, WinsomeDB database, String multicastAddress, int multicastPort, RMIServiceInterface stub){
        this.multicastAddress = multicastAddress;
        this.multicastPort = multicastPort;
        this.stub = stub;
        this.selector = selector;
        this.database = database;
    }

    protected void terminate(){
        toStop = true;
        selector.wakeup();
    }

    private double getRandom(){
        double random = 0.0;
        try{
            URL url = new URL(randomURL);
            URLConnection urlConn = url.openConnection();
            BufferedReader buf = new BufferedReader(new InputStreamReader(urlConn.getInputStream()));
            random = Double.parseDouble(buf.readLine());

        } catch ( NumberFormatException e ){
            // TODO se viene sollevata un'eccezione qui dentro, ha senso terminare il server
            e.printStackTrace();
        } catch ( IOException e ){
            // TODO se viene sollevata un'eccezione qui dentro, ha senso terminare il server
            e.printStackTrace();
        } catch ( Exception e ){
            // TODO se viene sollevata un'eccezione qui dentro, ha senso terminare il server
            e.printStackTrace();
        }
        
        return random;
    }

    private String toSimplePost(Set<WinsomePost> tmp){
        StringBuilder s = new StringBuilder();

        for ( WinsomePost post : tmp )
            s = s.append("\tId: " + post.getIdPost() + "\n\tAutore: " + post.getAuthor() + "\n\tTitolo: " + post.getTitle() + "\n");

        if ( s.length() == 0 )
            s.append("Nessun post presente\n");

        return s.toString();
    }

    private String processRequest(String request){
        String reply = ""; // Messaggio di risposta da inviare al client
        String description = Communication.Success.toString(); // Descrizione dell'esito dell'operazione
        String attr = ""; // Eventuali attributi da restituire al client
        
        // La richiesta è nel formato OPERATION;USERNAME;ATTRIBUTI
        String[] token = request.split(";");

        Operation operation = Operation.valueOf(new String(token[0]));
        String user = new String(token[1]);

        if ( DEBUG ) System.out.println("WORKER: Processo la richiesta di " + operation + " dell'utente " + user);

        try{
            switch ( operation ){
                case ADD_COMMENT:{
                    // l'operazione restituisce true o solleva eccezione, ma gestisco comunque un fallimento per future modifiche
                    description = database.addComment(user, Integer.parseInt(token[2]), token[3]) ? Communication.Success.toString() : Communication.Failure.toString();
                    break;
                }
                case CREATE_POST:{
                    // l'operazione restituisce true o solleva eccezione, ma gestisco comunque un fallimento per future modifiche
                    description = database.createPost(user, token[2], token[3]) ? Communication.Success.toString() : Communication.Failure.toString();
                    break;
                }
                case DELETE_POST:{
                    description = database.deletePost(user, Integer.parseInt(token[2])) ? Communication.Success.toString() : Communication.Failure.toString();
                    break;
                }
                case FOLLOW_USER:{
                    // user inizia a seguire
                    // l'operazione restituisce true o solleva eccezione, ma gestisco comunque un fallimento per future modifiche
                    if ( database.followUser(user, token[2]) ){
                        description = Communication.Success.toString();
                        // notifico all'utente che viene seguito che user ha iniziato a seguirlo
                        stub.doCallback(token[2], "FOLLOW;" + user +";");
                    }
                    else 
                        description = Communication.Failure.toString();

                    break;
                }
                case GET_WALLET:{
                    attr = "";
                    double wallet = 0;
                    Queue<WinsomeWallet> queue = database.getWallet(user);
                    if ( queue == null || queue.isEmpty() ){ // Non solleva NullPointerException perché java ha la Short-circuit evaluation
                        description = Communication.EmptySet.toString();
                        break;
                    }
                    for ( WinsomeWallet w : queue ){
                        attr = attr + w.getKey() + " " + w.getValue() + "\n";
                        wallet = wallet + w.getValue();
                    }

                    attr += "Valore del portafoglio : " + wallet + "\n;";

                    break;
                }
                case GET_WALLET_BITCOIN:{
                    Queue<WinsomeWallet> queue = database.getWallet(user);
                    if ( queue == null || queue.isEmpty() ){ // Non solleva NullPointerException perché java ha la Short-circuit evaluation
                        description = Communication.EmptySet.toString();
                        break;
                    }
                    double n = getRandom();
                    double walletbtc = 0;
                    attr = "";
                    for ( WinsomeWallet w : queue ){
                        attr = attr + w.getKey() + " " + w.getValue()*n + ")\n";
                        walletbtc = walletbtc + w.getValue()*n;
                    }

                    attr += "Valore del portafoglio in bitcoin : " + walletbtc + " (Tasso di conversione: " + n + "\n;";

                    break;
                }
                case LIST_FOLLOWING:{
                    Set<String> following = database.listFollowing(user);
                    if ( following == null || following.isEmpty() ){ // Non solleva NullPointerException perché java ha la Short-circuit evaluation
                        description = Communication.EmptySet.toString();
                        break;
                    }
                    attr = Arrays.toString( following.toArray() );
                    break;
                }
                case LIST_USERS:{
                    Set<String> users = database.listUsers(user);
                    if ( users == null || users.isEmpty() ){ // Non solleva NullPointerException perché java ha la Short-circuit evaluation
                        description = Communication.EmptySet.toString();
                        break;
                    }
                    attr = Arrays.toString( users.toArray());
                    break;
                }
                case LOGIN:{
                    // l'operazione restituisce true o solleva eccezione, ma gestisco comunque un fallimento per future modifiche
                    if ( database.login(user, new String(token[2])) ){
                        description = Communication.Success.toString();
                        attr = multicastAddress + "\n" + multicastPort;
                    }
                    else
                        description = Communication.Failure.toString();
                        
                    break;
                }
                case LOGOUT:{
                    // l'operazione restituisce true o solleva eccezione, ma gestisco comunque un fallimento per future modifiche
                    description = database.logout(user) ? Communication.Success.toString() : Communication.Failure.toString();
                    break;
                }
                case RATE_POST:{
                    // l'operazione restituisce true o solleva eccezione, ma gestisco comunque un fallimento per future modifiche
                    description = database.ratePost(user, Integer.parseInt(token[2]), Integer.parseInt(token[3])) ? Communication.Success.toString() : Communication.Failure.toString();
                    break;
                }
                case REWIN_POST:{
                    // l'operazione restituisce true o solleva eccezione, ma gestisco comunque un fallimento per future modifiche
                    description = database.rewinPost(user, Integer.parseInt(token[2])) ? Communication.Success.toString() : Communication.Failure.toString();
                    break;
                }
                case SHOW_FEED:{
                    Set<WinsomePost> tmp = database.showFeed(user, true);
                    if ( tmp == null || tmp.isEmpty() ){ // Non solleva NullPointerException perché java ha la Short-circuit evaluation
                        description = Communication.EmptySet.toString();
                        break;
                    }
                    description = Communication.Success.toString();
                    attr = toSimplePost(tmp) + ";";

                    break;
                }
                case SHOW_POST:{
                    attr = database.showPost(Integer.parseInt(token[2])) + "\n;";
                    description = Communication.Success.toString();
                    break;
                }
                case UNFOLLOW_USER:{
                    // user smette di seguire
                    // l'operazione restituisce true o solleva eccezione, ma gestisco comunque un fallimento per future modifiche
                    if ( database.unfollowUser(user, token[2]) ){
                        description = Communication.Success.toString();
                        // notifico all'utente che viene seguito che user ha smesso di seguirlo
                        stub.doCallback(token[2], "UNFOLLOW;" + user +";");
                    }
                    else 
                        description = Communication.Failure.toString();

                    break;
                }
                case VIEW_BLOG:{
                    Set<WinsomePost> tmp = database.viewBlog(user, true);
                    if ( tmp == null || tmp.isEmpty() ){ // Non solleva NullPointerException perché java ha la Short-circuit evaluation
                        description = Communication.EmptySet.toString();
                        break;
                    }
                    description = Communication.Success.toString();
                    attr = toSimplePost(tmp) + ";";

                    break;
                }
                default:{
                    description = Communication.OperationNotSupported.toString();
                    if ( DEBUG ) System.out.println("WORKER: Qualcosa è andato storto nel processare la richiesta: " + operation);
                    
                    return description.toString() + "\n" + attr.toString() + "\n";
                }
            }
        } catch ( WinsomeException e ){
            attr = "";
            description = e.getMessage();
        } catch ( NullPointerException | IllegalArgumentException e ){
            attr = "";
            description = Communication.Failure.toString();
        }
        catch ( Exception e ){
            attr = "";
            description = Communication.Failure.toString();
            e.printStackTrace();
        } finally {
            reply = description + "\n" + attr.toString() + "\n";
        }            

        if ( DEBUG ) System.out.println("WORKER: Restituisco " + reply + " da processRequest");
        return reply;
    }

    public void run(){
        while( !toStop ){

            try{
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
                        if ( DEBUG ) System.out.println("WORKER: Connessione accettata");
                        client.configureBlocking(false);
                        
                        // Nuovo client, l'operazione che voglio associare è la lettura
                        client.register(selector, SelectionKey.OP_READ);
                    }
                    else if ( key.isReadable() ){
                        
                        SocketChannel client = (SocketChannel) key.channel();
                        String msg = ( String ) key.attachment();
                        client.configureBlocking(false);

                        // if ( DEBUG ) System.out.println("WORKER: Provo a leggere cosa mi ha inviato un client");
                        ByteBuffer buffer = ByteBuffer.allocate(KILOBYTE);
                        buffer.clear();

                        int byteRead = client.read(buffer);
                        // if ( DEBUG ) System.out.println("WORKER: Leggo "+ byteRead + " bytes dal client");

                        buffer.flip();
                        if ( msg == null )
                            msg = StandardCharsets.UTF_8.decode(buffer).toString();
                        else
                            msg = msg + StandardCharsets.UTF_8.decode(buffer).toString();
                        
                        if ( byteRead == KILOBYTE ){
                            // Ho riempito il buffer, potrei non aver letto tutto
                            key.attach(msg);
                            // if ( DEBUG ) System.out.println("WORKER: Lettura incompleta, compongo il messaggio al ciclo successivo, per ora ho letto \""+ msg +"\"");
                        }
                        else if ( byteRead == -1 ){
                            key.cancel();
                            key.channel().close();
                            if ( DEBUG ) System.out.println("WORKER: Socket chiusa dal client\n");
                        }
                        else if ( byteRead < KILOBYTE ){
                            // Ho letto tutto quello che il client ha inviato al server
                            if ( DEBUG ) System.out.println("WORKER: Leggo una richiesta dal client");
                            // Elaboro la richiesta
                            // Metto la risposta nell'attachment
                            String reply = processRequest(msg);
                            // if ( DEBUG ) System.out.println("WORKER: Spero di leggere questo messaggio! Significa che processRequest è ritornata");
                            key.attach(reply);

                            key.interestOps(SelectionKey.OP_WRITE);
                            
                        }
                    }
                    else if ( key.isWritable() ){
                        
                        SocketChannel client = (SocketChannel) key.channel();
                        client.configureBlocking(false);

                        String reply = ( String ) key.attachment();

                        if ( reply == null ){
                            System.err.println("WORKER: Errore con il client, chiudo la connessione");
                            key.cancel();
                            client.close();
                        }
                        // if ( DEBUG ) System.out.println("WORKER: Sto per inviare la risposta al client, analiziamola:\n" + reply);
                        // if ( DEBUG ) System.out.println("WORKER: Faccio la wrap della richiesta, poi la write");

                        ByteBuffer buffer = ByteBuffer.wrap(reply.getBytes());
                        int byteWrote = client.write(buffer);

                        if ( byteWrote == reply.getBytes().length ){
                            // Ho scritto tutto
                            if ( DEBUG ) System.out.println("WORKER: Ho inviato la risposta al client\n");
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
                        
                    }
                }
            }

        }
        try{
            selector.close();
        } catch ( IOException e ){
            e.printStackTrace();
            
        }
        System.out.println("WORKER: Terminazione");
        
    }
}