package server;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Set;

import shared.*;

public class WinsomeRMIService implements RMIServiceInterface {

    private HashMap<String, ClientNotify> clients;
    private WinsomeDB db;
    
    public WinsomeRMIService(WinsomeDB db){
        clients = new HashMap<String, ClientNotify>();
        this.db = db;
    }

    @Override
    public boolean register(String username, String password, Set<String> tags)
    throws RemoteException {

        return true;
    }

    @Override
    public Set<String> registerForCallback(String user)
    throws RemoteException {
        clients.put(user, new ClientNotify());

        Set<String> followers = db.getUsers().get(user).getFollower();
        return followers;
    }

    @Override
    public boolean unregisterForCallback(String user)
    throws RemoteException {
        if ( clients.remove(user) == null )
            return false;

        return true;
    }

    public boolean doCallback(String user, String notify)
    throws RemoteException {
        ClientNotify tmp = clients.get(user);
        if ( tmp == null )
            return false;
        
        tmp.notify(notify);
        return true;
    }
    
}
