package server;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

// Classe che si occupa del caricamento e del salvataggio dello stato di Winsome
public class WinsomeState implements Runnable {
    private WinsomeDB db;
    private File file;
    private long period;
    private volatile boolean toStop = false;

    public WinsomeState(String filename, WinsomeDB db, long period)
    throws IOException {
        this.db = db;
        this.period = period;

        // Apre il file JSON dove è salvato lo stato del server
        // Se non è ancora stato creato, lo crea
        this.file = new File(filename);
        if ( !file.exists() )
            file.createNewFile();
    }

    public void Stop(){
        toStop = true;
    }

    public void run(){
        while ( !toStop ){
            try{
                Thread.sleep(period);

                // Salvo lo stato attuale del Database su un nuovo file
                System.out.println("DATABASE: Autosalvataggio in corso...");
                if ( updateWinsomeState() )
                    System.out.println("DATABASE: Autosalvataggio completato!");

            } catch ( InterruptedException e ){
                e.printStackTrace();
            }
        }
    }

    public boolean updateWinsomeState(){
        WinsomeDB copy = db.getDBCopy();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(copy);
        File uptadedDB = new File("New_DataBase.json");

        try(
            PrintWriter out = new PrintWriter(uptadedDB);
        ){
            uptadedDB.createNewFile();
            out.write(json);
        } catch ( IOException e ){
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public WinsomeDB loadWinsomeState(){
        // Se il file contiene del testo allora provo a recuperare lo stato di Winsome
        WinsomeDB database = new WinsomeDB();

        if ( file.length() != 0 ){
            try{
                Gson gson = new Gson();
                // TODO da java 11
                String tmp = Files.readString(file.toPath());

                WinsomeUser[] users = gson.fromJson(tmp, WinsomeUser[].class);
                for ( WinsomeUser user : users ){
                    System.out.println("JSON: Sto stampando un utente winsome");
                    database.addUser(user);
                    System.out.println(user.toPrint());
                }

            } catch ( IOException e ){
                e.printStackTrace();
                return null;
            }
        }

        System.out.println("DATABASE: Stato di Winsome ripristinato correttamente");
        this.db = database;
        return database;
    }
    
}
