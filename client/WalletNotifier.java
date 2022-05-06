package client;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.concurrent.atomic.AtomicBoolean;

// Thread che sta in ascolto della notifica per l'aggiornamento del portafoglio
public class WalletNotifier implements Runnable {

    private MulticastSocket socket;
    private InetAddress address;
    private int port;
    private volatile AtomicBoolean stop;
    
    public WalletNotifier(String address, int port){
        stop = new AtomicBoolean(false);
        try{
            this.address = InetAddress.getByName(address);
            if ( !this.address.isMulticastAddress() || port < 1024 || port > 65535 )
                throw new IllegalArgumentException();

            this.port = port;
        } catch ( Exception e ){
            throw new IllegalArgumentException();
        }
    }

    public boolean terminate(){
        stop.set(true);
        return true;
    }

    @Override
    public void run(){
        try{
            socket = new MulticastSocket(port);
            socket.joinGroup(address);
            // Finché non ricevo ordine di terminare
            while ( !stop.get() ){

                // Preparo il pacchetto per la ricezione
                byte[] buf = new byte[Integer.BYTES];
                DatagramPacket packet = new DatagramPacket(buf, Integer.BYTES);
                socket.receive(packet);
                System.out.println(new String(packet.getData()));
            }

        } catch ( Exception e ){
            // TODO errore fatale, terminazione
            e.getMessage();
            System.exit(1);
        }
    }
}
