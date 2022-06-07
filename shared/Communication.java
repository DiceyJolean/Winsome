package shared;

public enum Communication {
    Success("Successo"),
    AlreadyLogged("L'utente ha già effettuato il login"),
    SameUser("Non è possibile "),
    WrongCredential(""),
    NotLogged(""),
    Failure("");

    private String message;

    private Communication(String s){
        message = s;
    }

    public String toString(){
        return message;
    }
}
