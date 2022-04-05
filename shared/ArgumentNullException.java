package shared;

/**
 * Thrown to indicate that a method has been passed a null argument
 */
public class ArgumentNullException extends Exception{

    public ArgumentNullException(){
        super();
    }

    public ArgumentNullException(String s){
        super(s);
    }
}
