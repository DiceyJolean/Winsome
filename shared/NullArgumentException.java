package shared;

/**
 * Thrown to indicate that a method has been passed a null argument
 */
public class NullArgumentException extends Exception{

    public NullArgumentException(){
        super();
    }

    public NullArgumentException(String s){
        super(s);
    }
}
