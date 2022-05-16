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
            WinsomeUser newUser = new WinsomeUser(username, password, tags);
            if ( DEBUG ) System.out.println("RMIService: Aggiunto nuovo utente \"" + newUser.toString() + "\" a Winsome");

            if ( DEBUG ){
                // Aggiungo follower fittizi
                db.addUser(newUser);
                db.getUsers().get(username).addFollower("UnFollower");

            }

            return true; // db.addUser(newUser);
        } catch ( NullArgumentException e ){
            return false;
        }
    }

    @Override
    public Set<String> registerForCallback(ClientNotifyInterface user)
    throws RemoteException {
        clients.add(user);
        if ( DEBUG ) System.out.println("RMIService: Aggiunto nuovo utente \"" + user.getUser() + "\" al servizio di notifica");

        Set<String> toReturn = db.getUsers().get(user.getUser()).getFollower();
        try{
            db.getUsers().get(user.getUser()).addFollower("NewFollower");
            doCallback(user.getUser(), "Notifica");
        } catch ( NullArgumentException e ){
            return null;
        }

        return toReturn;
    }

    @Override
    public boolean unregisterForCallback(ClientNotifyInterface user)
    throws RemoteException {

        if ( DEBUG ) System.out.println("RMIService: Rimuovo un utente \"" + user.getUser() + "\" a Winsome");
        return clients.remove(user);
    }

    public boolean doCallback(String user, String notify)
    throws RemoteException {
        for ( ClientNotifyInterface stub : clients )
            // Ok equals perché estendo RemoteObject
            if ( stub.getUser().equals(user) ){
                
                if ( DEBUG ) System.out.println("RMIService: Invio una notifica a un utente \"" + user + "\"");

                stub.notify(notify);
                return true;
            }
        
        return false;        
    }
    
}
