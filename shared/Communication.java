package shared;

/**
 * Classe che gestisce la varie casistiche di risposta dal server al client.
 * Anche se non è stata grandemente utilizzata, ho preferito lasciarla per eventuali modifiche
 */
public enum Communication {
    Success("200 OK"),
    AlreadyLogged("L'utente ha già effettuato il login"),
    SameUser("Non è possibile seguire/essere seguiti da/commentare un post di/votare un post di se stessi"),
    WrongCredential("La password o l'username sono errati"),
    NotLogged("L'utente non ha effettuato il login"),
    EmptySet("L'insieme è vuoto"),
    OperationNotSupported("L'operazione non è supportata dal server"),
    Failure("400 Errore generico del server");

    private String message;

    private Communication(String s){
        message = s;
    }

    public String toString(){
        return message;
    }
}
