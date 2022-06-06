package server;

import java.util.Date;

public class WinsomeWallet {
    private Date date;
    private Double value;

    public WinsomeWallet(Date key, Double value) {
        this.date = key;
        this.value = value;
    }

    public Date getKey() {
        return date;
    }

    public Double getValue() {
        return value;
    }
}
