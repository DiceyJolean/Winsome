package server;

import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.util.HashSet;
import java.util.Set;

import shared.*;

public class WinsomeRMIService extends RemoteObject implements RMIServiceInterface {
    private final static boolean DEBUG = true;

    private Set<ClientNotifyInterface> clients;
    private WinsomeDB db;
    
    public WinsomeRMIService(WinsomeDB db){
        clients = new HashSet<ClientNotifyInterface>();
        this.db = db;
    }

    @Override
    public boolean register(String username, String password, Set<String> tags)
    throws RemoteException {
        try{
            for ( String tag : tags )
                tag.toLowerCase();
            WinsomeUser newUser = new WinsomeUser(username, password, tags);
            if ( !db.addUser(newUser) ){
                if ( DEBUG ) System.out.println("RMI register: inserimento di " + username + " fallito");
                return false;
            }

            return true;
        } catch ( Exception e ){
            return false;
        }
    }

    @Override
    public Set<String> registerForCallback( ClientNotifyInterface user)
    throws RemoteException {
        clients.add(user);
        if ( DEBUG ) System.out.println("RMIService: Aggiunto nuovo utente \"" + user.getUser() + "\" al servizio di notifica");

        try{
            Set<String> followers = db.getUsers().get(user.getUser()).getFollower();
            return followers;
        } catch ( Exception e ){
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean unregisterForCallback(ClientNotifyInterface user)
    throws RemoteException {

        if ( DEBUG ) System.out.println("RMIService: Rimuovo un utente \"" + user.getUser() + "\" a Winsome");
        return clients.remove(user);
    }

    public boolean doCallback(String user, String notify)
    throws RemoteException {
        // Cerco lo stub dell'utente che dovrà ricevere la notifica
        for ( ClientNotifyInterface stub : clients )
            // Ok equals perché estendo RemoteObject
            if ( stub.getUser().equals(user) ){
                
                if ( DEBUG ) System.out.println("RMIService: Invio una notifica a un utente \"" + user + "\"");

                stub.notify(notify);
                return true;
            }
        /**
         * se user non è connesso la notifica non verrà inviata
         * perché non si è ancora registrato alle callback, o si è de-registrato al logout
         */
        
        return false;        
    }
    
}
