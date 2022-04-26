package server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.GsonBuilder;

// Classe che si occupa del caricamento e del salvataggio dello stato di Winsome
public class PersistentState {
    File file;
    Gson gson;

    public PersistentState(String filename)
    throws IOException {
        // Apre il file JSON dove è salvato lo stato del server
        // Se non è ancora stato creato, lo crea
        gson = new GsonBuilder().setPrettyPrinting().create();
        this.file = new File(filename);
        if ( !file.exists() && !file.createNewFile() )
            throw new IOException();
    }

    public boolean loadWinsomeState(WinsomeDB db){
        try(
            BufferedReader reader = new BufferedReader(new FileReader(file))
        ){
            Type dbType = new TypeToken<ConcurrentHashMap<String, WinsomeUser>>() {}.getType();
            db = gson.fromJson(reader, dbType);

        } catch ( IOException e ){
            return false;
        }

        return true;
    }

    public boolean saveWinsomeState(WinsomeDB db){
        /**
         * Salvando gli utenti dovrei salvare tutto, perché
         * all'interno della struttura ci sono le password hashate,
         * i riferimenti ai post pubblicati, il portafoglio
         * e i post rewinnati. Ci dovrebbe essere tutto
         */

        String json = gson.toJson(db.getUsers());
        
        // Scrivo il file usando NIO
        try( 
            FileOutputStream fout = new FileOutputStream(file);
            FileChannel out = fout.getChannel();
        ){
            ByteBuffer buf = ByteBuffer.allocate(json.length());
            buf.clear();
            buf.put( json.getBytes() );
            buf.flip();
            while ( buf.hasRemaining() )
                out.write(buf);

        } catch ( IOException e ){
            return false;
        }

        return true;
    }
    
}
