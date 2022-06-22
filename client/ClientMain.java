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

/* 
    Legge i comandi da terminale e trasferisce le richieste al server
    Fa controlli sulla validità della richiesta prima di inoltrarla
*/
public class ClientMain {
    private static final boolean DEBUG = false;
    private static final int MINTAGS = 1;
    private static final int MAXTAGS = 5;
    private static final int SUCCESS = 0;
    private static final int FAILURE = 1;

    // Variabili per connettersi a Winsome
    private static Socket socket = null;
    private static int rmiPort = 0;
    private static String rmiServiceName;
    private static RMIServiceInterface serviceRMI = null;

    private static String thisUser = ""; // Nick dell'utente che si logga utilizzando questo client
    private static boolean logged = false; // Flag che indica se un utente è attualmente connesso con questo client
    private static BufferedReader in = null; // Stream per leggere dal server
    private static PrintWriter out = null; // Stream per scrivere al server
    private static Set<String> followers = null; // Follower dell'utente attualmente loggato
    private static RewardUpdater rewardUpdater = null; // Thread che riceve la notifica del calcolo delle ricompense
    private static ClientNotify stub = null; // Classe che aggiorna i follower quando riceve la notifica

    public static void main(String[] args){
        // Leggo i parametri per la configurazione iniziale dal file passato come argomento
        File configFile = new File(args[0]);
        if ( !configFile.exists() || !configFile.isFile() ){
            System.err.println("Impossibile trovare il file con i parametri di configurazione");
            System.exit(FAILURE);
        }

        int tcpPort = -1, connectionAttempt = -1;
        long retryTime = -1;

        // Lettura dei parametri di configurazione iniziali
        try (
            BufferedReader input = new BufferedReader(new FileReader(configFile))
        ){
            for ( String line = input.readLine(); line != null; line = input.readLine() ){
                String[] token = line.split("=");

                switch ( token[0] ){
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
                    case "CONNECTION_ATTEMPT":{
                        connectionAttempt = Integer.parseInt(token[1]);
                        if ( connectionAttempt < 1 )
                            System.exit(FAILURE);
                        break;
                    }
                    case "RETRY_TIME":{
                        retryTime = Long.parseLong(token[1]);
                        if ( retryTime < 1 )
                            System.exit(FAILURE);
                        break;
                    }
                    default:{
                        break;
                    }
                }
            }
            if ( rmiPort < 0 || rmiServiceName == null || tcpPort < 0 || retryTime < 0 || connectionAttempt < 0 ){
                System.err.println("Errore nei parametri di configurazione, terminazione");
                System.exit(FAILURE);
            }
        } catch ( Exception e ){
            e.getMessage();
            System.exit(FAILURE);
        }

        // Apertura della connessione TCP con il server
        InetAddress address = null;
        try{
            address = InetAddress.getLocalHost();
            int i = 0;
            // Il client tenta di connettersi al server fino a un massimo di connectionAttempt tentativi
            // Fra un tentativo di connessione e l'altro attende un tempo di retryTime millisecondi
            while ( socket == null ){
                if ( socket != null )
                    break;
                try{
                    if ( DEBUG ) System.out.println("Provo a  connettermi sulla porta " + tcpPort + "\n");
                    socket = new Socket(address, tcpPort);
                } catch ( ConnectException e ){
                    if ( i < connectionAttempt )
                        i++;
                    else {
                        System.err.println("Connessione con il server non stabilita, terminazione");
                        System.exit(FAILURE);
                    }
                }
                    try{
                        Thread.sleep(retryTime);
                    } catch ( InterruptedException e ){
                        e.printStackTrace();
                        System.exit(FAILURE);
                    }
                
            }
            if ( socket == null ){
                System.err.println("Connessione con il server non stabilita, terminazione");
                System.exit(FAILURE);
            }
            System.err.println("Connessione con il server stabilita con successo");
            out = new PrintWriter(socket.getOutputStream(), true); // Flush automatico
            in = new BufferedReader( new InputStreamReader( socket.getInputStream() ));      

        } catch ( Exception e ){
            e.printStackTrace();
            System.exit(FAILURE);
        }

        // Parsing delle richieste da riga di comando
        try (
            BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        ){
            // Ciclo per parsare le richieste in ingresso, il client termina quando legge "quit"
            for (String line = input.readLine(); !line.equals("quit"); line = input.readLine()){
                String[] req = line.split(" ");
                
                // Controllo soltanto il numero di parametri in ingresso, il resto viene svolto all'interno del metodo corrispondente
                switch ( req[0] ){
                    case "register":{
                        // Mi aspetto da uno a cinque tag
                        if ( req.length < 3 + MINTAGS || req.length > 3 + MAXTAGS ){
                            System.err.println("Richiesta formulata con sintassi errata, digitare help per visualizzare la forma corretta");
                            break;
                        }
                        String nickname = new String(req[1]);
                        String psw = new String(req[2]);

                        Set<String> tags = new HashSet<>(req.length - 3);
                        for ( int i = 3; i < req.length; i++ )
                            tags.add(req[i]);

                        register(nickname, psw, tags);
                        break;
                    }
                    case "login":{
                        if ( req.length != 3 ){
                            System.err.println("Richiesta formulata con sintassi errata, digitare help per visualizzare la forma corretta");
                            break;
                        }
                        thisUser = new String(req[1]);
                        String psw = new String(req[2]);
                        
                        login(thisUser, psw);
                        break;
                    }
                    case "logout":{
                        if ( req.length != 1 ){
                            System.err.println("Richiesta formulata con sintassi errata, digitare help per visualizzare la forma corretta");
                            break;
                        }
                        logout();
                        break;
                    }
                    case "post":{
                        if ( req.length < 3 ){
                            System.err.println("Richiesta formulata con sintassi errata, digitare help per visualizzare la forma corretta");
                            break;
                        }
                        // Il titolo non deve contenere spazi
                        String title = new String(req[1]);
                        StringBuilder content = new StringBuilder(req[2]);
                        for ( int i = 3; i < req.length; i++ )
                            content.append(" " + req[i]);
                            
                        createPost(title, content.toString());
                        break;
                    }
                    case "comment":{
                        if ( req.length < 3 ){
                            System.err.println("Richiesta formulata con sintassi errata, digitare help per visualizzare la forma corretta");
                            break;
                        }
                        try {
                            int idPost = Integer.parseInt(req[1]);
                            StringBuilder content = new StringBuilder(req[2]);
                            for ( int i = 3; i < req.length; i++ )
                                content.append(" " + req[i]);
                                                            
                            addComment(idPost, content.toString());
                            break;

                        } catch ( NumberFormatException e ){
                            System.err.println("Richiesta formulata con sintassi errata, digitare help per visualizzare la forma corretta");
                            break;
                        }
                    }
                    case "rate":{
                        if ( req.length != 3 ){
                            System.err.println("Richiesta formulata con sintassi errata, digitare help per visualizzare la forma corretta");
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
                                    System.err.println("Richiesta formulata con sintassi errata, digitare help per visualizzare la forma corretta");
                                    break;
                                }
                            }
                            ratePost(idPost, vote);
                            break;
                            
                        } catch ( NumberFormatException e ){
                            System.err.println("Richiesta formulata con sintassi errata, digitare help per visualizzare la forma corretta");
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
                        System.err.println("Richiesta formulata con sintassi errata, digitare help per visualizzare la forma corretta");
                        break;
                    }
                    case "list":{
                        if ( req.length != 2 ){
                            System.err.println("Richiesta formulata con sintassi errata, digitare help per visualizzare la forma corretta");
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
                                System.err.println("Richiesta formulata con sintassi errata, digitare help per visualizzare la forma corretta");
                                break;
                            }
                        }
                        break;
                    }
                    case "show":{
                        if ( req.length < 2 ){
                            System.err.println("Richiesta formulata con sintassi errata, digitare help per visualizzare la forma corretta");
                            break;
                        }
                        switch( req[1] ){
                            case "feed":{
                                showFeed();
                                break;
                            }
                            case "post":{
                                if ( req.length != 3 ){
                                    System.err.println("Richiesta formulata con sintassi errata, digitare help per visualizzare la forma corretta");
                                    break;
                                }
                                try{
                                    int idPost = Integer.parseInt(req[2]);

                                    showPost(idPost);
                                    break;
                                } catch ( NumberFormatException e ){
                                    System.err.println("Richiesta formulata con sintassi errata, digitare help per visualizzare la forma corretta");
                                    break;
                                }
                            }
                            default:{
                                System.err.println("Richiesta formulata con sintassi errata, digitare help per visualizzare la forma corretta");
                                break;
                            }
                        }
                        break;
                    }
                    case "delete":{
                        if ( req.length != 2 ){
                            System.err.println("Richiesta formulata con sintassi errata, digitare help per visualizzare la forma corretta");
                            break;
                        }

                        try{
                            int idPost = Integer.parseInt(req[1]);

                            deletePost(idPost);
                            break;
                        } catch ( NumberFormatException e ){
                            System.err.println("Richiesta formulata con sintassi errata, digitare help per visualizzare la forma corretta");
                            break;
                        }
                    }
                    case "rewin":{
                        if ( req.length != 2 ){
                            System.err.println("Richiesta formulata con sintassi errata, digitare help per visualizzare la forma corretta");
                            break;
                        }

                        try{
                            int idPost = Integer.parseInt(req[1]);

                            rewinPost(idPost);
                            break;
                        } catch ( NumberFormatException e ){
                            System.err.println("Richiesta formulata con sintassi errata, digitare help per visualizzare la forma corretta");
                            break;
                        }
                    }
                    case "blog":{
                        viewBlog();
                        break;
                    }
                    case "follow":{
                        if ( req.length != 2 ){
                            System.err.println("Richiesta formulata con sintassi errata, digitare help per visualizzare la forma corretta");
                            break;
                        }
                        String user = new String(req[1]);

                        followUser(user);
                        break;
                    }
                    case "unfollow":{
                        if ( req.length != 2 ){
                            System.err.println("Richiesta formulata con sintassi errata, digitare help per visualizzare la forma corretta");
                            break;
                        }
                        String user = new String(req[1]);

                        unfollowUser(user);
                        break;
                    }
                    case "help":{
                        helpMessage();
                        break;
                    }
                    default:{
                        System.err.println("Richiesta formulata con sintassi errata, digitare help per visualizzare la forma corretta");
                        break;
                    }
                }
            }

