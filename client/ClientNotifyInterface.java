package client;

import java.rmi.Remote;
import java.rmi.RemoteException;

// TODO, questa classe serve?
public interface ClientNotifyInterface extends Remote {
    
    public abstract boolean notify(String notify)
    throws RemoteException;

}
