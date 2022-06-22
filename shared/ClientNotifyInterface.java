package shared;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ClientNotifyInterface extends Remote {
    
    public static final String FOLLOW = "FOLLOW";
    public static final String UNFOLLOW = "UNFOLLOW";

    public abstract String getUser()
    throws RemoteException;

    public abstract boolean notify(String notify)
    throws RemoteException;

}
