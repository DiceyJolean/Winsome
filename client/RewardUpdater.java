package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;

/**
 * Thread che resta in ascolto sulla porta UDP per ricevere gli aggiornamenti
 * del calcolo delle ricompense di Winsome
 */
@SuppressWarnings("deprecation")
public class RewardUpdater implements Runnable{
    MulticastSocket multicastSocket = null;
    InetAddress address = null;

    public RewardUpdater(String multicastAddress, int multicastPort)
    throws IOException{

        try{
            // Genero l'indirizzo IP di multicast
            address = InetAddress.getByName(multicastAddress);
            if ( !address.isMulticastAddress() ){
                System.err.println("CLIENT: L'indirizzo " + address + " non è un Multicast Address\n");
                return;
            }
        }
        catch ( UnknownHostException e ){
            throw e;
        }
        
        try{
            // Mi collego al gruppo multicast
            multicastSocket = new MulticastSocket(multicastPort);
            multicastSocket.joinGroup(address);
        }
        catch ( Exception e ){
            System.err.printf("CLIENT: Errore unendosi al gruppo multicast - %s\n", e.getMessage());
            throw e;
        }
    }

    public void stop(){
        multicastSocket.close();
    }

    public void run(){
        byte[] buf = new byte[Integer.BYTES];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);

        while ( true ){
            try{
                multicastSocket.receive(packet);
                System.out.println("Calcolo delle ricompense effettuato");
                System.out.flush();
            } catch ( IOException e ){
                // Il client termina e chiude la socket con il metodo stop per permettere a questo thread di terminare a sua volta
                return;
            }
        }
    }

}
