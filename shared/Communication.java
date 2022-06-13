package shared;

public enum Communication {
    Success("Successo"),
    AlreadyLogged("L'utente ha già effettuato il login"),
    SameUser("Non è possibile seguire/essere seguiti da/commentare un post di/votare un post di se stessi"),
    WrongCredential("La password o l'username sono errati"),
    NotLogged("L'utente non ha effettuato il login"),
    EmptySet("L'insieme è vuoto"),
    OperationNotSupported("L'operazione non è supportata dal server"),
    Failure("Errore generico del server");

    private String message;

    private Communication(String s){
        message = s;
    }

    public String toString(){
        return message;
    }
}
