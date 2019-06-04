package com.eveningoutpost.dexdrip.insulin.watlaa.messages;

public class WatlaaEvent {
    public static final String CALLIBRATION = "CALLIBRATION";
    public static final String UNITS = "UNITS";

    private String type;
    private String value;
    private Double low;
    private Double height;

    public WatlaaEvent(String type,String value){
        this.type=type;
        this.value=value;
    }
    public WatlaaEvent(String type,Double low,Double height){
        this.type=type;
        this.height=height;
        this.low=low;
    }

    public Double getLow() {
        return low;
    }

    public Double getHeight() {
        return height;
    }

    public String getType() {
        return type;
    }

    public String getValue() {
        return value;
    }
}
