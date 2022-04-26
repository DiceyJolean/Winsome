package client;

import java.util.Set;

public class API {

    public static boolean register(String username, String password, Set<String> tags){
        System.out.println("REGISTER\t username: " + username + ", password: " + password + ", tags: " + tags.toString());

        return true;
    }

    public static boolean login(String username, String password){
        System.out.println("LOGIN\t username: " + username + ", password: " + password);

        return true;
    }

    public static boolean logout(String username){
        System.out.println("LOGOUT\t username: " + username);

        return true;
    }

    public static boolean listUsers(){
        System.out.println("LIST_USERS");

        return true;
    }

    public static boolean listFollowers(){
        System.out.println("LIST_FOLLOWERS");

        return true;
    }

    public static boolean listFollowing(){
        System.out.println("LIST_FOLLOWING");

        return true;
    }

    public static boolean followUser(String idUser){
        System.out.println("FOLLOW_USER\t idUser: " + idUser);

        return true;
    }

    public static boolean unfollow(String idUser){
        System.out.println("UNFOLLOW\t idUSer: " + idUser);
        
        return true;
    }

    public static boolean viewBlog(){
        System.out.println("VIEW_BLOG");

        return true;
    }

    public static boolean createPost(String title, String content){
        System.out.println("CREATE_POST\t title: " + title + ", content: " + content);

        return true;
    }

    public static boolean showFeed(){
        System.out.println("SHOW_FEED");

        return true;
    }

    public static boolean showPost(int idPost){
        System.out.println("SHOW_POST\t idPost: " + idPost);

        return true;
    }

    public static boolean deletePost(int idPost){
        System.out.println("DELETE_POST\t idPost: " + idPost);

        return true;
    }

    public static boolean rewinPost(int idPost){
        System.out.println("REWIN_POST\t idPost: " + idPost);

        return true;
    }

    public static boolean ratePost(int idPost, int vote){
        System.out.println("RATE_POST\t idPost: " + idPost + ", vote: " + vote);

        return true;
    }

    public static boolean addComment(int idPost, String content){
        System.out.println("ADD_COMMENT\t idPost: " + idPost + ", content: " + content);

        return true;
    }

    public static boolean getWallet(){
        System.out.println("GET_WALLET");

        return true;
    }

    public static boolean getWalletBitcoin(){
        System.out.println("GET_WALLET_BITCOIN");

        return true;
    }

    
}
