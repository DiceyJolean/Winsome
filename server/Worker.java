package server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;

public class Worker implements Runnable{
    public String op;
    private Socket task;
    
    public Worker(Socket task){
        this.task = task;
    }

    @Override
    public void run(){
        try(
            BufferedReader read = new BufferedReader(new InputStreamReader(task.getInputStream()));
        ){
            
        } catch ( Exception e ){

        }
    }
}
