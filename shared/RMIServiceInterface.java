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
     * @return Un messaggio che indica l'esito dell'operazione
     * @throws RemoteException
     */
    public abstract String register(String username, String password, Set<String> tags)
    throws RemoteException;

    public abstract Set<String> registerForCallback( ClientNotifyInterface user)
    throws RemoteException;

    /**
     * Al momento del logout, il client cancella l'utente dal servizio di callback
     * 
     * @param user L'utente che effettua il logout
     * @return true se l'operazione è andata a buon fine, false altrimenti
     * @throws RemoteException
     */
    public abstract boolean unregisterForCallback(ClientNotifyInterface user)
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
