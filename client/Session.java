package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Set;

public class Session implements ClientAPI{

    private String thisUser = null;
    private boolean logged = false;
    private BufferedReader in;
    private PrintWriter out;

    public Session (String user, Socket serverSocket)
    throws IOException {
        this.thisUser = user;
        try{
            out = new PrintWriter( serverSocket.getOutputStream() );
            in = new BufferedReader( new InputStreamReader( serverSocket.getInputStream() ));
        } catch ( IOException e ){
            throw new IOException();
        }
    }

    @Override
    public boolean register(String username, String password, Set<String> tags)
    throws IOException {
        if ( !username.equals(thisUser) )
            // Voglio evitare inconsistenze
            return false;

        String msg = new String("REGISTER\t username: " + username + ", password: " + password + ", tags: " + tags.toString());
        try{
            out.write(msg);
            System.out.println("Attendo la risposta dal server");
            int code = Integer.parseInt( in.readLine() );
            System.out.println("Leggo lo status code: " + code);
            String status = in.readLine();
            System.out.println("Ricevo la status line: " + status);
        } catch ( IOException e ){
            throw new IOException();
        }
        
        return false;
    }

    @Override
    public boolean login(String username, String password) {
        if ( !username.equals(thisUser) || logged )
            // Voglio evitare inconsistenze
            return false;
        
        return false;
    }

    @Override
    public boolean logout(String username) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean listUsers() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean listFollowers() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean listFollowing() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean followUser(String idUser) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean unfollow(String idUser) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean viewBlog() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean createPost(String title, String content) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean showFeed() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean showPost(int idPost) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean deletePost(int idPost) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean rewinPost(int idPost) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean ratePost(int idPost, int vote) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean addComment(int idPost, String content) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean getWallet() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean getWalletBitcoin() {
        // TODO Auto-generated method stub
        return false;
    }



}