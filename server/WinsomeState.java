package server;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Map;
import java.lang.reflect.Type;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

// Classe che si occupa del caricamento e del salvataggio dello stato di Winsome
public class WinsomeState implements Runnable {
    private WinsomeDB db;
    private File file;
    private String filename;
    private long period;
    private volatile boolean toStop = false;

    public WinsomeState(String filename, WinsomeDB db, long period)
    throws IOException {
        this.db = db;
        this.period = period;
        this.filename = filename;

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
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = null;
        db.lock.readLock().lock();
        // Sincronizzo, invece di lavorare su una copia, perché il metodo toJson probabilmente sarà più veloce che fare la copia
        json = gson.toJson(db.getUsers());
        // Perché mi servono le lock se users è concurrent? Perché con la get ci accedo in lettura, così come quando faccio modifiche ai singoli user
        db.lock.readLock().unlock();
        File uptadedDB = new File(filename);

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

                Type gsonType = new TypeToken<Map<String, WinsomeUser>>(){}.getType();
                database.loadDatabase( gson.fromJson(tmp, gsonType) );

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
