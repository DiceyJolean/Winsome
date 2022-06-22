package client;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Set;

import shared.ClientNotifyInterface;

public class ClientNotify extends UnicastRemoteObject implements ClientNotifyInterface {

    private Set<String> followers = null;
    private String username = null;

    public ClientNotify(String username)
    throws RemoteException {
        this.username = username;
    }

    public String getUser()
    throws RemoteException{
        return this.username;
    }

    public synchronized void setFollowers(Set<String> followers){
        this.followers = followers;
    }

    // Sincronizzo perché la registrazione è già avvenuta, potrebbe capitare che un utente inizi a seguirmi mentre inizializzo la struttura dei follower
    public synchronized boolean notify(String notify)
    throws RemoteException{
        if ( notify == null )
            return false;

        if ( followers == null )
            // Non deve verificarsi mai
            return false;

        // La notifica è nella forma FOLLOW/UNFOLLOW;NomeFollower;
        String[] token = notify.split(";");
        if ( token.length < 2 )
            return false;

        String event = new String(token[0]);
        String follower = new String(token[1]);

        if ( event.equals(FOLLOW)){
            System.out.println("Un utente ha iniziato a seguirti");
            System.out.flush();
            synchronized ( followers ){
                followers.add(follower);
            }
        }

        if ( event.equals(UNFOLLOW)){
            System.out.println("Un utente ha smesso di seguirti");
            System.out.flush();
            synchronized ( followers ){
                followers.remove(follower);
            }
        }

        return true;
    }
    
}
