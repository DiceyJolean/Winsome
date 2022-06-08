package shared;

public enum Communication {
    Success("Successo"),
    AlreadyLogged("L'utente ha già effettuato il login"),
    SameUser("Non è possibile "),
    WrongCredential("La password o l'username sono errati"),
    NotLogged("L'utente non ha effettuato il login"),
    Failure("Errore generico");

    private String message;

    private Communication(String s){
        message = s;
    }

    public String toString(){
        return message;
    }
}