            System.out.println("Terminazione in corso...");
            if ( logged ) logout();
            socket.close();
        } catch ( Exception e ){
            System.err.println("Errore fatale: " + e.getMessage() + ", terminazione");
            System.exit(FAILURE);
        }
        System.out.println("Terminazione avvenuta con successo");
        System.exit(SUCCESS);
    }

    // Formalizza la richiesta per renderla leggibile al server
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

            String reply = serviceRMI.register(username, password, tags);
            if ( !reply.equals(Communication.Success.toString()) ){
                System.err.println("REGISTER fallita: " + reply);
                return false;
            }
        }
        catch ( Exception e ){
            System.err.println("Errore fatale: " + e.getMessage() + ", terminazione");
            System.exit(FAILURE);
        }

        System.out.println("Registrazione a Winsome eseguita con successo");
        return true;
    }

    public static boolean login(String username, String password){
        System.out.println("LOGIN\t username: " + username + ", password: " + password);
        if ( thisUser == null || logged ){
            // Questo utente ha già effettuato il login (con questo client)
            System.out.println(Operation.LOGIN + " fallita: è già stato effettuato il login con l'utente " + thisUser + " con questo client");
            return false;
        }

        // La fase di login viene fatta tramite connessione TCP
        String request = toRequest(new ArrayList<String>( Arrays.asList(Operation.LOGIN.toString(), username, password)));

        try{
            out.println(request);
            if ( DEBUG ) System.out.println("CLIENT: Ho inviato la richiesta al server");
            String reply = in.readLine();
            logged = reply.equals(Communication.Success.toString()) ? true : false;
            
            if ( !logged ){
                System.out.println(Operation.LOGIN + " fallita: " + reply);
                return false;
            }

            thisUser = username;
            // se il login ha avuto successo, il client si registra al servizio di multicast per la notifica delle ricompense
            String multicastAddress = in.readLine();
            int multicastPort = Integer.parseInt(in.readLine());
            
            // Iscrizione al gruppo di multicast per l'aggiornamento delle ricompense
            try{
                rewardUpdater = new RewardUpdater(multicastAddress, multicastPort);
                Thread t = new Thread(rewardUpdater);
                t.start();
            } catch ( Exception e ){
                System.err.println("Errore durante la connessione al servizio di multicast per le ricompense " + e.getMessage());
                System.exit(FAILURE);
            }

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
                System.err.println("Errore fatale: " + e.getMessage() + ", terminazione");
                System.exit(FAILURE);
            } catch ( RemoteException e ){
                System.err.println("Errore fatale: " + e.getMessage() + ", terminazione");
                System.exit(FAILURE);
            }
        } catch ( IOException | NullPointerException e ){
            System.err.println("Errore fatale: " + e.getMessage() + ", terminazione");
            System.exit(FAILURE);
        }

        System.out.println(Operation.LOGIN + " eseguita con successo");
        return true;
    }

    public static boolean logout(){
        if ( !logged || thisUser.equals("") ){
            // Questo utente non aveva effettuato il login (con questo client)
            System.err.println(Operation.LOGOUT + " fallita: Nessun utente si era loggato con questo client");
            return false;
        }

        // La fase di logout viene fatta tramite connessione TCP
        String request = toRequest(new ArrayList<String>( Arrays.asList(Operation.LOGOUT.toString(), thisUser)));
        try{
            out.println(request);

            String reply = in.readLine();
            in.readLine(); // Leggo gli attributi, ma li ignoro perché non servono
            logged =  reply.equals(Communication.Success.toString()) ? false : true;
            
            if ( logged ){
                System.out.println(Operation.LOGOUT + " fallita: " + reply);
                return false;
            }
            thisUser = null;

            // Una volta chiusa la sessione mi tolgo dal servizio di multicast
            rewardUpdater.stop();
            // Finita la sessione su Winsome, il client si cancella dal servizio di callback
            try{
                serviceRMI.unregisterForCallback(stub);
                if ( DEBUG ) System.out.println("CLIENT: Mi cancello dal servizio di notifica");
            } catch (RemoteException e ){
                System.err.println("Errore fatale: " + e.getMessage() + ", terminazione");
                System.exit(FAILURE);
            }
        } catch ( IOException | NullPointerException e ){
            System.err.println("Errore fatale: " + e.getMessage() + ", terminazione");
            System.exit(FAILURE);
        }

        System.err.println(Operation.LOGOUT + " eseguita con successo");
        return true;
    }

    public static boolean listUsers(){
        // Preparo la richiesta nel formato che il server riesce a leggere
        String request = toRequest(new ArrayList<String>(Arrays.asList(Operation.LIST_USERS.toString(), thisUser)));
        try{
            out.println(request);

            String reply = in.readLine();
            if ( !reply.equals(Communication.Success.toString()) ){
                System.err.println(Operation.LIST_USERS + " fallita: " + reply);
                in.readLine(); // Leggo gli attributi, ma li ignoro perché non servono
                return false;
            }

            // Stampo a video gli utenti con almeno un tag in comune a thisUser
            System.out.println(in.readLine());

        } catch ( IOException | NullPointerException e ){
            System.err.println("Errore fatale: " + e.getMessage() + ", terminazione");
            System.exit(FAILURE);
        }

        return true;
    }

    public static boolean listFollowers(){
        if ( !logged )
            System.out.println("Per visualizzare i proprio follower è necessario effettuare il login");

        synchronized ( followers ){
            if ( followers == null || followers.isEmpty() )
                System.out.println("L'utente non ha follower");
            else
                System.out.println(followers.toString());
        }

        return true;
    }

    public static boolean listFollowing(){

        String request = toRequest(new ArrayList<String>(Arrays.asList(Operation.LIST_FOLLOWING.toString(), thisUser)));
        try{
            out.println(request);

            String reply = in.readLine();
            if ( !reply.equals(Communication.Success.toString()) ){
                System.err.println(Operation.LIST_FOLLOWING + " fallita: " + reply);
                in.readLine(); // Leggo gli attributi, ma li ignoro perché non servono
                return false;
            }

            // Stampo a video la lista degli utenti che thisUser segue
            System.out.println(in.readLine());

        } catch ( IOException | NullPointerException e ){
            System.err.println("Errore fatale: " + e.getMessage() + ", terminazione");
            System.exit(FAILURE);
        }

        return true;
    }

    public static boolean followUser(String idUser){
        
        String request = toRequest(new ArrayList<String>(Arrays.asList(Operation.FOLLOW_USER.toString(), thisUser, idUser)));
        try{
            out.println(request);
            String reply = in.readLine();
            in.readLine(); // Leggo gli attributi, ma li ignoro perché non servono

            if ( !reply.equals(Communication.Success.toString()) ){
                System.err.println(Operation.FOLLOW_USER + " fallita: " + reply);
                return false;
            }

        } catch ( IOException | NullPointerException e ){
            System.err.println("Errore fatale: " + e.getMessage() + ", terminazione");
            System.exit(FAILURE);
        }

        System.out.println(Operation.FOLLOW_USER + " eseguita con successo");
        return true;
    }

    public static boolean unfollowUser(String idUser){

        String request = toRequest(new ArrayList<String>(Arrays.asList(Operation.UNFOLLOW_USER.toString(), thisUser, idUser)));
        try{
            out.println(request);
            String reply = in.readLine();
            in.readLine(); // Leggo gli attributi, ma li ignoro perché non servono

            if ( !reply.equals(Communication.Success.toString()) ){
                System.err.println(Operation.UNFOLLOW_USER + " fallita: " + reply);
                return false;
            }

        } catch ( IOException | NullPointerException e ){
            System.err.println("Errore fatale: " + e.getMessage() + ", terminazione");
            System.exit(FAILURE);
        }

        System.out.println(Operation.UNFOLLOW_USER + " eseguita con successo");
        return true;
    }

    public static boolean viewBlog(){

        String request = toRequest(new ArrayList<String>(Arrays.asList(Operation.VIEW_BLOG.toString(), thisUser)));
        try{
            out.println(request);
            String reply = in.readLine();
            if ( !reply.equals(Communication.Success.toString()) ){
                System.err.println(Operation.VIEW_BLOG + " fallita: " + reply);
                in.readLine(); // Leggo gli attributi, ma li ignoro perché non servono
                return false;
            }

            String blog = "", s = "";
            while ( !( s = in.readLine() ).equals(";") )
                blog = blog + s + "\n";
            
            // Stampo a video il blog di thisUser
            System.out.println(blog);

        } catch ( IOException | NullPointerException e ){
            System.err.println("Errore fatale: " + e.getMessage() + ", terminazione");
            System.exit(FAILURE);
        }

        return true;
    }

    public static boolean createPost(String title, String content){

        String request = toRequest(new ArrayList<String>(Arrays.asList(Operation.CREATE_POST.toString(), thisUser, title, content)));
        try{
            out.println(request);
            String reply = in.readLine();
            in.readLine(); // Leggo gli attributi, ma li ignoro perché non servono

            if ( !reply.equals(Communication.Success.toString()) ){
                System.err.println(Operation.CREATE_POST + " fallita: " + reply);
                return false;
            }

        } catch ( IOException | NullPointerException e ){
            System.err.println("Errore fatale: " + e.getMessage() + ", terminazione");
            System.exit(FAILURE);
        }

        System.out.println(Operation.CREATE_POST + " eseguita con successo");
        return true;
    }

    public static boolean showFeed(){

        String request = toRequest(new ArrayList<String>(Arrays.asList(Operation.SHOW_FEED.toString(), thisUser)));
        try{
            out.println(request);

            String reply = in.readLine();
            if ( !reply.equals(Communication.Success.toString()) ){
                System.err.println(Operation.SHOW_FEED + " fallita: " + reply);
                in.readLine(); // Leggo gli attributi, ma li ignoro perché non servono
                return false;
            }
            String feed = "", s = "";
            while ( !( s = in.readLine() ).equals(";") )
                feed = feed + s + "\n";

            // Stampo a video il feed di thisUser
            System.out.println(feed);

        } catch ( IOException | NullPointerException e ){
            System.err.println("Errore fatale: " + e.getMessage() + ", terminazione");
            System.exit(FAILURE);
        }

        return true;
    }

    public static boolean showPost(Integer idPost){

        String request = toRequest(new ArrayList<String>(Arrays.asList(Operation.SHOW_POST.toString(), thisUser, idPost.toString())));
        try{
            out.println(request);

            String reply = in.readLine();
            if ( !reply.equals(Communication.Success.toString()) ){
                System.err.println(Operation.SHOW_POST + " fallita: " + reply);
                in.readLine(); // Leggo gli attributi, ma li ignoro perché non servono
                return false;
            }

            String post = "", s = "";
            while ( !( s = in.readLine() ).equals(";") )
                post = post + s + "\n";

            // Stampo a video il post richiesto
            System.out.println(post);

        } catch ( IOException | NullPointerException e ){
            System.err.println("Errore fatale: " + e.getMessage() + ", terminazione");
            System.exit(FAILURE);
        }

        return true;
    }

    public static boolean deletePost(Integer idPost){

        String request = toRequest(new ArrayList<String>(Arrays.asList(Operation.DELETE_POST.toString(), thisUser, idPost.toString())));
        try{
            out.println(request);
            String reply = in.readLine();
            in.readLine(); // Leggo gli attributi, ma li ignoro perché non servono

            if ( !reply.equals(Communication.Success.toString()) ){
                System.err.println(Operation.DELETE_POST + " fallita: " + reply);
                return false;
            }

        } catch ( IOException | NullPointerException e ){
            System.err.println("Errore fatale: " + e.getMessage() + ", terminazione");
            System.exit(FAILURE);
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

            if ( !reply.equals(Communication.Success.toString()) ){
                System.err.println(Operation.REWIN_POST + " fallita: " + reply);
                return false;
            }

        } catch ( IOException | NullPointerException e ){
            System.err.println("Errore fatale: " + e.getMessage() + ", terminazione");
            System.exit(FAILURE);
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

            if ( !reply.equals(Communication.Success.toString()) ){
                System.err.println(Operation.RATE_POST + " fallita: " + reply);
                return false;
            }

        } catch ( IOException | NullPointerException e ){
            System.err.println("Errore fatale: " + e.getMessage() + ", terminazione");
            System.exit(FAILURE);
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

            if ( !reply.equals(Communication.Success.toString()) ){
                System.err.println(Operation.ADD_COMMENT + " fallita: " + reply);
                return false;
            }

        } catch ( IOException | NullPointerException e ){
            System.err.println("Errore fatale: " + e.getMessage() + ", terminazione");
            System.exit(FAILURE);
        }

        System.out.println(Operation.ADD_COMMENT + " eseguita con successo");
        return true;
    }

    public static boolean getWallet(){

        String request = toRequest(new ArrayList<String>(Arrays.asList(Operation.GET_WALLET.toString(), thisUser)));
        try{
            out.println(request);

            String reply = in.readLine();
            if ( !reply.equals(Communication.Success.toString()) ){
                System.err.println(Operation.GET_WALLET + " fallita: " + reply);
                in.readLine(); // Leggo gli attributi, ma li ignoro perché non servono
                return false;
            }

            String wallet = "", s = "";
            while ( !( s = in.readLine() ).equals(";") )
                wallet = wallet + s + "\n";

            // Stampo a video il wallet di thisUser
            System.out.println(wallet);

        } catch ( IOException | NullPointerException e ){
            System.err.println("Errore fatale: " + e.getMessage() + ", terminazione");
            System.exit(FAILURE);
        }

        return true;
    }

    public static boolean getWalletBitcoin(){

        String request = toRequest(new ArrayList<String>(Arrays.asList(Operation.GET_WALLET_BITCOIN.toString(), thisUser)));
        try{
            out.println(request);

            String reply = in.readLine();
            if ( !reply.equals(Communication.Success.toString()) ){
                System.err.println(Operation.GET_WALLET_BITCOIN + " fallita: " + reply);
                in.readLine(); // Leggo gli attributi, ma li ignoro perché non servono
                return false;
            }

            String walletbtc = "", s = "";
            while ( !( s = in.readLine() ).equals(";") )
                walletbtc = walletbtc + s + "\n";

            // Stampo a video il wallet di thisUser
            System.out.println(walletbtc);

        } catch ( IOException | NullPointerException e ){
            System.err.println("Errore fatale: " + e.getMessage() + ", terminazione");
            System.exit(FAILURE);
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
