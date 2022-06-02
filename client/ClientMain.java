package client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import shared.*;
/**
 * Il parser potrebbe essere un thread avviato dal client dopo aver
 * settato i parametri di configurazione ed essersi collegato.
 * Il thread parser chiama e quindi esegue le funzioni dell'interfaccia client,
 * una per volta perché è l'unico thread del client.
 * Che poi in realtà diventa il main.
 * Allora perché non lasciar fare direttamente tutto al client?
 */

/* 
    Legge i comandi da terminale e trasferisce le richieste al client
    Fa controlli sulla validità della richiesta prima di inoltrarla al client
*/
public class ClientMain {
    private static final boolean DEBUG = true;
    private static final int MINTAGS = 1;
    private static final int MAXTAGS = 5;

    private static Socket socket = null;
    private static int rmiPort = 0;
    private static String rmiServiceName;

    private static String thisUser = ""; // Nick dell'utente che si logga utilizzando questo client
    private static boolean logged = false;
    private static BufferedReader in = null;
    private static PrintWriter out = null;
    private static Set<String> followers = null;
    private static RMIServiceInterface serviceRMI = null;
    private static RewardUpdater rewardUpdater = null;
    private static ClientNotify stub = null;

    public static void main(String[] args){
        // Leggo i parametri per la configurazione iniziale dal file passato come argomento
        File configFile = new File(args[0]);
        if ( !configFile.exists() || !configFile.isFile() ){
            // TODO terminazione
            System.exit(1);
        }

        String multicastAddress = null;
        int multicastPort = 0;
        int tcpPort = 0;

        // Lettura dei parametri iniziali
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
                    default:{
                        break;
                    }
                }
            }
        } catch ( Exception e ){
            // TODO terminazione
            e.getMessage();
            System.exit(1);
        }
        if ( DEBUG ){
            System.out.println("CLIENT: Provo a  connettermi sulla porta " + tcpPort + "\n");
        }

        // Apertura della connessione TCP con il server
        InetAddress address = null;
        try{
            address = InetAddress.getLocalHost();
            while ( socket == null ){
                try{
                    socket = new Socket(address, tcpPort);
                } catch ( ConnectException e ){
                    // TODO 
                    try{
                    Thread.sleep(10000);
                    } catch ( InterruptedException ex ){
                        e.printStackTrace();
                        System.exit(1);
                    }
                }
            }
            out = new PrintWriter(socket.getOutputStream(), true); // Flush automatico
            in = new BufferedReader( new InputStreamReader( socket.getInputStream() ));            
        } catch ( Exception e ){
            e.printStackTrace();
            System.exit(1);
        }

        // Iscrizione al gruppo di multicast per l'aggiornamento delle ricompense
        try{
            rewardUpdater = new RewardUpdater(multicastAddress, multicastPort);
            Thread t = new Thread(rewardUpdater);
            t.start();
        } catch ( Exception e ){
            System.err.println("Errore durante la connessione al servizio di multicast per le ricompense " + e.getMessage());
            System.exit(1);
        }

        // Parsing delle richieste da riga di comando
        try (
            BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        ){
            // Ciclo per parsare le richieste in ingresso
            for (String line = input.readLine(); !line.equals("quit"); line = input.readLine()){
                String[] req = line.split(" ");
                
                // Controllo soltanto i parametri in ingresso, il resto viene svolto all'interno del metodo corrispondente
                switch ( req[0] ){
                    case "register":{
                        // Mi aspetto da uno a cinque tag
                        if ( req.length < 3 + MINTAGS || req.length > 3 + MAXTAGS ){
                            helpMessage();
                            break;
                        }
                        String nickname = new String(req[1]);
                        String psw = new String(req[2]);

                        Set<String> tags = new HashSet<>(req.length - 3);
                        for ( int i = 3; i < req.length; i++ )
                            tags.add(req[i]);

                        if ( register(nickname, psw, tags) ){
                            if ( DEBUG ) System.out.println("CLIENT: Registrazione a Winsome avvenuta con successo");
                        }
                        else {
                            // TODO errore durante la fase di registrazione
                            System.err.println("CLIENT: Errore durante la fase di registrazione");
                        }
                        
                        break;
                    }
                    case "login":{
                        if ( req.length != 3 ){
                            helpMessage();
                            break;
                        }
                        /*  Il client viene "associato" a un utente al momento del login
                            Più utenti possono registrarsi a Winsome sullo stesso client
                        */
                        /*  TODO controllare che sia coerente con il resto dell'implementazione
                            Dovrebbe esserlo, infatti la register aggiunge un nuovo utente a Winsome
                            ma solo la login lo registra al servizio di callback
                        */

                        thisUser = new String(req[1]);
                        String psw = new String(req[2]);
                        
                        try{
                            if ( !login(thisUser, psw) ){
                                // TODO errore nel login
                                System.err.println("CLIENT: Errore durante la fase di login");
                            }
                        } catch ( Exception e ){
                            e.printStackTrace();
                            System.exit(1);
                        }
                        break;
                    }
                    case "logout":{
                        // Qui si effettuano solo i controlli sui dati in ingresso
                        /*
                        Questa roba spetta alla funzione relatiiva
                        if ( thisUser == null ){
                            // TODO messaggio di errore
                            break;
                        }
                        */
                        if ( logout() ){
                            if ( DEBUG ) System.out.println("CLIENT: logout effettuato con successo");
                            
                        }
                        else {
                            // TODO errore nel logout
                            System.err.println("CLIENT: Errore durante la fase di logout");
                        }
                        break;
                    }
                    case "post":{
                        if ( req.length < 3 ){
                            helpMessage();
                            break;
                        }

                        String title = new String(req[1]);
                        StringBuilder content = new StringBuilder(req[2] + " ");
                        for ( int i = 3; i < req.length; i++ )
                            content.append(req[i] + " ");
                            
                        createPost(title, content.toString());
                        break;
                    }
                    case "comment":{
                        if ( req.length < 3 ){
                            helpMessage();
                            break;
                        }
                        try {
                            int idPost = Integer.parseInt(req[1]);
                            StringBuilder content = new StringBuilder(req[2] + " ");
                            for ( int i = 3; i < req.length; i++ )
                                content.append(req[i] + " ");
                                                            
                            addComment(idPost, content.toString());
                            break;

                        } catch ( NumberFormatException e ){
                            helpMessage();
                            break;
                        }
                    }
                    case "rate":{
                        if ( req.length != 3 ){
                            helpMessage();
                            break;
                        }    
                        try {
                            int idPost = Integer.parseInt(req[1]);
                            int vote = 0;
                            switch ( req[2] ){
                                case "+1":{
                                    vote = 1;
                                    break;
                                }
                                case "-1":{
                                    vote = -1;
                                    break;
                                }
                                default:{
                                    helpMessage();
                                    break;
                                }
                            }
                            ratePost(idPost, vote);
                            break;
                            
                        } catch ( NumberFormatException e ){
                            helpMessage();
                            break;
                        }
                    }
                    case "wallet":{
                        if ( req.length == 1 ){
                            // wallet
                            getWallet();
                            break;
                        }
                        if ( req[1].equals("btc") && req.length == 2 ){
                            // wallet btc
                            getWalletBitcoin();
                            break;
                        }
                        // Nessuna delle due, vuol dire che la richiesta è stata formulata in modo scorretto
                        helpMessage();
                        break;
                    }
                    case "list":{
                        if ( req.length != 2 ){
                            helpMessage();
                            break;
                        }
                        switch ( req[1] ){
                            case "followers":{
                                listFollowers();
                                break;
                            }
                            case "users":{
                                listUsers();
                                break;
                            }
                            case "following":{
                                listFollowing();
                                break;
                            }
                            default:{
                                helpMessage();
                                break;
                            }
                        }
                        break;
                    }
                    case "show":{
                        if ( req.length < 2 ){
                            helpMessage();
                            break;
                        }
                        switch( req[1] ){
                            case "feed":{
                                showFeed();
                                break;
                            }
                            case "post":{
                                if ( req.length != 3 ){
                                    helpMessage();
                                    break;
                                }
                                try{
                                    int idPost = Integer.parseInt(req[2]);

                                    showPost(idPost);
                                    break;
                                } catch ( NumberFormatException e ){
                                    helpMessage();
                                    break;
                                }
                            }
                            default:{
                                helpMessage();
                                break;
                            }
                        }
                        break;
                    }
                    case "delete":{
                        if ( req.length != 3 || !req[1].equals("post") ){
                            helpMessage();
                            break;
                        }

                        try{
                            int idPost = Integer.parseInt(req[2]);

                            deletePost(idPost);
                            break;
                        } catch ( NumberFormatException e ){
                            helpMessage();
                            break;
                        }
                    }
                    case "rewin":{
                        if ( req.length != 3 || !req[1].equals("post") ){
                            helpMessage();
                            break;
                        }

                        try{
                            int idPost = Integer.parseInt(req[2]);

                            rewinPost(idPost);
                            break;
                        } catch ( NumberFormatException e ){
                            helpMessage();
                            break;
                        }
                    }
                    case "blog":{
                        viewBlog();
                        break;
                    }
                    case "follow":{
                        if ( req.length != 2 ){
                            helpMessage();
                            break;
                        }
                        String user = new String(req[1]);

                        followUser(user);
                        break;
                    }
                    case "unfollow":{
                        if ( req.length != 2 ){
                            helpMessage();
                            break;
                        }
                        String user = new String(req[1]);

                        unfollowUser(user);
                        break;
                    }
                    default:{
                        helpMessage();
                        break;
                    }
                }
            }

            System.out.println("Chiusura del client");
            // TODO ci saranno delle operazioni di cleanup da dover fare?
            
            // Ad esempio la logout? 
            if ( logged ) logout(); // mi importa controllare il valore di ritorno?
            rewardUpdater.stop();
            socket.close();
        } catch ( IOException e ){
            System.err.println(e.getMessage());
            System.exit(1);
        }
        System.exit(0);
    }

    private static String toRequest(ArrayList<String> args){
        String req = "";
        for ( String elem : args )
            req = req + elem + ";";

        return req;
    }

    public static boolean register(String username, String password, Set<String> tags){
        // Non controllo che username sia uguale a thisUser perché posso registrare più utenti, poi soltanto uno si loggherà
        
        // Mi registro a Winsome tramite RMI
        try{
            Registry registry = LocateRegistry.getRegistry(rmiPort);
            serviceRMI = ( RMIServiceInterface ) registry.lookup(rmiServiceName);
            
            if ( DEBUG ) System.out.println("CLIENT: Mi iscrivo a Winsome");

            return serviceRMI.register(username, password, tags);
        } catch ( RemoteException e ){
            // Errore del client (non dell'utente), è ragionevole terminare TODO
            return false;
        } catch ( NotBoundException e ){
            // Errore del server (non dell'utente), è ragionevole terminare TODO
            return false;
        }
        catch ( Exception e ){
            e.printStackTrace();
            return false;
        }

    }

    public static boolean login(String username, String password){
        System.out.println("LOGIN\t username: " + username + ", password: " + password);
        if ( thisUser == null || logged ){
            // Questo utente ha già effettuato il login (con questo client)
            System.out.println("LOGIN fallita: è già stato effettuato il login con l'utente " + thisUser + " con questo client");
            return false;
        }

        // La fase di login viene fatta tramite connessione TCP
        String request = toRequest(new ArrayList<String>( Arrays.asList(Operation.LOGIN.toString(), username, password)));

        try{
            out.println(request);
            System.out.println("CLIENT: Ho inviato la richiesta al server");
            String reply = in.readLine();
            in.readLine(); // Leggo gli attributi, ma li ignoro perché non servono
            logged = reply.equals(Communication.success) ? true : false;
            
            if ( !logged ){
                System.out.println("LOGIN fallita: " + reply);
                return false;
            }

            thisUser = username;
            
            // se il login ha avuto successo, il client si registra al servizio di callback tramite RMI
            try{
                if ( serviceRMI == null ){
                    // L'utente si è registrato su un client diverso da questo dove sta effettuando il login
                    Registry registry = LocateRegistry.getRegistry(rmiPort);
                    serviceRMI = ( RMIServiceInterface ) registry.lookup(rmiServiceName);
                }
                
                stub = new ClientNotify(username);
                synchronized ( stub ){
                    followers = serviceRMI.registerForCallback(stub);
                    stub.setFollowers(followers);
                }

                if ( DEBUG ) System.out.println("CLIENT: Mi registro al servizio di notifica");
                // if ( DEBUG ) System.out.println("CLIENT: I miei follower sono: " + followers.toString());
            } catch ( NotBoundException e ){
                // Errore del server (non dell'utente), è ragionevole terminare TODO
                return false;
            } catch ( RemoteException e ){
                e.printStackTrace();
                return false;
            }
        } catch ( IOException e ){
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public static boolean logout(){
        if ( !logged || thisUser.equals("") ){
            // Questo utente non aveva effettuato il login (con questo client)
            System.err.println("LOGOUT fallita: Nessun utente si era loggato con questo client");
            return false;
        }

        // La fase di logout viene fatta tramite connessione TCP
        String request = toRequest(new ArrayList<String>( Arrays.asList(Operation.LOGOUT.toString(), thisUser)));
        try{
            out.println(request);

            String reply = in.readLine();
            in.readLine(); // Leggo gli attributi, ma li ignoro perché non servono
            logged =  reply.equals(Communication.success) ? false : true;
            
            if ( logged ){
                System.out.println("Logout fallita: " + reply);
                return false;
            }
            thisUser = null;

            // Finita la fase di logout su Winsome, il client si cancella dal servizio di callback
            try{
                serviceRMI.unregisterForCallback(stub);
                if ( DEBUG ) System.out.println("CLIENT: Mi cancello dal servizio di notifica");
            } catch (RemoteException e ){
                if ( DEBUG ) e.printStackTrace();
                else e.getMessage();
                return false;
            }
        } catch ( IOException e ){
            if ( DEBUG ) e.printStackTrace();
            else e.getMessage();
            return false;
        }

        System.err.println("LOGOUT eseguita con successo");
        return true;
    }

    public static boolean listUsers(){
        // Preparo la richiesta nel formato che il server riesce a leggere
        String request = toRequest(new ArrayList<String>(Arrays.asList(Operation.LIST_USERS.toString(), thisUser)));
        try{
            out.println(request);

            String reply = in.readLine();
            if ( !reply.equals(Communication.success) ){
                System.err.println("LIST_USERS fallita: " + reply);
                in.readLine(); // Leggo gli attributi, ma li ignoro perché non servono
                return false;
            }

            // Stampo a video gli utenti con almeno un tag in comune a thisUser
            System.out.println(in.readLine());

        } catch ( IOException e ){
            if ( DEBUG ) e.printStackTrace();
            else e.getMessage();
            return false;
        }

        return true;
    }

    public static boolean listFollowers(){
        System.out.println("LIST_FOLLOWERS");
        System.out.println(followers.toString());

        return true;
    }

    public static boolean listFollowing(){

        String request = toRequest(new ArrayList<String>(Arrays.asList(Operation.LIST_FOLLOWING.toString(), thisUser)));
        try{
            out.println(request);

            String reply = in.readLine();
            if ( !reply.equals(Communication.success) ){
                System.err.println(Operation.LIST_FOLLOWING + " fallita: " + reply);
                in.readLine(); // Leggo gli attributi, ma li ignoro perché non servono
                return false;
            }

            // Stampo a video la lista degli utenti che thisUser segue
            System.out.println(in.readLine());

        } catch ( IOException e ){
            // TODO in tutti questi casi di IOException penso sia meglio far terminare il client
            // perché si potrebbero avere inconsistenze con i messaggi inviati dal server
            if ( DEBUG ) e.printStackTrace();
            else e.getMessage();
            return false;
        }

        return true;
    }

    public static boolean followUser(String idUser){
        
        String request = toRequest(new ArrayList<String>(Arrays.asList(Operation.FOLLOW_USER.toString(), thisUser, idUser)));
        try{
            out.println(request);
            String reply = in.readLine();
            in.readLine(); // Leggo gli attributi, ma li ignoro perché non servono

            if ( !reply.equals(Communication.success) ){
                System.err.println("FOLLOW_USER fallita: " + reply);
                return false;
            }

        } catch ( IOException e ){
            // TODO in tutti questi casi di IOException penso sia meglio far terminare il client
            // perché si potrebbero avere inconsistenze con i messaggi inviati dal server
            if ( DEBUG ) e.printStackTrace();
            else e.getMessage();
            return false;
        }

        System.out.println("FOLLOW_USER eseguita con successo");
        return true;
    }

    public static boolean unfollowUser(String idUser){

        String request = toRequest(new ArrayList<String>(Arrays.asList(Operation.UNFOLLOW_USER.toString(), thisUser, idUser)));
        try{
            out.println(request);
            String reply = in.readLine();
            in.readLine(); // Leggo gli attributi, ma li ignoro perché non servono

            if ( !reply.equals(Communication.success) ){
                System.err.println("UNFOLLOW_USER fallita: " + reply);
                return false;
            }

        } catch ( IOException e ){
            // TODO in tutti questi casi di IOException penso sia meglio far terminare il client
            // perché si potrebbero avere inconsistenze con i messaggi inviati dal server
            if ( DEBUG ) e.printStackTrace();
            else e.getMessage();
            return false;
        }

        System.out.println("UNFOLLOW_USER eseguita con successo");
        return true;
    }

    public static boolean viewBlog(){

        String request = toRequest(new ArrayList<String>(Arrays.asList(Operation.VIEW_BLOG.toString(), thisUser)));
        try{
            out.println(request);
            String reply = in.readLine();
            if ( !reply.equals(Communication.success) ){
                System.err.println("VIEW_BLOG fallita: " + reply);
                in.readLine(); // Leggo gli attributi, ma li ignoro perché non servono
                return false;
            }

            // Stampo a video il blog di thisUser
            System.out.println(in.readLine());

        } catch ( IOException e ){
            if ( DEBUG ) e.printStackTrace();
            else e.getMessage();
            return false;
        }

        return true;
    }

    public static boolean createPost(String title, String content){

        String request = toRequest(new ArrayList<String>(Arrays.asList(Operation.CREATE_POST.toString(), thisUser, title, content)));
        try{
            out.println(request);
            String reply = in.readLine();
            in.readLine(); // Leggo gli attributi, ma li ignoro perché non servono

            if ( !reply.equals(Communication.success) ){
                System.err.println("CREATE_POST fallita: " + reply);
                return false;
            }

        } catch ( IOException e ){
            // TODO in tutti questi casi di IOException penso sia meglio far terminare il client
            // perché si potrebbero avere inconsistenze con i messaggi inviati dal server
            if ( DEBUG ) e.printStackTrace();
            else e.getMessage();
            return false;
        }

        System.out.println("CREATE_POST eseguita con successo");
        return true;
    }

    public static boolean showFeed(){

        String request = toRequest(new ArrayList<String>(Arrays.asList(Operation.SHOW_FEED.toString(), thisUser)));
        try{
            out.println(request);

            String reply = in.readLine();
            if ( !reply.equals(Communication.success) ){
                System.err.println(Operation.SHOW_FEED + " fallita: " + reply);
                in.readLine(); // Leggo gli attributi, ma li ignoro perché non servono
                return false;
            }

            // Stampo a video il feed di thisUser
            System.out.println(in.readLine());

        } catch ( IOException e ){
            // TODO in tutti questi casi di IOException penso sia meglio far terminare il client
            // perché si potrebbero avere inconsistenze con i messaggi inviati dal server
            if ( DEBUG ) e.printStackTrace();
            else e.getMessage();
            return false;
        }

        return true;
    }

    public static boolean showPost(Integer idPost){

        String request = toRequest(new ArrayList<String>(Arrays.asList(Operation.SHOW_POST.toString(), thisUser, idPost.toString())));
        try{
            out.println(request);

            String reply = in.readLine();
            if ( !reply.equals(Communication.success) ){
                System.err.println(Operation.SHOW_POST + " fallita: " + reply);
                in.readLine(); // Leggo gli attributi, ma li ignoro perché non servono
                return false;
            }

            String post = "", s = "";
            while ( ( s = in.readLine() ) != null )
                post = post + s;

            // Stampo a video il post richiesto
            System.out.println(post);

        } catch ( IOException e ){
            // TODO in tutti questi casi di IOException penso sia meglio far terminare il client
            // perché si potrebbero avere inconsistenze con i messaggi inviati dal server
            if ( DEBUG ) e.printStackTrace();
            else e.getMessage();
            return false;
        }

        return true;
    }

    public static boolean deletePost(Integer idPost){

        String request = toRequest(new ArrayList<String>(Arrays.asList(Operation.DELETE_POST.toString(), thisUser, idPost.toString())));
        try{
            out.println(request);
            String reply = in.readLine();
            in.readLine(); // Leggo gli attributi, ma li ignoro perché non servono

            if ( !reply.equals(Communication.success) ){
                System.err.println(Operation.DELETE_POST + " fallita: " + reply);
                return false;
            }

        } catch ( IOException e ){
            // TODO in tutti questi casi di IOException penso sia meglio far terminare il client
            // perché si potrebbero avere inconsistenze con i messaggi inviati dal server
            if ( DEBUG ) e.printStackTrace();
            else e.getMessage();
            return false;
        }

        System.out.println(Operation.DELETE_POST + " eseguita con successo");
        return true;
    }

    public static boolean rewinPost(Integer idPost){

        String request = toRequest(new ArrayList<String>(Arrays.asList(Operation.REWIN_POST.toString(), thisUser, idPost.toString())));
        try{
            out.println(request);
            String reply = in.readLine();
            in.readLine(); // Leggo gli attributi, ma li ignoro perché non servono

            if ( !reply.equals(Communication.success) ){
                System.err.println(Operation.REWIN_POST + " fallita: " + reply);
                return false;
            }

        } catch ( IOException e ){
            // TODO in tutti questi casi di IOException penso sia meglio far terminare il client
            // perché si potrebbero avere inconsistenze con i messaggi inviati dal server
            if ( DEBUG ) e.printStackTrace();
            else e.getMessage();
            return false;
        }

        System.out.println(Operation.REWIN_POST + " eseguita con successo");
        return true;
    }

    public static boolean ratePost(Integer idPost, Integer vote){

        String request = toRequest(new ArrayList<String>(Arrays.asList(Operation.RATE_POST.toString(), thisUser, idPost.toString(), vote.toString())));
        try{
            out.println(request);
            String reply = in.readLine();
            in.readLine(); // Leggo gli attributi, ma li ignoro perché non servono

            if ( !reply.equals(Communication.success) ){
                System.err.println(Operation.RATE_POST + " fallita: " + reply);
                return false;
            }

        } catch ( IOException e ){
            // TODO in tutti questi casi di IOException penso sia meglio far terminare il client
            // perché si potrebbero avere inconsistenze con i messaggi inviati dal server
            if ( DEBUG ) e.printStackTrace();
            else e.getMessage();
            return false;
        }

        System.out.println(Operation.RATE_POST + " eseguita con successo");
        return true;
    }

    public static boolean addComment(Integer idPost, String content){

        String request = toRequest(new ArrayList<String>(Arrays.asList(Operation.ADD_COMMENT.toString(), thisUser, idPost.toString(), content)));
        try{
            out.println(request);
            String reply = in.readLine();
            in.readLine(); // Leggo gli attributi, ma li ignoro perché non servono

            if ( !reply.equals(Communication.success) ){
                System.err.println(Operation.ADD_COMMENT + " fallita: " + reply);
                return false;
            }

        } catch ( IOException e ){
            // TODO in tutti questi casi di IOException penso sia meglio far terminare il client
            // perché si potrebbero avere inconsistenze con i messaggi inviati dal server
            if ( DEBUG ) e.printStackTrace();
            else e.getMessage();
            return false;
        }

        System.out.println(Operation.ADD_COMMENT + " eseguita con successo");
        return true;
    }

    public static boolean getWallet(){

        String request = toRequest(new ArrayList<String>(Arrays.asList(Operation.GET_WALLET.toString(), thisUser)));
        try{
            out.println(request);

            String reply = in.readLine();
            if ( !reply.equals(Communication.success) ){
                System.err.println(Operation.GET_WALLET + " fallita: " + reply);
                in.readLine(); // Leggo gli attributi, ma li ignoro perché non servono
                return false;
            }

            // Stampo a video il wallet di thisUser
            System.out.println(in.readLine());

        } catch ( IOException e ){
            // TODO in tutti questi casi di IOException penso sia meglio far terminare il client
            // perché si potrebbero avere inconsistenze con i messaggi inviati dal server
            if ( DEBUG ) e.printStackTrace();
            else e.getMessage();
            return false;
        }

        return true;
    }

    public static boolean getWalletBitcoin(){

        String request = toRequest(new ArrayList<String>(Arrays.asList(Operation.GET_WALLET_BITCOIN.toString(), thisUser)));
        try{
            out.println(request);

            String reply = in.readLine();
            if ( !reply.equals(Communication.success) ){
                System.err.println(Operation.GET_WALLET_BITCOIN + " fallita: " + reply);
                in.readLine(); // Leggo gli attributi, ma li ignoro perché non servono
                return false;
            }

            // Stampo a video il wallet in bitcoin di thisUser
            System.out.println(in.readLine());

        } catch ( IOException e ){
            // TODO in tutti questi casi di IOException penso sia meglio far terminare il client
            // perché si potrebbero avere inconsistenze con i messaggi inviati dal server
            if ( DEBUG ) e.printStackTrace();
            else e.getMessage();
            return false;
        }

        return true;
    }

    public static void helpMessage(){
        System.out.println(
            "\nregister <username> <password> <tags>:\t Effettua la registrazione dell'utente" +
            "\nlogin <username> <password>:\t\t Effettua il login dell'utente" +
            "\nlogout:\t\t\t\t\t Effettua il logout dell'utente" +
            "\nlist users:\t\t\t\t Restituisce gli utenti che hanno almeno un tag in comune" +
            "\nlist followers:\t\t\t\t Restituisce la lista dei follower" +
            "\nlist following:\t\t\t\t Restituisce la lista degli utenti seguiti" +
            "\nfollow <username>:\t\t\t Permette di seguire un utente" +
            "\nunfollow <username>:\t\t\t Permette di smettere di seguire un utente" +
            "\nblog:\t\t\t\t\t Visualizza i post di cui l'utente è autore" +
            "\npost <title> <content>:\t\t\t Crea un post" +
            "\nshow feed:\t\t\t\t Visualizza il feed dell'utente" +
            "\nshow post <id>:\t\t\t\t Visualizza il post" +
            "\ndelete <idPost>:\t\t\t Elimina il post" +
            "\nrewin <idPost>:\t\t\t\t Effettua il rewin del post" +
            "\nrate <idPost> <vote>:\t\t\t Aggiunge un voto al post" +
            "\ncomment <idPost> <comment>:\t\t Aggiunge un commento al post" +
            "\nwallet:\t\t\t\t\t Visualizza il portafoglio dell'utente" +
            "\nwallet btc:\t\t\t\t Visualizza il portafoglio dell'utente in bitcoin" +
            "\nhelp:\t\t\t\t\t Visualizza questo messaggio"
        );
    }
}
