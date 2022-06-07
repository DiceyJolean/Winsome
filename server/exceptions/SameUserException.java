package server.exceptions;

public class SameUserException extends Exception {
    
    public SameUserException(){
        super();
    }

    public SameUserException(String s){
        super(s);
    }

}
