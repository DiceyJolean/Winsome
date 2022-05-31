package client;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

import shared.ClientNotifyInterface;

// Classe che serve al server per mandare la notifica al client (per effettuare la callback)
public class ClientNotify extends UnicastRemoteObject implements ClientNotifyInterface {

    private String username;

    public ClientNotify(String username)
    throws RemoteException {
        this.username = username;
    }

    public String getUser()
    throws RemoteException{
        return this.username;
    }

    public boolean notify(String notify)
    throws RemoteException{
        // TODO aggiungere o togliere un utente alla lista dei follower lato client

        return true;
    }
    
}
