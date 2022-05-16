package client;

import java.rmi.RemoteException;

import shared.ClientNotifyInterface;

// Classe che serve al server per mandare la notifica al client (per effettuare la callback)
public class ClientNotify implements ClientNotifyInterface {
    private final static boolean DEBUG = true;

    private String username;

    public ClientNotify(String username){
        this.username = username;
    }

    public String getUser(){
        return this.username;
    }

    public boolean notify(String notify)
    throws RemoteException{
        if ( DEBUG ) System.out.println("Ricevuta una notifica \""+ notify +"\"");


        return true;
    }
    
}
