package server;

import java.util.Date;

/**
 * Classe che rappresenta un elemento nello storico del portafoglio di un utente Winsome
 */
public class WinsomeWallet {
    private Date date; // Data in cui avviene l'aggiornamento del portafoglio
    private Double value; // Valore del nuovo incremento del portafoglio

    public WinsomeWallet(Date date, Double value) {
        this.date = date;
        this.value = value;
    }

    public Date getDate() {
        return date;
    }

    public Double getValue() {
        return value;
    }
}
