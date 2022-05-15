package shared;

import java.rmi.RemoteException;

// Classe che serve al server per mandare la notifica al client (per effettuare la callback)
public class ClientNotify implements ClientNotifyInterface {

    public boolean notify(String notify)
    throws RemoteException{


        return false;
    }
    
}
