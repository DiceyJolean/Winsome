package shared;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Set;

public interface RMIServiceInterface extends Remote {

    /**
     * Registrazione di un nuovo utente a Winsome
     * 
     * @param username Username univoco dell'utente
     * @param password Password del nuovo utente
     * @param tags Tra uno e cinque tags che individuano gli interessi dell'utente
     * @return true se la registrazione è andata a buon fine, false altrimenti
     * @throws RemoteException
     */
    public abstract boolean register(String username, String password, Set<String> tags)
    throws RemoteException;

    /**
     * Al momento del login, il client si registra al servizio per ricevere
     * una notifica quando un utente segue o smette di seguire l'utente che
     * si è loggato con questo client
     * 
     * @param user L'utente che si è loggato
     * @return L'insieme dei follower che attualmente ha l'utente che si è loggato, null è un valore valido
     * @throws RemoteException
     */
    public abstract Set<String> registerForCallback(String user)
    throws RemoteException;

    /**
     * Al momento del logout, il client cancella l'utente dal servizio di callback
     * 
     * @param user L'utente che effettua il logout
     * @return true se l'operazione è andata a buon fine, false altrimenti
     * @throws RemoteException
     */
    public abstract boolean unregisterForCallback(String user)
    throws RemoteException;

    /**
     * Invia la notifica del servizio all'utente
     * 
     * @param user Utente da notificare
     * @param notify Contenuto della notifica (follow o unfollow)
     * @return true se 'operazione è andata a buon fine, false altrimenti'
     * @throws RemoteException
     */
    public abstract boolean doCallback(String user, String notify)
    throws RemoteException;
}
