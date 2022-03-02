package server;

import java.util.HashSet;
import java.util.Set;

import org.mindrot.jbcrypt.BCrypt;

public class WinsomeUser {
    
    private String nickname;
    private String psw;
    private Set<String> follower;
    private Set<String> following;
    private int loggedIn;
    private Set<String> tag;
    private Set<Integer> postRewinned; // I post sono indicati univocamente dal loro postID
    private Set<WinsomePost> blog;

    public WinsomeUser(String nickname, String psw, Set<String> tags){
        if ( tags.size() < 1 || tags.size() > 5 )
            throw new IndexOutOfBoundsException();

        if ( nickname == null || psw == null )
            throw new IllegalArgumentException();

        String salt = BCrypt.gensalt();
        String hashedPsw = BCrypt.hashpw(psw, salt);

        this.psw = hashedPsw;
        this.nickname = nickname;
        this.follower = new HashSet<String>();
        this.following = new HashSet<String>();
        this.loggedIn = 0;
        this.postRewinned = new HashSet<Integer>();
        this.blog = new HashSet<WinsomePost>();
        this.tag = new HashSet<String>();
        this.tag.addAll(tags);
    }

    // TODO return value?
    public int login(String psw){
        if ( BCrypt.checkpw(psw, this.psw) )
            this.loggedIn = 1;

        return this.loggedIn;
    }

    

}
