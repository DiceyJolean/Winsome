package server;

/**
 * Eccezione specifica per Winsome, è sempre necessario specificare il messaggio con le cause
 */
public class WinsomeException extends Exception {

    // Non è presente il costruttore senza parametri

    public WinsomeException(String message){
        super(message);
    }
}
