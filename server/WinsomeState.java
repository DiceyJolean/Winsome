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

/**
 *  Classe che si occupa del caricamento e del salvataggio periodico dello stato di Winsome
 */
public class WinsomeState extends Thread {
    private WinsomeDB db; // Puntatore al database di Winsome
    private File file; // File dove recuperare e salvare lo stato di Winsome
    private String filename; // Nome del file dove recuperare e salvare lo stato di Winsome
    private long period; // Periodo ogni quanto effettuare il salvataggio dello stato di Winsome
    private volatile boolean toStop = false; // Variabile per la terminazione del thread

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

    public void terminate(){
        toStop = true;
        this.interrupt();
    }

    public void run(){
        try{
            while ( !toStop ){
                // Finché il thread non viene interrotto, periodicamente salva lo stato di Winsome
                Thread.sleep(period);

                // Salvo lo stato attuale del Database su un nuovo file
                System.out.println("BACKUP: Autosalvataggio in corso...");
                if ( updateWinsomeState() )
                    System.out.println("BACKUP: Autosalvataggio completato!");
            }

        } catch ( InterruptedException e ){
            System.out.println("BACKUP: Thread interrotto, in chiusura...");
        } finally {
            updateWinsomeState();
            System.out.println("BACKUP: Terminazione");
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
                // Il meodo readString è presente da java 11
                String tmp = Files.readString(file.toPath());

                Type gsonType = new TypeToken<Map<String, WinsomeUser>>(){}.getType();
                database.loadDatabase( gson.fromJson(tmp, gsonType) );

            } catch ( IOException e ){
                e.printStackTrace();
                return null;
            }
        }

        System.out.println("BACKUP: Stato di Winsome ripristinato correttamente");
        this.db = database;
        return database;
    }
    
}
