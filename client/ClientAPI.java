package client;

import java.io.IOException;
import java.util.Set;

public interface ClientAPI {
    public abstract boolean register(String username, String password, Set<String> tags)
    throws IOException;

    public abstract boolean login(String username, String password);

    public abstract boolean logout(String username);

    public abstract boolean listUsers();

    public abstract boolean listFollowers();

    public abstract boolean listFollowing();

    public abstract boolean followUser(String idUser);

    public abstract boolean unfollow(String idUser);

    public abstract boolean viewBlog();

    public abstract boolean createPost(String title, String content);

    public abstract boolean showFeed();

    public abstract boolean showPost(int idPost);

    public abstract boolean deletePost(int idPost);

    public abstract boolean rewinPost(int idPost);

    public abstract boolean ratePost(int idPost, int vote);

    public abstract boolean addComment(int idPost, String content);

    public abstract boolean getWallet();

    public abstract boolean getWalletBitcoin();

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
