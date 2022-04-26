package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

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

    private static final int MINTAGS = 1;
    private static final int MAXTAGS = 5;

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
    
    public static void main(String[] args){
        String thisUser = null; // Nick dell'utente che si logga utilizzando questo client

        try (
            BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        ){
            for (String line = input.readLine(); !line.equals("quit"); line = input.readLine()){
                String[] req = line.split(" ");

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

                        API.register(nickname, psw, tags);
                        break;
                    }
                    case "login":{
                        if ( req.length != 3 ){
                            helpMessage();
                            break;
                        }
                        thisUser = new String(req[1]);
                        String psw = new String(req[2]);
                        
                        API.login(thisUser, psw);
                        break;
                    }
                    case "logout":{
                        if ( thisUser == null ){
                            // TODO messaggio di errore
                            break;
                        }

                        API.logout(thisUser);
                        thisUser = null;
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
                            
                        API.createPost(title, content.toString());
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
                                                            
                            API.addComment(idPost, content.toString());
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
                            API.ratePost(idPost, vote);
                            break;
                            
                        } catch ( NumberFormatException e ){
                            helpMessage();
                            break;
                        }
                    }
                    case "wallet":{
                        if ( req.length == 1 ){
                            // wallet
                            API.getWallet();
                            break;
                        }
                        if ( req[1].equals("btc") && req.length == 2 ){
                            // wallet btc
                            API.getWalletBitcoin();
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
                                API.listFollowers();
                                break;
                            }
                            case "users":{
                                API.listUsers();
                                break;
                            }
                            case "following":{
                                API.listFollowing();
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
                                API.showFeed();
                                break;
                            }
                            case "post":{
                                if ( req.length != 3 ){
                                    helpMessage();
                                    break;
                                }
                                try{
                                    int idPost = Integer.parseInt(req[2]);

                                    API.showPost(idPost);
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

                            API.deletePost(idPost);
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

                            API.rewinPost(idPost);
                            break;
                        } catch ( NumberFormatException e ){
                            helpMessage();
                            break;
                        }
                    }
                    case "blog":{
                        API.viewBlog();
                        break;
                    }
                    case "follow":{
                        if ( req.length != 2 ){
                            helpMessage();
                            break;
                        }
                        String user = new String(req[1]);

                        API.followUser(user);
                        break;
                    }
                    case "unfollow":{
                        if ( req.length != 2 ){
                            helpMessage();
                            break;
                        }
                        String user = new String(req[1]);

                        API.unfollow(user);
                        break;
                    }
                    default:{
                        helpMessage();
                        break;
                    }
                }
            }
            System.out.println("Chiusura del client");


        } catch ( IOException e ){
            System.err.println(e.getMessage());
            System.exit(1);
        }

    }

}
