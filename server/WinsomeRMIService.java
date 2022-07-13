package server;

import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.util.HashSet;
import java.util.Set;

import shared.*;

public class WinsomeRMIService extends RemoteObject implements RMIServiceInterface {
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
    public synchronized Set<String> registerForCallback(ClientNotifyInterface user)
    throws RemoteException {
        clients.add(user);

        try{
            // Non è sezione critica perché getFollower è synchronized e al client restituisce una copia
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

        return clients.remove(user);
    }

    // La doCallback la chiama il server, che è in multiplexing, quindi non è una sezione critica
    public boolean doCallback(String user, String notify)
    throws RemoteException {
        // Cerco lo stub dell'utente che dovrà ricevere la notifica
        for ( ClientNotifyInterface stub : clients )
            // Ok equals perché estendo RemoteObject
            if ( stub.getUser().equals(user) ){
                
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
