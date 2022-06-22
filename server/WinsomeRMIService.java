package server;

import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.util.HashSet;
import java.util.Set;

import shared.*;

public class WinsomeRMIService extends RemoteObject implements RMIServiceInterface {
    private final static boolean DEBUG = false;

    private Set<ClientNotifyInterface> clients;
    private WinsomeDB db;
    
    public WinsomeRMIService(WinsomeDB db){
        clients = new HashSet<ClientNotifyInterface>();
        this.db = db;
    }

    @Override
    public String register(String username, String password, Set<String> tags)
    throws RemoteException {
        
            for ( String tag : tags )
                tag.toLowerCase();
            WinsomeUser newUser = new WinsomeUser(username, password, tags);
            try{
                db.addUser(newUser);
            } catch ( WinsomeException e ){
                // Nickname già in uso
                return e.getMessage();
            }       

            return Communication.Success.toString();
    }

    @Override
    // TODO Il synch è per clients, ma la getFollower è una sezione critica
    public synchronized Set<String> registerForCallback(ClientNotifyInterface user)
    throws RemoteException {
        clients.add(user);
        if ( DEBUG ) System.out.println("RMIService: Aggiunto nuovo utente \"" + user.getUser() + "\" al servizio di notifica");

        try{
            // TODO followers di user com'è sincronizzata? getFoll restituisce una copia, e nel frattempo è sincronizzata
            Set<String> followers = db.getUsers().get(user.getUser()).getFollower();
            return followers;
        } catch ( Exception e ){
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public synchronized boolean unregisterForCallback(ClientNotifyInterface user)
    throws RemoteException {

        if ( DEBUG ) System.out.println("RMIService: Rimuovo un utente \"" + user.getUser() + "\" a Winsome");
        return clients.remove(user);
    }

    // La doCallback la chiama il server, che è in multiplexing, quindi non è una sezione critica
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
        
        /*
        se user non è connesso la notifica non verrà inviata perché non
        si è ancora registrato alle callback o si è de-registrato al logout
        */
        
        return false;
    }
    
}
