package com.example.taller1aruitectura.Sensor;


public class SensorServ {

    private String tipo;
    private SensorData valor;

    public SensorData getValor() {
        return valor;
    }

    public String getTipo() {
        return tipo;
    }

    public SensorServ(TipoSensor tipo, SensorData valor){
        this.valor = valor;
        this.tipo = tipo.name();
    }
}
