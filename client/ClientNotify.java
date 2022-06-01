package client;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Set;

import shared.ClientNotifyInterface;

// Classe che serve al server per mandare la notifica al client (per effettuare la callback)
public class ClientNotify extends UnicastRemoteObject implements ClientNotifyInterface {

    private Set<String> followers = null;
    private String username = null;

    public ClientNotify(String username, Set<String> followers)
    throws RemoteException {
        this.followers = followers;
        this.username = username;
    }

    public String getUser()
    throws RemoteException{
        return this.username;
    }

    public boolean notify(String notify)
    throws RemoteException{
        // TODO aggiungere o togliere un utente alla lista dei follower lato client
        if ( notify == null )
            return false;

        if ( followers == null )
            // Se il server può invocare questo metodo, significa che lo stub dell'utente
            // era presente, quindi l'utente è attualmente registrato al servizio di notifica
            // e se questa condizione è verifica c'è stato un errore fatale
            throw new RemoteException();

        String[] token = notify.split(";");
        if ( token.length < 2 )
            return false;

        String event = new String(token[0]);
        String follower = new String(token[1]);

        if ( event.equals(FOLLOW)){
            System.out.println("RMIService: Un utente ha iniziato a seguirti");
            followers.add(follower);
        }

        if ( event.equals(UNFOLLOW)){
            System.out.println("RMIService: Un utente ha smesso di seguirti");
            followers.remove(follower);
        }

        return true;
    }
    
}
